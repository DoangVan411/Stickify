package com.jetpack.stickify.data.gif

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.jetpack.stickify.domain.model.StickerAnimationType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tạo các frame animation từ một bitmap gốc dựa trên loại hiệu ứng.
 * Kết quả là danh sách Bitmap đã được transform, sẵn sàng encode GIF.
 *
 * Mỗi frame được vẽ trên canvas có kích thước lớn hơn bitmap gốc
 * (thêm padding) để chứa chuyển động mà không bị cắt.
 */
object StickerAnimationRenderer {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /**
     * Tạo danh sách frame cho animation.
     *
     * @param source Bitmap gốc (đã burn decor vào)
     * @param type Loại hiệu ứng
     * @return Danh sách frame bitmaps
     */
    fun renderFrames(source: Bitmap, type: StickerAnimationType): List<Bitmap> {
        if (!type.isAnimated) {
            return listOf(source)
        }

        val totalFrames = type.totalFrames
        val frames = mutableListOf<Bitmap>()

        // Padding thêm cho chuyển động
        val padRatio = when (type) {
            StickerAnimationType.BOUNCE -> 0.15f
            StickerAnimationType.SHAKE -> 0.1f
            StickerAnimationType.SPIN -> 0.2f
            StickerAnimationType.PULSE -> 0.2f
            StickerAnimationType.WOBBLE -> 0.1f
            else -> 0f
        }

        val padX = (source.width * padRatio).toInt()
        val padY = (source.height * padRatio).toInt()
        val canvasW = source.width + padX * 2
        val canvasH = source.height + padY * 2

        for (i in 0 until totalFrames) {
            val progress = i.toFloat() / totalFrames // 0.0 → ~1.0
            val frame = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frame)

            // Canvas trong suốt (GIF sẽ có nền trắng hoặc transparent)
            canvas.drawColor(Color.TRANSPARENT)

            val matrix = Matrix()
            val cx = canvasW / 2f
            val cy = canvasH / 2f

            when (type) {
                StickerAnimationType.BOUNCE -> {
                    // Nhún nhảy: dịch Y theo sin, squash/stretch nhẹ
                    val phase = sin(progress * 2 * PI).toFloat()
                    val bounceY = phase * source.height * 0.12f
                    val squash = 1f + phase * 0.05f
                    val stretch = 1f - phase * 0.05f

                    matrix.postTranslate(-source.width / 2f, -source.height / 2f)
                    matrix.postScale(stretch, squash)
                    matrix.postTranslate(cx, cy + bounceY)
                }

                StickerAnimationType.SHAKE -> {
                    // Rung lắc: dịch X nhanh theo sin, nhẹ rotation
                    val phase = sin(progress * 4 * PI).toFloat()
                    val shakeX = phase * source.width * 0.06f
                    val tilt = phase * 3f // ±3 độ

                    matrix.postTranslate(-source.width / 2f, -source.height / 2f)
                    matrix.postRotate(tilt)
                    matrix.postTranslate(cx + shakeX, cy)
                }

                StickerAnimationType.SPIN -> {
                    // Xoay tròn 360°
                    val angle = progress * 360f

                    matrix.postTranslate(-source.width / 2f, -source.height / 2f)
                    matrix.postRotate(angle)
                    matrix.postTranslate(cx, cy)
                }

                StickerAnimationType.PULSE -> {
                    // Co giãn: scale lên xuống theo sin
                    val phase = sin(progress * 2 * PI).toFloat()
                    val scale = 1f + phase * 0.15f

                    matrix.postTranslate(-source.width / 2f, -source.height / 2f)
                    matrix.postScale(scale, scale)
                    matrix.postTranslate(cx, cy)
                }

                StickerAnimationType.WOBBLE -> {
                    // Lắc lư: rotation oscillation ±15°
                    val phase = sin(progress * 2 * PI).toFloat()
                    val angle = phase * 15f

                    matrix.postTranslate(-source.width / 2f, -source.height / 2f)
                    matrix.postRotate(angle)
                    matrix.postTranslate(cx, cy)
                }

                else -> {
                    matrix.postTranslate(padX.toFloat(), padY.toFloat())
                }
            }

            canvas.drawBitmap(source, matrix, paint)
            frames.add(frame)
        }

        return frames
    }

    /**
     * Tính toán transform cho frame cụ thể — dùng cho preview trực tiếp trên canvas.
     *
     * @return FloatArray [translateX, translateY, scaleX, scaleY, rotation]
     */
    fun getFrameTransform(type: StickerAnimationType, frameIndex: Int): FloatArray {
        val totalFrames = if (type.totalFrames > 0) type.totalFrames else 1
        val progress = frameIndex.toFloat() / totalFrames

        return when (type) {
            StickerAnimationType.BOUNCE -> {
                val phase = sin(progress * 2 * PI).toFloat()
                floatArrayOf(0f, phase * 0.12f, 1f - phase * 0.05f, 1f + phase * 0.05f, 0f)
            }
            StickerAnimationType.SHAKE -> {
                val phase = sin(progress * 4 * PI).toFloat()
                floatArrayOf(phase * 0.06f, 0f, 1f, 1f, phase * 3f)
            }
            StickerAnimationType.SPIN -> {
                floatArrayOf(0f, 0f, 1f, 1f, progress * 360f)
            }
            StickerAnimationType.PULSE -> {
                val phase = sin(progress * 2 * PI).toFloat()
                val scale = 1f + phase * 0.15f
                floatArrayOf(0f, 0f, scale, scale, 0f)
            }
            StickerAnimationType.WOBBLE -> {
                val phase = sin(progress * 2 * PI).toFloat()
                floatArrayOf(0f, 0f, 1f, 1f, phase * 15f)
            }
            else -> floatArrayOf(0f, 0f, 1f, 1f, 0f)
        }
    }
}
