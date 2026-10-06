package com.jetpack.stickify.presentation.ui.edit_sticker.prompt_sticker

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

/**
 * Viền gradient xanh – cam chạy vòng quanh (kiểu Samsung Galaxy AI).
 *
 * Dùng làm FOREGROUND của promptCard:  promptCard.foreground = AiGlowBorderDrawable(context)
 *
 * Vì là foreground của chính card nên:
 * - luôn khớp đúng bounds + bo góc của card, dù card bị PromptKeyboardAdjuster dịch translationY
 *   hay đổi chiều cao (EditText nhiều dòng);
 * - không chen vào layout, không nhận touch -> EditText / nút gửi vẫn dùng bình thường.
 */
class AiGlowBorderDrawable(
    context: Context,
    private val cornerRadiusDp: Float = 26f   // khớp app:cardCornerRadius của promptCard
) : Drawable() {

    private val density = context.resources.displayMetrics.density

    private val coreWidth = 2.5f * density
    private val glowWidths = floatArrayOf(10f * density, 6f * density)
    private val glowAlphas = intArrayOf(45, 90)

    // Xanh dương -> xanh lơ -> xanh dương -> cam -> vàng cam -> về xanh dương để khép vòng mượt
    private val gradientColors = intArrayOf(
        0xFF2979FF.toInt(),
        0xFF00B0FF.toInt(),
        0xFF2979FF.toInt(),
        0xFFFF6D00.toInt(),
        0xFFFFB300.toInt(),
        0xFF2979FF.toInt()
    )
    private val gradientPositions = floatArrayOf(0f, 0.2f, 0.4f, 0.65f, 0.85f, 1f)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val borderPath = Path()
    private val clipPath = Path()
    private val rect = RectF()
    private val shaderMatrix = Matrix()
    private var shader: SweepGradient? = null

    private var angle = 0f
    private var fade = 0f // 0..1
    private var rotator: ValueAnimator? = null
    private var fader: ValueAnimator? = null

    fun start() {
        if (rotator?.isRunning != true) {
            rotator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 2400L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    angle = it.animatedValue as Float
                    invalidateSelf()
                }
                start()
            }
        }
        fadeTo(1f, 250L) {}
    }

    fun stop() {
        fadeTo(0f, 200L) { stopRotator() }
    }

    /** Dừng ngay, không animation – gọi trong onDestroy. */
    fun release() {
        fader?.cancel(); fader = null
        stopRotator()
        fade = 0f
    }

    private fun stopRotator() {
        rotator?.cancel()
        rotator = null
    }

    private fun fadeTo(target: Float, durationMs: Long, onEnd: () -> Unit) {
        fader?.cancel()
        fader = ValueAnimator.ofFloat(fade, target).apply {
            duration = durationMs
            addUpdateListener {
                fade = it.animatedValue as Float
                invalidateSelf()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!cancelled) onEnd()
                }
            })
            start()
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        if (bounds.isEmpty) return
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        shader = SweepGradient(bounds.exactCenterX(), bounds.exactCenterY(), gradientColors, gradientPositions)

        val r = (cornerRadiusDp * density).coerceAtMost(h / 2f)

        // Vùng clip = đúng hình dạng card
        rect.set(bounds.left.toFloat(), bounds.top.toFloat(), bounds.left + w, bounds.top + h)
        clipPath.reset()
        clipPath.addRoundRect(rect, r, r, Path.Direction.CW)

        // Đường viền lệch vào trong nửa nét lõi
        val inset = coreWidth / 2f
        rect.inset(inset, inset)
        val rr = (r - inset).coerceAtLeast(0f)
        borderPath.reset()
        borderPath.addRoundRect(rect, rr, rr, Path.Direction.CW)
    }

    override fun draw(canvas: Canvas) {
        val sg = shader ?: return
        if (fade <= 0f) return
        val b = bounds

        shaderMatrix.setRotate(angle, b.exactCenterX(), b.exactCenterY())
        sg.setLocalMatrix(shaderMatrix)
        paint.shader = sg

        canvas.save()
        canvas.clipPath(clipPath)

        // Lớp phát sáng mờ (nét rộng, alpha thấp) – nửa ngoài bị clip nên sáng dần vào trong
        for (i in glowWidths.indices) {
            paint.strokeWidth = glowWidths[i]
            paint.alpha = (glowAlphas[i] * fade).toInt()
            canvas.drawPath(borderPath, paint)
        }
        // Nét lõi sắc nét
        paint.strokeWidth = coreWidth
        paint.alpha = (255 * fade).toInt()
        canvas.drawPath(borderPath, paint)

        canvas.restore()
    }

    override fun setAlpha(alpha: Int) { /* alpha điều khiển bằng fade */ }
    override fun setColorFilter(colorFilter: ColorFilter?) { /* không dùng */ }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}