package com.jetpack.stickify.presentation.ui.edit_sticker

import android.annotation.SuppressLint
import android.content.Context
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
import com.jetpack.stickify.domain.model.StickerAnimationType
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.jetpack.stickify.domain.model.TextAlign
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.activity.OnBackPressedCallback // Nhớ thêm import này
import com.jetpack.stickify.presentation.ui.edit_sticker.cancel.SaveConfirmDialogFragment

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
            currentProjectId = projectId
            editViewModel.loadProject(projectId)
            return
        }

        // 2. Kiểm tra nếu mở từ CutoutActivity qua Uri (Legacy Flow)
        val uri: Uri? = intent.getParcelableExtra(EXTRA_CROPPED_IMAGE_URI)
        if (uri != null) {
            currentProjectId = "proj_${System.currentTimeMillis()}"
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
        // Observers cho SharedViewModel
        sharedViewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        sharedViewModel.currentStyle.observe(this) { style ->
            val bitmap = sharedViewModel.styleBitmaps[style]
            if (bitmap != null) {
                binding.zoomableView.setBitmap(bitmap, animate = true)
            }
        }

        sharedViewModel.historyState.observe(this) { (canUndo, canRedo) ->
            updateUndoRedoButtons(canUndo, canRedo)
        }

        sharedViewModel.saveSuccessEvent.observe(this) { savedUriString ->
            if (savedUriString != null) {
                val resultUri = Uri.parse(savedUriString)
                setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT_URI, resultUri))
                Toast.makeText(this, "Đã tạo sticker thành công!", Toast.LENGTH_SHORT).show()
                finish()
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

        // Observer cho StickerEditViewModel
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                editViewModel.uiState.collect { state ->
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

    private fun updateUndoRedoButtons(canUndo: Boolean, canRedo: Boolean) {
        val legacyMode = currentProjectId == null
        val canUndoEffective = if (legacyMode) true else canUndo
        binding.btnUndo.isEnabled = canUndoEffective
        binding.btnUndo.alpha = if (canUndoEffective) 1f else 0.35f

        binding.btnRedo.isEnabled = canRedo
        binding.btnRedo.alpha = if (canRedo) 1f else 0.35f
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { showSaveConfirmationDialog() }

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
                if (currentProjectId != null) {
                    if (editingTextLayerId != null) {
                        editViewModel.updateTextLayer(editingTextLayerId!!, input, currentTextColor, currentTextAlign)
                    } else {
                        editViewModel.addTextLayer(input, currentTextColor, currentTextAlign)
                    }
                }
                binding.zoomableView.addTextItem(
                    text = input,
                    textColor = currentTextColor,
                    existingId = editingTextLayerId
                )
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
        // Lắng nghe Double Tap từ luồng mới (TextLayer)
        binding.zoomableView.onTextLayerDoubleTapped = { textLayer ->
            editingTextLayerId = textLayer.id
            currentTextColor = textLayer.colorArgb
            currentTextAlign = textLayer.align

            showTextInputOverlay(textLayer.content, textLayer.align)
        }

        // Lắng nghe Double Tap từ luồng cũ (DecorItemState)
        binding.zoomableView.onTextDecorDoubleTapped = { decor ->
            editingTextLayerId = decor.id
            currentTextColor = decor.textColor ?: Color.WHITE
            currentTextAlign = TextAlign.CENTER // Mặc định do luồng cũ vẽ chữ căn giữa

            showTextInputOverlay(decor.textContent ?: "", TextAlign.CENTER)
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

    private fun syncBorderToEditViewModel() {
        if (currentProjectId != null) {
            val thickness = sharedViewModel.currentBorderThickness.value?.toFloat() ?: 30f
            val distance = sharedViewModel.currentBorderDistance.value?.toFloat() ?: 20f
            val color = sharedViewModel.currentBorderColor.value ?: Color.WHITE
            editViewModel.updateBorder(thickness, distance, color)
        }
    }

    private fun handleSaveProjectAction() {
        val projectId = currentProjectId ?: "proj_${System.currentTimeMillis()}".also { currentProjectId = it }
        captureThumbnailIfValid(projectId)

        val baseBitmap = sharedViewModel.styleBitmaps[sharedViewModel.currentStyle.value ?: StickerStyle.ORIGINAL]
        val finalBitmap = if (baseBitmap != null && binding.zoomableView.hasDecors()) {
            binding.zoomableView.renderCompositeBitmap(baseBitmap)
        } else {
            baseBitmap ?: binding.zoomableView.captureToBitmap()
        }

        if (finalBitmap != null) {
            sharedViewModel.saveCurrentSticker(finalBitmap)
        }

        val thickness = sharedViewModel.currentBorderThickness.value?.toFloat() ?: 30f
        val distance = sharedViewModel.currentBorderDistance.value?.toFloat() ?: 20f
        val color = sharedViewModel.currentBorderColor.value ?: Color.WHITE
        val anim = sharedViewModel.currentAnimation.value ?: StickerAnimationType.NONE

        editViewModel.saveProjectWithDetails(
            projectId = projectId,
            borderThickness = thickness,
            borderDistance = distance,
            borderColor = color,
            animationType = anim
        )
    }

    private fun captureThumbnailIfValid(projectId: String) {
        val canvasView = binding.zoomableView
        if (canvasView.width > 0 && canvasView.height > 0) {
            runCatching {
                val bitmap = canvasView.captureToBitmap()
                editViewModel.captureAndSaveThumbnail(projectId, bitmap)
            }.onFailure { e ->
                e.printStackTrace()
            }
        }
    }

    private fun handleDiscardChangesAction() {
        isDiscardingChanges = true // Bật cờ để onStop() bỏ qua việc lưu thumbnail
        finish() // Thoát ngay lập tức, trả lại trạng thái gốc
    }
}
