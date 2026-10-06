package com.jetpack.stickify.presentation.ui.edit_gif

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jetpack.stickify.databinding.ActivityVideoTrimBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.min

/**
 * Màn hình Cắt đoạn video (Màn 1):
 * - Xem trước video trong TextureView bo góc
 * - Thanh cắt VideoTrimmerView kéo chọn khoảng thời gian
 * - Hiển thị badge thời lượng (ví dụ: 0:08)
 * - Nút "Tiếp tục" chuyển sang màn hình chỉnh sửa GIF (GifEditActivity)
 */
class VideoTrimActivity : AppCompatActivity(), TextureView.SurfaceTextureListener {

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_START_MS = "extra_start_ms"
        const val EXTRA_END_MS = "extra_end_ms"
        private const val THUMB_COUNT = 8
    }

    private lateinit var binding: ActivityVideoTrimBinding
    private var videoUri: Uri? = null

    private var mediaPlayer: MediaPlayer? = null
    private var surface: Surface? = null
    private var isSurfaceReady = false

    private var videoDurationMs: Long = 0L
    private var startTrimMs: Long = 0L
    private var endTrimMs: Long = 8000L

    private var playbackLoopJob: Job? = null
    private var isUserDragging = false
    private var videoWidth = 0
    private var videoHeight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoTrimBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uriExtra: Uri? = intent.getParcelableExtra(EXTRA_VIDEO_URI)
        val uriString = intent.getStringExtra(EXTRA_VIDEO_URI)
        videoUri = uriExtra ?: uriString?.let { Uri.parse(it) }

        if (videoUri == null) {
            Toast.makeText(this, "Không tìm thấy video", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupViews()
    }

    private fun setupViews() {
        binding.btnBack.setOnClickListener { finish() }

        binding.textureView.surfaceTextureListener = this

        binding.trimmerView.listener = object : VideoTrimmerView.OnTrimChangeListener {
            override fun onTrimRangeChanged(startMs: Long, endMs: Long) {
                startTrimMs = startMs
                endTrimMs = endMs
                updateDurationBadge(endMs - startMs)
            }

            override fun onTrimHandleTouch(isDragging: Boolean, currentMs: Long) {
                isUserDragging = isDragging
                if (isDragging) {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) player.pause()
                        player.seekTo(currentMs.toInt())
                    }
                } else {
                    mediaPlayer?.let { player ->
                        player.seekTo(startTrimMs.toInt())
                        player.start()
                    }
                }
            }
        }

        binding.btnContinue.setOnClickListener {
            val uri = videoUri ?: return@setOnClickListener
            val resultIntent = Intent().apply {
                putExtra(EXTRA_VIDEO_URI, uri)
                putExtra(EXTRA_START_MS, startTrimMs)
                putExtra(EXTRA_END_MS, endTrimMs)
            }
            setResult(RESULT_OK, resultIntent)
            Toast.makeText(this, "Đã chọn đoạn video: ${binding.tvDurationBadge.text}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDurationBadge(durationMs: Long) {
        val totalSec = (durationMs + 500) / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        binding.tvDurationBadge.text = String.format(Locale.getDefault(), "%d:%02d", min, sec)
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

        val matrix = android.graphics.Matrix().apply {
            setScale(scaleX, scaleY, viewW / 2f, viewH / 2f)
        }
        binding.textureView.setTransform(matrix)
    }

    private fun initMediaPlayer() {
        val uri = videoUri ?: return
        if (!isSurfaceReady || surface == null) return

        binding.loadingPreview.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val player = MediaPlayer().apply {
                    setDataSource(this@VideoTrimActivity, uri)
                    setSurface(surface)
                    isLooping = false
                    setOnPreparedListener { mp ->
                        binding.loadingPreview.visibility = View.GONE
                        videoDurationMs = mp.duration.toLong().coerceAtLeast(1000L)
                        startTrimMs = 0L
                        endTrimMs = min(videoDurationMs, 8000L)
                        this@VideoTrimActivity.videoWidth = mp.videoWidth
                        this@VideoTrimActivity.videoHeight = mp.videoHeight
                        adjustTextureViewSize(this@VideoTrimActivity.videoWidth, this@VideoTrimActivity.videoHeight)

                        binding.trimmerView.videoDurationMs = videoDurationMs
                        binding.trimmerView.setTrimRange(startTrimMs, endTrimMs)
                        updateDurationBadge(endTrimMs - startTrimMs)

                        mp.seekTo(startTrimMs.toInt())
                        mp.start()
                        startPlaybackLoopMonitor()
                    }
                    setOnErrorListener { _, _, _ ->
                        binding.loadingPreview.visibility = View.GONE
                        false
                    }
                    prepareAsync()
                }
                withContext(Dispatchers.Main) {
                    mediaPlayer?.release()
                    mediaPlayer = player
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.loadingPreview.visibility = View.GONE
                    Toast.makeText(this@VideoTrimActivity, "Không thể phát video: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        loadVideoThumbnails()
    }

    private fun loadVideoThumbnails() {
        val uri = videoUri ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this@VideoTrimActivity, uri)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val duration = durationStr?.toLongOrNull() ?: 10000L

                val thumbs = mutableListOf<Bitmap>()
                val stepUs = (duration * 1000L) / THUMB_COUNT
                for (i in 0 until THUMB_COUNT) {
                    val timeUs = i * stepUs
                    val frame = retriever.getFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    if (frame != null) {
                        // Thu nhỏ thumbnail để tiết kiệm RAM
                        val scaled = Bitmap.createScaledBitmap(frame, 120, 120, true)
                        thumbs.add(scaled)
                    }
                }
                withContext(Dispatchers.Main) {
                    if (thumbs.isNotEmpty()) {
                        binding.trimmerView.setThumbnails(thumbs)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
        }
    }

    private fun startPlaybackLoopMonitor() {
        playbackLoopJob?.cancel()
        playbackLoopJob = lifecycleScope.launch {
            while (isActive) {
                delay(30)
                val player = mediaPlayer ?: continue
                if (!isUserDragging && player.isPlaying) {
                    val pos = player.currentPosition.toLong()
                    if (pos >= endTrimMs || pos < startTrimMs) {
                        player.seekTo(startTrimMs.toInt())
                    }
                }
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
