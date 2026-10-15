package com.jetpack.stickify.presentation.ui.edit_sticker.prompt_sticker

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Quầng sáng xanh – cam nhòe ra NGOÀI promptCard, lượn sóng như sóng biển.
 *
 * Cách dùng:
 * - Đặt view này là anh em (cùng parent) với promptCard, full màn hình (0dp + constraint 4 phía),
 *   nằm SAU lớp phủ tối và elevation THẤP HƠN promptCard để card luôn nằm trên.
 * - Gán [target] = promptCard.
 *
 * View đọc vị trí thực của card (x, y đã gồm translation) ở MỖI frame nên luôn bám theo card,
 * kể cả khi PromptKeyboardAdjuster dịch card lên hoặc card đổi chiều cao.
 * View không nhận touch.
 *
 * Phần quầng nằm bên trong card bị card (nền trắng) che đi, nên chỉ thấy phần nhòe ra ngoài.
 */
class AiGlowHaloView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** View card cần bám theo (phải cùng parent với view này). */
    var target: View? = null

    /** Khớp app:cardCornerRadius của promptCard. */
    var cornerRadiusDp: Float = 26f

    private val d = resources.displayMetrics.density

    // ---- Thông số hiệu ứng (chỉnh ở đây) ----
    private val maxGlow = 30f * d          // quầng toả ra tối đa bao xa
    private val waveAmp = 9f * d           // biên độ sóng ở mép ngoài
    private val waveLen1 = 90f * d         // bước sóng chính
    private val waveLen2 = 150f * d        // bước sóng phụ (tạo nhiễu tự nhiên)
    private val layers = 10                // số lớp chồng để tạo độ nhòe
    private val layerAlpha = 38f           // độ đậm lớp sát card
    private val samples = 220              // số điểm lấy mẫu quanh viền
    private val loopMs = 2400L             // 1 vòng màu = 2.4s (khớp AiGlowBorderDrawable)

    private val colors = intArrayOf(
        0xFF2979FF.toInt(), 0xFF00B0FF.toInt(), 0xFF2979FF.toInt(),
        0xFFFF6D00.toInt(), 0xFFFFB300.toInt(), 0xFF2979FF.toInt()
    )
    private val positions = floatArrayOf(0f, 0.2f, 0.4f, 0.65f, 0.85f, 1f)
    // Tâm gradient ở (0,0); dịch tới tâm card bằng matrix mỗi frame
    private val shader = SweepGradient(0f, 0f, colors, positions)
    private val shaderMatrix = Matrix()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeWidth = (maxGlow / layers) * 2.4f
        shader = this@AiGlowHaloView.shader
    }

    // ---- Dữ liệu lấy mẫu viền card (tính lại khi kích thước card đổi) ----
    private val px = FloatArray(samples)
    private val py = FloatArray(samples)
    private val nx = FloatArray(samples)
    private val ny = FloatArray(samples)
    private var cachedW = -1f
    private var cachedH = -1f
    private var k1 = 1
    private var k2 = 1

    private val path = Path()
    private val basePath = Path()
    private val rect = RectF()
    private val measure = PathMeasure()
    private val pos = FloatArray(2)
    private val tan = FloatArray(2)

    private var progress = 0f // 0..1 lặp vô hạn
    private var fade = 0f
    private var loop: ValueAnimator? = null
    private var fader: ValueAnimator? = null

    init {
        visibility = GONE
        isClickable = false
        isFocusable = false
    }

    fun start() {
        visibility = VISIBLE
        if (loop?.isRunning != true) {
            loop = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = loopMs
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
        fadeTo(1f, 300L) {}
    }

    fun stop() {
        fadeTo(0f, 250L) {
            loop?.cancel(); loop = null
            visibility = GONE
        }
    }

    fun release() {
        fader?.cancel(); fader = null
        loop?.cancel(); loop = null
        fade = 0f
    }

    private fun fadeTo(to: Float, ms: Long, onEnd: () -> Unit) {
        fader?.cancel()
        fader = ValueAnimator.ofFloat(fade, to).apply {
            duration = ms
            addUpdateListener { fade = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) { if (!cancelled) onEnd() }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    /** Lấy mẫu viền bo góc của card: vị trí + pháp tuyến hướng ra ngoài. */
    private fun rebuildSamples(w: Float, h: Float) {
        cachedW = w; cachedH = h
        val r = (cornerRadiusDp * d).coerceAtMost(h / 2f)
        rect.set(0f, 0f, w, h)
        basePath.reset()
        basePath.addRoundRect(rect, r, r, Path.Direction.CW)
        measure.setPath(basePath, true)
        val len = measure.length
        for (i in 0 until samples) {
            measure.getPosTan(len * i / samples, pos, tan)
            px[i] = pos[0]; py[i] = pos[1]
            // Path CW trong toạ độ màn hình: pháp tuyến ra ngoài = (ty, -tx)
            nx[i] = tan[1]; ny[i] = -tan[0]
        }
        // Số chu kỳ sóng là số nguyên để sóng khép kín, không bị gãy ở điểm nối
        k1 = max(1, (len / waveLen1).roundToInt())
        k2 = max(1, (len / waveLen2).roundToInt())
    }

    override fun onDraw(canvas: Canvas) {
        val t = target ?: return
        if (fade <= 0f || t.width == 0 || t.height == 0) return

        val w = t.width.toFloat()
        val h = t.height.toFloat()
        if (w != cachedW || h != cachedH) rebuildSamples(w, h)

        // Vị trí card hiện tại trong parent (đã gồm translationX/Y)
        val ox = t.x
        val oy = t.y

        // Xoay gradient quanh tâm card
        shaderMatrix.setRotate(progress * 360f, 0f, 0f)
        shaderMatrix.postTranslate(ox + w / 2f, oy + h / 2f)
        shader.setLocalMatrix(shaderMatrix)
        paint.shader = shader

        val twoPi = (2.0 * PI).toFloat()
        val phase1 = progress * twoPi          // sóng chính chạy theo 1 chiều
        val phase2 = -progress * twoPi * 2f    // sóng phụ chạy ngược chiều, nhanh gấp đôi
        val breath = 0.82f + 0.18f * sin(progress * twoPi) // quầng phập phồng nhẹ

        // Vẽ từ lớp ngoài cùng (mờ) vào trong (đậm)
        for (layer in layers - 1 downTo 0) {
            val frac = (layer + 1f) / layers
            val offset = frac * maxGlow * breath
            val amp = frac * waveAmp

            path.reset()
            for (i in 0 until samples) {
                val u = i.toFloat() / samples
                val wave = amp * (
                        0.6f * sin(k1 * twoPi * u + phase1) +
                                0.4f * sin(k2 * twoPi * u + phase2)
                        )
                val dist = offset + wave
                val x = ox + px[i] + nx[i] * dist
                val y = oy + py[i] + ny[i] * dist
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()

            val a = layerAlpha * (1f - frac * 0.9f).pow(1.5f) * fade
            paint.alpha = a.toInt().coerceIn(0, 255)
            canvas.drawPath(path, paint)
        }
    }
}