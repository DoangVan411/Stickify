package com.jetpack.stickify.presentation.ui.edit_sticker

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

class ZoomableStickerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val MIN_SCALE = 0.5f
        private const val MAX_SCALE = 10f // Nới lỏng max scale để dễ xem viền
        private const val CROSSFADE_DURATION_MS = 260L
        private const val CHECKER_TILE_DP = 12f
    }

    private var currentBitmap: Bitmap? = null
    private var previousBitmap: Bitmap? = null
    private var crossfadeProgress = 1f
    private var crossfadeAnimator: ValueAnimator? = null

    // Ma trận hiển thị ảnh hiện tại
    private val displayMatrix = Matrix()

    // Ma trận hiển thị ảnh trước đó (dành riêng cho quá trình crossfade)
    private val previousMatrix = Matrix()

    private var isMatrixInitialized = false

    private val bitmapPaintCurrent = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bitmapPaintPrevious = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val checkerPaint: Paint by lazy { buildCheckerPaint() }

    // Touch events
    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isPanning = false

    fun setBitmap(bitmap: Bitmap, animate: Boolean = true) {
        val previous = currentBitmap
        currentBitmap = bitmap

        if (!isMatrixInitialized) {
            // Lần đầu tiên load ảnh: Căn giữa màn hình
            resetTransformToFit(bitmap)
        } else if (previous != null) {
            // CÁC LẦN SAU: Bù trừ độ chênh lệch kích thước để giữ nguyên trọng tâm ảnh
            compensateMatrixForNewBitmap(previous, bitmap)
        }

        if (animate && previous != null) {
            previousBitmap = previous
            // Lưu lại ma trận của ảnh cũ ngay tại khoảnh khắc crossfade bắt đầu
            // Để dù user có kéo ảnh mới đi, ảnh mờ cũ (đang fade out) vẫn dính vào ảnh mới
            previousMatrix.set(displayMatrix)

            crossfadeProgress = 0f
            startCrossfadeAnimation()
        } else {
            previousBitmap = null
            crossfadeProgress = 1f
            invalidate()
        }
    }

    /**
     * THUẬT TOÁN BÙ TRỪ TÂM ẢNH:
     * Khi ảnh mới to/nhỏ hơn ảnh cũ (do thêm viền), nếu áp dụng y xì matrix cũ, ảnh mới sẽ bị lệch góc.
     * Ta cần dịch chuyển matrix ngược lại (lên trên, sang trái) một đoạn bằng đúng nửa độ chênh lệch kích thước,
     * nhân với Scale hiện tại, để ảnh mới "mọc ra" từ chính giữa ảnh cũ.
     */
    private fun compensateMatrixForNewBitmap(oldBitmap: Bitmap, newBitmap: Bitmap) {
        val currentScale = currentMatrixScale()

        // Tính chênh lệch kích thước thực tế giữa 2 ảnh
        val dw = newBitmap.width - oldBitmap.width
        val dh = newBitmap.height - oldBitmap.height

        // Dịch chuyển ma trận lên trên/sang trái để giữ tâm cố định
        val dx = -(dw / 2f) * currentScale
        val dy = -(dh / 2f) * currentScale

        displayMatrix.postTranslate(dx, dy)
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

    fun resetTransformToFit(bitmap: Bitmap? = currentBitmap) {
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
        // 1. Vẽ nền caro
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), checkerPaint)

        val curr = currentBitmap ?: return
        val prev = previousBitmap

        // 2. Vẽ ảnh cũ đang phai đi (Fade Out)
        // Dùng previousMatrix (đã tính toán bù trừ từ khoảnh khắc bắt đầu hiệu ứng)
        if (prev != null && crossfadeProgress < 1f) {

            // Tính toán lại previousMatrix nếu người dùng đang di chuyển ảnh lúc crossfade diễn ra
            val tempMatrix = Matrix(displayMatrix)
            val currScale = currentMatrixScale()
            val dw = curr.width - prev.width
            val dh = curr.height - prev.height
            val dx = (dw / 2f) * currScale
            val dy = (dh / 2f) * currScale
            tempMatrix.postTranslate(dx, dy)

            bitmapPaintPrevious.alpha = ((1f - crossfadeProgress) * 255).toInt()
            canvas.drawBitmap(prev, tempMatrix, bitmapPaintPrevious)
        }

        // 3. Vẽ ảnh mới đang hiện lên (Fade In)
        bitmapPaintCurrent.alpha = (crossfadeProgress * 255).toInt().coerceAtLeast(if (prev == null) 255 else 0)
        canvas.drawBitmap(curr, displayMatrix, bitmapPaintCurrent)
    }

    // ---------- Paint Caro ----------
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
                    lastTouchX = averageX(event)
                    lastTouchY = averageY(event)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
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