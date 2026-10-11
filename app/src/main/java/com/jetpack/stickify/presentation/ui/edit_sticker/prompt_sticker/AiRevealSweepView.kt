package com.jetpack.stickify.presentation.ui.edit_sticker.prompt_sticker

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random
import androidx.core.graphics.toColorInt

/**
 * Hiệu ứng Reveal Sóng nước (Radial Sweep) Cao cấp.
 * - Sóng lan từ 1 điểm NGẪU NHIÊN trên CẠNH TRÊN (cy = 0).
 * - NỀN ĐEN MỜ: Phần ảnh cũ chưa được quét sẽ bị tối đi, làm nổi bật ảnh AI mới.
 */
class AiRevealSweepView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val d = resources.displayMetrics.density

    // ---- THÔNG SỐ HIỆU ỨNG ----
    private val sweepMs = 2200L             // Thời gian 1.8s
    private val fadeStartRatio = 0.4f       // Bắt đầu mờ dần đi từ 40% quãng đường

    private val waveColor = 0xFFB3EFFF.toInt() // Màu Cyan cực nhạt

    private val strokeWidthStart = 40f * d
    private val strokeWidthEnd = 200f * d
    private val blurStart = 18f * d
    private val blurEnd = 120f * d

    // 1. Cọ vẽ ảnh cũ
    private val snapshotPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // 2. Cọ phủ NỀN ĐEN MỜ (Dim overlay)
    // Dùng mã màu giống hệ thống loading (#B3000000 -> 70% Đen) để nối tiếp mượt mà
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#0D000000".toColorInt()
    }

    // 3. Cọ TẨY ảnh cũ (Masking feather)
    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    // 4. Cọ Quầng sáng (Glow)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = waveColor
    }

    // ---- Trạng thái ----
    private var snapshot: Bitmap? = null
    private var progress = 0f

    private var randomFactorX = 0.5f
    private var cx = 0f
    private var cy = 0f
    private var maxRadius = 0f

    private var running = false
    private var animator: ValueAnimator? = null
    private var endCallback: (() -> Unit)? = null

    init {
        visibility = GONE
        isClickable = false
        isFocusable = false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }
    }

    fun captureSnapshot(source: View, backdropColor: Int = Color.TRANSPARENT) {
        cancel()
        clearSnapshot()
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (backdropColor != Color.TRANSPARENT) c.drawColor(backdropColor)
        source.draw(c)
        snapshot = bmp
    }

    fun discardSnapshot() {
        if (!running) clearSnapshot()
    }

    fun play(startDelayMs: Long = 0L, onEnd: (() -> Unit)? = null) {
        if (snapshot == null) {
            onEnd?.invoke()
            return
        }
        animator?.cancel()
        endCallback = onEnd
        progress = 0f
        running = true
        visibility = VISIBLE

        randomFactorX = Random.nextFloat()

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = sweepMs
            startDelay = startDelayMs
            interpolator = DecelerateInterpolator(1.2f)
            addUpdateListener {
                progress = it.animatedValue as Float
                updateDynamicWaveProps(progress)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) { if (!cancelled) finish() }
            })
            start()
        }
    }

    fun cancel() {
        animator?.cancel()
        animator = null
        if (running) finish()
    }

    fun release() {
        cancel()
    }

    private fun finish() {
        animator = null
        running = false
        visibility = GONE
        clearSnapshot()
        val cb = endCallback
        endCallback = null
        cb?.invoke()
    }

    private fun clearSnapshot() {
        snapshot = null
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }

    private fun updateDynamicWaveProps(p: Float) {
        val currentWidth = lerp(strokeWidthStart, strokeWidthEnd, p)
        val currentBlur = lerp(blurStart, blurEnd, p)

        glowPaint.strokeWidth = currentWidth

        val safeBlur = if (currentBlur > 0.1f) currentBlur else 0.1f
        glowPaint.maskFilter = BlurMaskFilter(safeBlur, BlurMaskFilter.Blur.NORMAL)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        if (running) {
            val bmp = snapshot
            if (bmp == null || bmp.width != w || bmp.height != h) cancel()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!running) return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val bmp = snapshot ?: return
        if (bmp.width != width || bmp.height != height) return

        cx = w * randomFactorX
        cy = 0f

        val d1 = hypot(cx, h)
        val d2 = hypot(w - cx, h)
        maxRadius = max(d1, d2)

        val p = progress
        val currentRadius = maxRadius * p

        // ==========================================
        // BƯỚC 1: TẨY ẢNH CŨ VỚI NỀN ĐEN MỜ
        // ==========================================
        val layerId = canvas.saveLayer(0f, 0f, w, h, null)

        // 1a. Vẽ ảnh cũ
        canvas.drawBitmap(bmp, 0f, 0f, snapshotPaint)

        // 1b. PHỦ NỀN ĐEN MỜ (Toàn bộ phần chưa bị quét sẽ nằm trong bóng tối)
        canvas.drawRect(0f, 0f, w, h, dimPaint)

        // 1c. Tẩy (Khoét lỗ) xuyên qua cả lớp đen mờ và ảnh cũ
        val feather = 24f * d
        val outerR = currentRadius.coerceAtLeast(1f)
        val innerR = (outerR - feather).coerceAtLeast(0.001f)

        eraserPaint.shader = RadialGradient(
            cx, cy, outerR,
            intArrayOf(Color.BLACK, Color.TRANSPARENT),
            floatArrayOf(innerR / outerR, 1f),
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(cx, cy, outerR, eraserPaint)
        canvas.restoreToCount(layerId)

        // ==========================================
        // BƯỚC 2: VẼ LUỒNG SÁNG (GLOW)
        // ==========================================
        val alphaRatio = if (p < fadeStartRatio) 1f else (1f - p) / (1f - fadeStartRatio)
        val currentAlpha = (255 * alphaRatio).toInt()

        if (p > 0f && p < 1f) {
            glowPaint.alpha = (currentAlpha * 0.75f).toInt()
            canvas.drawCircle(cx, cy, outerR, glowPaint)
        }
    }
}