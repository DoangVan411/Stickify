package com.jetpack.stickify.presentation.ui.edit_sticker.text


import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.jetpack.stickify.domain.model.TextAlign
import kotlin.math.ceil

/**
 * Nơi DUY NHẤT quyết định chữ + nền/viền trông như thế nào.
 * - EditText ở màn hình nhập chữ lấy màu, padding, bo góc, độ dày viền từ đây.
 * - ZoomableStickerView vẽ bitmap chữ bằng [render].
 * => Hai nơi luôn hiển thị giống nhau.
 *
 * Mọi kích thước tính theo "em" (tỉ lệ với cỡ chữ) nên giống nhau ở mọi cỡ chữ.
 */
object TextStyleRenderer {

    /** Cỡ chữ (px) khi vẽ ra bitmap cho ZoomableStickerView. */
    const val BASE_TEXT_SIZE = 120f

    private const val PAD_X_EM = 0.35f
    private const val PAD_Y_EM = 0.18f
    private const val RADIUS_EM = 0.28f
    private const val STROKE_EM = 0.07f

    fun padX(textSizePx: Float) = textSizePx * PAD_X_EM
    fun padY(textSizePx: Float) = textSizePx * PAD_Y_EM
    fun cornerRadius(textSizePx: Float) = textSizePx * RADIUS_EM
    fun strokeWidth(textSizePx: Float) = textSizePx * STROKE_EM

    data class Colors(val text: Int, val fill: Int?, val stroke: Int?)

    fun resolveColors(color: Int, mode: TextBackgroundMode): Colors = when (mode) {
        TextBackgroundMode.NONE -> Colors(text = color, fill = null, stroke = null)
        TextBackgroundMode.FILL -> Colors(text = contrastColor(color), fill = color, stroke = null)
        TextBackgroundMode.OUTLINE -> Colors(text = color, fill = null, stroke = color)
    }

    /** Màu chữ dễ đọc trên nền [bg]: nền sáng -> chữ tối, nền tối -> chữ trắng. */
    fun contrastColor(bg: Int): Int {
        val luminance = 0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)
        return if (luminance > 170) Color.parseColor("#111111") else Color.WHITE
    }

    fun typefaceOf(font: TextFont): Typeface = when (font) {
        TextFont.DEFAULT -> Typeface.DEFAULT
        TextFont.TYPEWRITER -> Typeface.MONOSPACE
        TextFont.BOLD -> Typeface.DEFAULT_BOLD
    }

    /**
     * Vẽ chữ (nhiều dòng) kèm nền/viền ra bitmap trong suốt.
     * Xung quanh có một lề nhỏ để nét viền và chữ nghiêng không bị cắt.
     */
    fun render(
        text: String,
        colorArgb: Int,
        align: TextAlign,
        style: TextStyleSpec,
        italic: Boolean = false,
        textSizePx: Float = BASE_TEXT_SIZE
    ): Bitmap {
        val colors = resolveColors(colorArgb, style.background)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.text
            textSize = textSizePx
            typeface = typefaceOf(style.font)
            textSkewX = if (italic) -0.25f else 0f
        }

        val lines = text.split("\n")
        val lineHeight = textPaint.descent() - textPaint.ascent()
        val maxWidth = lines.maxOf { textPaint.measureText(it) }

        val padX = padX(textSizePx)
        val padY = padY(textSizePx)
        val stroke = strokeWidth(textSizePx)
        val margin = stroke + if (italic) textSizePx * 0.15f else 0f

        val boxW = maxWidth + padX * 2
        val boxH = lineHeight * lines.size + padY * 2
        val bmp = Bitmap.createBitmap(
            ceil(boxW + margin * 2).toInt().coerceAtLeast(1),
            ceil(boxH + margin * 2).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        val box = RectF(margin, margin, margin + boxW, margin + boxH)
        val radius = cornerRadius(textSizePx)

        colors.fill?.let { fill ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill; this.style = Paint.Style.FILL }
            canvas.drawRoundRect(box, radius, radius, paint)
        }
        colors.stroke?.let { strokeColor ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = strokeColor
                this.style = Paint.Style.STROKE
                strokeWidth = stroke
            }
            val inset = RectF(box).apply { inset(stroke / 2f, stroke / 2f) }
            canvas.drawRoundRect(inset, radius, radius, paint)
        }

        val factor = when (align) {
            TextAlign.LEFT -> 0f
            TextAlign.CENTER -> 0.5f
            TextAlign.RIGHT -> 1f
        }
        var y = margin + padY - textPaint.ascent()
        for (line in lines) {
            val x = margin + padX + (maxWidth - textPaint.measureText(line)) * factor
            canvas.drawText(line, x, y, textPaint)
            y += lineHeight
        }
        return bmp
    }
}