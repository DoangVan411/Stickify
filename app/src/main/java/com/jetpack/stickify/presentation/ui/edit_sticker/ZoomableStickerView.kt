package com.jetpack.stickify.presentation.ui.edit_sticker

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.*
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.LinearInterpolator
import com.jetpack.stickify.data.source.local.AssetLoader
import com.jetpack.stickify.domain.model.*
import kotlinx.coroutines.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.jetpack.stickify.R

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

    // Ma trận hiển thị ảnh trước đó (dành riêng cho quá trình crossfade)
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

    // Decor data & state
    data class DecorItemState(
        val id: String,
        val bitmap: Bitmap,
        var x: Float, // Center X in base bitmap coordinates
        var y: Float, // Center Y in base bitmap coordinates
        var width: Float, // Width in base bitmap pixels
        var height: Float, // Height in base bitmap pixels
        var scale: Float = 1f,
        var rotation: Float = 0f,
        val isEditable: Boolean = true
    )

    private val decorItems = mutableListOf<DecorItemState>()
    private data class DrawDecorBrush(
        val id: String,
        val bitmap: Bitmap,
        val widthRatio: Float = 0.14f
    )

    private var drawDecorBrush: DrawDecorBrush? = null
    private var isDrawingDecorStroke = false
    private var lastDrawCanvasX = 0f
    private var lastDrawCanvasY = 0f
    private var currentDrawStrokeIds = mutableListOf<String>()
    private val drawStrokeHistory = mutableListOf<List<String>>()

    private var selectedDecor: DecorItemState? = null
    private var isDraggingDecor = false

    private val decorPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val decorBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7A7A7A")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
    }
    private val actionPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val actionPillDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8E8E8")
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
    }

    // 4 corner handle paints
    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7A7A7A")
        style = Paint.Style.FILL
    }
    private val handleRadius = 6f * resources.displayMetrics.density
    private val handleTouchRadius = 28f * resources.displayMetrics.density

    private val duplicateBitmap: Bitmap by lazy {
        drawableToBitmap(ContextCompat.getDrawable(context, R.drawable.ic_duplicate))
    }
    private val trashBitmap: Bitmap by lazy {
        drawableToBitmap(ContextCompat.getDrawable(context, R.drawable.ic_trash))
    }

    // Single-finger handle drag state
    private var isDraggingHandle = false
    private var handleDragStartAngle = 0.0
    private var handleDragStartDistance = 0f
    private var handleDragStartRotation = 0f
    private var handleDragStartScale = 0f

    private enum class DecorAction {
        DUPLICATE, DELETE
    }

    private data class ActionPillLayout(
        val pillRect: RectF,
        val duplicateRect: RectF,
        val deleteRect: RectF
    )

    fun hasDecors(): Boolean = decorItems.isNotEmpty()

    fun addDecorBitmap(bitmap: Bitmap, id: String = java.util.UUID.randomUUID().toString()) {
        val curr = currentBitmap ?: return
        val decor = createDecorState(
            id = id,
            bitmap = bitmap,
            x = curr.width / 2f,
            y = curr.height / 2f,
            widthRatio = 0.45f
        )
        decorItems.add(decor)
        selectedDecor = decor
        invalidate()
    }

    fun setDrawDecorBrush(bitmap: Bitmap?, id: String = java.util.UUID.randomUUID().toString()) {
        drawDecorBrush = if (bitmap != null) DrawDecorBrush(id = id, bitmap = bitmap) else null
        selectedDecor = null
        isDraggingDecor = false
        isDraggingHandle = false
        isPanning = false
        invalidate()
    }

    fun undoLastDrawDecorStroke(): Boolean {
        if (drawStrokeHistory.isEmpty()) return false
        val lastStrokeIds = drawStrokeHistory.removeAt(drawStrokeHistory.lastIndex).toHashSet()
        if (lastStrokeIds.isEmpty()) return false
        decorItems.removeAll { it.id in lastStrokeIds }
        if (selectedDecor?.id in lastStrokeIds) {
            selectedDecor = null
        }
        invalidate()
        return true
    }

    fun clearDecors() {
        decorItems.clear()
        drawStrokeHistory.clear()
        selectedDecor = null
        invalidate()
    }

    fun renderCompositeBitmap(baseBitmap: Bitmap): Bitmap {
        if (decorItems.isEmpty()) return baseBitmap
        val result = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (decor in decorItems) {
            canvas.save()
            canvas.translate(decor.x, decor.y)
            canvas.rotate(decor.rotation)
            val w = decor.width * decor.scale
            val h = decor.height * decor.scale
            val rect = android.graphics.RectF(-w / 2f, -h / 2f, w / 2f, h / 2f)
            canvas.drawBitmap(decor.bitmap, null, rect, paint)
            canvas.restore()
        }
        return result
    }

    fun setBitmap(bitmap: Bitmap, animate: Boolean = true) {
        projectContent = null
        val previous = currentBitmap
        currentBitmap = bitmap

        if (!isMatrixInitialized) {
            // Lần đầu tiên load ảnh: Căn giữa màn hình
            resetTransformToFit(bitmap.width.toFloat(), bitmap.height.toFloat())
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

    fun resetTransformToFit(bitmap: Bitmap? = currentBitmap) {
        val target = bitmap ?: return
        resetTransformToFit(target.width.toFloat(), target.height.toFloat())
    }

    private fun min(a: Float, b: Float) = if (a < b) a else b

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!isMatrixInitialized) {
            currentBitmap?.let { resetTransformToFit(it.width.toFloat(), it.height.toFloat()) }
                ?: projectContent?.let { resetTransformToFit(it.canvas.width.toFloat(), it.canvas.height.toFloat()) }
        }
    }

    // Animation Preview
    private var currentAnimationType = com.jetpack.stickify.domain.model.StickerAnimationType.NONE
    private var animationAnimator: ValueAnimator? = null
    private var animFrameIndex = 0

    fun setAnimationType(type: com.jetpack.stickify.domain.model.StickerAnimationType) {
        currentAnimationType = type
        animationAnimator?.cancel()
        if (type.isAnimated) {
            val duration = (type.totalFrames * type.frameDelayMs).toLong().coerceAtLeast(300L)
            animationAnimator = ValueAnimator.ofInt(0, type.totalFrames - 1).apply {
                this.duration = duration
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    animFrameIndex = it.animatedValue as Int
                    invalidate()
                }
                start()
            }
        } else {
            animFrameIndex = 0
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        crossfadeAnimator?.cancel()
        viewScope.cancel()
        animationAnimator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 1. Vẽ nền caro
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), checkerPaint)

        // 2. Nếu có projectContent (Multi-layer mode từ Room DB)
        val content = projectContent
        if (content != null) {
            canvas.save()
            canvas.concat(displayMatrix)
            for (layer in content.layers) {
                if (!layer.visible) continue
                drawLayer(canvas, layer)
            }
            canvas.restore()
        } else {
            // 3. Single bitmap preview mode (Legacy Cutout / SharedViewModel)
            val curr = currentBitmap ?: return
            val prev = previousBitmap

            val hasAnim = currentAnimationType.isAnimated
            if (hasAnim) {
                canvas.save()
                val pts = floatArrayOf(curr.width / 2f, curr.height / 2f)
                displayMatrix.mapPoints(pts)
                val cx = pts[0]
                val cy = pts[1]
                val tf = com.jetpack.stickify.data.gif.StickerAnimationRenderer.getFrameTransform(currentAnimationType, animFrameIndex)
                val currentScale = currentMatrixScale()
                // tf: [translateX (ratio of w), translateY (ratio of h), scaleX, scaleY, rotation]
                canvas.translate(cx + tf[0] * curr.width * currentScale, cy + tf[1] * curr.height * currentScale)
                canvas.rotate(tf[4])
                canvas.scale(tf[2], tf[3])
                canvas.translate(-cx, -cy)
            }

            // 2. Vẽ ảnh cũ đang phai đi (Fade Out)
            if (prev != null && crossfadeProgress < 1f) {
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

            // 4. Vẽ các vật phẩm Decor
            val currentScale = currentMatrixScale()
            val density = resources.displayMetrics.density

            for (decor in decorItems) {
                val pts = floatArrayOf(decor.x, decor.y)
                displayMatrix.mapPoints(pts)
                val screenX = pts[0]
                val screenY = pts[1]
                val screenW = decor.width * decor.scale * currentScale
                val screenH = decor.height * decor.scale * currentScale

                canvas.save()
                canvas.translate(screenX, screenY)
                canvas.rotate(decor.rotation)

                val rect = RectF(-screenW / 2f, -screenH / 2f, screenW / 2f, screenH / 2f)
                canvas.drawBitmap(decor.bitmap, null, rect, decorPaint)
                canvas.restore()

                if (decor == selectedDecor) {
                    drawSelectedDecorOverlay(canvas, decor, currentScale, density)
                }
            }

            if (hasAnim) {
                canvas.restore()
            }
        }
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

    private fun getDecorAt(touchX: Float, touchY: Float): DecorItemState? {
        val currentScale = currentMatrixScale()
        for (i in decorItems.indices.reversed()) {
            val decor = decorItems[i]
            if (!decor.isEditable) continue
            val pts = floatArrayOf(decor.x, decor.y)
            displayMatrix.mapPoints(pts)
            val screenX = pts[0]
            val screenY = pts[1]
            val screenW = decor.width * decor.scale * currentScale
            val screenH = decor.height * decor.scale * currentScale

            val localX = touchX - screenX
            val localY = touchY - screenY
            val rad = Math.toRadians(-decor.rotation.toDouble())
            val rotX = (localX * Math.cos(rad) - localY * Math.sin(rad)).toFloat()
            val rotY = (localX * Math.sin(rad) + localY * Math.cos(rad)).toFloat()

            val halfW = screenW / 2f + 24f
            val halfH = screenH / 2f + 24f
            if (rotX in -halfW..halfW && rotY in -halfH..halfH) {
                return decor
            }
        }
        return null
    }

    private fun drawSelectedDecorOverlay(
        canvas: Canvas,
        decor: DecorItemState,
        matrixScale: Float,
        density: Float
    ) {
        val corners = getDecorCornersInScreen(decor, matrixScale)
        if (corners.size != 4) return

        val borderPath = Path().apply {
            moveTo(corners[0][0], corners[0][1])
            lineTo(corners[1][0], corners[1][1])
            lineTo(corners[3][0], corners[3][1])
            lineTo(corners[2][0], corners[2][1])
            close()
        }
        canvas.drawPath(borderPath, decorBorderPaint)

        for (corner in corners) {
            canvas.drawCircle(corner[0], corner[1], handleRadius, handleFillPaint)
        }

        val actionLayout = buildActionPillLayout(corners, density)
        val radius = actionLayout.pillRect.height() / 2f
        canvas.drawRoundRect(actionLayout.pillRect, radius, radius, actionPillPaint)
        canvas.drawLine(
            actionLayout.duplicateRect.right,
            actionLayout.pillRect.top + 8f * density,
            actionLayout.duplicateRect.right,
            actionLayout.pillRect.bottom - 8f * density,
            actionPillDividerPaint
        )
        canvas.drawBitmap(duplicateBitmap, null, actionLayout.duplicateRect, decorPaint)
        canvas.drawBitmap(trashBitmap, null, actionLayout.deleteRect, decorPaint)
    }

    private fun getDecorCornersInScreen(decor: DecorItemState, matrixScale: Float): Array<FloatArray> {
        val pts = floatArrayOf(decor.x, decor.y)
        displayMatrix.mapPoints(pts)
        val screenX = pts[0]
        val screenY = pts[1]
        val screenW = decor.width * decor.scale * matrixScale
        val screenH = decor.height * decor.scale * matrixScale
        val halfW = screenW / 2f
        val halfH = screenH / 2f
        val localCorners = arrayOf(
            floatArrayOf(-halfW, -halfH),
            floatArrayOf(halfW, -halfH),
            floatArrayOf(-halfW, halfH),
            floatArrayOf(halfW, halfH)
        )

        val rad = Math.toRadians(decor.rotation.toDouble())
        val cosR = Math.cos(rad).toFloat()
        val sinR = Math.sin(rad).toFloat()

        return Array(localCorners.size) { i ->
            val lx = localCorners[i][0]
            val ly = localCorners[i][1]
            floatArrayOf(
                screenX + lx * cosR - ly * sinR,
                screenY + lx * sinR + ly * cosR
            )
        }
    }

    private fun buildActionPillLayout(corners: Array<FloatArray>, density: Float): ActionPillLayout {
        val topRight = corners[1]
        val buttonSize = 28f * density
        val horizontalPadding = 12f * density
        val verticalPadding = 8f * density
        val pillWidth = horizontalPadding * 2f + buttonSize * 2f
        val pillHeight = verticalPadding * 2f + buttonSize
        val spacingTop = 14f * density

        val pillLeft = topRight[0] - pillWidth * 0.85f
        val pillTop = topRight[1] - spacingTop - pillHeight
        val pillRect = RectF(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight)

        val duplicateRect = RectF(
            pillRect.left + horizontalPadding,
            pillRect.top + verticalPadding,
            pillRect.left + horizontalPadding + buttonSize,
            pillRect.top + verticalPadding + buttonSize
        )
        val deleteRect = RectF(
            duplicateRect.right,
            duplicateRect.top,
            duplicateRect.right + buttonSize,
            duplicateRect.bottom
        )

        return ActionPillLayout(
            pillRect = pillRect,
            duplicateRect = duplicateRect,
            deleteRect = deleteRect
        )
    }

    private fun getDecorActionAt(decor: DecorItemState, touchX: Float, touchY: Float): DecorAction? {
        val corners = getDecorCornersInScreen(decor, currentMatrixScale())
        val layout = buildActionPillLayout(corners, resources.displayMetrics.density)
        return when {
            layout.duplicateRect.contains(touchX, touchY) -> DecorAction.DUPLICATE
            layout.deleteRect.contains(touchX, touchY) -> DecorAction.DELETE
            else -> null
        }
    }

    private fun duplicateDecor(decor: DecorItemState) {
        val currentScale = currentMatrixScale().coerceAtLeast(0.01f)
        val offset = 24f * resources.displayMetrics.density / currentScale
        val duplicated = decor.copy(
            id = java.util.UUID.randomUUID().toString(),
            x = decor.x + offset,
            y = decor.y + offset
        )
        decorItems.add(duplicated)
        selectedDecor = duplicated
        invalidate()
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap {
        val safeDrawable = drawable ?: error("Drawable is required for action icon")
        val size = (20f * resources.displayMetrics.density).toInt()
        return safeDrawable.toBitmap(size, size, Bitmap.Config.ARGB_8888)
    }

    /**
     * Kiểm tra xem touch có đang nằm trên 1 trong 4 handle góc không.
     * Trả về index 0-3 nếu hit, -1 nếu không.
     */
    private fun getHandleAt(decor: DecorItemState, touchX: Float, touchY: Float): Int {
        val mScale = currentMatrixScale()
        val pts = floatArrayOf(decor.x, decor.y)
        displayMatrix.mapPoints(pts)
        val screenX = pts[0]
        val screenY = pts[1]
        val screenW = decor.width * decor.scale * mScale
        val screenH = decor.height * decor.scale * mScale

        // 4 góc trong local space (đã translate nhưng chưa rotate)
        val corners = arrayOf(
            floatArrayOf(-screenW / 2f, -screenH / 2f),  // TL = 0
            floatArrayOf(screenW / 2f, -screenH / 2f),   // TR = 1
            floatArrayOf(-screenW / 2f, screenH / 2f),   // BL = 2
            floatArrayOf(screenW / 2f, screenH / 2f)     // BR = 3
        )

        val rad = Math.toRadians(decor.rotation.toDouble())
        val cosR = Math.cos(rad).toFloat()
        val sinR = Math.sin(rad).toFloat()

        for (i in corners.indices) {
            val lx = corners[i][0]
            val ly = corners[i][1]
            // Chuyển từ local sang screen (áp dụng rotation)
            val handleScreenX = screenX + lx * cosR - ly * sinR
            val handleScreenY = screenY + lx * sinR + ly * cosR

            val dx = touchX - handleScreenX
            val dy = touchY - handleScreenY
            if (dx * dx + dy * dy <= handleTouchRadius * handleTouchRadius) {
                return i
            }
        }
        return -1
    }

    /** Tâm decor trên màn hình (screen coords) */
    private fun getDecorScreenCenter(decor: DecorItemState): FloatArray {
        val pts = floatArrayOf(decor.x, decor.y)
        displayMatrix.mapPoints(pts)
        return pts
    }

    private fun createDecorState(
        id: String,
        bitmap: Bitmap,
        x: Float,
        y: Float,
        widthRatio: Float,
        isEditable: Boolean = true
    ): DecorItemState {
        val curr = currentBitmap ?: error("Current bitmap must exist before adding decor")
        val targetWidth = curr.width * widthRatio
        val aspect = (bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)).coerceAtLeast(0.01f)
        val targetHeight = targetWidth / aspect
        return DecorItemState(
            id = id,
            bitmap = bitmap,
            x = x,
            y = y,
            width = targetWidth,
            height = targetHeight,
            isEditable = isEditable
        )
    }

    private fun screenToCanvasPoint(screenX: Float, screenY: Float): PointF? {
        val inverse = Matrix()
        if (!displayMatrix.invert(inverse)) return null
        val pts = floatArrayOf(screenX, screenY)
        inverse.mapPoints(pts)
        return PointF(pts[0], pts[1])
    }

    private fun addDrawDecorAt(screenX: Float, screenY: Float): Boolean {
        val brush = drawDecorBrush ?: return false
        val point = screenToCanvasPoint(screenX, screenY) ?: return false
        val decor = createDecorState(
            id = java.util.UUID.randomUUID().toString(),
            bitmap = brush.bitmap,
            x = point.x,
            y = point.y,
            widthRatio = brush.widthRatio,
            isEditable = false
        )
        decorItems.add(decor)
        currentDrawStrokeIds.add(decor.id)
        lastDrawCanvasX = point.x
        lastDrawCanvasY = point.y
        invalidate()
        return true
    }

    private fun maybeAddDrawDecorAt(screenX: Float, screenY: Float) {
        val brush = drawDecorBrush ?: return
        val point = screenToCanvasPoint(screenX, screenY) ?: return
        val spacing = (currentBitmap?.width ?: 0) * brush.widthRatio * 0.35f
        val dx = point.x - lastDrawCanvasX
        val dy = point.y - lastDrawCanvasY
        if (dx * dx + dy * dy >= spacing * spacing) {
            addDrawDecorAt(screenX, screenY)
        }
    }

    // ---------- Two-finger gesture state for decor ----------
    private var prevDecorAngle = 0f
    private var prevDecorSpan = 0f
    private var isDecorMultiTouch = false

    private fun twoFingerAngle(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        return Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    private fun twoFingerSpan(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 1f
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat().coerceAtLeast(1f)
    }

    // ---------- Touch: pinch zoom + pan + decor rotate/scale + handle ----------
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Chỉ dùng ScaleGestureDetector khi KHÔNG thao tác trên decor
        if (!isDraggingDecor && !isDraggingHandle) {
            scaleGestureDetector.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (drawDecorBrush != null && currentBitmap != null) {
                    currentDrawStrokeIds = mutableListOf()
                    isDrawingDecorStroke = addDrawDecorAt(event.x, event.y)
                    isPanning = false
                    return true
                }

                val sel = selectedDecor

                // Ưu tiên 1: Nhấn vào nhóm action (nhân bản/xoá)
                val action = sel?.let { getDecorActionAt(it, event.x, event.y) }
                if (sel != null && action != null) {
                    when (action) {
                        DecorAction.DUPLICATE -> duplicateDecor(sel)
                        DecorAction.DELETE -> {
                            decorItems.remove(sel)
                            selectedDecor = null
                            invalidate()
                        }
                    }
                    return true
                }

                // Ưu tiên 2: Nhấn vào 1 trong 4 handle góc → bắt đầu xoay/phóng 1 ngón
                if (sel != null) {
                    val handleIdx = getHandleAt(sel, event.x, event.y)
                    if (handleIdx >= 0) {
                        isDraggingHandle = true
                        isDraggingDecor = false
                        isDecorMultiTouch = false
                        val center = getDecorScreenCenter(sel)
                        handleDragStartAngle = kotlin.math.atan2(
                            (event.y - center[1]).toDouble(),
                            (event.x - center[0]).toDouble()
                        )
                        handleDragStartDistance = kotlin.math.sqrt(
                            ((event.x - center[0]) * (event.x - center[0]) +
                             (event.y - center[1]) * (event.y - center[1])).toDouble()
                        ).toFloat().coerceAtLeast(1f)
                        handleDragStartRotation = sel.rotation
                        handleDragStartScale = sel.scale
                        return true
                    }
                }

                // Ưu tiên 3: Nhấn vào decor → kéo
                val hitDecor = getDecorAt(event.x, event.y)
                if (hitDecor != null) {
                    selectedDecor = hitDecor
                    decorItems.remove(hitDecor)
                    decorItems.add(hitDecor)
                    isDraggingDecor = true
                    isDraggingHandle = false
                    isDecorMultiTouch = false
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                    return true
                } else {
                    // Nhấn vào vùng trống → bỏ chọn decor, bắt đầu pan
                    if (selectedDecor != null) {
                        selectedDecor = null
                        invalidate()
                    }
                    isDraggingDecor = false
                    isDraggingHandle = false
                    isDecorMultiTouch = false
                    lastTouchX = averageX(event)
                    lastTouchY = averageY(event)
                    isPanning = true
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (isDrawingDecorStroke) return true
                if (isDraggingDecor && selectedDecor != null && event.pointerCount >= 2) {
                    // Bắt đầu gesture 2 ngón trên decor
                    isDecorMultiTouch = true
                    prevDecorAngle = twoFingerAngle(event)
                    prevDecorSpan = twoFingerSpan(event)
                } else if (!isDraggingDecor && !isDraggingHandle) {
                    lastTouchX = averageX(event)
                    lastTouchY = averageY(event)
                    isPanning = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawingDecorStroke) {
                    maybeAddDrawDecorAt(event.x, event.y)
                    return true
                }
                // ---- Kéo handle góc → xoay + phóng ----
                if (isDraggingHandle && selectedDecor != null) {
                    val sel = selectedDecor!!
                    val center = getDecorScreenCenter(sel)
                    val currentAngle = kotlin.math.atan2(
                        (event.y - center[1]).toDouble(),
                        (event.x - center[0]).toDouble()
                    )
                    val currentDistance = kotlin.math.sqrt(
                        ((event.x - center[0]) * (event.x - center[0]) +
                         (event.y - center[1]) * (event.y - center[1])).toDouble()
                    ).toFloat().coerceAtLeast(1f)

                    // Xoay
                    var deltaAngle = Math.toDegrees(currentAngle - handleDragStartAngle).toFloat()
                    if (deltaAngle > 180f) deltaAngle -= 360f
                    if (deltaAngle < -180f) deltaAngle += 360f
                    sel.rotation = handleDragStartRotation + deltaAngle

                    // Phóng to / thu nhỏ
                    sel.scale = (handleDragStartScale * (currentDistance / handleDragStartDistance))
                        .coerceIn(0.1f, 8f)

                    invalidate()
                    return true
                }
                // ---- Kéo decor body ----
                else if (isDraggingDecor && selectedDecor != null) {
                    if (event.pointerCount >= 2 && isDecorMultiTouch) {
                        // 2 ngón: xoay + thu phóng
                        val newAngle = twoFingerAngle(event)
                        val newSpan = twoFingerSpan(event)

                        var deltaAngle = newAngle - prevDecorAngle
                        if (deltaAngle > 180f) deltaAngle -= 360f
                        if (deltaAngle < -180f) deltaAngle += 360f
                        selectedDecor!!.rotation += deltaAngle

                        val scaleFactor = newSpan / prevDecorSpan
                        selectedDecor!!.scale = (selectedDecor!!.scale * scaleFactor).coerceIn(0.1f, 8f)

                        prevDecorAngle = newAngle
                        prevDecorSpan = newSpan

                        val cx = averageX(event)
                        val cy = averageY(event)
                        val matScale = currentMatrixScale().coerceAtLeast(0.01f)
                        selectedDecor!!.x += (cx - lastTouchX) / matScale
                        selectedDecor!!.y += (cy - lastTouchY) / matScale
                        lastTouchX = cx
                        lastTouchY = cy

                        invalidate()
                        return true
                    } else if (event.pointerCount == 1) {
                        // 1 ngón: kéo di chuyển
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        val curScale = currentMatrixScale().coerceAtLeast(0.01f)
                        selectedDecor!!.x += dx / curScale
                        selectedDecor!!.y += dy / curScale
                        lastTouchX = event.x
                        lastTouchY = event.y
                        invalidate()
                        return true
                    }
                }
                // ---- Pan canvas ----
                else if (isPanning && !scaleGestureDetector.isInProgress) {
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
                if (isDrawingDecorStroke) return true
                if (isDraggingDecor && isDecorMultiTouch) {
                    isDecorMultiTouch = false
                    val remainingIndex = if (event.actionIndex == 0) 1 else 0
                    if (remainingIndex < event.pointerCount) {
                        lastTouchX = event.getX(remainingIndex)
                        lastTouchY = event.getY(remainingIndex)
                    }
                } else {
                    val remainingIndex = if (event.actionIndex == 0) 1 else 0
                    if (remainingIndex < event.pointerCount) {
                        lastTouchX = event.getX(remainingIndex)
                        lastTouchY = event.getY(remainingIndex)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDrawingDecorStroke) {
                    if (currentDrawStrokeIds.isNotEmpty()) {
                        drawStrokeHistory.add(currentDrawStrokeIds.toList())
                    }
                    currentDrawStrokeIds = mutableListOf()
                    isDrawingDecorStroke = false
                }
                isPanning = false
                isDraggingDecor = false
                isDraggingHandle = false
                isDecorMultiTouch = false
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
            // Khi đang thao tác trên decor, bỏ qua ScaleGestureDetector (dùng logic tự tính span)
            if (isDraggingDecor) return true

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