package com.jetpack.stickify.presentation.ui.edit_sticker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jetpack.stickify.R
import com.jetpack.stickify.databinding.ActivityStickerEditBinding
import com.jetpack.stickify.domain.model.BuiltinAsset
import com.jetpack.stickify.domain.model.StickerStyle
import com.jetpack.stickify.presentation.ui.edit_sticker.border.BorderToolFragment
import com.jetpack.stickify.presentation.ui.edit_sticker.prompt_sticker.AiGlowBorderDrawable
import com.jetpack.stickify.presentation.ui.edit_sticker.custom_view.EditorPanelView
import com.jetpack.stickify.presentation.ui.edit_sticker.decor.DecorToolFragment
import com.jetpack.stickify.presentation.ui.edit_sticker.effect.EffectToolFragment
import com.jetpack.stickify.presentation.ui.edit_sticker.suggestion.SuggestionFragment
import com.jetpack.stickify.presentation.ui.edit_sticker.text.TextToolFragment
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import android.graphics.Typeface
import android.util.Log
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import com.jetpack.stickify.domain.model.AiAnimation
import com.jetpack.stickify.presentation.ui.edit_sticker.prompt_sticker.AiStickerEvent
import com.jetpack.stickify.presentation.ui.edit_sticker.prompt_sticker.AiStickerUiState
import com.jetpack.stickify.presentation.ui.edit_sticker.prompt_sticker.AiStickerViewModel
import com.jetpack.stickify.domain.model.BorderStyle
import com.jetpack.stickify.domain.model.DecorationCategory
import com.jetpack.stickify.domain.model.StickerAnimationType
import com.jetpack.stickify.domain.model.SubjectLayer
import com.jetpack.stickify.domain.model.TextAlign
import com.jetpack.stickify.domain.model.TextLayer
import com.jetpack.stickify.presentation.ui.edit_sticker.text.TextInputOverlayController
import com.jetpack.stickify.presentation.ui.edit_sticker.text.TextStyleSpec
import com.jetpack.stickify.presentation.ui.edit_sticker.cancel.SaveConfirmDialogFragment
import com.jetpack.stickify.presentation.ui.edit_sticker.decor.MaxDecorLimitDialogFragment
import com.jetpack.stickify.presentation.ui.edit_sticker.prompt_sticker.AiHistoryResult
import com.jetpack.stickify.presentation.ui.edit_sticker.prompt_sticker.EditSpan
import com.jetpack.stickify.presentation.ui.edit_sticker.utils.PromptKeyboardAdjuster
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import com.jetpack.stickify.presentation.ui.edit_sticker.utils.PromptUtils
import java.util.UUID

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
        const val EXTRA_TEMPLATE_ID = "extra_template_id"
        const val EXTRA_RESULT_URI = "extra_result_uri"
    }

    private lateinit var binding: ActivityStickerEditBinding

    private val sharedViewModel: StickerSharedViewModel by viewModels()
    private val editViewModel: StickerEditViewModel by viewModels()

    // Lưu trữ tham chiếu đến các Fragment để thực hiện logic Hide/Show
    private var suggestionFragment: SuggestionFragment? = null
    private var borderToolFragment: BorderToolFragment? = null
    private var decorToolFragment: DecorToolFragment? = null
    private var effectToolFragment: EffectToolFragment? = null

    private var currentProjectId: String? = null

    // Toàn bộ UI + trạng thái nhập chữ (màu, font, nền, căn lề) nằm trong controller riêng
    private lateinit var textController: TextInputOverlayController

    private var previousTabIndex = 0

    // Thêm cờ này để chặn onStop lưu thumbnail nếu chọn "Không lưu"
    private var isDiscardingChanges = false

    var isProjectMode = false

    fun hasAiAnimation(): Boolean = aiViewModel.uiState.value.animation != null || binding.zoomableView.hasAiAnimation()

    // Chỉ finish() khi CẢ export ảnh/GIF lẫn lưu project đã xong
    private var pendingExport = false
    private var pendingProject = false
    private var exportResultUri: Uri? = null

    // Thêm biến isNewProject và gán mặc định isProjectMode = true
    private var isNewProject = false
    private var keyboardAdjuster: PromptKeyboardAdjuster? = null

    // true khi AI đang tạo sticker (hiện viền chạy + lớp phủ tối)
    private var isAiGenerating = false
    private var pendingReveal = false

    // Viền xanh-cam: gắn làm foreground của promptCard nên luôn bám sát card
    private lateinit var aiGlow: AiGlowBorderDrawable

    private val aiViewModel: AiStickerViewModel by viewModels()
    private var shownAnimation: AiAnimation? = null
    private var lastAiState = AiStickerUiState()
    private var editCanUndo = false
    private var editCanRedo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_sticker_edit)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top, v.paddingRight, v.paddingBottom)
            insets
        }




        aiGlow = AiGlowBorderDrawable(this)
        binding.promptCard.foreground = aiGlow          // viền sắc nét bên trong card
        binding.aiGlowHalo.target = binding.promptCard  // quầng sóng nhòe ra ngoài card

        setupClickListeners()
        observeAiState()
        setupKeyboard()
        setupObservers()
        setupTextOverlayLogic() // phải khởi tạo trước tab menu (tab "Chữ" dùng textController)
        setupEditorTabMenu()
        setupBackPressHandler() //  Đăng ký Back stack
        setupSaveConfirmDialogListener()// setup comfirm

        // 1. Kiểm tra nếu có projectId chuyển sang từ RecentProjectAdapter (Room DB Clean Architecture flow)
        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID)
        if (!projectId.isNullOrBlank()) {
            isProjectMode = true
            isNewProject = false
            currentProjectId = projectId
            editViewModel.loadProject(this, projectId) { subjectLayer, border, playback, restoredAi ->
                if (subjectLayer != null) {
                    sharedViewModel.restoreFromProject(this, projectId, subjectLayer, border, playback)
                }
                if (restoredAi.isNotEmpty()) {
                    aiViewModel.restore(restoredAi, editViewModel.undoDepth)
                }
            }
            return
        }

        // 2. Kiểm tra nếu mở từ Template flow
        val templateId = intent.getStringExtra(EXTRA_TEMPLATE_ID)
        val templateUri: Uri? = intent.getParcelableExtra(EXTRA_CROPPED_IMAGE_URI)
        if (!templateId.isNullOrBlank() && templateUri != null) {
            val newId = editViewModel.uiState.value.projectId ?: "proj_${System.currentTimeMillis()}"
            currentProjectId = newId
            isProjectMode = true
            isNewProject = true
            editViewModel.initTemplateProject(this, newId, templateId, templateUri.toString())
            sharedViewModel.loadAndPrepareStyles(templateUri.toString())
            return
        }

        // 3. Kiểm tra nếu mở từ CutoutActivity qua Uri (Legacy Flow)
        val uri: Uri? = intent.getParcelableExtra(EXTRA_CROPPED_IMAGE_URI)
        if (uri != null) {
            // Giữ nguyên id khi Activity bị tạo lại (xoay màn hình)
            val newId = editViewModel.uiState.value.projectId ?: "proj_${System.currentTimeMillis()}"
            currentProjectId = newId

            isProjectMode = true // LUÔN CHẠY CHẾ ĐỘ PROJECT (LAYER)
            isNewProject = true // Bật cờ dự án mới
            // Tạo project (SubjectLayer + copy ảnh vào bộ nhớ app). Trước đây luồng này không bao giờ gọi
            // initNewProject nên project được lưu với danh sách layer RỖNG.
            editViewModel.initNewProject(this, newId, uri.toString())
            sharedViewModel.loadAndPrepareStyles(uri.toString())
        } else {
            Toast.makeText(this, "Không nhận được dữ liệu project hoặc ảnh đầu vào", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // ---------- AI sticker: Activity chỉ quan sát state, mọi xử lý nằm ở AiStickerViewModel ----------
    private fun observeAiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { aiViewModel.uiState.collect { renderAiState(it) } }
                launch { aiViewModel.events.collect { handleAiEvent(it) } }
            }
        }
    }

    private fun renderAiState(state: AiStickerUiState) {
        lastAiState = state
        setAiLoading(state.isGenerating, state.percent, state.message)

        if (pendingReveal && !state.isGenerating) {
            pendingReveal = false
            if (state.error == null && state.animation != null) {
                binding.aiRevealSweep.play(startDelayMs = 220L) { Log.d("AiReveal", "sweep end") }
            } else {
                binding.aiRevealSweep.discardSnapshot()
            }
        }
        val isNewResult = state.animation !== shownAnimation


        if (isNewResult) {
            shownAnimation = state.animation

            // CHỈ CẦN THIẾT LẬP LẠI KHUNG HÌNH AI CHO VIEW NHƯ CŨ
            val firstFrame = state.animation?.frames?.firstOrNull()
            if (firstFrame != null) {
                binding.zoomableView.resetTransformToFitWidth(firstFrame.width.toFloat(), firstFrame.height.toFloat())
            }

            binding.zoomableView.setAiAnimation(state.animation?.frames, state.animation?.frameDelayMs ?: 83)
        }
        //xử lý lỗi
        state.error?.let { errorMessage ->
            PromptUtils.showErrorDialog(
                supportFragmentManager,
                "Lỗi tạo Sticker",
                errorMessage
            )
            aiViewModel.consumeError()
        }
        refreshUndoRedo()
    }

    private fun handleAiEvent(event: AiStickerEvent) {
        when (event) {
            is AiStickerEvent.Exported -> {
                exportResultUri = Uri.parse(event.uri)
                pendingExport = false
                finishWhenSaveDone()
            }
            is AiStickerEvent.ExportFailed -> {
                Toast.makeText(this, "Xuất sticker động thất bại: ${event.message}", Toast.LENGTH_LONG).show()
                pendingExport = false
                finishWhenSaveDone()
            }
        }
    }

    /** Undo/Redo = lịch sử layer (editViewModel) + kết quả AI. */
    private fun refreshUndoRedo() {
        val depth = editViewModel.undoDepth
        updateUndoRedoButtons(
            canUndo = editCanUndo || lastAiState.canUndoAi,
            canRedo = editCanRedo || aiViewModel.canRedoAi(depth)
        )
    }

    private fun setupKeyboard() {
        // Khởi tạo và kích hoạt bộ điều chỉnh bàn phím
        keyboardAdjuster = PromptKeyboardAdjuster(
            rootView = binding.root,
            targetView = binding.promptCard,
            focusView = binding.etPrompt,
            bottomMarginDp = 10
        ).apply {
            attach()
        }
    }

    override fun onDestroy() {
        if (::aiGlow.isInitialized) aiGlow.release()
        binding.aiGlowHalo.release()
        binding.aiRevealSweep.release()
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        // Chọn "Không lưu" hoặc project chưa từng được lưu -> không ghi thumbnail
        if (isDiscardingChanges || !editViewModel.isPersisted) return
        val projectId = currentProjectId ?: return
        captureCurrentBitmap()?.let { editViewModel.captureAndSaveThumbnail(projectId, it) }
    }

    private fun captureCurrentBitmap(): Bitmap? {
        val view = binding.zoomableView
        if (view.width <= 0 || view.height <= 0) return null
        return runCatching {
            if (isProjectMode) view.renderProjectBitmap() else view.captureToBitmap()
        }.getOrNull()
    }

    private fun finishWhenSaveDone() {
        if (pendingExport || pendingProject) return
        val uri = exportResultUri
        if (uri != null) {
            val anim = sharedViewModel.currentAnimation.value
            val isAnimated = (anim != null && anim.isAnimated) || aiViewModel.uiState.value.animation != null

            val intent = Intent(this, StickerPreviewActivity::class.java).apply {
                putExtra(StickerPreviewActivity.EXTRA_STICKER_URI, uri)
                putExtra(StickerPreviewActivity.EXTRA_PROJECT_ID, currentProjectId)
                putExtra(StickerPreviewActivity.EXTRA_IS_ANIMATED, isAnimated)
            }
            startActivity(intent)
            finish()
        } else {
            setResult(RESULT_OK)
            finish()
        }
    }

    // 2 nguồn loading (shared: xử lý ảnh/viền, edit: load/lưu project) dùng chung 1 progress bar,
    // nếu mỗi nơi tự set thì nguồn này sẽ tắt progress của nguồn kia.
    private var sharedLoading = false
    private var editLoading = false

    private fun refreshProgress() {
        val isLoading = sharedLoading || editLoading || pendingProject || pendingExport
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE

        val message = when {
            editLoading -> "Đang tải dự án..."
            pendingProject || pendingExport -> "Đang lưu sticker..."
            else -> "Đang xử lý..."
        }
        binding.tvLoadingMessage.text = message
    }

    /** Đẩy bitmap của style đang chọn (gốc/viền/cartoon) vào layer chủ thể. */
    private fun applySubjectStyleBitmap() {
        val style = sharedViewModel.currentStyle.value ?: return
        val bitmap = sharedViewModel.styleBitmaps[style] ?: return
        val subjectLayerId = editViewModel.uiState.value.editorSession.content.layers
            .find { it is SubjectLayer }?.id ?: return
        binding.zoomableView.preloadLayerBitmap(subjectLayerId, bitmap)
    }

    private fun setupObservers() {
        // Observers cho SharedViewModel
        sharedViewModel.isLoading.observe(this) { isLoading ->
            sharedLoading = isLoading == true
            refreshProgress()
        }

        sharedViewModel.imagesReady.observe(this) { isReady ->
            if (isReady == true && isProjectMode) {
                applySubjectStyleBitmap()
            }
        }

        sharedViewModel.currentStyle.observe(this) { style ->
            val bitmap = sharedViewModel.styleBitmaps[style]
            if (bitmap != null) {
                if (isProjectMode) {
                    // Cập nhật ảnh Preview trực tiếp vào SubjectLayer thay vì ghi đè toàn bộ View
                    applySubjectStyleBitmap()
                } else {
                    binding.zoomableView.setBitmap(bitmap, animate = true)
                }
            }
        }

        sharedViewModel.historyState.observe(this) { (canUndo, canRedo) ->
            // Project dùng lịch sử của editViewModel; không để lịch sử style của shared ghi đè trạng thái nút
            if (!isProjectMode) updateUndoRedoButtons(canUndo, canRedo)
        }

        sharedViewModel.saveSuccessEvent.observe(this) { savedUriString ->
            if (savedUriString != null) {
                exportResultUri = Uri.parse(savedUriString)
                pendingExport = false
                finishWhenSaveDone()
            }
        }

        // Thêm decor khi người dùng chọn trong DecorToolFragment
        sharedViewModel.addedDecorEvent.observe(this) { decor ->
            if (decor != null) {
                val currentDecorCount = if (isProjectMode) {
                    editViewModel.uiState.value.editorSession.content.layers.count { it !is SubjectLayer }
                } else {
                    binding.zoomableView.getDecorCount()
                }
                if (currentDecorCount >= binding.zoomableView.maxDecorLimit) {
                    binding.zoomableView.setDrawDecorBrush(null)
                    showMaxDecorLimitDialog()
                    return@observe
                }
                binding.zoomableView.setDrawDecorBrush(null)

                if (isProjectMode && decor.asset != null && !decor.asset.isCustom) {
                    val category = if (decor.asset.category == "DECORATION") DecorationCategory.DRAWN else DecorationCategory.LABEL
                    editViewModel.addBuiltinDecorationFromAsset(decor.asset, category)
                } else {
                    val bitmap = decor.customBitmap ?: decor.path?.let { loadBitmapFromPath(this, it) } ?: (if (decor.resId != 0) BitmapFactory.decodeResource(resources, decor.resId) else null)
                    if (bitmap != null) {
                        if (isProjectMode) {
                            // Project: decor là DecorationLayer (lưu DB + undo/redo), không chỉ là decor tạm trong view
                            val layerId = "layer_dec_${UUID.randomUUID()}"
                            binding.zoomableView.preloadLayerBitmap(layerId, bitmap)
                            editViewModel.addDecorationFromBitmap(this, bitmap, layerId)
                        } else {
                            binding.zoomableView.addDecorBitmap(bitmap, decor.id)
                        }
                    }
                }
            }
        }

        // Vẽ trang trí theo cử chỉ kéo
        sharedViewModel.drawDecorModeEvent.observe(this) { decor ->
            if (decor == null) {
                binding.zoomableView.setDrawDecorBrush(null)
            } else {
                val currentDecorCount = if (isProjectMode) {
                    editViewModel.uiState.value.editorSession.content.layers.count { it !is SubjectLayer }
                } else {
                    binding.zoomableView.getDecorCount()
                }
                if (currentDecorCount >= binding.zoomableView.maxDecorLimit) {
                    binding.zoomableView.setDrawDecorBrush(null)
                    showMaxDecorLimitDialog()
                } else {
                    val bitmap = decor.customBitmap ?: decor.path?.let { loadBitmapFromPath(this, it) } ?: (if (decor.resId != 0) BitmapFactory.decodeResource(resources, decor.resId) else null)
                    
                    val assetRef = if (decor.asset != null && !decor.asset.isCustom) {
                        val parts = decor.asset.path.split("/")
                        val packId = if (parts.size >= 2) parts[parts.size - 2] else "pack_default"
                        val assetId = parts.lastOrNull() ?: decor.asset.path
                        BuiltinAsset(assetId = assetId, packId = packId)
                    } else if (decor.path != null && !decor.path.startsWith("content://") && !decor.path.startsWith("file://")) {
                        val parts = decor.path.split("/")
                        val packId = if (parts.size >= 2) parts[parts.size - 2] else "pack_default"
                        val assetId = parts.lastOrNull() ?: decor.path
                        BuiltinAsset(assetId = assetId, packId = packId)
                    } else {
                        null
                    }

                    binding.zoomableView.setDrawDecorBrush(bitmap, decor.id, assetRef)
                }
            }
        }

        // Lắng nghe hiệu ứng animation được chọn để preview động
        sharedViewModel.currentAnimation.observe(this) { animationType ->
            binding.zoomableView.setAnimationType(animationType)
        }

        // Observer cho StickerEditViewModel
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                editViewModel.uiState.collect { state ->
                    if (state.loadFailed) {
                        Toast.makeText(
                            this@StickerEditActivity,
                            state.error ?: "Không thể mở project",
                            Toast.LENGTH_LONG
                        ).show()
                        editViewModel.consumeError()
                        finish()
                        return@collect
                    }

                    if (isProjectMode) {
                        editLoading = state.isLoading
                        refreshProgress()
                        editCanUndo = state.canUndo
                        editCanRedo = state.canRedo
                        aiViewModel.onEditDepthChanged(editViewModel.undoDepth)
                        refreshUndoRedo()

                        val contentAnim = state.editorSession.content.playback?.animationType ?: StickerAnimationType.NONE
                        if (sharedViewModel.currentAnimation.value != contentAnim) {
                            sharedViewModel.setAnimation(contentAnim)
                        }
                        binding.zoomableView.setAnimationType(contentAnim)

                        // BƯỚC BỊ THIẾU trước đây: đẩy nội dung project (các layer) vào view để vẽ.
                        if (!state.isLoading) {
                            binding.zoomableView.setProjectContent(
                                state.editorSession.content,
                                editViewModel.assetLoader
                            )
                            // Layer chủ thể có thể xuất hiện SAU khi style đã sẵn sàng (project mới copy ảnh bất đồng bộ)
                            applySubjectStyleBitmap()
                        }
                    }

                    state.lastAddedLayerId?.let { id ->
                        binding.zoomableView.selectLayer(id)
                        editViewModel.consumeAddedLayer()
                    }

                    if (state.isSaveSuccess && pendingProject) {
                        pendingProject = false
                        finishWhenSaveDone()
                    }

                    val err = state.error
                    if (!err.isNullOrBlank()) {
                        Toast.makeText(this@StickerEditActivity, err, Toast.LENGTH_SHORT).show()
                        editViewModel.consumeError() // tránh Toast lặp lại ở mỗi lần state đổi
                    }
                }
            }
        }
    }

    private fun updateUndoRedoButtons(canUndo: Boolean, canRedo: Boolean) {
        val legacyMode = !isProjectMode
        val canUndoEffective = if (legacyMode) true else canUndo
        binding.btnUndo.isEnabled = canUndoEffective
        binding.btnUndo.alpha = if (canUndoEffective) 1f else 0.35f

        binding.btnRedo.isEnabled = canRedo
        binding.btnRedo.alpha = if (canRedo) 1f else 0.35f
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { showSaveConfirmationDialog() }

        binding.btnUndo.setOnClickListener {
            if (isProjectMode) {
                if (isAiGenerating) return@setOnClickListener
                when (aiViewModel.undo(editViewModel.undoDepth)) {
                    AiHistoryResult.HANDLED -> Unit
                    else -> editViewModel.undo()   // NOT_HANDLED hoặc HANDLED_AND_STEP_EDIT
                }
            } else {
                if (binding.zoomableView.undoLastDrawDecorStroke()) {
                    return@setOnClickListener
                }
                sharedViewModel.moveHistory(-1)
            }
        }

        binding.btnRedo.setOnClickListener {
            if (isProjectMode) {
                if (isAiGenerating) return@setOnClickListener
                when (aiViewModel.redo(editViewModel.undoDepth)) {
                    AiHistoryResult.HANDLED -> Unit
                    else -> if (editCanRedo) {
                        aiViewModel.expectEditRedo()
                        editViewModel.redo()
                    }
                }
            } else {
                sharedViewModel.moveHistory(1)
            }
        }

        // Gọi ViewModel thực hiện lưu ảnh (ghép decor nếu có, xuất GIF nếu có animation) hoặc lưu project
        binding.btnCreate.setOnClickListener {
            handleSaveProjectAction()
        }

        binding.btnSendPrompt.setOnClickListener {
            val prompt = binding.etPrompt.text?.toString()?.trim().orEmpty()

            // 1. Kiểm tra tính hợp lệ bằng Utils
            when (val validation = PromptUtils.validatePrompt(prompt)) {
                is PromptUtils.ValidationResult.Invalid -> {
                    // Nếu không hợp lệ, hiển thị Dialog
                    PromptUtils.showErrorDialog(supportFragmentManager, validation.title, validation.message)
                    return@setOnClickListener
                }
                is PromptUtils.ValidationResult.Valid -> {
                    // Hợp lệ thì đi tiếp
                }
            }

            if (isAiGenerating) return@setOnClickListener
            startAiGeneration(prompt)
        }
    }

    /**
     * Bắt đầu trạng thái "AI đang tạo": viền xanh-cam chạy quanh promptCard,
     * phần còn lại của màn hình tối đi kèm Lottie loading.
     */
    private fun startAiGeneration(prompt: String) {
        // Phải chụp ảnh trên Main thread (view đọc state decorItems); bản cũ chụp trong Dispatchers.IO
        val source = captureCurrentBitmap()
        if (source == null) {
            Toast.makeText(this, "Không thể lấy ảnh hiện tại", Toast.LENGTH_SHORT).show()
            return
        }

        binding.aiRevealSweep.captureSnapshot(binding.zoomableView)
        pendingReveal = true
        Log.d("AiReveal", "snapshot captured")

        val editVm = editViewModel   // chỉ capture ViewModel, không capture Activity (job sống qua xoay màn hình)
        aiViewModel.generate(source, prompt) {
            val before = editVm.undoDepth
            editVm.clearAllDecorAndTextWithUndo()
            EditSpan(before, editVm.undoDepth)
        }
    }

    private fun setAiLoading(loading: Boolean, percent: Int = 0, message: String = "") {
        val displayMsg = if (message.isNotBlank()) "$message..." else "Đang tạo sticker AI..."

        // Nếu đang trong trạng thái loading và tiếp tục nhận progress update
        if (loading && isAiGenerating) {
            if (binding.tvAiLoadingProgress.text.toString() != displayMsg) {
                // Hủy animation cũ nếu có để tránh xung đột khi percent về quá nhanh
                binding.tvAiLoadingProgress.animate().cancel()

                // 1. Trượt text cũ lên trên và mờ dần (tốn một nửa thời gian)
                binding.tvAiLoadingProgress.animate()
                    .translationY(-40f)
                    .alpha(0f)
                    .setDuration(100)
                    .withEndAction {
                        // 2. Cập nhật text mới, dời View xuống dưới và trượt lên vị trí gốc
                        binding.tvAiLoadingProgress.text = displayMsg
                        binding.tvAiLoadingProgress.translationY = 40f
                        binding.tvAiLoadingProgress.animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(250)
                            .start()
                    }.start()
            }
            return
        }

        if (isAiGenerating == loading) return
        isAiGenerating = loading

        if (loading) {
            // Ẩn bàn phím + bỏ focus để card không bị đẩy lung tung khi đang chạy
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(binding.etPrompt.windowToken, 0)
            binding.etPrompt.clearFocus()

            binding.aiLoadingOverlay.apply {
                alpha = 0f
                visibility = View.VISIBLE
                animate().alpha(1f).setDuration(250).start()
            }
            binding.progressLayout.apply {
                alpha = 0f
                visibility = View.VISIBLE
                animate().alpha(1f).setDuration(250).start()
            }
            binding.aiLoadingLottie.playAnimation()
            aiGlow.start()
            binding.aiGlowHalo.start()

            binding.tvAiLoadingProgress.text = displayMsg
        } else {
            aiGlow.stop()
            binding.aiGlowHalo.stop()
            binding.aiLoadingOverlay.animate().alpha(0f).setDuration(200).withEndAction {
                if (!isAiGenerating) {
                    binding.aiLoadingOverlay.visibility = View.GONE
                }
            }.start()
            binding.progressLayout.animate().alpha(0f).setDuration(200).withEndAction {
                if (!isAiGenerating) {
                    binding.progressLayout.visibility = View.GONE
                    binding.aiLoadingLottie.cancelAnimation()
                }
            }.start()
        }

        binding.etPrompt.isEnabled = !loading
        binding.btnSendPrompt.isEnabled = !loading
    }

    private fun setupTextOverlayLogic() {
        textController = TextInputOverlayController(binding.addTextLayout) { result ->
            if (result != null) applyTextResult(result)
            // Đóng màn hình nhập chữ -> quay lại tab trước đó
            binding.editorPanel.selectTab(previousTabIndex)
        }
        setupDoubleTapToEditText()
    }

    private fun showMaxDecorLimitDialog() {
        if (supportFragmentManager.findFragmentByTag(MaxDecorLimitDialogFragment.TAG) != null) return
        MaxDecorLimitDialogFragment.newInstance()
            .show(supportFragmentManager, MaxDecorLimitDialogFragment.TAG)
    }

    private fun applyTextResult(result: TextInputOverlayController.TextEditResult) {
        if (result.layerId == null) {
            val currentDecorCount = if (isProjectMode) {
                editViewModel.uiState.value.editorSession.content.layers.count { it !is SubjectLayer }
            } else {
                binding.zoomableView.getDecorCount()
            }
            if (currentDecorCount >= binding.zoomableView.maxDecorLimit) {
                showMaxDecorLimitDialog()
                return
            }
        }
        if (isProjectMode) {
            // Project: chữ là TextLayer (có Undo/Redo, được lưu vào DB, kèm font + nền/viền)
            val id = result.layerId
            if (id != null) {
                editViewModel.updateTextLayer(id, result.text, result.colorArgb, result.align, result.style)
            } else {
                editViewModel.addTextLayer(result.text, result.colorArgb, result.align, result.style)
            }
        } else {
            binding.zoomableView.addTextItem(
                text = result.text,
                textColor = result.colorArgb,
                existingId = result.layerId
            )
        }
    }

    private fun setupDoubleTapToEditText() {
        binding.zoomableView.onMaxDecorLimitReached = {
            showMaxDecorLimitDialog()
        }
        // Layer decor/chữ của project: kéo/xoay/phóng/xóa/nhân bản -> ghi vào lịch sử qua ViewModel
        binding.zoomableView.onLayerTransformed = { id, cx, cy, scale, rotation ->
            if (isProjectMode) editViewModel.updateLayerTransform(id, cx, cy, scale, rotation)
        }
        binding.zoomableView.onLayerDeleted = { id ->
            if (isProjectMode) editViewModel.removeLayer(id)
        }
        binding.zoomableView.onLayerDuplicated = { sourceId, newId, cx, cy ->
            if (isProjectMode) editViewModel.duplicateLayer(sourceId, newId, cx, cy)
        }
        binding.zoomableView.onDecorStampsCommitted = { bitmap, assetRef, stamps ->
            if (isProjectMode) editViewModel.addStampLayers(this, bitmap, assetRef, stamps)
        }

        // Double tap vào chữ -> mở lại màn hình nhập chữ với đúng màu / căn lề / font / nền của chữ đó
        binding.zoomableView.onTextDecorDoubleTapped = { decor ->
            val layer = if (isProjectMode) {
                editViewModel.uiState.value.editorSession.content.layers
                    .find { it.id == decor.id } as? TextLayer
            } else null

            textController.showEdit(
                layerId = decor.id,
                text = layer?.content ?: decor.textContent ?: "",
                colorArgb = layer?.colorArgb ?: decor.textColor ?: Color.WHITE,
                align = layer?.align ?: TextAlign.CENTER,
                style = TextStyleSpec.decode(layer?.fontId)
            )
        }
    }

    private fun setupEditorTabMenu() {
        binding.editorPanel.setOnTabSelectedListener(
            object : EditorPanelView.OnTabSelectedListener {
                override fun onTabSelected(position: Int, tabName: String) {

                    if (tabName != "Chữ" && tabName != "text") {
                        previousTabIndex = position
                    }
                    switchFragment(tabName)

                }
            }
        )
        switchFragment("Đề xuất")
    }

    private fun switchFragment(tabName: String) {
        // Tắt cọ vẽ trang trí khi người dùng chuyển sang tab khác
        if (tabName != "Trang trí") {
            binding.zoomableView.setDrawDecorBrush(null)
            sharedViewModel.clearDrawDecor()
        }

        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()

        suggestionFragment?.let { transaction.hide(it) }
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
            "Chữ" -> textController.showNew()
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

    private fun setupBackPressHandler() {
        // Chặn sự kiện nút Back vật lý / vuốt Back của điện thoại
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isAiGenerating) {
                    aiViewModel.cancel() // Back khi đang tạo AI = huỷ job + ngắt socket
                } else if (::textController.isInitialized && textController.isShowing) {
                    textController.commit()
                } else {
                    showSaveConfirmationDialog()
                }
            }
        })
    }

    private fun showSaveConfirmationDialog() {
        // Tránh mở trùng Dialog nếu nó đang hiển thị sẵn trên màn hình
        if (supportFragmentManager.findFragmentByTag(SaveConfirmDialogFragment.TAG) != null) {
            return
        }

        SaveConfirmDialogFragment.newInstance()
            .show(supportFragmentManager, SaveConfirmDialogFragment.TAG)
    }

    /**
     * Đăng ký nhận kết quả từ SaveConfirmDialogFragment.
     * GỌI HÀM NÀY TRONG onCreate() HOẶC onViewCreated().
     */
    private fun setupSaveConfirmDialogListener() {
        supportFragmentManager.setFragmentResultListener(
            SaveConfirmDialogFragment.REQUEST_KEY,
            this // Nếu dùng trong Fragment thì thay 'this' bằng 'viewLifecycleOwner'
        ) { _, bundle ->
            when (bundle.getString(SaveConfirmDialogFragment.EXTRA_ACTION)) {
                SaveConfirmDialogFragment.ACTION_SAVE -> {
                    handleSaveProjectAction()
                }
                SaveConfirmDialogFragment.ACTION_DISCARD -> {
                    handleDiscardChangesAction()
                }
                SaveConfirmDialogFragment.ACTION_CANCEL -> {
                    // Người dùng chọn "Hủy" -> Tiếp tục ở lại chỉnh sửa
                }
            }
        }
    }

    private fun handleSaveProjectAction() {
        val projectId = currentProjectId ?: "proj_${System.currentTimeMillis()}".also { currentProjectId = it }
        val view = binding.zoomableView

        val styledBitmap: Bitmap?
        val exportBitmap: Bitmap?
        if (isProjectMode) {
            styledBitmap = sharedViewModel.styleBitmaps[sharedViewModel.currentStyle.value ?: StickerStyle.ORIGINAL]
            exportBitmap = view.renderProjectBitmap()
        } else {
            val base = sharedViewModel.styleBitmaps[sharedViewModel.currentStyle.value ?: StickerStyle.ORIGINAL]
            val finalBitmap = if (base != null && view.hasDecors()) view.renderCompositeBitmap(base) else base
            styledBitmap = finalBitmap
            exportBitmap = finalBitmap ?: captureCurrentBitmap()
        }

        pendingProject = true
        pendingExport = false
        exportResultUri = null
        val aiFrames = if (isProjectMode) view.renderAiFrames() else null
        if (aiFrames != null) {
            // Sticker động từ AI: ghép từng frame video + chữ/decor rồi xuất WebP động
            pendingExport = true
            aiViewModel.exportAnimated(aiFrames, aiViewModel.frameDelayMs)
        } else if (exportBitmap != null) {
            pendingExport = true
            sharedViewModel.saveCurrentSticker(exportBitmap, currentProjectId)
        }

        val currentStyle = sharedViewModel.currentStyle.value ?: StickerStyle.ORIGINAL
        val anim = sharedViewModel.currentAnimation.value ?: StickerAnimationType.NONE

        val border = if (sharedViewModel.imagesReady.value == true) {
            BorderStyle(
                thickness = sharedViewModel.currentBorderThickness.value?.toFloat() ?: 30f,
                spacing = sharedViewModel.currentBorderDistance.value?.toFloat() ?: 20f,
                colorArgb = sharedViewModel.currentBorderColor.value ?: Color.WHITE
            )
        } else null

        val originalCore = sharedViewModel.coreBitmaps[StickerStyle.ORIGINAL]
        val cartoonCore = sharedViewModel.coreBitmaps[StickerStyle.CARTOON]

        val appliedEntries = aiViewModel.getAppliedEntries()
        val hasUndoneAi = aiViewModel.getUndoneEntriesCount() > 0

        editViewModel.saveProjectWithDetails(
            context = this,
            projectId = projectId,
            border = border,
            animationType = anim,
            selectedStyle = currentStyle,
            originalCoreBitmap = originalCore,
            cartoonCoreBitmap = cartoonCore,
            styledBitmap = styledBitmap,
            thumbnailBitmap = captureCurrentBitmap(),
            aiEntries = appliedEntries,
            hasUndoneAi = hasUndoneAi
        )
    }

    private fun handleDiscardChangesAction() {
        isDiscardingChanges = true // Bật cờ để onStop() bỏ qua việc lưu thumbnail
        finish() // Thoát ngay lập tức, trả lại trạng thái gốc
    }

    private fun loadBitmapFromPath(context: Context, path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        return try {
            context.assets.open(path).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (_: Exception) {
            try {
                val file = File(path)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else {
                    val uri = Uri.parse(path)
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}