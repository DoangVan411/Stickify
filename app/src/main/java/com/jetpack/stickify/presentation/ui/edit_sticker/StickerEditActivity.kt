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
import com.jetpack.stickify.presentation.ui.edit_sticker.decoration.DecorationFragment
import com.jetpack.stickify.presentation.ui.edit_sticker.suggestion.SuggestionFragment
import com.jetpack.stickify.presentation.ui.edit_sticker.text.TextToolFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Màn hình "Chỉnh sửa" sticker:
 * - Nhận projectId từ Intent extra EXTRA_PROJECT_ID, nạp dữ liệu từ Room DB và phục hồi EditorSession (content & history).
 * - Observe state canUndo/canRedo để cập nhật nút Undo/Redo.
 * - Observe ProjectContent để truyền vào ZoomableStickerView vẽ các layer lên Canvas.
 * - Tự động chụp và cập nhật Thumbnail khi thoát/ẩn màn hình (onStop).
 */
@AndroidEntryPoint
class StickerEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROJECT_ID = "extra_project_id"
        const val EXTRA_CROPPED_IMAGE_URI = "extra_cropped_image_uri"
        const val EXTRA_RESULT_URI = "extra_result_uri"
    }

    private lateinit var binding: ActivityStickerEditBinding

    private val sharedViewModel: StickerSharedViewModel by viewModels()
    private val editViewModel: StickerEditViewModel by viewModels()

    private var suggestionFragment: SuggestionFragment? = null
    private var textToolFragment: TextToolFragment? = null
    private var borderToolFragment: BorderToolFragment? = null
    private var decorationFragment: DecorationFragment? = null

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
    }

    private fun updateUndoRedoButtons(canUndo: Boolean, canRedo: Boolean) {
        binding.btnUndo.isEnabled = canUndo
        binding.btnUndo.alpha = if (canUndo) 1f else 0.35f

        binding.btnRedo.isEnabled = canRedo
        binding.btnRedo.alpha = if (canRedo) 1f else 0.35f
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.btnUndo.setOnClickListener {
            if (currentProjectId != null) {
                editViewModel.undo()
            } else {
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

        binding.btnCreate.setOnClickListener {
            if (currentProjectId != null) {
                editViewModel.saveProject()
            } else {
                sharedViewModel.saveCurrentSticker()
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
                    switchFragment(position)
                }
            }
        )
        switchFragment(0)
    }

    private fun switchFragment(position: Int) {
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()

        suggestionFragment?.let { transaction.hide(it) }
        textToolFragment?.let { transaction.hide(it) }
        borderToolFragment?.let { transaction.hide(it) }
        decorationFragment?.let { transaction.hide(it) }


        when (position) {
            0 -> {
                if (suggestionFragment == null) {
                    suggestionFragment = SuggestionFragment()
                    transaction.add(R.id.featureContainer, suggestionFragment!!, "SUGGESTION")
                } else {
                    transaction.show(suggestionFragment!!)
                }
            }
            1 -> {
                if (textToolFragment == null) {
                    textToolFragment = TextToolFragment()
                    transaction.add(R.id.featureContainer, textToolFragment!!, "TEXT_TOOL")
                } else {
                    transaction.show(textToolFragment!!)
                }
            }
            3->{
                if (decorationFragment == null) {
                    decorationFragment = DecorationFragment()
                    transaction.add(R.id.featureContainer, decorationFragment!!, "DECORATION")
                } else {
                    transaction.show(decorationFragment!!)
                }
            }
            4 -> {
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
