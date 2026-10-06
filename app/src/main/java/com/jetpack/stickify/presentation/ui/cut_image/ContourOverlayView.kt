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
 * - Brush (thêm/xóa): vẽ nét ĐỨT 1.5dp bao quanh vùng cần thêm/xóa. Khi nhấc tay, nét được
 *   "hút" vào biên thật của ảnh (xem [EdgeSnapper]), khép kín rồi fill vào/ra khỏi mask.
 *
 * Nguồn dữ liệu "chân lý" cho vùng chọn là [selectionMask] (ALPHA_8, cùng kích thước ảnh gốc).
 */
class ContourOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private enum class Interaction { NONE, PAN, DRAG_POINT, BRUSH }

    private val dp = resources.displayMetrics.density

    private var bitmap: Bitmap? = null
    @Volatile private var edgeMap: EdgeMap? = null
    private var edgeToken = 0
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
            cancelStroke()
            invalidate()
        }

    /** Bán kính tìm biên để hút nét vẽ vào (dp trên màn hình). */
    var snapRadiusDp: Float = 14f

    private var interaction = Interaction.NONE
    private var draggingIndex: Int = -1
    private val touchSlopPx = 48f

    // ---- Brush (nét đứt) ----
    private val rawStroke = ArrayList<PointF>()   // nét vẽ theo tọa độ ảnh gốc
    private val viewStrokePath = Path()           // nét vẽ theo tọa độ view (để preview)
    private val closePath = Path()                // đoạn khép kín preview (điểm cuối -> điểm đầu)
    private var strokeStartView: PointF? = null
    private var strokeLastView: PointF? = null
    private var isBrushing = false

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

    // Paint fill lên mask
    private val brushAddPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val brushErasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    // Nét đứt 1.5dp của brush: lớp bóng đen mờ (để nhìn rõ trên mọi nền) + lớp màu bên trên.
    private fun brushDashEffect() = DashPathEffect(floatArrayOf(8f * dp, 5f * dp), 0f)

    private val brushShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * dp
        color = Color.argb(120, 0, 0, 0)
        pathEffect = brushDashEffect()
    }
    private val brushDashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
        pathEffect = brushDashEffect()
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
        computeEdgeMapAsync(bmp)
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

    private fun computeEdgeMapAsync(bmp: Bitmap) {
        edgeMap = null
        val token = ++edgeToken
        Thread {
            val map = try { EdgeSnapper.computeEdgeMap(bmp) } catch (e: Throwable) { null }
            post { if (token == edgeToken) edgeMap = map }
        }.start()
    }

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
        edgeToken++ // huỷ kết quả edge map đang tính dở
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

        if (contourPoints.size >= 3) {
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
            brushDashPaint.color =
                if (currentTool == EditTool.BRUSH_ADD) Color.rgb(76, 175, 80)
                else Color.rgb(244, 67, 54)

            canvas.drawPath(viewStrokePath, brushShadowPaint)
            canvas.drawPath(viewStrokePath, brushDashPaint)

            // Đoạn khép kín (mờ hơn) cho user hình dung vùng sẽ được fill.
            val s0 = strokeStartView
            val s1 = strokeLastView
            if (s0 != null && s1 != null && rawStroke.size >= 2) {
                closePath.reset()
                closePath.moveTo(s1.x, s1.y)
                closePath.lineTo(s0.x, s0.y)
                val oldAlpha = brushDashPaint.alpha
                brushDashPaint.alpha = 110
                canvas.drawPath(closePath, brushDashPaint)
                brushDashPaint.alpha = oldAlpha
            }
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
                endCurrentInteraction(commit = false)
                interaction = Interaction.PAN
            }
            MotionEvent.ACTION_MOVE -> when (interaction) {
                Interaction.DRAG_POINT -> dragPoint(event)
                Interaction.BRUSH -> continueStroke(event)
                else -> Unit
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                endCurrentInteraction(commit = event.actionMasked == MotionEvent.ACTION_UP)
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

    private fun endCurrentInteraction(commit: Boolean) {
        when (interaction) {
            Interaction.DRAG_POINT -> {
                if (draggingIndex != -1) {
                    selectionMask?.let { ContourUtils.rasterizePolygonIntoMask(it, contourPoints) }
                }
                draggingIndex = -1
                invalidate()
            }
            Interaction.BRUSH -> if (commit) endStroke() else cancelStroke()
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

    // ---------- Brush nét đứt + bám biên ----------

    private fun beginStroke(event: MotionEvent) {
        rawStroke.clear()
        viewStrokePath.reset()

        val p = mapViewToBitmap(event.x, event.y)
        clampToBitmapBounds(p)
        rawStroke.add(p)

        viewStrokePath.moveTo(event.x, event.y)
        strokeStartView = PointF(event.x, event.y)
        strokeLastView = PointF(event.x, event.y)
        isBrushing = true
        invalidate()
    }

    private fun continueStroke(event: MotionEvent) {
        if (!isBrushing) return
        // Dùng cả các điểm lịch sử để nét vẽ mượt khi vẽ nhanh
        for (h in 0 until event.historySize) {
            appendStrokePoint(event.getHistoricalX(h), event.getHistoricalY(h))
        }
        appendStrokePoint(event.x, event.y)
        invalidate()
    }

    private fun appendStrokePoint(viewX: Float, viewY: Float) {
        val p = mapViewToBitmap(viewX, viewY)
        clampToBitmapBounds(p)
        val last = rawStroke.lastOrNull()
        if (last != null && kotlin.math.hypot(p.x - last.x, p.y - last.y) < 0.5f) return
        rawStroke.add(p)
        viewStrokePath.lineTo(viewX, viewY)
        strokeLastView = PointF(viewX, viewY)
    }

    private fun cancelStroke() {
        isBrushing = false
        rawStroke.clear()
        viewStrokePath.reset()
        strokeStartView = null
        strokeLastView = null
        invalidate()
    }

    private fun endStroke() {
        val points = ArrayList(rawStroke)
        cancelStroke()
        applyStrokeToMask(points)
    }

    /** Hút nét vào biên -> khép kín -> fill (thêm) hoặc clear (xóa) trên mask. */
    private fun applyStrokeToMask(points: List<PointF>) {
        val canvas = maskCanvas ?: return
        if (points.size < 3) return

        var length = 0f
        for (i in 1 until points.size) {
            length += kotlin.math.hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        }
        if (length < 10f * dp / currentScale()) return // nét quá ngắn -> bỏ qua

        val radius = (snapRadiusDp * dp / currentScale()).coerceIn(3f, 48f)
        val edge = edgeMap
        val snapped = if (edge != null) EdgeSnapper.snapStroke(points, edge, radius) else points
        if (snapped.size < 3) return

        val region = Path().apply {
            moveTo(snapped[0].x, snapped[0].y)
            for (i in 1 until snapped.size) lineTo(snapped[i].x, snapped[i].y)
            close()
        }
        val paint = if (currentTool == EditTool.BRUSH_ADD) brushAddPaint else brushErasePaint
        canvas.drawPath(region, paint)

        retraceContourFromMask() // dò lại polygon 1 lần sau khi nhấc tay
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