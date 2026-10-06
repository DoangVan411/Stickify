package com.jetpack.stickify.presentation.ui.edit_gif

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.jetpack.stickify.R
import com.jetpack.stickify.data.gif.AnimatedGifEncoder
import com.jetpack.stickify.databinding.ActivityGifEditBinding
import com.jetpack.stickify.domain.model.TextAlign
import com.jetpack.stickify.presentation.ui.edit_sticker.StickerSharedViewModel
import com.jetpack.stickify.presentation.ui.edit_sticker.cancel.SaveConfirmDialogFragment
import com.jetpack.stickify.presentation.ui.edit_sticker.custom_view.EditorPanelView
import com.jetpack.stickify.presentation.ui.edit_sticker.custom_view.TabItem
import com.jetpack.stickify.presentation.ui.edit_sticker.decor.DecorToolFragment
import com.jetpack.stickify.presentation.ui.edit_sticker.text.TextInputOverlayController
import com.jetpack.stickify.presentation.ui.edit_sticker.text.TextStyleSpec
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Màn hình Chỉnh sửa GIF (Màn 2):
 * - Phát loop đoạn video đã cắt từ VideoTrimActivity trên TextureView
 * - Cho phép thêm trang trí (vẽ cọ họa tiết, thêm sticker/nhãn, thêm ảnh tùy chỉnh) giống edit sticker
 * - Cho phép thêm chữ & điều chỉnh tốc độ phát GIF
 * - Hỗ trợ đầy đủ Undo / Redo cho mọi thao tác trang trí
 * - Nút "Tạo" xuất ảnh động GIF hoàn chỉnh
 */
@AndroidEntryPoint
class GifEditActivity : AppCompatActivity(), TextureView.SurfaceTextureListener {

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_START_MS = "extra_start_ms"
        const val EXTRA_END_MS = "extra_end_ms"
        const val EXTRA_RESULT_GIF_URI = "extra_result_gif_uri"
    }

    private lateinit var binding: ActivityGifEditBinding
    private val sharedViewModel: StickerSharedViewModel by viewModels()

    private var videoUri: Uri? = null
    private var startTrimMs: Long = 0L
    private var endTrimMs: Long = 8000L

    private var mediaPlayer: MediaPlayer? = null
    private var surface: Surface? = null
    private var isSurfaceReady = false

    private var videoWidth = 0
    private var videoHeight = 0
    private var currentSpeed = 1.0f

    private var playbackLoopJob: Job? = null
    private var decorToolFragment: DecorToolFragment? = null
    private lateinit var textController: TextInputOverlayController
    private var previousTabIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_gif_edit)

        val uriExtra: Uri? = intent.getParcelableExtra(EXTRA_VIDEO_URI)
        val uriString = intent.getStringExtra(EXTRA_VIDEO_URI)
        videoUri = uriExtra ?: uriString?.let { Uri.parse(it) }
        startTrimMs = intent.getLongExtra(EXTRA_START_MS, 0L)
        endTrimMs = intent.getLongExtra(EXTRA_END_MS, 8000L)

        if (videoUri == null) {
            Toast.makeText(this, "Không tìm thấy video đầu vào", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupCanvas()
        setupClickListeners()
        setupObservers()
        setupTextOverlay()
        setupEditorTabs()
        setupSpeedControls()
        setupBackPressHandler()
    }

    private fun setupCanvas() {
        binding.textureView.surfaceTextureListener = this
        // Canvas ZoomableStickerView làm lớp phủ trong suốt trên video
        binding.zoomableView.showCheckerBackground = false

        binding.zoomableView.onHistoryChanged = { canUndo, canRedo ->
            updateUndoRedoButtons(canUndo, canRedo)
        }
        updateUndoRedoButtons(canUndo = false, canRedo = false)
    }

    private fun updateUndoRedoButtons(canUndo: Boolean, canRedo: Boolean) {
        binding.btnUndo.isEnabled = canUndo
        binding.btnUndo.alpha = if (canUndo) 1f else 0.35f
        binding.btnRedo.isEnabled = canRedo
        binding.btnRedo.alpha = if (canRedo) 1f else 0.35f
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { showSaveConfirmationDialog() }

        binding.btnUndo.setOnClickListener {
            binding.zoomableView.undo()
        }

        binding.btnRedo.setOnClickListener {
            binding.zoomableView.redo()
        }

        binding.btnCreate.setOnClickListener {
            exportGif()
        }
    }

    private fun showSaveConfirmationDialog() {
        // Tránh mở trùng Dialog nếu nó đang hiển thị sẵn trên màn hình
        if (supportFragmentManager.findFragmentByTag(SaveConfirmDialogFragment.TAG) != null) {
            return
        }

        SaveConfirmDialogFragment.newInstance()
            .show(supportFragmentManager, SaveConfirmDialogFragment.TAG)
    }

    private fun setupObservers() {
        // Nhận sự kiện thêm Decor từ DecorToolFragment
        sharedViewModel.addedDecorEvent.observe(this) { decor ->
            if (decor != null) {
                binding.zoomableView.setDrawDecorBrush(null)
                val bitmap = decor.customBitmap ?: BitmapFactory.decodeResource(resources, decor.resId)
                if (bitmap != null) {
                    binding.zoomableView.addDecorBitmap(bitmap, decor.id)
                }
            }
        }

        // Nhận sự kiện chọn cọ vẽ trang trí
        sharedViewModel.drawDecorModeEvent.observe(this) { decor ->
            if (decor == null) {
                binding.zoomableView.setDrawDecorBrush(null)
            } else {
                val bitmap = decor.customBitmap ?: runCatching {
                    BitmapFactory.decodeResource(resources, decor.resId)
                }.getOrNull() ?: ContextCompat.getDrawable(this, decor.resId)?.toBitmap()
                binding.zoomableView.setDrawDecorBrush(bitmap, decor.id)
            }
        }
    }

    private fun setupTextOverlay() {
        textController = TextInputOverlayController(binding.addTextLayout) { result ->
            if (result != null) {
                binding.zoomableView.addTextItem(
                    text = result.text,
                    textColor = result.colorArgb,
                    existingId = result.layerId,
                    style = result.style,
                    align = result.align
                )
            }
            binding.editorPanel.selectTab(previousTabIndex)
        }

        binding.zoomableView.onTextDecorDoubleTapped = { decor ->
            textController.showEdit(
                layerId = decor.id,
                text = decor.textContent ?: "",
                colorArgb = decor.textColor ?: Color.WHITE,
                align = decor.textAlign ?: TextAlign.CENTER,
                style = decor.textStyle ?: TextStyleSpec.DEFAULT
            )
        }
    }

    private fun setupEditorTabs() {
        val gifTabs = listOf(
            TabItem("decor", getString(R.string.tool_decor), R.drawable.ic_decore),
            TabItem("text", getString(R.string.tool_text), R.drawable.ic_text),
            TabItem("speed", getString(R.string.tool_speed), R.drawable.ic_border)
        )

        binding.editorPanel.setOnTabSelectedListener(object : EditorPanelView.OnTabSelectedListener {
            override fun onTabSelected(position: Int, tabName: String) {
                if (tabName != getString(R.string.tool_text) && tabName != "Chữ") {
                    previousTabIndex = position
                }
                switchTool(tabName)
            }
        })

        binding.editorPanel.setTabs(gifTabs, defaultPosition = 0)
        switchTool(getString(R.string.tool_decor))
    }

    private fun switchTool(tabName: String) {
        if (tabName != getString(R.string.tool_decor) && tabName != "Trang trí") {
            binding.zoomableView.setDrawDecorBrush(null)
            sharedViewModel.clearDrawDecor()
        }

        val fm = supportFragmentManager
        val ft = fm.beginTransaction()

        when (tabName) {
            getString(R.string.tool_decor), "Trang trí" -> {
                binding.speedControlContainer.visibility = View.GONE
                binding.featureContainer.visibility = View.VISIBLE
                if (decorToolFragment == null) {
                    decorToolFragment = DecorToolFragment()
                    ft.add(R.id.featureContainer, decorToolFragment!!, "DECOR_TOOL")
                } else {
                    ft.show(decorToolFragment!!)
                }
            }
            getString(R.string.tool_text), "Chữ" -> {
                textController.showNew()
            }
            getString(R.string.tool_speed), "Tốc độ" -> {
                decorToolFragment?.let { ft.hide(it) }
                binding.featureContainer.visibility = View.GONE
                binding.speedControlContainer.visibility = View.VISIBLE
            }
            else -> {
                decorToolFragment?.let { ft.show(it) }
            }
        }
        ft.commit()
    }

    private val speedValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.5f, 2.0f)
    private val speedLabels = arrayOf("0.5x", "0.75x", "1.0x", "1.5x", "2.0x")

    private fun setupSpeedControls() {
        val speedTextViews = listOf(
            binding.tvSpeed05,
            binding.tvSpeed075,
            binding.tvSpeed10,
            binding.tvSpeed15,
            binding.tvSpeed20
        )

        fun updateSpeedDisplay(index: Int) {
            val validIndex = index.coerceIn(0, speedValues.size - 1)
            val speed = speedValues[validIndex]
            currentSpeed = speed

            binding.tvCurrentSpeed.text = speedLabels[validIndex]

            for (i in speedTextViews.indices) {
                if (i == validIndex) {
                    speedTextViews[i].setTextColor(ContextCompat.getColor(this, R.color.colorPrimary))
                    speedTextViews[i].setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    speedTextViews[i].setTextColor(Color.parseColor("#7A7A7A"))
                    speedTextViews[i].setTypeface(null, android.graphics.Typeface.NORMAL)
                }
            }
            applyPlaybackSpeed(speed)
        }

        binding.sbSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateSpeedDisplay(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Cho phép bấm vào chữ mốc bên dưới thanh kéo để nhảy đến tốc độ tương ứng
        for (i in speedTextViews.indices) {
            speedTextViews[i].setOnClickListener {
                binding.sbSpeed.progress = i
            }
        }

        // Khởi tạo mặc định: 1.0x (vị trí index 2)
        binding.sbSpeed.progress = 2
        updateSpeedDisplay(2)
    }

    private fun applyPlaybackSpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.let { player ->
                try {
                    val params = player.playbackParams ?: PlaybackParams()
                    params.speed = speed
                    player.playbackParams = params
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::textController.isInitialized && textController.isShowing) {
                    textController.commit()
                } else {
                    finish()
                }
            }
        })
    }

    private fun initMediaPlayer() {
        val uri = videoUri ?: return
        if (!isSurfaceReady || surface == null) return

        binding.progressBar.visibility = View.VISIBLE
        mediaPlayer?.release()
        try {
            val player = MediaPlayer().apply {
                setDataSource(this@GifEditActivity, uri)
                setSurface(surface)
                isLooping = false
                setOnPreparedListener { mp ->
                    binding.progressBar.visibility = View.GONE
                    this@GifEditActivity.videoWidth = mp.videoWidth
                    this@GifEditActivity.videoHeight = mp.videoHeight

                    adjustTextureViewSize(this@GifEditActivity.videoWidth, this@GifEditActivity.videoHeight)
                    setupZoomableBaseSize(this@GifEditActivity.videoWidth, this@GifEditActivity.videoHeight)

                    applyPlaybackSpeed(currentSpeed)
                    mp.seekTo(startTrimMs.toInt())
                    mp.start()
                    startPlaybackLoopMonitor()
                }
                setOnErrorListener { _, _, _ ->
                    binding.progressBar.visibility = View.GONE
                    false
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this@GifEditActivity, "Lỗi phát video: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupZoomableBaseSize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val transparentBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        binding.zoomableView.setBitmap(transparentBitmap, animate = false)
    }

    private fun adjustTextureViewSize(videoW: Int, videoH: Int) {
        if (videoW <= 0 || videoH <= 0) return
        val viewW = binding.textureView.width.toFloat()
        val viewH = binding.textureView.height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return

        val viewAspect = viewW / viewH
        val videoAspect = videoW.toFloat() / videoH.toFloat()

        var scaleX = 1f
        var scaleY = 1f

        if (videoAspect > viewAspect) {
            scaleY = viewAspect / videoAspect
        } else {
            scaleX = videoAspect / viewAspect
        }

        val matrix = Matrix().apply {
            setScale(scaleX, scaleY, viewW / 2f, viewH / 2f)
        }
        binding.textureView.setTransform(matrix)
    }

    private fun startPlaybackLoopMonitor() {
        playbackLoopJob?.cancel()
        playbackLoopJob = lifecycleScope.launch {
            while (isActive) {
                delay(30)
                val player = mediaPlayer ?: continue
                if (player.isPlaying) {
                    val pos = player.currentPosition.toLong()
                    if (pos >= endTrimMs || pos < startTrimMs) {
                        player.seekTo(startTrimMs.toInt())
                    }
                }
            }
        }
    }

    private fun exportGif() {
        val uri = videoUri ?: return
        val durationMs = max(100L, endTrimMs - startTrimMs)
        binding.progressBar.visibility = View.VISIBLE
        binding.btnCreate.isEnabled = false
        Toast.makeText(this, getString(R.string.exporting_gif), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this@GifEditActivity, uri)

                // Cấu hình số frame xuất ảnh: ~10 fps, tối đa 40 frame
                val frameRateFps = 10
                val totalFrames = ((durationMs * frameRateFps) / 1000L).toInt().coerceIn(6, 40)
                val frameIntervalUs = (durationMs * 1000L) / totalFrames
                val frameDelayMs = (1000 / (frameRateFps * currentSpeed)).toInt().coerceAtLeast(30)

                val outputFile = File(cacheDir, "stickify_gif_${System.currentTimeMillis()}.gif")
                val fos = FileOutputStream(outputFile)
                val encoder = AnimatedGifEncoder()
                encoder.start(fos)
                encoder.setDelay(frameDelayMs)
                encoder.setRepeat(0) // Lặp vô hạn

                for (i in 0 until totalFrames) {
                    val timeUs = (startTrimMs * 1000L) + i * frameIntervalUs
                    val rawFrame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    if (rawFrame != null) {
                        // Thu nhỏ frame để xuất GIF nhanh và dung lượng nhẹ (~400px)
                        val maxDim = 400
                        val scale = min(maxDim.toFloat() / rawFrame.width, maxDim.toFloat() / rawFrame.height)
                        val targetW = (rawFrame.width * scale).toInt().coerceAtLeast(1)
                        val targetH = (rawFrame.height * scale).toInt().coerceAtLeast(1)
                        val scaledFrame = Bitmap.createScaledBitmap(rawFrame, targetW, targetH, true)

                        // Ghép toàn bộ decor/chữ/vẽ cọ lên từng frame
                        val compositeBitmap = withContext(Dispatchers.Main) {
                            binding.zoomableView.renderCompositeBitmap(scaledFrame)
                        }

                        encoder.addFrame(compositeBitmap)
                    }
                }
                encoder.finish()
                fos.close()

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCreate.isEnabled = true
                    Toast.makeText(this@GifEditActivity, getString(R.string.gif_exported_success), Toast.LENGTH_SHORT).show()
                    val resultIntent = Intent().apply {
                        putExtra(EXTRA_RESULT_GIF_URI, Uri.fromFile(outputFile).toString())
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCreate.isEnabled = true
                    Toast.makeText(this@GifEditActivity, "Lỗi tạo GIF: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
        }
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(surfaceTexture)
        isSurfaceReady = true
        initMediaPlayer()
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        adjustTextureViewSize(videoWidth, videoHeight)
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        isSurfaceReady = false
        surface?.release()
        surface = null
        playbackLoopJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}

    override fun onResume() {
        super.onResume()
        if (isSurfaceReady && mediaPlayer == null) {
            initMediaPlayer()
        } else {
            mediaPlayer?.start()
        }
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        playbackLoopJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        surface?.release()
        surface = null
    }
}
