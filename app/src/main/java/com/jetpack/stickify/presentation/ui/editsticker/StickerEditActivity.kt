package com.jetpack.stickify.presentation.ui.editsticker

import com.jetpack.stickify.R
import com.jetpack.stickify.presentation.ui.cutimage.ImageUtils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Màn hình "Chỉnh sửa" sticker:
 * - Nhận ảnh đã cắt qua EXTRA_CROPPED_IMAGE_URI (từ CutoutActivity).
 * - Preview có thể pinch-zoom/pan tự do (ZoomableStickerView).
 * - 3 kiểu đề xuất "Giữ nguyên / Viền ngoài / Hoạt hình" được TÍNH TRƯỚC 1 lần khi vào màn
 *   hình, nên khi bấm chuyển kiểu, ảnh preview đổi ngay + có animation crossfade mượt mà,
 *   không phải chờ tính toán lại.
 * - Có lịch sử chọn kiểu để Undo/Redo.
 */
class StickerEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CROPPED_IMAGE_URI = "extra_cropped_image_uri"
        const val EXTRA_RESULT_URI = "extra_result_uri"

        private const val BORDER_WIDTH_PX = 22f
    }

    private enum class StickerStyle(val label: String) {
        ORIGINAL("Giữ nguyên"),
        BORDER("Viền ngoài"),
        CARTOON("Hoạt hình")
    }

    private lateinit var zoomableView: ZoomableStickerView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnBack: ImageButton
    private lateinit var btnCreate: Button
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var suggestedStylesRow: LinearLayout
    private lateinit var categoryTabsRow: LinearLayout

    private val styleBitmaps = mutableMapOf<StickerStyle, Bitmap>()
    private val styleItemViews = mutableMapOf<StickerStyle, View>()

    // Lịch sử để Undo/Redo (chỉ áp dụng cho việc đổi kiểu, không bao gồm zoom/pan).
    private val history = mutableListOf(StickerStyle.ORIGINAL)
    private var historyIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sticker_edit)

        zoomableView = findViewById(R.id.zoomableView)
        progressBar = findViewById(R.id.progressBar)
        btnBack = findViewById(R.id.btnBack)
        btnCreate = findViewById(R.id.btnCreate)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        suggestedStylesRow = findViewById(R.id.suggestedStylesRow)
        categoryTabsRow = findViewById(R.id.categoryTabsRow)

        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        btnUndo.setOnClickListener { moveHistory(-1) }
        btnRedo.setOnClickListener { moveHistory(1) }
        btnCreate.setOnClickListener { onCreateClicked() }

        setupPromptRow()
        setupCategoryTabs()
        updateUndoRedoEnabled()

        val uri: Uri? = intent.getParcelableExtra(EXTRA_CROPPED_IMAGE_URI)
        if (uri == null) {
            Toast.makeText(this, "Không nhận được ảnh đầu vào", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        loadAndPrepareStyles(uri)
    }

    private fun setupPromptRow() {
        val btnSend: ImageButton = findViewById(R.id.btnSendPrompt)
        btnSend.setOnClickListener {
            // TODO: nối API sinh sticker động bằng AI khi có backend.
            Toast.makeText(this, "Tính năng tạo sticker AI đang được phát triển", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupCategoryTabs() {
        val tabs = listOf("Đề xuất", "Chữ", "Hiệu ứng", "Trang trí", "Viền")
        tabs.forEachIndexed { index, label ->
            val tv = TextView(this).apply {
                text = label
                textSize = 13f
                setPadding(dp(16), dp(8), dp(16), dp(8))
                setTextColor(if (index == 0) Color.parseColor("#2196F3") else Color.parseColor("#666666"))
                setOnClickListener {
                    if (index != 0) {
                        // Các tab khác chưa có nội dung thật, chỉ là placeholder trong bản này.
                        Toast.makeText(this@StickerEditActivity, "$label: sắp ra mắt", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            categoryTabsRow.addView(tv)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun loadAndPrepareStyles(uri: Uri) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val original = withContext(Dispatchers.IO) {
                    val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                        ?: throw IllegalArgumentException("Không đọc được ảnh")
                }

                val (border, cartoon) = withContext(Dispatchers.Default) {
                    val b = StickerStyleProcessor.addOuterBorder(original, Color.WHITE, BORDER_WIDTH_PX)
                    val c = StickerStyleProcessor.cartoonify(original)
                    b to c
                }

                styleBitmaps[StickerStyle.ORIGINAL] = original
                styleBitmaps[StickerStyle.BORDER] = border
                styleBitmaps[StickerStyle.CARTOON] = cartoon

                buildSuggestedStylesRow()
                zoomableView.setBitmap(original, animate = false)
                selectStyle(StickerStyle.ORIGINAL, addToHistory = false)
            } catch (e: Exception) {
                Toast.makeText(this@StickerEditActivity, "Lỗi khi xử lý ảnh: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun buildSuggestedStylesRow() {
        suggestedStylesRow.removeAllViews()
        styleItemViews.clear()

        val inflater = LayoutInflater.from(this)
        for (style in StickerStyle.values()) {
            val bitmap = styleBitmaps[style] ?: continue
            val itemView = inflater.inflate(R.layout.item_suggested_style, suggestedStylesRow, false)

            val ivThumb: ImageView = itemView.findViewById(R.id.ivStyleThumb)
            val tvLabel: TextView = itemView.findViewById(R.id.tvStyleLabel)

            ivThumb.setImageBitmap(bitmap)
            tvLabel.text = style.label
            itemView.setOnClickListener { onStyleClicked(style) }

            styleItemViews[style] = itemView
            suggestedStylesRow.addView(itemView)
        }
        updateSelectedStyleUi(StickerStyle.ORIGINAL)
    }

    private fun onStyleClicked(style: StickerStyle) {
        selectStyle(style, addToHistory = true)
    }

    private fun selectStyle(style: StickerStyle, addToHistory: Boolean) {
        val bitmap = styleBitmaps[style] ?: return
        // Phần preview phía trên đổi theo, có animation crossfade mượt (giữ nguyên zoom/pan).
        zoomableView.setBitmap(bitmap, animate = true)
        updateSelectedStyleUi(style)

        if (addToHistory) {
            // Cắt bỏ phần "redo" cũ nếu người dùng chọn kiểu mới sau khi đã undo.
            while (history.size > historyIndex + 1) history.removeAt(history.size - 1)
            history.add(style)
            historyIndex = history.size - 1
            updateUndoRedoEnabled()
        }
    }

    private fun updateSelectedStyleUi(selected: StickerStyle) {
        for ((style, view) in styleItemViews) {
            val frame = (view as LinearLayout).getChildAt(0) as FrameLayout
            frame.setBackgroundResource(
                if (style == selected) R.drawable.bg_style_thumb_frame_selected else R.drawable.bg_style_thumb_frame
            )
        }
    }

    private fun moveHistory(delta: Int) {
        val newIndex = historyIndex + delta
        if (newIndex !in history.indices) return
        historyIndex = newIndex
        selectStyle(history[historyIndex], addToHistory = false)
        updateUndoRedoEnabled()
    }

    private fun updateUndoRedoEnabled() {
        btnUndo.isEnabled = historyIndex > 0
        btnUndo.alpha = if (btnUndo.isEnabled) 1f else 0.35f
        btnRedo.isEnabled = historyIndex < history.size - 1
        btnRedo.alpha = if (btnRedo.isEnabled) 1f else 0.35f
    }

    private fun onCreateClicked() {
        val currentStyle = history.getOrNull(historyIndex) ?: StickerStyle.ORIGINAL
        val bitmap = styleBitmaps[currentStyle] ?: return

        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    ImageUtils.saveBitmapAndGetUri(this@StickerEditActivity, bitmap, "sticker_${System.currentTimeMillis()}.png")
                }
                setResult(RESULT_OK, android.content.Intent().putExtra(EXTRA_RESULT_URI, uri))
                Toast.makeText(this@StickerEditActivity, "Đã tạo sticker", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@StickerEditActivity, "Lỗi khi lưu sticker: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
}