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
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.jetpack.stickify.R

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

    // Decor data & state
    data class DecorItemState(
        val id: String,
        val bitmap: Bitmap,
        var x: Float, // Center X in base bitmap coordinates
        var y: Float, // Center Y in base bitmap coordinates
        var width: Float, // Width in base bitmap pixels
        var height: Float, // Height in base bitmap pixels
        var scale: Float = 1f,
        var rotation: Float = 0f
    )

    private val decorItems = mutableListOf<DecorItemState>()
    private var selectedDecor: DecorItemState? = null
    private var isDraggingDecor = false

    private val decorPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val decorBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val deleteBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5252")
        style = Paint.Style.FILL
    }

    // 4 corner handle paints
    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(4f * resources.displayMetrics.density, 0f, 1f, Color.argb(60, 0, 0, 0))
    }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private val handleRadius = 7f * resources.displayMetrics.density
    private val handleTouchRadius = 28f * resources.displayMetrics.density

    // Trash icon bitmap (lazy loaded from vector drawable)
    private val trashBitmap: Bitmap by lazy {
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_trash)!!
        val size = (16f * resources.displayMetrics.density).toInt()
        drawable.toBitmap(size, size, Bitmap.Config.ARGB_8888)
    }

    // Single-finger handle drag state
    private var isDraggingHandle = false
    private var handleDragStartAngle = 0.0
    private var handleDragStartDistance = 0f
    private var handleDragStartRotation = 0f
    private var handleDragStartScale = 0f

    fun hasDecors(): Boolean = decorItems.isNotEmpty()

    fun addDecorBitmap(bitmap: Bitmap, id: String = java.util.UUID.randomUUID().toString()) {
        val curr = currentBitmap ?: return
        val targetWidth = curr.width * 0.45f
        val aspect = (bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)).coerceAtLeast(0.01f)
        val targetHeight = targetWidth / aspect

        val decor = DecorItemState(
            id = id,
            bitmap = bitmap,
            x = curr.width / 2f,
            y = curr.height / 2f,
            width = targetWidth,
            height = targetHeight
        )
        decorItems.add(decor)
        selectedDecor = decor
        invalidate()
    }

    fun clearDecors() {
        decorItems.clear()
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
        animationAnimator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 1. Vẽ nền caro
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), checkerPaint)

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

        // 4. Vẽ các vật phẩm Decor
        val currentScale = currentMatrixScale()
        val density = resources.displayMetrics.density
        val deleteRadius = 14f * density

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

            if (decor == selectedDecor) {
                // Khung viền nét đứt khi được chọn
                canvas.drawRoundRect(rect, 8f * density, 8f * density, decorBorderPaint)

                // 4 chấm tròn ở 4 góc (handles xoay/phóng)
                val corners = arrayOf(
                    floatArrayOf(rect.left, rect.top),      // Trên-trái
                    floatArrayOf(rect.right, rect.top),     // Trên-phải
                    floatArrayOf(rect.left, rect.bottom),   // Dưới-trái
                    floatArrayOf(rect.right, rect.bottom)   // Dưới-phải
                )
                for (corner in corners) {
                    canvas.drawCircle(corner[0], corner[1], handleRadius, handleFillPaint)
                    canvas.drawCircle(corner[0], corner[1], handleRadius, handleStrokePaint)
                }

                // Nút xoá (thùng rác) — đặt phía trên góc trên-phải
                val delCx = rect.right + deleteRadius * 0.5f
                val delCy = rect.top - deleteRadius * 0.9f
                canvas.drawCircle(delCx, delCy, deleteRadius, deleteBgPaint)
                val trashIconSize = deleteRadius * 1.2f
                val trashRect = RectF(
                    delCx - trashIconSize / 2f,
                    delCy - trashIconSize / 2f,
                    delCx + trashIconSize / 2f,
                    delCy + trashIconSize / 2f
                )
                canvas.drawBitmap(trashBitmap, null, trashRect, decorPaint)
            }

            canvas.restore()
        }

        if (hasAnim) {
            canvas.restore()
        }
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

    private fun isTouchOnDelete(decor: DecorItemState, touchX: Float, touchY: Float): Boolean {
        val mScale = currentMatrixScale()
        val density = resources.displayMetrics.density
        val deleteRadius = 14f * density
        val pts = floatArrayOf(decor.x, decor.y)
        displayMatrix.mapPoints(pts)
        val screenX = pts[0]
        val screenY = pts[1]
        val screenW = decor.width * decor.scale * mScale
        val screenH = decor.height * decor.scale * mScale

        // Vị trí nút xoá trong hệ toạ độ quay: phía trên góc trên-phải
        val delLocalX = screenW / 2f + deleteRadius * 0.5f
        val delLocalY = -screenH / 2f - deleteRadius * 0.9f

        val localX = touchX - screenX
        val localY = touchY - screenY
        val rad = Math.toRadians(-decor.rotation.toDouble())
        val rotX = (localX * Math.cos(rad) - localY * Math.sin(rad)).toFloat()
        val rotY = (localX * Math.sin(rad) + localY * Math.cos(rad)).toFloat()

        val radius = 28f * density
        val dx = rotX - delLocalX
        val dy = rotY - delLocalY
        return (dx * dx + dy * dy) <= radius * radius
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
                val sel = selectedDecor

                // Ưu tiên 1: Nhấn vào nút thùng rác
                if (sel != null && isTouchOnDelete(sel, event.x, event.y)) {
                    decorItems.remove(sel)
                    selectedDecor = null
                    invalidate()
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