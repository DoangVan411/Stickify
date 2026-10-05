package com.jetpack.stickify.presentation.ui.edit_sticker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jetpack.stickify.R
import com.jetpack.stickify.databinding.ActivityStickerEditBinding
import com.jetpack.stickify.domain.model.StickerStyle
import com.jetpack.stickify.presentation.ui.edit_sticker.border.BorderToolFragment
import com.jetpack.stickify.presentation.ui.edit_sticker.custom_view.EditorPanelView
import com.jetpack.stickify.presentation.ui.edit_sticker.decor.DecorToolFragment
import com.jetpack.stickify.presentation.ui.edit_sticker.effect.EffectToolFragment
import com.jetpack.stickify.presentation.ui.edit_sticker.suggestion.SuggestionFragment
import com.jetpack.stickify.presentation.ui.edit_sticker.text.TextToolFragment
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import com.jetpack.stickify.domain.model.BorderStyle
import com.jetpack.stickify.domain.model.StickerAnimationType
import com.jetpack.stickify.domain.model.SubjectLayer
import com.jetpack.stickify.domain.model.TextAlign
import com.jetpack.stickify.presentation.ui.edit_sticker.cancel.SaveConfirmDialogFragment
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
    private var borderToolFragment: BorderToolFragment? = null
    private var decorToolFragment: DecorToolFragment? = null
    private var effectToolFragment: EffectToolFragment? = null

    private var currentProjectId: String? = null

    // Thêm biến state để lưu trạng thái chữ hiện tại
    private var currentTextAlign: TextAlign = TextAlign.CENTER
    private var currentTextColor = Color.WHITE
    private var editingTextLayerId: String? = null

    private var previousTabIndex = 0

    // Thêm cờ này để chặn onStop lưu thumbnail nếu chọn "Không lưu"
    private var isDiscardingChanges = false

    // true = mở project có sẵn từ DB (render bằng layer). false = luồng mới từ CutoutActivity (render bằng bitmap style).
    // KHÔNG dùng `currentProjectId != null` để phân biệt vì id luôn được gán ở cả 2 luồng.
    private var isProjectMode = false

    // Chỉ finish() khi CẢ export ảnh/GIF lẫn lưu project đã xong
    private var pendingExport = false
    private var pendingProject = false
    private var exportResultUri: Uri? = null

    // Thêm biến isNewProject và gán mặc định isProjectMode = true
    private var isNewProject = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_sticker_edit)

        setupClickListeners()
        setupObservers()
        setupEditorTabMenu()
        setupTextOverlayLogic()
        setupBackPressHandler() //  Đăng ký Back stack
        setupSaveConfirmDialogListener()// setup comfirm

        // 1. Kiểm tra nếu có projectId chuyển sang từ RecentProjectAdapter (Room DB Clean Architecture flow)
        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID)
        if (!projectId.isNullOrBlank()) {
            isProjectMode = true
            isNewProject = false
            currentProjectId = projectId
            editViewModel.loadProject(projectId)
            return
        }

        // 2. Kiểm tra nếu mở từ CutoutActivity qua Uri (Legacy Flow)
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
        if (uri != null) setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT_URI, uri)) else setResult(RESULT_OK)
        Toast.makeText(this, "Đã lưu sticker thành công!", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun setupObservers() {
        // Observers cho SharedViewModel
        sharedViewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        sharedViewModel.currentStyle.observe(this) { style ->
            val bitmap = sharedViewModel.styleBitmaps[style]
            if (bitmap != null) {
                if (isProjectMode) {
                    // Cập nhật ảnh Preview trực tiếp vào SubjectLayer thay vì ghi đè toàn bộ View
                    val subjectLayerId = editViewModel.uiState.value.editorSession.content.layers
                        .find { it is SubjectLayer }?.id
                    if (subjectLayerId != null) {
                        binding.zoomableView.preloadLayerBitmap(subjectLayerId, bitmap)
                    }
                } else {
                    binding.zoomableView.setBitmap(bitmap, animate = true)
                }
            }
        }

        sharedViewModel.historyState.observe(this) { (canUndo, canRedo) ->
            updateUndoRedoButtons(canUndo, canRedo)
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
                binding.zoomableView.setDrawDecorBrush(null)
                val bitmap = decor.customBitmap ?: BitmapFactory.decodeResource(resources, decor.resId)
                if (bitmap != null) {
                    if (isProjectMode) {
                        // Project: decor là DecorationLayer (lưu DB + undo/redo), không chỉ là decor tạm trong view
                        val layerId = "layer_dec_${java.util.UUID.randomUUID()}"
                        binding.zoomableView.preloadLayerBitmap(layerId, bitmap)
                        editViewModel.addDecorationFromBitmap(this, bitmap, layerId)
                    } else {
                        binding.zoomableView.addDecorBitmap(bitmap, decor.id)
                    }
                }
            }
        }

        // Vẽ trang trí theo cử chỉ kéo
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
                        binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                        updateUndoRedoButtons(state.canUndo, state.canRedo)
                        // BƯỚC BỊ THIẾU trước đây: đẩy nội dung project (các layer) vào view để vẽ.
                        if (!state.isLoading) {
                            binding.zoomableView.setProjectContent(
                                state.editorSession.content,
                                editViewModel.assetLoader
                            )
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
                editViewModel.undo()
            } else {
                if (binding.zoomableView.undoLastDrawDecorStroke()) {
                    return@setOnClickListener
                }
                sharedViewModel.moveHistory(-1)
            }
        }

        binding.btnRedo.setOnClickListener {
            if (isProjectMode) {
                editViewModel.redo()
            } else {
                sharedViewModel.moveHistory(1)
            }
        }

        // Gọi ViewModel thực hiện lưu ảnh (ghép decor nếu có, xuất GIF nếu có animation) hoặc lưu project
        binding.btnCreate.setOnClickListener {
            handleSaveProjectAction()
        }

        binding.btnSendPrompt.setOnClickListener {
            Toast.makeText(this, "Tính năng tạo sticker AI đang được phát triển", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTextOverlayLogic() {
        val layoutText = binding.addTextLayout

        setupDoubleTapToEditText()

        // 1. Logic hoàn tất khi BẤM RA NGOÀI nền đen (textInputOverlay)
        layoutText.textInputOverlay.setOnClickListener {
            val input = layoutText.etOverlayText.text.toString().trim()

            if (input.isNotEmpty()) {
                if (isProjectMode) {
                    // Project: chữ là TextLayer (có Undo/Redo, được lưu vào DB)
                    val id = editingTextLayerId
                    if (id != null) {
                        editViewModel.updateTextLayer(id, input, currentTextColor, currentTextAlign)
                    } else {
                        editViewModel.addTextLayer(input, currentTextColor, currentTextAlign)
                    }
                } else {
                    // Luồng mới: chữ là decor trong view, sẽ được "nướng" vào ảnh khi lưu.
                    // (Trước đây gọi cả 2 -> chữ bị nhân đôi.)
                    binding.zoomableView.addTextItem(
                        text = input,
                        textColor = currentTextColor,
                        existingId = editingTextLayerId
                    )
                }
            }

            layoutText.textInputOverlay.visibility = View.GONE
            layoutText.etOverlayText.clearFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(layoutText.etOverlayText.windowToken, 0)

            binding.editorPanel.selectTab(previousTabIndex)
            editingTextLayerId = null // Reset ID sau khi lưu xong
        }

        // 2. Chặn sự kiện click thủng (Nếu bấm vào EditText hoặc ScrollView thì không bị tắt)
        layoutText.etOverlayText.setOnClickListener { /* Consume click */ }
        layoutText.fontScrollView.setOnClickListener { /* Consume click */ }
        layoutText.llTextTopTools.setOnClickListener { /* Consume click */ }

        // 3. Xử lý Căn lề (Xoay vòng: Giữa -> Trái -> Phải -> Giữa...)
        layoutText.btnTextAlign.setOnClickListener {
            val nextGravity = when (layoutText.etOverlayText.gravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
                Gravity.CENTER_HORIZONTAL -> Gravity.START
                Gravity.START, Gravity.LEFT -> Gravity.END
                else -> Gravity.CENTER_HORIZONTAL
            }

            layoutText.etOverlayText.gravity = nextGravity

            // Dùng hàm extension .toTextAlign() để chuyển đổi an toàn sang Domain model
            currentTextAlign = nextGravity.toTextAlign()
        }

        // 4. Xử lý đổi Font chữ
        layoutText.fontDefault.setOnClickListener {
            layoutText.etOverlayText.typeface = Typeface.DEFAULT
        }
        layoutText.fontTypewriter.setOnClickListener {
            layoutText.etOverlayText.typeface = Typeface.MONOSPACE
        }
        layoutText.fontBold.setOnClickListener {
            layoutText.etOverlayText.typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun setupDoubleTapToEditText() {
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
        binding.zoomableView.onDecorStampsCommitted = { bitmap, stamps ->
            if (isProjectMode) editViewModel.addStampLayers(this, bitmap, stamps)
        }

        // Double tap vào chữ (ở cả 2 chế độ chữ đều là decor có textContent trong view)
        binding.zoomableView.onTextDecorDoubleTapped = { decor ->
            editingTextLayerId = decor.id
            val layer = if (isProjectMode) {
                editViewModel.uiState.value.editorSession.content.layers
                    .find { it.id == decor.id } as? com.jetpack.stickify.domain.model.TextLayer
            } else null
            currentTextColor = layer?.colorArgb ?: decor.textColor ?: Color.WHITE
            currentTextAlign = layer?.align ?: TextAlign.CENTER

            showTextInputOverlay(layer?.content ?: decor.textContent ?: "", currentTextAlign)
        }
    }

    // Tách phần hiển thị UI ra một hàm riêng để tái sử dụng
    private fun showTextInputOverlay(text: String, align: TextAlign) {
        val layoutText = binding.addTextLayout
        layoutText.textInputOverlay.visibility = View.VISIBLE
        layoutText.etOverlayText.setText(text)
        layoutText.etOverlayText.setSelection(text.length)

        layoutText.etOverlayText.gravity = when (align) {
            TextAlign.LEFT -> Gravity.START
            TextAlign.CENTER -> Gravity.CENTER
            TextAlign.RIGHT -> Gravity.END
        }

        layoutText.etOverlayText.requestFocus()
        layoutText.etOverlayText.postDelayed({
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(layoutText.etOverlayText, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
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
            "Chữ" -> {
                editingTextLayerId = null // Tạo mới hoàn toàn -> Reset ID
                currentTextAlign = TextAlign.CENTER // Mặc định căn giữa
                binding.addTextLayout.etOverlayText.gravity = Gravity.CENTER

                binding.addTextLayout.textInputOverlay.visibility = View.VISIBLE
                binding.addTextLayout.etOverlayText.setText("")
                binding.addTextLayout.etOverlayText.requestFocus()

                binding.addTextLayout.etOverlayText.postDelayed({
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(binding.addTextLayout.etOverlayText, InputMethodManager.SHOW_IMPLICIT)
                }, 100)
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

    private fun TextAlign.toGravity(): Int {
        return when (this) {
            TextAlign.LEFT -> Gravity.START
            TextAlign.CENTER -> Gravity.CENTER
            TextAlign.RIGHT -> Gravity.END
        }
    }

    private fun Int.toTextAlign(): TextAlign {
        return when (this) {
            Gravity.START, Gravity.LEFT -> TextAlign.LEFT
            Gravity.END, Gravity.RIGHT -> TextAlign.RIGHT
            else -> TextAlign.CENTER
        }
    }
    private fun setupBackPressHandler() {
        // Chặn sự kiện nút Back vật lý / vuốt Back của điện thoại
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showSaveConfirmationDialog()
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
            // Chỉ lấy styledBitmap nếu là project mới (để lưu viền/cartoon vào layer ảnh gốc)
            styledBitmap = if (isNewProject) sharedViewModel.styleBitmaps[sharedViewModel.currentStyle.value ?: StickerStyle.ORIGINAL] else null
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
        if (exportBitmap != null) {
            pendingExport = true
            sharedViewModel.saveCurrentSticker(exportBitmap)
        }

        // Nếu là mở project cũ, không ghi đè viền bằng giá trị mặc định của thanh công cụ
        val anim = sharedViewModel.currentAnimation.value ?: StickerAnimationType.NONE
        val border = if (!isNewProject) null else BorderStyle(
            thickness = sharedViewModel.currentBorderThickness.value?.toFloat() ?: 30f,
            spacing = sharedViewModel.currentBorderDistance.value?.toFloat() ?: 20f,
            colorArgb = sharedViewModel.currentBorderColor.value ?: Color.WHITE
        )

        editViewModel.saveProjectWithDetails(
            context = this,
            projectId = projectId,
            border = border,
            animationType = if (!isNewProject && anim == StickerAnimationType.NONE) null else anim,
            styledBitmap = styledBitmap,
            thumbnailBitmap = captureCurrentBitmap()
        )
    }

    private fun handleDiscardChangesAction() {
        isDiscardingChanges = true // Bật cờ để onStop() bỏ qua việc lưu thumbnail
        finish() // Thoát ngay lập tức, trả lại trạng thái gốc
    }
}