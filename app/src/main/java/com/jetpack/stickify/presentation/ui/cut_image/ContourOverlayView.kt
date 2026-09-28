package com.jetpack.stickify.presentation.ui.cut_image

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.max
import kotlin.math.min

/**
 * Công cụ chỉnh sửa vùng cắt: kéo điểm (POINTS) hoặc vẽ tự do (BRUSH_ADD / BRUSH_ERASE).
 */
enum class EditTool { NONE, POINTS, BRUSH_ADD, BRUSH_ERASE }

/**
 * View tự vẽ bitmap + mask vùng chọn + đường viền nét đứt.
 *
 * - Ảnh mặc định được KHÉO DÃN FULL CHIỀU NGANG (fit-width).
 * - Hỗ trợ pinch-zoom, kéo (pan) bằng 1 ngón (khi không vẽ) hoặc 2 ngón (luôn luôn), double-tap để zoom.
 * - Brush: vẽ tới đâu hiện tới đó (mask + nét vẽ xanh/đỏ + con trỏ brush cập nhật realtime).
 *
 * Nguồn dữ liệu "chân lý" cho vùng chọn là [selectionMask] (ALPHA_8, cùng kích thước ảnh gốc).
 */
class ContourOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private enum class Interaction { NONE, PAN, DRAG_POINT, BRUSH }

    private var bitmap: Bitmap? = null
    private val imageMatrix = Matrix()
    private val inverseMatrix = Matrix()

    // ---- Trạng thái zoom / pan ----
    private var baseScale = 1f      // scale để ảnh vừa full chiều ngang
    private var zoom = 1f           // hệ số zoom của người dùng, 1 = fit-width
    private var transX = 0f
    private var transY = 0f
    private val maxZoom = 8f

    private var selectionMask: Bitmap? = null
    private var maskCanvas: Canvas? = null

    private var contourPoints: MutableList<PointF> = mutableListOf()

    var editMode: Boolean = false
        set(value) {
            field = value
            if (!value) currentTool = EditTool.NONE
            invalidate()
        }

    var currentTool: EditTool = EditTool.NONE
        set(value) {
            field = value
            lastBrushPoint = null
            invalidate()
        }

    /** Bán kính brush theo tọa độ ẢNH GỐC. */
    var brushRadiusPx: Float = 60f

    private var interaction = Interaction.NONE
    private var draggingIndex: Int = -1
    private var lastBrushPoint: PointF? = null
    private val touchSlopPx = 48f

    // ---- Live brush preview ----
    private val strokePath = Path()            // theo tọa độ bitmap
    private var isBrushing = false
    private var cursorView: PointF? = null      // vị trí ngón tay (tọa độ view)

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
        pathEffect = DashPathEffect(floatArrayOf(22f, 14f), 0f)
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#2196F3")
    }

    private val scrimPaint = Paint().apply { color = Color.argb(160, 0, 0, 0) }

    private val maskCutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        isFilterBitmap = true
    }

    // Paint vẽ lên mask (FILL cho chấm tròn, linePaint = bản copy dạng STROKE cho đoạn thẳng)
    private val brushAddPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val brushErasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Nét vẽ xem trước (xanh = thêm, đỏ = xóa)
    private val strokePreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }

    private var dashAnimator: ValueAnimator? = null

    // ---- Gesture detectors ----
    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomBy(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (interaction != Interaction.PAN) return false
                transX -= dx
                transY -= dy
                applyTransform()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Không chiếm double-tap khi đang dùng brush/kéo điểm.
                if (editMode && currentTool != EditTool.NONE) return false
                if (zoom > 1.01f) resetZoom() else zoomBy(2.5f, e.x, e.y)
                return true
            }
        })

    // ======================= Public API =======================

    fun setImageBitmap(bmp: Bitmap) {
        bitmap = bmp
        resetZoom()
        requestLayout()
        invalidate()
    }

    fun setSelectionMask(mask: Bitmap) {
        val mutableMask = if (mask.isMutable && mask.config == Bitmap.Config.ALPHA_8) {
            mask
        } else {
            mask.copy(Bitmap.Config.ALPHA_8, true)
        }
        selectionMask = mutableMask
        maskCanvas = Canvas(mutableMask)
        retraceContourFromMask()
    }

    fun getSelectionMask(): Bitmap? = selectionMask

    fun getContourPointsInBitmapSpace(): List<PointF> = contourPoints.map { PointF(it.x, it.y) }

    /** Đưa ảnh về trạng thái fit-width, không zoom. */
    fun resetZoom() {
        zoom = 1f
        transX = 0f
        transY = 0f
        applyTransform()
    }

    fun startDashAnimation() {
        dashAnimator?.cancel()
        dashAnimator = ValueAnimator.ofFloat(0f, 36f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                dashPaint.pathEffect = DashPathEffect(floatArrayOf(22f, 14f), it.animatedValue as Float)
                invalidate()
            }
            start()
        }
    }

    fun stopDashAnimation() {
        dashAnimator?.cancel()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        dashAnimator?.cancel()
    }

    // ======================= Transform =======================

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyTransform()
    }

    private fun currentScale() = baseScale * zoom

    /** Cho phép zoom nhỏ hơn fit-width một chút nếu ảnh cao, để xem được toàn bộ ảnh. */
    private fun minZoom(): Float {
        val bmp = bitmap ?: return 1f
        if (width == 0 || height == 0) return 1f
        val fitAll = height.toFloat() / (bmp.height * baseScale)
        return min(1f, fitAll)
    }

    private fun zoomBy(factor: Float, focusX: Float, focusY: Float) {
        val newZoom = (zoom * factor).coerceIn(minZoom(), maxZoom)
        val f = newZoom / zoom
        transX = focusX - (focusX - transX) * f
        transY = focusY - (focusY - transY) * f
        zoom = newZoom
        applyTransform()
    }

    private fun applyTransform() {
        updateImageMatrix()
        invalidate()
    }

    private fun updateImageMatrix() {
        val bmp = bitmap ?: return
        if (width == 0 || height == 0) return

        baseScale = width.toFloat() / bmp.width
        val s = baseScale * zoom

        // Giới hạn kéo: không cho ảnh trôi ra khỏi màn hình; nếu ảnh nhỏ hơn view thì căn giữa.
        val sw = bmp.width * s
        val sh = bmp.height * s
        transX = if (sw <= width) (width - sw) / 2f else transX.coerceIn(width - sw, 0f)
        transY = if (sh <= height) (height - sh) / 2f else transY.coerceIn(height - sh, 0f)

        imageMatrix.reset()
        imageMatrix.postScale(s, s)
        imageMatrix.postTranslate(transX, transY)
        imageMatrix.invert(inverseMatrix)
    }

    // ======================= Draw =======================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        canvas.drawBitmap(bmp, imageMatrix, bitmapPaint)

        val mask = selectionMask
        if (editMode && mask != null) {
            val layerId = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
            canvas.drawBitmap(mask, imageMatrix, maskCutPaint)
            canvas.restoreToCount(layerId)
        }

        // Đang vẽ brush thì ẩn contour cũ (đã lỗi thời) cho đỡ rối, chỉ hiện lại sau khi nhấc tay.
        if (contourPoints.size >= 3 && !isBrushing) {
            val viewPoints = contourPoints.map { mapBitmapToView(it) }
            val smoothPath = ContourUtils.buildSmoothClosedPath(viewPoints)
            canvas.drawPath(smoothPath, dashPaint)

            if (editMode && currentTool == EditTool.POINTS) {
                for (p in viewPoints) {
                    canvas.drawCircle(p.x, p.y, 10f, handlePaint)
                    canvas.drawCircle(p.x, p.y, 10f, handleStrokePaint)
                }
            }
        }

        if (isBrushing) {
            // Nét vẽ xem trước, hiện đến đâu vẽ đến đó
            canvas.save()
            canvas.concat(imageMatrix)
            strokePreviewPaint.strokeWidth = brushRadiusPx * 2
            strokePreviewPaint.color =
                if (currentTool == EditTool.BRUSH_ADD) Color.argb(110, 76, 175, 80)
                else Color.argb(110, 244, 67, 54)
            canvas.drawPath(strokePath, strokePreviewPaint)
            canvas.restore()

            // Con trỏ brush (bán kính quy đổi ra px màn hình theo zoom)
            cursorView?.let { canvas.drawCircle(it.x, it.y, brushRadiusPx * currentScale(), cursorPaint) }
        }
    }

    private fun mapBitmapToView(p: PointF): PointF {
        val pts = floatArrayOf(p.x, p.y)
        imageMatrix.mapPoints(pts)
        return PointF(pts[0], pts[1])
    }

    private fun mapViewToBitmap(x: Float, y: Float): PointF {
        val pts = floatArrayOf(x, y)
        inverseMatrix.mapPoints(pts)
        return PointF(pts[0], pts[1])
    }

    // ======================= Touch =======================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null) return false

        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                interaction = decideInteraction(event)
                if (interaction == Interaction.BRUSH) beginStroke(event)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Ngón thứ 2 chạm vào -> chuyển sang zoom/pan, kết thúc thao tác đang làm.
                endCurrentInteraction()
                interaction = Interaction.PAN
            }
            MotionEvent.ACTION_MOVE -> when (interaction) {
                Interaction.DRAG_POINT -> dragPoint(event)
                Interaction.BRUSH -> continueStroke(event)
                else -> Unit
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                endCurrentInteraction()
                interaction = Interaction.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun decideInteraction(event: MotionEvent): Interaction {
        if (!editMode) return Interaction.PAN
        return when (currentTool) {
            EditTool.POINTS -> {
                val idx = findNearestHandleIndex(event.x, event.y)
                if (idx != -1) {
                    draggingIndex = idx
                    Interaction.DRAG_POINT
                } else Interaction.PAN // chạm ra ngoài handle -> kéo ảnh
            }
            EditTool.BRUSH_ADD, EditTool.BRUSH_ERASE ->
                if (maskCanvas != null) Interaction.BRUSH else Interaction.PAN
            EditTool.NONE -> Interaction.PAN
        }
    }

    private fun endCurrentInteraction() {
        when (interaction) {
            Interaction.DRAG_POINT -> {
                if (draggingIndex != -1) {
                    selectionMask?.let { ContourUtils.rasterizePolygonIntoMask(it, contourPoints) }
                }
                draggingIndex = -1
                invalidate()
            }
            Interaction.BRUSH -> endStroke()
            else -> Unit
        }
    }

    // ---------- Kéo điểm ----------

    private fun dragPoint(event: MotionEvent) {
        if (draggingIndex == -1) return
        val p = mapViewToBitmap(event.x, event.y)
        clampToBitmapBounds(p)
        contourPoints[draggingIndex] = p
        invalidate()
    }

    private fun findNearestHandleIndex(viewX: Float, viewY: Float): Int {
        var bestIndex = -1
        var bestDist = touchSlopPx
        for (i in contourPoints.indices) {
            val vp = mapBitmapToView(contourPoints[i])
            val d = kotlin.math.hypot((vp.x - viewX).toDouble(), (vp.y - viewY).toDouble()).toFloat()
            if (d < bestDist) { bestDist = d; bestIndex = i }
        }
        return bestIndex
    }

    // ---------- Brush (vẽ realtime) ----------

    private fun beginStroke(event: MotionEvent) {
        val canvas = maskCanvas ?: return
        val p = mapViewToBitmap(event.x, event.y)
        clampToBitmapBounds(p)

        isBrushing = true
        strokePath.reset()
        strokePath.moveTo(p.x, p.y)
        strokePath.lineTo(p.x + 0.01f, p.y) // để chấm đơn cũng hiện nét (round cap)

        drawBrushDot(canvas, p)
        lastBrushPoint = p
        cursorView = PointF(event.x, event.y)
        invalidate()
    }

    private fun continueStroke(event: MotionEvent) {
        val canvas = maskCanvas ?: return
        // Dùng cả các điểm lịch sử để nét vẽ mượt khi vẽ nhanh
        for (h in 0 until event.historySize) {
            appendStrokePoint(canvas, event.getHistoricalX(h), event.getHistoricalY(h))
        }
        appendStrokePoint(canvas, event.x, event.y)
        cursorView = PointF(event.x, event.y)
        invalidate()
    }

    private fun appendStrokePoint(canvas: Canvas, viewX: Float, viewY: Float) {
        val p = mapViewToBitmap(viewX, viewY)
        clampToBitmapBounds(p)
        val last = lastBrushPoint
        if (last != null) {
            drawBrushLine(canvas, last, p)
            strokePath.lineTo(p.x, p.y)
        } else {
            drawBrushDot(canvas, p)
            strokePath.moveTo(p.x, p.y)
        }
        lastBrushPoint = p
    }

    private fun endStroke() {
        lastBrushPoint = null
        isBrushing = false
        cursorView = null
        strokePath.reset()
        retraceContourFromMask() // dò lại polygon 1 lần sau khi nhấc tay để không bị lag
    }

    private fun drawBrushDot(canvas: Canvas, p: PointF) {
        val paint = if (currentTool == EditTool.BRUSH_ADD) brushAddPaint else brushErasePaint
        canvas.drawCircle(p.x, p.y, brushRadiusPx, paint)
    }

    private fun drawBrushLine(canvas: Canvas, from: PointF, to: PointF) {
        val base = if (currentTool == EditTool.BRUSH_ADD) brushAddPaint else brushErasePaint
        linePaint.set(base)
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeCap = Paint.Cap.ROUND
        linePaint.strokeJoin = Paint.Join.ROUND
        linePaint.strokeWidth = brushRadiusPx * 2
        canvas.drawLine(from.x, from.y, to.x, to.y, linePaint)
    }

    // ---------- Đồng bộ polygon <-> mask ----------

    private fun retraceContourFromMask() {
        val mask = selectionMask
        if (mask == null) {
            contourPoints = mutableListOf()
            invalidate()
            return
        }
        val boolMask = ContourUtils.booleanArrayFromMask(mask)
        val raw = ContourUtils.traceLargestContour(boolMask, mask.width, mask.height)
        contourPoints = if (raw.size < 3) {
            mutableListOf()
        } else {
            val epsilon = max(mask.width, mask.height) * 0.004f
            ContourUtils.simplify(raw, epsilon).toMutableList()
        }
        invalidate()
    }

    private fun clampToBitmapBounds(p: PointF) {
        val bmp = bitmap ?: return
        p.x = p.x.coerceIn(0f, bmp.width.toFloat())
        p.y = p.y.coerceIn(0f, bmp.height.toFloat())
    }
}