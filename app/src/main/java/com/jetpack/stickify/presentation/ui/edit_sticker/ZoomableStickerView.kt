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

    // xử lý text
    var onTextLayerDoubleTapped: ((TextLayer) -> Unit)? = null
    var onTextDecorDoubleTapped: ((DecorItemState) -> Unit)? = null // Thêm dòng này

    private var lastTapTimeMs: Long = 0
    private var lastTappedLayerId: String? = null

    // Multi-layer rendering support
    private var projectContent: ProjectContent? = null
    private var assetLoader: AssetLoader? = null
    private val layerBitmaps = mutableMapOf<String, Bitmap>()
    private val layerSourceKeys = mutableMapOf<String, String>()
    private var viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
        var bitmap: Bitmap,
        var x: Float, // Center X in base bitmap coordinates
        var y: Float, // Center Y in base bitmap coordinates
        var width: Float, // Width in base bitmap pixels
        var height: Float, // Height in base bitmap pixels
        var scale: Float = 1f,
        var rotation: Float = 0f,
        val isEditable: Boolean = true,
        var textContent: String? = null, // Lưu lại nội dung chữ
        var textColor: Int? = null,       // Lưu lại màu chữ
        val isSubject: Boolean = false // <--- THÊM CỜ NÀY
    )

    private val decorItems = mutableListOf<DecorItemState>()

    // ---------- Project mode: layer decor/text <-> decorItems ----------
    /** Thông tin 1 "con dấu" khi vẽ decor bằng brush ở project mode (đơn vị: tọa độ canvas project). */
    data class StampSpec(val id: String, val cx: Float, val cy: Float, val scale: Float)

    /** Người dùng kéo/xoay/phóng xong 1 layer: (layerId, cx, cy, scale, rotationDeg). */
    var onLayerTransformed: ((String, Float, Float, Float, Float) -> Unit)? = null
    /** Người dùng bấm xóa 1 layer. */
    var onLayerDeleted: ((String) -> Unit)? = null
    /** Người dùng bấm nhân bản: (sourceId, newId, cx, cy). */
    var onLayerDuplicated: ((String, String, Float, Float) -> Unit)? = null
    /** Kết thúc 1 nét vẽ brush: (bitmap brush, danh sách con dấu). */
    var onDecorStampsCommitted: ((Bitmap, List<StampSpec>) -> Unit)? = null

    private var gestureSnapshot: FloatArray? = null
    private var pendingSelectId: String? = null
    private val preloadedIds = mutableSetOf<String>()
    private val textBitmapCache = mutableMapOf<String, Pair<String, Bitmap>>()

    private fun isProjectMode() = projectContent != null
    private fun hasContent() = currentBitmap != null || projectContent != null
    private fun baseWidth(): Float = currentBitmap?.width?.toFloat()
        ?: projectContent?.canvas?.width?.toFloat()?.takeIf { it > 0f } ?: 512f
    private fun baseHeight(): Float = currentBitmap?.height?.toFloat()
        ?: projectContent?.canvas?.height?.toFloat()?.takeIf { it > 0f } ?: 512f
    private data class DrawDecorBrush(
        val id: String,
        val bitmap: Bitmap,
        val widthRatio: Float = 0.18f,
        val minScaleRatio: Float = 0.85f,
        val maxScaleRatio: Float = 1.25f
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
    private val decorFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#151B85F3")
        style = Paint.Style.FILL
    }
    private val decorBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B85F3")
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
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
        color = Color.parseColor("#1B85F3")
        style = Paint.Style.FILL
    }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
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

    private fun getBaseCanvasWidth(): Float {
        return currentBitmap?.width?.toFloat()
            ?: projectContent?.canvas?.width?.toFloat()?.takeIf { it > 0f }
            ?: 512f
    }

    private fun getBaseCanvasHeight(): Float {
        return currentBitmap?.height?.toFloat()
            ?: projectContent?.canvas?.height?.toFloat()?.takeIf { it > 0f }
            ?: 512f
    }

    fun addDecorBitmap(bitmap: Bitmap, id: String = java.util.UUID.randomUUID().toString()) {
        if (!hasContent()) return
        val decor = createDecorState(
            id = id,
            bitmap = bitmap,
            x = baseWidth() / 2f,
            y = baseHeight() / 2f,
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

    private fun assetKey(asset: AssetRef): String = when (asset) {
        is CustomAsset -> "c:${asset.relativePath}"
        is BuiltinAsset -> "b:${asset.packId}/${asset.assetId}"
        is RemoteAsset -> "r:${asset.url}"
        else -> asset.toString()
    }

    fun setProjectContent(content: ProjectContent, loader: AssetLoader) {
        crossfadeAnimator?.cancel()
        currentBitmap = null
        previousBitmap = null
        projectContent = content
        assetLoader = loader

        val canvasWidth = content.canvas.width.toFloat().takeIf { it > 0f } ?: 512f
        val canvasHeight = content.canvas.height.toFloat().takeIf { it > 0f } ?: 512f

        if (!isMatrixInitialized) {
            resetTransformToFit(canvasWidth, canvasHeight)
        }

        // Bỏ bitmap của các layer đã bị xóa (Undo thêm layer, v.v.)
        val liveIds = content.layers.map { it.id }.toSet()
        preloadedIds.removeAll(liveIds) // layer đã xuất hiện trong content -> hết là "preload"
        layerBitmaps.keys.retainAll(liveIds + preloadedIds)
        layerSourceKeys.keys.retainAll(liveIds)
        textBitmapCache.keys.retainAll(liveIds)

        // Chỉ load layer mới hoặc layer đổi nguồn ảnh -> không nháy/không decode lại mỗi lần thao tác
        val pending = content.layers.mapNotNull { layer ->
            val asset: AssetRef? = when (layer) {
                is DecorationLayer -> layer.asset
                is SubjectLayer ->
                    if (layer.styledPath.isNotBlank()) CustomAsset(layer.styledPath) else layer.source
                else -> null
            }
            if (asset == null) return@mapNotNull null
            val key = assetKey(asset)
            val cached = layerBitmaps[layer.id]
            if (layerSourceKeys[layer.id] == key && cached != null && !cached.isRecycled) null
            else Triple(layer, asset, key)
        }

        syncLayerItems(content)
        invalidate()
        if (pending.isEmpty()) return

        viewScope.launch {
            for ((layer, asset, key) in pending) {
                // Decode ảnh ở IO, không chặn main thread
                val bmp = withContext(Dispatchers.IO) {
                    loader.loadBitmap(asset)
                        ?: if (layer is SubjectLayer) loader.loadBitmap(layer.source) else null
                }
                if (bmp != null) {
                    layerBitmaps[layer.id] = bmp
                    layerSourceKeys[layer.id] = key
                    // Chỉ sync nếu content hiện tại vẫn là content đã yêu cầu (tránh ghi đè state mới hơn)
                    if (projectContent === content) syncLayerItems(content)
                    postInvalidate()
                } else {
                    android.util.Log.w("ZoomableStickerView", "Không load được ảnh cho layer ${layer.id}: $key")
                }
            }
        }
    }

    /** Vẽ toàn bộ layer của project ra bitmap đúng kích thước canvas (dùng để export / thumbnail). */
    fun renderProjectBitmap(): Bitmap? {
        val content = projectContent ?: return null
        val w = content.canvas.width.takeIf { it > 0 } ?: 512
        val h = content.canvas.height.takeIf { it > 0 } ?: 512
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        for (layer in content.layers) {
            if (layer.visible && layer !is DecorationLayer && layer !is TextLayer && layer !is SubjectLayer) drawLayer(c, layer)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (decor in decorItems) {
            c.save()
            c.translate(decor.x, decor.y)
            c.rotate(decor.rotation)
            val dw = decor.width * decor.scale
            val dh = decor.height * decor.scale
            c.drawBitmap(decor.bitmap, null, RectF(-dw / 2f, -dh / 2f, dw / 2f, dh / 2f), paint)
            c.restore()
        }
        return bmp
    }

    /** Đăng ký trước bitmap cho 1 layer sắp được thêm (để hiển thị ngay, không phải chờ đọc file). */
    fun preloadLayerBitmap(layerId: String, bitmap: Bitmap) {
        preloadedIds.add(layerId)
        layerBitmaps[layerId] = bitmap
        projectContent?.let { syncLayerItems(it) } // Đồng bộ lại kích thước lập tức
        invalidate()
    }

    /** Chọn 1 layer (hiện khung + handle). Nếu layer chưa sẵn sàng sẽ chọn ngay khi nó xuất hiện. */
    fun selectLayer(layerId: String) {
        pendingSelectId = layerId
        applyPendingSelection()
        invalidate()
    }

    private fun applyPendingSelection() {
        val id = pendingSelectId ?: return
        val item = decorItems.find { it.id == id } ?: return
        selectedDecor = item
        pendingSelectId = null
    }

    private fun buildTextLayerBitmap(layer: TextLayer): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = layer.colorArgb
            textSize = 120f
            typeface = Typeface.DEFAULT_BOLD
            textSkewX = if (layer.italic) -0.25f else 0f
        }
        val lines = layer.content.split("\n")
        val lineHeight = paint.descent() - paint.ascent()
        val maxWidth = lines.maxOf { paint.measureText(it) }
        val padding = 40
        val bmp = Bitmap.createBitmap(
            (maxWidth + padding * 2).toInt().coerceAtLeast(1),
            (lineHeight * lines.size + padding * 2).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val c = Canvas(bmp)
        var y = padding - paint.ascent()
        val factor = when (layer.align) {
            TextAlign.LEFT -> 0f
            TextAlign.CENTER -> 0.5f
            TextAlign.RIGHT -> 1f
        }
        for (line in lines) {
            c.drawText(line, padding + (maxWidth - paint.measureText(line)) * factor, y, paint)
            y += lineHeight
        }
        return bmp
    }

    /**
     * Đồng bộ các layer Decoration/Text của project thành decorItems để dùng lại toàn bộ
     * cơ chế chọn / kéo / xoay / phóng / xóa / nhân bản của chế độ cũ.
     * Tọa độ decorItems ở project mode chính là tọa độ canvas project.
     */
    private fun syncLayerItems(content: ProjectContent) {
        // Đang kéo layer thì không dựng lại, tránh giật/đứt cử chỉ
        if (isDraggingDecor || isDraggingHandle) return

        val prevSelectedId = pendingSelectId ?: selectedDecor?.id
        val cw = content.canvas.width.toFloat().takeIf { it > 0f } ?: 512f
        val items = mutableListOf<DecorItemState>()

        for (layer in content.layers) {
            if (!layer.visible) continue
            val tf = layer.transform
            if (layer is DecorationLayer) {
                val bmp = layerBitmaps[layer.id]?.takeIf { !it.isRecycled } ?: continue
                val w = cw * 0.45f
                val h = w * bmp.height / bmp.width.coerceAtLeast(1)
                items.add(DecorItemState(layer.id, bmp, tf.cx, tf.cy, w, h, tf.scale, tf.rotationDeg))
            } else if (layer is TextLayer) {
                if (layer.content.isBlank()) continue
                val key = "${layer.content}|${layer.colorArgb}|${layer.align}|${layer.italic}"
                val cached = textBitmapCache[layer.id]
                val bmp = if (cached != null && cached.first == key) cached.second
                else buildTextLayerBitmap(layer).also { textBitmapCache[layer.id] = key to it }
                val k = 0.5f * layer.fontSizeRatio.coerceIn(0.5f, 3f)
                items.add(
                    DecorItemState(
                        layer.id, bmp, tf.cx, tf.cy, bmp.width * k, bmp.height * k,
                        tf.scale, tf.rotationDeg,
                        textContent = layer.content, textColor = layer.colorArgb
                    )
                )
            } else if(layer is SubjectLayer){
                // Chuyển SubjectLayer thành item có thể tương tác xoay/phóng
                val bmp = layerBitmaps[layer.id]?.takeIf { !it.isRecycled } ?: continue
                val cw = content.canvas.width.toFloat().takeIf { it > 0f } ?: 512f
                val ch = content.canvas.height.toFloat().takeIf { it > 0f } ?: 512f
                val base = min(cw / bmp.width, ch / bmp.height)
                items.add(DecorItemState(layer.id, bmp, tf.cx, tf.cy, bmp.width * base, bmp.height * base, tf.scale, tf.rotationDeg, isSubject = true))
            }
        }

        // Giữ lại các con dấu brush đang chờ lưu (chưa thành layer) để không bị nháy mất
        val liveIds = items.map { it.id }.toSet()
        val contentIds = content.layers.map { it.id }.toSet()
        val waiting = decorItems.filter { it.id in preloadedIds && it.id !in contentIds && it.id !in liveIds }

        decorItems.clear()
        decorItems.addAll(items)
        decorItems.addAll(waiting)

        selectedDecor = prevSelectedId?.let { id -> decorItems.find { it.id == id } }
        if (pendingSelectId != null) applyPendingSelection()
        invalidate()
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
            drawDecorItems(canvas)

            if (hasAnim) {
                canvas.restore()
            }
        }
    }

    /**
     * Tỉ lệ nền để ảnh nằm vừa trong canvas project (512x512):
     * - Chủ thể: fit vào canvas (ảnh cắt thường rất lớn, vẽ nguyên kích thước sẽ tràn khỏi canvas
     *   và người dùng chỉ thấy một mảnh ở giữa).
     * - Decor: rộng 45% canvas, giống luồng thêm decor cũ.
     */
    private fun baseScaleFor(layer: Layer, bmp: Bitmap): Float {
        val content = projectContent
        val cw = (content?.canvas?.width ?: 512).takeIf { it > 0 }?.toFloat() ?: 512f
        val ch = (content?.canvas?.height ?: 512).takeIf { it > 0 }?.toFloat() ?: 512f
        return when (layer) {
            is SubjectLayer -> min(cw / bmp.width, ch / bmp.height)
            is DecorationLayer -> cw * 0.45f / bmp.width
            else -> 1f
        }
    }

    /** Vẽ decorItems (và khung chọn) ở hệ tọa độ màn hình, dùng chung cho cả 2 chế độ. */
    private fun drawDecorItems(canvas: Canvas) {
        val currentScale = currentMatrixScale()
        val density = resources.displayMetrics.density
        for (decor in decorItems) {
            val pts = floatArrayOf(decor.x, decor.y)
            displayMatrix.mapPoints(pts)
            val screenW = decor.width * decor.scale * currentScale
            val screenH = decor.height * decor.scale * currentScale

            canvas.save()
            canvas.translate(pts[0], pts[1])
            canvas.rotate(decor.rotation)
            val rect = RectF(-screenW / 2f, -screenH / 2f, screenW / 2f, screenH / 2f)
            canvas.drawBitmap(decor.bitmap, null, rect, decorPaint)
            canvas.restore()

            if (decor == selectedDecor) {
                drawSelectedDecorOverlay(canvas, decor, currentScale, density)
            }
        }
    }

    private fun drawLayer(canvas: Canvas, layer: Layer) {
        canvas.save()
        val transform = layer.transform

        // translate -> rotate -> scale (flipX chỉ lật ảnh, không đảo chiều xoay)
        canvas.translate(transform.cx, transform.cy)
        canvas.rotate(transform.rotationDeg)
        canvas.scale(if (transform.flipX) -transform.scale else transform.scale, transform.scale)

        val alphaInt = (transform.opacity.coerceIn(0f, 1f) * 255).toInt()

        when (layer) {
            is DecorationLayer, is SubjectLayer -> {
                val bmp = layerBitmaps[layer.id]
                if (bmp != null && !bmp.isRecycled) {
                    val base = baseScaleFor(layer, bmp)
                    canvas.scale(base, base)
                    bitmapPaintCurrent.alpha = alphaInt
                    canvas.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, bitmapPaintCurrent)
                    bitmapPaintCurrent.alpha = 255
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
        canvas.drawPath(borderPath, decorFillPaint)
        canvas.drawPath(borderPath, decorBorderPaint)

        for (corner in corners) {
            canvas.drawCircle(corner[0], corner[1], handleRadius, handleFillPaint)
            canvas.drawCircle(corner[0], corner[1], handleRadius, handleStrokePaint)
        }
        if (!decor.isSubject) {
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
        val randomScale = brush.minScaleRatio +
            (brush.maxScaleRatio - brush.minScaleRatio) * kotlin.random.Random.nextFloat()
        val decor = createDecorState(
            id = java.util.UUID.randomUUID().toString(),
            bitmap = brush.bitmap,
            x = point.x,
            y = point.y,
            widthRatio = brush.widthRatio * randomScale,
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
        val spacing = baseWidth() * brush.widthRatio * 0.8f
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




                if (drawDecorBrush != null && hasContent()) {
                    currentDrawStrokeIds = mutableListOf()
                    isDrawingDecorStroke = addDrawDecorAt(event.x, event.y)
                    isPanning = false
                    return true
                }

                // Chữ/decor của project đã được đồng bộ vào decorItems nên dùng chung luồng hit-test bên dưới.
                val hitDecorForTap = getDecorAt(event.x, event.y)

                if (hitDecorForTap != null && hitDecorForTap.textContent != null) {
                    val currentTime = System.currentTimeMillis()
                    if (hitDecorForTap.id == lastTappedLayerId && (currentTime - lastTapTimeMs) < 300) {
                        onTextDecorDoubleTapped?.invoke(hitDecorForTap)
                    }
                    lastTapTimeMs = currentTime
                    lastTappedLayerId = hitDecorForTap.id
                }

                val sel = selectedDecor

                // Ưu tiên 1: Nhấn vào nhóm action (nhân bản/xoá)
                val action = sel?.let { getDecorActionAt(it, event.x, event.y) }
                if (sel != null && action != null) {
                    when (action) {
                        DecorAction.DUPLICATE -> {
                            if (isProjectMode()) {
                                val newId = java.util.UUID.randomUUID().toString()
                                val offset = 24f * resources.displayMetrics.density /
                                        currentMatrixScale().coerceAtLeast(0.01f)
                                pendingSelectId = newId
                                onLayerDuplicated?.invoke(sel.id, newId, sel.x + offset, sel.y + offset)
                            } else {
                                duplicateDecor(sel)
                            }
                        }
                        DecorAction.DELETE -> {
                            if (isProjectMode()) onLayerDeleted?.invoke(sel.id)
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
                        gestureSnapshot = floatArrayOf(sel.x, sel.y, sel.scale, sel.rotation)
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
                    gestureSnapshot = floatArrayOf(hitDecor.x, hitDecor.y, hitDecor.scale, hitDecor.rotation)
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
                        val brush = drawDecorBrush
                        if (isProjectMode() && brush != null) {
                            // Project: mỗi con dấu trở thành 1 DecorationLayer được lưu + undo được
                            val cw = baseWidth()
                            val ids = currentDrawStrokeIds.toHashSet()
                            val stamps = decorItems.filter { it.id in ids }.map {
                                preloadedIds.add(it.id)
                                layerBitmaps[it.id] = it.bitmap
                                StampSpec(it.id, it.x, it.y, it.width * it.scale / (cw * 0.45f))
                            }
                            if (stamps.isNotEmpty()) onDecorStampsCommitted?.invoke(brush.bitmap, stamps)
                        } else {
                            drawStrokeHistory.add(currentDrawStrokeIds.toList())
                        }
                    }
                    currentDrawStrokeIds = mutableListOf()
                    isDrawingDecorStroke = false
                }
                // Kết thúc kéo/xoay/phóng 1 layer -> báo ra ngoài để lưu vào lịch sử
                if (isProjectMode() && (isDraggingDecor || isDraggingHandle)) {
                    val d = selectedDecor
                    val snap = gestureSnapshot
                    // Reset cờ TRƯỚC khi báo ra ngoài: state mới có thể được áp dụng đồng bộ và syncLayerItems
                    // sẽ bỏ qua nếu vẫn còn cờ đang kéo.
                    isDraggingDecor = false
                    isDraggingHandle = false
                    isDecorMultiTouch = false
                    if (d != null && snap != null) {
                        val changed = Math.abs(d.x - snap[0]) > 0.01f || Math.abs(d.y - snap[1]) > 0.01f ||
                                Math.abs(d.scale - snap[2]) > 0.001f || Math.abs(d.rotation - snap[3]) > 0.01f
                        if (changed) onLayerTransformed?.invoke(d.id, d.x, d.y, d.scale, d.rotation)
                    }
                }
                gestureSnapshot = null
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

    fun addTextItem(text: String, textColor: Int = Color.WHITE, existingId: String? = null) {
        if (text.isBlank()) return

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 120f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }

        val lines = text.split("\n")
        var maxWidth = 0f
        var totalHeight = 0f
        val textHeight = paint.descent() - paint.ascent()

        for (line in lines) {
            val width = paint.measureText(line)
            if (width > maxWidth) maxWidth = width
            totalHeight += textHeight
        }

        val padding = 40
        val bmpWidth = (maxWidth + padding * 2).toInt()
        val bmpHeight = (totalHeight + padding * 2).toInt()

        val textBitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(textBitmap)

        var y = padding - paint.ascent()
        for (line in lines) {
            val lineWidth = paint.measureText(line)
            val x = padding + (maxWidth - lineWidth) / 2f
            canvas.drawText(line, x, y, paint)
            y += textHeight
        }

        val idToUse = existingId ?: java.util.UUID.randomUUID().toString()
        val existingDecor = decorItems.find { it.id == idToUse }

        if (existingDecor != null) {
            // Nếu đã tồn tại -> Cập nhật lại ảnh và nội dung chữ
            existingDecor.bitmap = textBitmap
            existingDecor.textContent = text
            existingDecor.textColor = textColor

            val aspect = (textBitmap.width.toFloat() / textBitmap.height.toFloat().coerceAtLeast(1f)).coerceAtLeast(0.01f)
            existingDecor.height = existingDecor.width / aspect
        } else {
            // Thêm mới
            if (!hasContent()) return
            val decor = createDecorState(
                id = idToUse,
                bitmap = textBitmap,
                x = baseWidth() / 2f,
                y = baseHeight() / 2f,
                widthRatio = 0.45f
            )
            decor.textContent = text
            decor.textColor = textColor
            decorItems.add(decor)
            selectedDecor = decor
        }
        invalidate()
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