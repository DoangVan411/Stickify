package com.jetpack.stickify.presentation.ui.editsticker

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * View hiển thị ảnh sticker (PNG có alpha) trên nền caro trong suốt, hỗ trợ:
 * - Pinch-zoom bằng 2 ngón + pan (kéo di chuyển) bằng 1 hoặc nhiều ngón.
 * - Chuyển ảnh mượt (crossfade) khi đổi kiểu (Giữ nguyên / Viền ngoài / Hoạt hình) mà
 *   KHÔNG reset lại zoom/pan hiện tại của người dùng — matrix biến đổi giữ nguyên,
 *   chỉ nội dung bitmap mờ dần chuyển sang bitmap mới.
 */
class ZoomableStickerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val MIN_SCALE = 0.5f
        private const val MAX_SCALE = 5f
        private const val CROSSFADE_DURATION_MS = 260L
        private const val CHECKER_TILE_DP = 12f
    }

    private var currentBitmap: Bitmap? = null
    private var previousBitmap: Bitmap? = null
    private var crossfadeProgress = 1f // 0 = đang hiện previousBitmap, 1 = đang hiện currentBitmap hoàn toàn
    private var crossfadeAnimator: ValueAnimator? = null

    /** Matrix chung áp dụng cho cả ảnh cũ lẫn ảnh mới khi crossfade, để giữ đúng zoom/pan. */
    private val displayMatrix = Matrix()
    private var isMatrixInitialized = false

    private val bitmapPaintCurrent = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bitmapPaintPrevious = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val checkerPaint: Paint by lazy { buildCheckerPaint() }

    // ---------- Pinch zoom + pan ----------
    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isPanning = false

    /** Gán bitmap mới để hiển thị. [animate] = true sẽ crossfade mượt, false hiện ngay lập tức. */
    fun setBitmap(bitmap: Bitmap, animate: Boolean = true) {
        val previous = currentBitmap
        currentBitmap = bitmap

        if (!isMatrixInitialized) {
            resetTransformToFit(bitmap)
        }

        if (animate && previous != null) {
            previousBitmap = previous
            crossfadeProgress = 0f
            startCrossfadeAnimation()
        } else {
            previousBitmap = null
            crossfadeProgress = 1f
            invalidate()
        }
    }

    private fun startCrossfadeAnimation() {
        crossfadeAnimator?.cancel()
        crossfadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = CROSSFADE_DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener {
                crossfadeProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** Đưa ảnh về giữa view, scale vừa khít (fit-center) — gọi khi load ảnh lần đầu. */
    fun resetTransformToFit(bitmap: Bitmap? = currentBitmap ) {

        val targetBitmap = bitmap ?: return

        if (width <= 0 || height <= 0 || targetBitmap.width <= 0 || targetBitmap.height <= 0) return

        val scale = min(width.toFloat() / targetBitmap.width, height.toFloat() / targetBitmap.height)
        val dx = (width - targetBitmap.width * scale) / 2f
        val dy = (height - targetBitmap.height * scale) / 2f

        displayMatrix.reset()
        displayMatrix.postScale(scale, scale)
        displayMatrix.postTranslate(dx, dy)

        isMatrixInitialized = true
        invalidate()
    }

    private fun min(a: Float, b: Float) = if (a < b) a else b

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!isMatrixInitialized) {
            currentBitmap?.let { resetTransformToFit(it) }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        crossfadeAnimator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), checkerPaint)

        val curr = currentBitmap ?: return
        val prev = previousBitmap

        if (prev != null && crossfadeProgress < 1f) {
            bitmapPaintPrevious.alpha = ((1f - crossfadeProgress) * 255).toInt()
            canvas.drawBitmap(prev, displayMatrix, bitmapPaintPrevious)
        }

        bitmapPaintCurrent.alpha = (crossfadeProgress * 255).toInt().coerceAtLeast(if (prev == null) 255 else 0)
        canvas.drawBitmap(curr, displayMatrix, bitmapPaintCurrent)
    }

    private fun buildCheckerPaint(): Paint {
        val tilePx = (CHECKER_TILE_DP * resources.displayMetrics.density).toInt().coerceAtLeast(4)
        val tile = Bitmap.createBitmap(tilePx * 2, tilePx * 2, Bitmap.Config.ARGB_8888)
        val tileCanvas = Canvas(tile)
        val light = Paint().apply { color = Color.parseColor("#FFFFFF") }
        val dark = Paint().apply { color = Color.parseColor("#E0E0E0") }
        tileCanvas.drawRect(0f, 0f, tilePx.toFloat(), tilePx.toFloat(), light)
        tileCanvas.drawRect(tilePx.toFloat(), 0f, (tilePx * 2).toFloat(), tilePx.toFloat(), dark)
        tileCanvas.drawRect(0f, tilePx.toFloat(), tilePx.toFloat(), (tilePx * 2).toFloat(), dark)
        tileCanvas.drawRect(tilePx.toFloat(), tilePx.toFloat(), (tilePx * 2).toFloat(), (tilePx * 2).toFloat(), light)

        return Paint().apply {
            shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }

    // ---------- Touch: pinch zoom + pan ----------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                lastTouchX = averageX(event)
                lastTouchY = averageY(event)
                isPanning = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isPanning && !scaleGestureDetector.isInProgress) {
                    val cx = averageX(event)
                    val cy = averageY(event)
                    displayMatrix.postTranslate(cx - lastTouchX, cy - lastTouchY)
                    lastTouchX = cx
                    lastTouchY = cy
                    invalidate()
                } else {
                    // Đang scale: vẫn cập nhật điểm tham chiếu để không bị giật khi buông 1 ngón.
                    lastTouchX = averageX(event)
                    lastTouchY = averageY(event)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Còn lại 1 ngón sau khi nhả bớt -> cập nhật lại điểm gốc để pan tiếp không giật.
                val remainingIndex = if (event.actionIndex == 0) 1 else 0
                if (remainingIndex < event.pointerCount) {
                    lastTouchX = event.getX(remainingIndex)
                    lastTouchY = event.getY(remainingIndex)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPanning = false
            }
        }
        return true
    }

    private fun averageX(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) sum += event.getX(i)
        return sum / event.pointerCount
    }

    private fun averageY(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) sum += event.getY(i)
        return sum / event.pointerCount
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val currentScale = currentMatrixScale()
            var factor = detector.scaleFactor
            // Chặn scale vượt giới hạn min/max để tránh phóng quá to/nhỏ.
            val targetScale = (currentScale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
            factor = targetScale / currentScale

            displayMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
            invalidate()
            return true
        }
    }

    private fun currentMatrixScale(): Float {
        val values = FloatArray(9)
        displayMatrix.getValues(values)
        return values[Matrix.MSCALE_X]
    }
}