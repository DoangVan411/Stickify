package com.jetpack.stickify.presentation.ui.edit_sticker

import com.jetpack.stickify.R
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.jetpack.stickify.data.processor.StickerStyleProcessor
import com.jetpack.stickify.databinding.ActivityStickerEditBinding
import com.jetpack.stickify.presentation.ui.edit_sticker.custom_view.EditorPanelView
import com.jetpack.stickify.presentation.ui.edit_sticker.suggestion.SuggestionFragment
import com.jetpack.stickify.presentation.ui.edit_sticker.text.TextToolFragment
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels
import com.jetpack.stickify.presentation.ui.edit_sticker.border.BorderToolFragment

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
        const val EXTRA_CROPPED_IMAGE_URI = "extra_cropped_image_uri"
        const val EXTRA_RESULT_URI = "extra_result_uri"
    }

    private lateinit var binding: ActivityStickerEditBinding

    // Inject Shared ViewModel
    private val sharedViewModel: StickerSharedViewModel by viewModels()

    // Lưu trữ tham chiếu đến các Fragment để thực hiện logic Hide/Show
    private var suggestionFragment: SuggestionFragment? = null
    private var textToolFragment: TextToolFragment? = null
    private var borderToolFragment: BorderToolFragment? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this,R.layout.activity_sticker_edit)

        setupClickListeners()
        setupObservers()
        setupEditorTabMenu()

        // Bắt đầu quy trình xử lý ảnh từ Intent
        val uri: Uri? = intent.getParcelableExtra(EXTRA_CROPPED_IMAGE_URI)
        if (uri == null) {
            Toast.makeText(this, "Không nhận được ảnh đầu vào", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Đẩy Uri String xuống ViewModel (ViewModel sẽ gọi UseCase -> Repository xử lý)
        sharedViewModel.loadAndPrepareStyles(uri.toString())
    }

    private fun setupObservers() {
        // 1. Quản lý trạng thái Loading chung
        sharedViewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // 2. Lắng nghe thay đổi Style -> Đổi ảnh Preview
        sharedViewModel.currentStyle.observe(this) { style ->
            val bitmap = sharedViewModel.styleBitmaps[style]
            if (bitmap != null) {
                // crossfade animation giữ nguyên zoom/pan
                binding.zoomableView.setBitmap(bitmap, animate = true)
            }
        }

        // 3. Cập nhật UI của nút Undo/Redo
        sharedViewModel.historyState.observe(this) { (canUndo, canRedo) ->
            binding.btnUndo.isEnabled = canUndo
            binding.btnUndo.alpha = if (canUndo) 1f else 0.35f

            binding.btnRedo.isEnabled = canRedo
            binding.btnRedo.alpha = if (canRedo) 1f else 0.35f
        }

        // 4. Lắng nghe kết quả khi bấm "Tạo" (Save thành công)
        sharedViewModel.saveSuccessEvent.observe(this) { savedUriString ->
            if (savedUriString != null) {
                val resultUri = Uri.parse(savedUriString)
                setResult(RESULT_OK, android.content.Intent().putExtra(EXTRA_RESULT_URI, resultUri))
                Toast.makeText(this, "Đã tạo sticker thành công!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Gọi thẳng vào ViewModel để lùi/tiến Lịch sử
        binding.btnUndo.setOnClickListener { sharedViewModel.moveHistory(-1) }
        binding.btnRedo.setOnClickListener { sharedViewModel.moveHistory(1) }

        // Gọi ViewModel thực hiện lưu ảnh
        binding.btnCreate.setOnClickListener { sharedViewModel.saveCurrentSticker() }

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
        // Mặc định gọi Tab đầu tiên (position 0 - Đề xuất)
        switchFragment(0)
    }

    /**
     * Kỹ thuật HIDE/SHOW Fragments:
     * Thay vì `.replace()` sẽ tiêu hủy Fragment, ta dùng `.add()` lần đầu và `.hide()`/`.show()`
     * những lần sau. Nhờ vậy, khi user gõ Text, chọn Sticker... trạng thái giao diện bên dưới
     * không bao giờ bị mất hoặc giật (flicker).
     */
    private fun switchFragment(position: Int) {
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()

        // 1. Hide tất cả các fragment hiện có
        suggestionFragment?.let { transaction.hide(it) }
        textToolFragment?.let { transaction.hide(it) }
        borderToolFragment?.let{transaction.hide(it)}

        // 2. Show Fragment tương ứng với Position của Tab
        when (position) {
            0 -> {
                if (suggestionFragment == null) {
                    suggestionFragment = SuggestionFragment()
                    // Nên định nghĩa một FrameLayout id = featureContainer trong XML Activity
                    transaction.add(R.id.featureContainer, suggestionFragment!!, "SUGGESTION")
                } else {
                    transaction.show(suggestionFragment!!)
                }
            }
            1 -> {
                if (textToolFragment == null) {
                    textToolFragment = TextToolFragment() // Bạn cần tạo Fragment này sau
                    transaction.add(R.id.featureContainer, textToolFragment!!, "TEXT_TOOL")
                } else {
                    transaction.show(textToolFragment!!)
                }
            }
            4->{
                if (borderToolFragment == null) {
                    borderToolFragment = BorderToolFragment() // Bạn cần tạo Fragment này sau
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