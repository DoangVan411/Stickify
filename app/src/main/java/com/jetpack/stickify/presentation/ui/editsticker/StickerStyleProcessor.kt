package com.jetpack.stickify.presentation.ui.editsticker

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Các thuật toán tạo biến thể sticker từ ảnh đã cắt (PNG có alpha):
 * - [addOuterBorder]: viền ngoài đồng màu quanh silhouette.
 * - [cartoonify]: hiệu ứng hoạt hình (posterize màu + viền nét đen tại biên chi tiết).
 *
 * Cả 2 hàm chạy nặng CPU nên LUÔN gọi trên background thread (Dispatchers.Default).
 */
object StickerStyleProcessor {

    /**
     * Vẽ 1 lớp silhouette đồng màu [borderColor] "phình" ra ngoài biên alpha của [source]
     * khoảng [borderWidthPx], sau đó vẽ ảnh gốc đè lên trên -> hiệu ứng viền sticker.
     *
     * Kỹ thuật: BlurMaskFilter(radius, Blur.SOLID) khi vẽ 1 shape đặc sẽ tạo ra hình dạng
     * "giãn nở + làm mềm biên" ra ngoài đúng bằng bán kính blur — nhanh hơn nhiều so với
     * tự cài thuật toán dilation/distance-transform thủ công.
     */
    fun addOuterBorder(
        source: Bitmap,
        borderColor: Int = Color.WHITE,
        borderWidthPx: Float = 24f
    ): Bitmap {
        val w = source.width
        val h = source.height

        // Bước 1: tạo silhouette đồng màu borderColor, giữ nguyên hình dạng alpha của source.
        val silhouette = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val silhouetteCanvas = Canvas(silhouette)
        silhouetteCanvas.drawBitmap(source, 0f, 0f, null)
        val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) // chỉ tô màu vùng đã có alpha
        }
        silhouetteCanvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), tintPaint)

        // Bước 2: vẽ silhouette với BlurMaskFilter SOLID để "phình" ra ngoài biên.
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result) // Canvas gắn với Bitmap luôn là software rasterizer -> BlurMaskFilter hoạt động được
        val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(borderWidthPx, BlurMaskFilter.Blur.SOLID)
        }
        canvas.drawBitmap(silhouette, 0f, 0f, blurPaint)

        // Bước 3: vẽ ảnh gốc đè lên trên để phần bên trong hiển thị đúng màu thật.
        canvas.drawBitmap(source, 0f, 0f, null)

        return result
    }

    /**
     * Hiệu ứng hoạt hình đơn giản: posterize màu (giảm số cấp mỗi kênh RGB) để tạo mảng màu
     * phẳng, cộng thêm viền nét tối tại vùng có độ chênh sáng lớn (biên chi tiết khuôn mặt,
     * tóc...). Chạy ở độ phân giải giới hạn [maxWorkingDimension] để đảm bảo tốc độ.
     */
    fun cartoonify(
        source: Bitmap,
        posterizeLevels: Int = 6,
        edgeThreshold: Int = 28,
        maxWorkingDimension: Int = 900
    ): Bitmap {
        val working = downscaleIfNeeded(source, maxWorkingDimension)
        val w = working.width
        val h = working.height

        val pixels = IntArray(w * h)
        working.getPixels(pixels, 0, w, 0, 0, w, h)

        // Luminance (độ sáng) từng pixel, dùng để dò biên.
        val luminance = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            luminance[i] = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
        }

        val step = 255 / (posterizeLevels - 1)
        val output = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val p = pixels[idx]
                val alpha = (p ushr 24) and 0xFF
                if (alpha == 0) {
                    output[idx] = 0
                    continue
                }

                // Posterize từng kênh màu.
                val r = posterizeChannel((p shr 16) and 0xFF, step)
                val g = posterizeChannel((p shr 8) and 0xFF, step)
                val b = posterizeChannel(p and 0xFF, step)

                // Dò biên bằng sai khác luminance với 4 pixel lân cận (chỉ khi cả 4 đều nằm
                // trong vùng có alpha, tránh tạo viền giả ngay tại mép cắt của ảnh).
                var isEdge = false
                if (x in 1 until w - 1 && y in 1 until h - 1) {
                    val aLeft = (pixels[idx - 1] ushr 24) and 0xFF
                    val aRight = (pixels[idx + 1] ushr 24) and 0xFF
                    val aUp = (pixels[idx - w] ushr 24) and 0xFF
                    val aDown = (pixels[idx + w] ushr 24) and 0xFF
                    if (aLeft > 0 && aRight > 0 && aUp > 0 && aDown > 0) {
                        val gx = abs(luminance[idx + 1] - luminance[idx - 1])
                        val gy = abs(luminance[idx + w] - luminance[idx - w])
                        isEdge = (gx + gy) > edgeThreshold
                    }
                }

                output[idx] = if (isEdge) {
                    // Tô tối màu tại biên thay vì vẽ đen tuyệt đối, giữ cảm giác tự nhiên hơn.
                    val darkR = (r * 0.25f).toInt().coerceIn(0, 255)
                    val darkG = (g * 0.25f).toInt().coerceIn(0, 255)
                    val darkB = (b * 0.25f).toInt().coerceIn(0, 255)
                    (alpha shl 24) or (darkR shl 16) or (darkG shl 8) or darkB
                } else {
                    (alpha shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(output, 0, w, 0, 0, w, h)
        return result
    }

    private fun posterizeChannel(value: Int, step: Int): Int {
        if (step <= 0) return value
        val level = Math.round(value / step.toFloat())
        return min(255, max(0, level * step))
    }

    private fun downscaleIfNeeded(source: Bitmap, maxDimension: Int): Bitmap {
        val longSide = max(source.width, source.height)
        if (longSide <= maxDimension) return source
        val scale = maxDimension.toFloat() / longSide
        val newW = (source.width * scale).toInt().coerceAtLeast(1)
        val newH = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }
}