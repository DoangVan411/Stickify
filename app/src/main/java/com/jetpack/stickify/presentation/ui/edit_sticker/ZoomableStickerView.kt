package com.jetpack.stickify.presentation.ui.edit_sticker

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.LinearInterpolator
import com.jetpack.stickify.data.source.local.AssetLoader
import com.jetpack.stickify.domain.model.*
import kotlinx.coroutines.*

class ZoomableStickerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val MIN_SCALE = 0.1f
        private const val MAX_SCALE = 10f
        private const val CROSSFADE_DURATION_MS = 260L
        private const val CHECKER_TILE_DP = 12f
    }

    private var currentBitmap: Bitmap? = null
    private var previousBitmap: Bitmap? = null
    private var crossfadeProgress = 1f
    private var crossfadeAnimator: ValueAnimator? = null

    // Multi-layer rendering support
    private var projectContent: ProjectContent? = null
    private var assetLoader: AssetLoader? = null
    private val layerBitmaps = mutableMapOf<String, Bitmap>()
    private val viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Ma trận hiển thị chung
    private val displayMatrix = Matrix()
    private val previousMatrix = Matrix()
    private var isMatrixInitialized = false

    private val bitmapPaintCurrent = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bitmapPaintPrevious = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private val checkerPaint: Paint by lazy { buildCheckerPaint() }

    // Touch events
    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isPanning = false

    fun setBitmap(bitmap: Bitmap, animate: Boolean = true) {
        projectContent = null
        val previous = currentBitmap
        currentBitmap = bitmap

        if (!isMatrixInitialized) {
            resetTransformToFit(bitmap.width.toFloat(), bitmap.height.toFloat())
        } else if (previous != null) {
            compensateMatrixForNewBitmap(previous.width.toFloat(), previous.height.toFloat(), bitmap.width.toFloat(), bitmap.height.toFloat())
        }

        if (animate && previous != null) {
            previousBitmap = previous
            previousMatrix.set(displayMatrix)
            crossfadeProgress = 0f
            startCrossfadeAnimation()
        } else {
            previousBitmap = null
            crossfadeProgress = 1f
            invalidate()
        }
    }

    fun setProjectContent(content: ProjectContent, loader: AssetLoader) {
        currentBitmap = null
        previousBitmap = null
        projectContent = content
        assetLoader = loader

        val canvasWidth = content.canvas.width.toFloat().takeIf { it > 0f } ?: 512f
        val canvasHeight = content.canvas.height.toFloat().takeIf { it > 0f } ?: 512f

        if (!isMatrixInitialized) {
            resetTransformToFit(canvasWidth, canvasHeight)
        }

        // Load bitmaps cho các layer bất đồng bộ
        viewScope.launch {
            for (layer in content.layers) {
                if (layer is DecorationLayer) {
                    val bmp = loader.loadBitmap(layer.asset)
                    if (bmp != null) {
                        layerBitmaps[layer.id] = bmp
                    }
                } else if (layer is SubjectLayer) {
                    val sourceAsset = layer.styledPath?.let { CustomAsset(it) } ?: layer.source
                    val bmp = loader.loadBitmap(sourceAsset)
                    if (bmp != null) {
                        layerBitmaps[layer.id] = bmp
                    }
                }
            }
            invalidate()
        }
    }

    private fun compensateMatrixForNewBitmap(oldW: Float, oldH: Float, newW: Float, newH: Float) {
        val currentScale = currentMatrixScale()
        val dw = newW - oldW
        val dh = newH - oldH
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

    fun resetTransformToFit(contentWidth: Float = 512f, contentHeight: Float = 512f) {
        if (width <= 0 || height <= 0 || contentWidth <= 0f || contentHeight <= 0f) return

        val scale = min(width.toFloat() / contentWidth, height.toFloat() / contentHeight)
        val dx = (width - contentWidth * scale) / 2f
        val dy = (height - contentHeight * scale) / 2f

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
            currentBitmap?.let { resetTransformToFit(it.width.toFloat(), it.height.toFloat()) }
                ?: projectContent?.let { resetTransformToFit(it.canvas.width.toFloat(), it.canvas.height.toFloat()) }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        crossfadeAnimator?.cancel()
        viewScope.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 1. Vẽ nền caro
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), checkerPaint)

        canvas.save()
        canvas.concat(displayMatrix)

        // 2. Nếu có projectContent (Multi-layer mode từ Room DB)
        val content = projectContent
        if (content != null) {
            for (layer in content.layers) {
                if (!layer.visible) continue
                drawLayer(canvas, layer)
            }
        } else {
            // 3. Single bitmap preview mode (Legacy Cutout / SharedViewModel)
            val curr = currentBitmap
            val prev = previousBitmap

            if (prev != null && crossfadeProgress < 1f) {
                bitmapPaintPrevious.alpha = ((1f - crossfadeProgress) * 255).toInt()
                canvas.drawBitmap(prev, 0f, 0f, bitmapPaintPrevious)
            }
            if (curr != null) {
                bitmapPaintCurrent.alpha = (crossfadeProgress * 255).toInt().coerceAtLeast(if (prev == null) 255 else 0)
                canvas.drawBitmap(curr, 0f, 0f, bitmapPaintCurrent)
            }
        }

        canvas.restore()
    }

    private fun drawLayer(canvas: Canvas, layer: Layer) {
        canvas.save()
        val transform = layer.transform

        // Áp dụng Transform của Layer (cx, cy, scale, rotation, opacity, flipX)
        canvas.translate(transform.cx, transform.cy)
        canvas.scale(if (transform.flipX) -transform.scale else transform.scale, transform.scale)
        canvas.rotate(transform.rotationDeg)

        val alphaInt = (transform.opacity.coerceIn(0f, 1f) * 255).toInt()

        when (layer) {
            is DecorationLayer -> {
                val bmp = layerBitmaps[layer.id]
                if (bmp != null && !bmp.isRecycled) {
                    bitmapPaintCurrent.alpha = alphaInt
                    val left = -bmp.width / 2f
                    val top = -bmp.height / 2f
                    canvas.drawBitmap(bmp, left, top, bitmapPaintCurrent)
                }
            }
            is SubjectLayer -> {
                val bmp = layerBitmaps[layer.id]
                if (bmp != null && !bmp.isRecycled) {
                    bitmapPaintCurrent.alpha = alphaInt
                    val left = -bmp.width / 2f
                    val top = -bmp.height / 2f
                    canvas.drawBitmap(bmp, left, top, bitmapPaintCurrent)
                }
            }
            is TextLayer -> {
                textPaint.color = layer.colorArgb
                textPaint.textSize = 48f * layer.fontSizeRatio.coerceAtLeast(0.1f)
                textPaint.isFakeBoldText = layer.bold
                textPaint.textSkewX = if (layer.italic) -0.25f else 0f
                textPaint.alpha = alphaInt

                val text = layer.content
                val textWidth = textPaint.measureText(text)
                canvas.drawText(text, -textWidth / 2f, 0f, textPaint)
            }
            is EffectLayer -> {
                // Effect rendering
            }
        }

        canvas.restore()
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
