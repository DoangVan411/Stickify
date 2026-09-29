package com.jetpack.stickify.presentation.ui.edit_sticker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jetpack.stickify.R
import com.jetpack.stickify.databinding.ActivityStickerEditBinding
import com.jetpack.stickify.presentation.ui.edit_sticker.border.BorderToolFragment
import com.jetpack.stickify.presentation.ui.edit_sticker.custom_view.EditorPanelView
import com.jetpack.stickify.presentation.ui.edit_sticker.decor.DecorToolFragment
import com.jetpack.stickify.presentation.ui.edit_sticker.decoration.DecorationFragment
import com.jetpack.stickify.presentation.ui.edit_sticker.effect.EffectToolFragment
import com.jetpack.stickify.presentation.ui.edit_sticker.suggestion.SuggestionFragment
import com.jetpack.stickify.presentation.ui.edit_sticker.text.TextToolFragment
import com.jetpack.stickify.domain.model.StickerStyle
import android.graphics.BitmapFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Màn hình "Chỉnh sửa" sticker:
 * - Nhận ảnh đã cắt qua EXTRA_CROPPED_IMAGE_URI (từ CutoutActivity).
 * - Preview có thể pinch-zoom/pan tự do (ZoomableStickerView).
 * - 3 kiểu đề xuất "Giữ nguyên / Viền ngoài / Hoạt hình" được TÍNH TRƯỚC 1 lần khi vào màn
 *   hình, nên khi bấm chuyển kiểu, ảnh preview đổi ngay + có animation crossfade mượt mà,
 *   không phải chờ tính toán lại.
 * - Có lịch sử chọn kiểu để Undo/Redo.
 */

@AndroidEntryPoint // Bắt buộc để Hilt có thể tiêm StickerSharedViewModel vào đây
class StickerEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROJECT_ID = "extra_project_id"
        const val EXTRA_CROPPED_IMAGE_URI = "extra_cropped_image_uri"
        const val EXTRA_RESULT_URI = "extra_result_uri"
    }

    private lateinit var binding: ActivityStickerEditBinding

    private val sharedViewModel: StickerSharedViewModel by viewModels()
    private val editViewModel: StickerEditViewModel by viewModels()

    // Lưu trữ tham chiếu đến các Fragment để thực hiện logic Hide/Show
    private var suggestionFragment: SuggestionFragment? = null
    private var textToolFragment: TextToolFragment? = null
    private var borderToolFragment: BorderToolFragment? = null
    private var decorToolFragment: DecorToolFragment? = null
    private var effectToolFragment: EffectToolFragment? = null

    private var currentProjectId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_sticker_edit)

        setupClickListeners()
        setupObservers()
        setupEditorTabMenu()

        // 1. Kiểm tra nếu có projectId chuyển sang từ RecentProjectAdapter (Room DB Clean Architecture flow)
        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID)
        if (!projectId.isNullOrBlank()) {
            currentProjectId = projectId
            editViewModel.loadProject(projectId)
            return
        }

        // 2. Kiểm tra nếu mở từ CutoutActivity qua Uri (Legacy Flow)
        val uri: Uri? = intent.getParcelableExtra(EXTRA_CROPPED_IMAGE_URI)
        if (uri != null) {
            sharedViewModel.loadAndPrepareStyles(uri.toString())
        } else {
            Toast.makeText(this, "Không nhận được dữ liệu project hoặc ảnh đầu vào", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        val projectId = currentProjectId
        if (!projectId.isNullOrEmpty()) {
            val canvasView = binding.zoomableView
            if (canvasView.width > 0 && canvasView.height > 0) {
                runCatching {
                    val bitmap = canvasView.captureToBitmap()
                    editViewModel.captureAndSaveThumbnail(projectId, bitmap)
                }
            }
        }
    }

    private fun setupObservers() {
        // Observers cho SharedViewModel (Legacy Flow)
        sharedViewModel.isLoading.observe(this) { isLoading ->
            if (currentProjectId == null) {
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        sharedViewModel.currentStyle.observe(this) { style ->
            if (currentProjectId == null) {
                val bitmap = sharedViewModel.styleBitmaps[style]
                if (bitmap != null) {
                    binding.zoomableView.setBitmap(bitmap, animate = true)
                }
            }
        }

        sharedViewModel.historyState.observe(this) { (canUndo, canRedo) ->
            if (currentProjectId == null) {
                updateUndoRedoButtons(canUndo, canRedo)
            }
        }

        sharedViewModel.saveSuccessEvent.observe(this) { savedUriString ->
            if (currentProjectId == null && savedUriString != null) {
                val resultUri = Uri.parse(savedUriString)
                setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT_URI, resultUri))
                Toast.makeText(this, "Đã tạo sticker thành công!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        // Observers cho StickerEditViewModel (Clean Architecture Flow từ Room Database)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                editViewModel.uiState.collect { state ->
                    if (currentProjectId != null) {
                        binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                        updateUndoRedoButtons(state.canUndo, state.canRedo)

                        // Truyền ProjectContent và AssetLoader vào ZoomableStickerView để render đa layer
                        binding.zoomableView.setProjectContent(
                            content = state.editorSession.content,
                            loader = editViewModel.assetLoader
                        )

                        if (state.isSaveSuccess) {
                            Toast.makeText(this@StickerEditActivity, "Đã lưu project thành công!", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        }

                        if (!state.error.isNullOrBlank()) {
                            Toast.makeText(this@StickerEditActivity, state.error, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        // Thêm decor khi người dùng chọn trong DecorToolFragment
        sharedViewModel.addedDecorEvent.observe(this) { decor ->
            if (decor != null) {
                binding.zoomableView.setDrawDecorBrush(null)
                val bitmap = decor.customBitmap ?: BitmapFactory.decodeResource(resources, decor.resId)
                if (bitmap != null) {
                    binding.zoomableView.addDecorBitmap(bitmap, decor.id)
                }
            }
        }

        // Vẽ trang trí theo cử chỉ kéo
        sharedViewModel.drawDecorModeEvent.observe(this) { decor ->
            if (decor == null) {
                binding.zoomableView.setDrawDecorBrush(null)
            } else {
                val bitmap = decor.customBitmap ?: BitmapFactory.decodeResource(resources, decor.resId)
                binding.zoomableView.setDrawDecorBrush(bitmap, decor.id)
            }
        }

        // Lắng nghe hiệu ứng animation được chọn để preview động
        sharedViewModel.currentAnimation.observe(this) { animationType ->
            binding.zoomableView.setAnimationType(animationType)
        }
    }

    private fun updateUndoRedoButtons(canUndo: Boolean, canRedo: Boolean) {
        val legacyMode = currentProjectId == null
        val canUndoEffective = if (legacyMode) true else canUndo
        binding.btnUndo.isEnabled = canUndoEffective
        binding.btnUndo.alpha = if (canUndoEffective) 1f else 0.35f

        binding.btnRedo.isEnabled = canRedo
        binding.btnRedo.alpha = if (canRedo) 1f else 0.35f
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.btnUndo.setOnClickListener {
            if (currentProjectId != null) {
                editViewModel.undo()
            } else {
                if (binding.zoomableView.undoLastDrawDecorStroke()) {
                    return@setOnClickListener
                }
                sharedViewModel.moveHistory(-1)
            }
        }

        binding.btnRedo.setOnClickListener {
            if (currentProjectId != null) {
                editViewModel.redo()
            } else {
                sharedViewModel.moveHistory(1)
            }
        }

        // Gọi ViewModel thực hiện lưu ảnh (ghép decor nếu có, xuất GIF nếu có animation) hoặc lưu project
        binding.btnCreate.setOnClickListener {
            if (currentProjectId != null) {
                editViewModel.saveProject()
            } else {
                val baseBitmap = sharedViewModel.styleBitmaps[sharedViewModel.currentStyle.value ?: StickerStyle.ORIGINAL]
                val finalBitmap = if (baseBitmap != null && binding.zoomableView.hasDecors()) {
                    binding.zoomableView.renderCompositeBitmap(baseBitmap)
                } else {
                    baseBitmap
                }
                sharedViewModel.saveCurrentSticker(finalBitmap)
            }
        }

        binding.btnSendPrompt.setOnClickListener {
            Toast.makeText(this, "Tính năng tạo sticker AI đang được phát triển", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupEditorTabMenu() {
        binding.editorPanel.setOnTabSelectedListener(
            object : EditorPanelView.OnTabSelectedListener {
                override fun onTabSelected(position: Int, tabName: String) {
                    switchFragment(tabName)
                }
            }
        )
        switchFragment("Đề xuất")
    }

    private fun switchFragment(tabName: String) {
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()

        suggestionFragment?.let { transaction.hide(it) }
        textToolFragment?.let { transaction.hide(it) }
        borderToolFragment?.let { transaction.hide(it) }
        decorToolFragment?.let { transaction.hide(it) }
        effectToolFragment?.let { transaction.hide(it) }

        // 2. Show Fragment tương ứng với Tab
        when (tabName) {
            "Đề xuất" -> {
                if (suggestionFragment == null) {
                    suggestionFragment = SuggestionFragment()
                    transaction.add(R.id.featureContainer, suggestionFragment!!, "SUGGESTION")
                } else {
                    transaction.show(suggestionFragment!!)
                }
            }
            "Chữ" -> {
                if (textToolFragment == null) {
                    textToolFragment = TextToolFragment()
                    transaction.add(R.id.featureContainer, textToolFragment!!, "TEXT_TOOL")
                } else {
                    transaction.show(textToolFragment!!)
                }
            }
            "Hiệu ứng" -> {
                if (effectToolFragment == null) {
                    effectToolFragment = EffectToolFragment()
                    transaction.add(R.id.featureContainer, effectToolFragment!!, "EFFECT_TOOL")
                } else {
                    transaction.show(effectToolFragment!!)
                }
            }
            "Trang trí" -> {
                if (decorToolFragment == null) {
                    decorToolFragment = DecorToolFragment()
                    transaction.add(R.id.featureContainer, decorToolFragment!!, "DECOR_TOOL")
                } else {
                    transaction.show(decorToolFragment!!)
                }
            }
            "Viền" -> {
                if (borderToolFragment == null) {
                    borderToolFragment = BorderToolFragment()
                    transaction.add(R.id.featureContainer, borderToolFragment!!, "BORDER_TOOL")
                } else {
                    transaction.show(borderToolFragment!!)
                }
            }
            else -> {
                suggestionFragment?.let { transaction.show(it) }
            }
        }
        transaction.commit()
    }
}
