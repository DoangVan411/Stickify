package com.jetpack.stickify.presentation.ui.edit_sticker.text

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.jetpack.stickify.databinding.LayoutAddTextBinding
import com.jetpack.stickify.domain.model.TextAlign

/**
 * Điều khiển toàn bộ màn hình nhập chữ (layout_add_text):
 *  - hiện/ẩn overlay + bàn phím, nhận chữ mới hoặc sửa chữ có sẵn;
 *  - btnTextColor: bấm để chuyển qua lại giữa danh sách FONT (mặc định) và bảng MÀU;
 *  - btnTextBackground: xoay vòng kiểu chữ  không nền -> nền theo màu -> viền theo màu;
 *  - btnTextAlign: xoay vòng căn lề giữa -> trái -> phải;
 *  - etOverlayText luôn hiển thị đúng kiểu đang chọn (cùng quy tắc với ZoomableStickerView
 *    thông qua [TextStyleRenderer]).
 *
 * Kết quả được trả về qua [onDone]: null nghĩa là không có chữ nào (để trống) -> không thêm/sửa gì.
 */
class TextInputOverlayController(
    private val binding: LayoutAddTextBinding,
    private val onDone: (TextEditResult?) -> Unit
) {

    data class TextEditResult(
        /** null = chữ mới; khác null = đang sửa layer có id này. */
        val layerId: String?,
        val text: String,
        /** Màu người dùng chọn (màu chữ / màu nền / màu viền tùy kiểu). */
        val colorArgb: Int,
        val align: TextAlign,
        val style: TextStyleSpec
    )

    private val context: Context get() = binding.root.context
    private val editText get() = binding.etOverlayText
    private val density get() = context.resources.displayMetrics.density

    // ---- Trạng thái đang chỉnh ----
    private var editingLayerId: String? = null
    private var color = Color.WHITE
    private var align = TextAlign.CENTER
    private var font = TextFont.DEFAULT
    private var background = TextBackgroundMode.NONE
    private var isColorPaletteShown = false

    private val colorDots = mutableListOf<Pair<Int, View>>()
    private val fontPills: List<Pair<TextView, TextFont>> = listOf(
        binding.fontDefault to TextFont.DEFAULT,
        binding.fontTypewriter to TextFont.TYPEWRITER,
        binding.fontBold to TextFont.BOLD
    )

    // Background gốc của 2 nút (ripple) để khôi phục khi tắt trạng thái active
    private val colorButtonDefaultBg: Drawable? = binding.btnTextColor.background
    private val backgroundButtonDefaultBg: Drawable? = binding.btnTextBackground.background

    val isShowing: Boolean get() = binding.textInputOverlay.visibility == View.VISIBLE

    init {
        buildColorPalette()
        setupClicks()
        refreshAll()
    }

    // ------------------------------------------------------------------ Public API

    /** Mở màn hình nhập chữ mới (kiểu mặc định). */
    fun showNew() {
        show(
            layerId = null,
            text = "",
            colorArgb = Color.WHITE,
            align = TextAlign.CENTER,
            style = TextStyleSpec.DEFAULT
        )
    }

    /** Mở màn hình sửa chữ có sẵn, giữ nguyên màu/căn lề/font/nền của chữ đó. */
    fun showEdit(layerId: String, text: String, colorArgb: Int, align: TextAlign, style: TextStyleSpec) {
        show(layerId, text, colorArgb, align, style)
    }

    /** Hoàn tất: ẩn overlay và trả kết quả (hoặc null nếu để trống). */
    fun commit() {
        val input = editText.text.toString().trim()
        val result = if (input.isNotEmpty()) {
            TextEditResult(
                layerId = editingLayerId,
                text = input,
                colorArgb = color,
                align = align,
                style = TextStyleSpec(font, background)
            )
        } else null
        hide()
        onDone(result)
    }

    // ------------------------------------------------------------------ Hiện / ẩn

    private fun show(layerId: String?, text: String, colorArgb: Int, align: TextAlign, style: TextStyleSpec) {
        editingLayerId = layerId
        color = colorArgb
        this.align = align
        font = style.font
        background = style.background
        // Luôn mở ở danh sách font (trạng thái "chưa bấm" nút màu)
        isColorPaletteShown = false

        binding.textInputOverlay.visibility = View.VISIBLE
        editText.setText(text)
        editText.setSelection(text.length)
        refreshAll()

        editText.requestFocus()
        editText.postDelayed({
            imm().showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    private fun hide() {
        binding.textInputOverlay.visibility = View.GONE
        editText.clearFocus()
        imm().hideSoftInputFromWindow(editText.windowToken, 0)
        editingLayerId = null
    }

    private fun imm() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    // ------------------------------------------------------------------ Sự kiện

    private fun setupClicks() {
        // Bấm ra ngoài vùng nhập -> hoàn tất
        binding.textInputOverlay.setOnClickListener { commit() }

        // Chặn click "thủng" xuống overlay phía sau
        editText.setOnClickListener { /* consume */ }
        binding.llTextBottomTools.setOnClickListener { /* consume */ }
        binding.fontScrollView.setOnClickListener { /* consume */ }
        binding.colorScrollView.setOnClickListener { /* consume */ }
        binding.llTextTopTools.setOnClickListener { /* consume */ }

        // Nút màu: chưa bấm -> hiện font; bấm -> hiện bảng màu; bấm nữa -> quay lại font
        binding.btnTextColor.setOnClickListener {
            isColorPaletteShown = !isColorPaletteShown
            refreshBottomPanel()
            refreshToolButtons()
        }

        // Nút nền chữ: không nền -> nền theo màu -> viền theo màu -> ...
        binding.btnTextBackground.setOnClickListener {
            background = background.next()
            refreshAll()
        }

        // Nút căn lề: giữa -> trái -> phải -> giữa
        binding.btnTextAlign.setOnClickListener {
            align = when (align) {
                TextAlign.CENTER -> TextAlign.LEFT
                TextAlign.LEFT -> TextAlign.RIGHT
                TextAlign.RIGHT -> TextAlign.CENTER
            }
            refreshEditText()
        }

        // Font
        fontPills.forEach { (pill, f) ->
            pill.typeface = TextStyleRenderer.typefaceOf(f) // xem trước font ngay trên nút
            pill.setOnClickListener {
                font = f
                refreshAll()
            }
        }
    }

    // ------------------------------------------------------------------ Bảng màu

    private fun buildColorPalette() {
        val palette = listOf(
            "#FFFFFF", "#000000", "#FF3B30", "#FF9500", "#FFCC00", "#34C759", "#00C7BE",
            "#1B85F3", "#5856D6", "#AF52DE", "#FF2D55", "#A2845E", "#8E8E93"
        ).map { Color.parseColor(it) }

        val container: LinearLayout = binding.llColorContainer
        container.removeAllViews()
        colorDots.clear()

        val size = (36 * density).toInt()
        val margin = (6 * density).toInt()
        palette.forEach { c ->
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(margin, margin, margin, margin)
                }
                setOnClickListener {
                    color = c
                    refreshAll()
                }
            }
            container.addView(dot)
            colorDots.add(c to dot)
        }
    }

    private fun refreshColorDots() {
        val ring = (2 * density)
        colorDots.forEach { (c, dot) ->
            val selected = c == color
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c)
                // Viền trắng dày cho ô đang chọn; ô màu trắng/đen thêm viền mảnh để vẫn nhìn thấy trên nền tối
                setStroke(
                    if (selected) (3 * density).toInt() else ring.toInt(),
                    if (selected) Color.WHITE else Color.parseColor("#66FFFFFF")
                )
            }
            dot.scaleX = if (selected) 1.15f else 1f
            dot.scaleY = if (selected) 1.15f else 1f
        }
    }

    // ------------------------------------------------------------------ Cập nhật giao diện

    private fun refreshAll() {
        refreshEditText()
        refreshBottomPanel()
        refreshToolButtons()
        refreshFontPills()
        refreshColorDots()
    }

    private fun refreshBottomPanel() {
        binding.fontScrollView.visibility = if (isColorPaletteShown) View.GONE else View.VISIBLE
        binding.colorScrollView.visibility = if (isColorPaletteShown) View.VISIBLE else View.GONE
    }

    private fun refreshFontPills() {
        fontPills.forEach { (pill, f) -> pill.alpha = if (f == font) 1f else 0.5f }
    }

    /** etOverlayText hiển thị đúng kiểu như sẽ vẽ trên ZoomableStickerView. */
    private fun refreshEditText() {
        val et = editText
        val sizePx = et.textSize
        val colors = TextStyleRenderer.resolveColors(color, background)

        et.typeface = TextStyleRenderer.typefaceOf(font)
        et.setTextColor(colors.text)
        et.setHintTextColor(ColorUtils.setAlphaComponent(colors.text, 0x99))
        et.gravity = when (align) {
            TextAlign.LEFT -> Gravity.START or Gravity.CENTER_VERTICAL
            TextAlign.CENTER -> Gravity.CENTER
            TextAlign.RIGHT -> Gravity.END or Gravity.CENTER_VERTICAL
        }

        et.background = when (background) {
            TextBackgroundMode.NONE -> null
            TextBackgroundMode.FILL -> GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = TextStyleRenderer.cornerRadius(sizePx)
                setColor(colors.fill ?: Color.TRANSPARENT)
            }
            TextBackgroundMode.OUTLINE -> GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = TextStyleRenderer.cornerRadius(sizePx)
                setColor(Color.TRANSPARENT)
                setStroke(
                    TextStyleRenderer.strokeWidth(sizePx).toInt().coerceAtLeast(1),
                    colors.stroke ?: Color.TRANSPARENT
                )
            }
        }
        // Padding luôn cố định (kể cả khi không có nền) để chữ không bị nhảy khi đổi kiểu
        et.setPadding(
            TextStyleRenderer.padX(sizePx).toInt(),
            TextStyleRenderer.padY(sizePx).toInt(),
            TextStyleRenderer.padX(sizePx).toInt(),
            TextStyleRenderer.padY(sizePx).toInt()
        )
    }

    private fun refreshToolButtons() {
        // Nút màu: có vòng tròn khi đang hiện bảng màu
        setToolButton(
            button = binding.btnTextColor,
            background = if (isColorPaletteShown) {
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke((1.5f * density).toInt(), Color.WHITE)
                }
            } else colorButtonDefaultBg,
            iconTint = null,
            alpha = if (isColorPaletteShown) 1f else 0.8f
        )

        // Nút nền chữ: hình dạng nút phản ánh kiểu đang chọn
        when (background) {
            TextBackgroundMode.NONE -> setToolButton(
                binding.btnTextBackground, backgroundButtonDefaultBg, iconTint = null, alpha = 0.8f
            )
            TextBackgroundMode.FILL -> setToolButton(
                binding.btnTextBackground,
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 6 * density
                    setColor(Color.WHITE)
                },
                iconTint = Color.parseColor("#111111"),
                alpha = 1f
            )
            TextBackgroundMode.OUTLINE -> setToolButton(
                binding.btnTextBackground,
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 6 * density
                    setColor(Color.TRANSPARENT)
                    setStroke((1.5f * density).toInt(), Color.WHITE)
                },
                iconTint = Color.WHITE,
                alpha = 1f
            )
        }
    }

    private fun setToolButton(button: ImageButton, background: Drawable?, iconTint: Int?, alpha: Float) {
        val pad = (3 * density).toInt()
        button.background = background
        button.setPadding(pad, pad, pad, pad)
        if (iconTint != null) button.setColorFilter(iconTint) else button.clearColorFilter()
        button.alpha = alpha
    }
}