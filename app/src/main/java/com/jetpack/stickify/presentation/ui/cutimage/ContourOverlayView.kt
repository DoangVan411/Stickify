package com.jetpack.stickify.presentation.ui.cutimage


import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Công cụ chỉnh sửa vùng cắt: kéo điểm (POINTS) hoặc vẽ tự do (BRUSH_ADD / BRUSH_ERASE).
 */
enum class EditTool { NONE, POINTS, BRUSH_ADD, BRUSH_ERASE }

/**
 * View tự vẽ bitmap (fit-center) + mask vùng chọn + đường viền nét đứt chạy (marching ants).
 *
 * Nguồn dữ liệu "chân lý" cho vùng chọn là [selectionMask] — một Bitmap ALPHA_8 CÙNG KÍCH
 * THƯỚC với ảnh gốc (255 = được chọn). Polygon (contourPoints, dùng để vẽ nét đứt và kéo
 * điểm) luôn được dò lại (retrace) TỪ mask mỗi khi mask thay đổi do brush, và ngược lại,
 * khi user kéo điểm xong thì polygon được rasterize NGƯỢC LẠI vào mask — nhờ vậy 2 cách
 * chỉnh sửa (kéo điểm & vẽ brush) luôn đồng bộ với nhau.
 */
class ContourOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    private val imageMatrix = Matrix()
    private val inverseMatrix = Matrix()

    private var selectionMask: Bitmap? = null
    private var maskCanvas: Canvas? = null

    /** Polygon hiện tại theo tọa độ bitmap gốc — chỉ dùng để VẼ nét đứt + handle, mask mới là dữ liệu chính. */
    private var contourPoints: MutableList<PointF> = mutableListOf()

    /** Bật/tắt toàn bộ chế độ chỉnh sửa (hiện scrim làm tối + cho tương tác). */
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

    /** Bán kính brush tính theo tọa độ ẢNH GỐC (không phải px màn hình) để cỡ nét không đổi khi zoom. */
    var brushRadiusPx: Float = 60f

    private var draggingIndex: Int = -1
    private var lastBrushPoint: PointF? = null
    private val touchSlopPx = 48f

    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
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

    /** Phủ tối toàn bộ view, sau đó "khoét lỗ" theo mask ở bước vẽ tiếp theo. */
    private val scrimPaint = Paint().apply { color = Color.argb(160, 0, 0, 0) }

    /** DST_OUT: xóa scrim tại vùng mask có alpha > 0 -> lộ ảnh gốc sáng bình thường ở vùng được chọn. */
    private val maskCutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        isFilterBitmap = true
    }

    private val brushAddPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL_AND_STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val brushErasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private var dashAnimator: ValueAnimator? = null

    fun setImageBitmap(bmp: Bitmap) {
        bitmap = bmp
        requestLayout()
        invalidate()
    }

    /**
     * Gán mask vùng chọn ban đầu (thường build từ kết quả ML Kit). Phải CÙNG KÍCH THƯỚC
     * với bitmap gốc. Sau khi gán, tự động dò lại polygon để hiển thị nét đứt + handle.
     */
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

    /** Polygon contour hiện tại (theo tọa độ bitmap gốc) — dùng để cắt ảnh cuối cùng. */
    fun getContourPointsInBitmapSpace(): List<PointF> = contourPoints.map { PointF(it.x, it.y) }

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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateImageMatrix()
    }

    private fun updateImageMatrix() {
        val bmp = bitmap ?: return
        if (width == 0 || height == 0) return

        val scale = min2(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        val dx = (width - bmp.width * scale) / 2f
        val dy = (height - bmp.height * scale) / 2f

        imageMatrix.reset()
        imageMatrix.postScale(scale, scale)
        imageMatrix.postTranslate(dx, dy)
        imageMatrix.invert(inverseMatrix)
    }

    private fun min2(a: Float, b: Float) = if (a < b) a else b

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        updateImageMatrix()
        canvas.drawBitmap(bmp, imageMatrix, null)

        val mask = selectionMask
        if (editMode && mask != null) {
            // saveLayer để "phủ tối rồi khoét lỗ" hoạt động đúng, không làm tối luôn ảnh gốc bên dưới.
            val layerId = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
            canvas.drawBitmap(mask, imageMatrix, maskCutPaint) // mask cùng hệ tọa độ bitmap nên dùng chung imageMatrix
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
    }

    private fun mapBitmapToView(p: PointF): PointF {
        val pts = floatArrayOf(p.x, p.y)
        imageMatrix.mapPoints(pts)
        return PointF(pts[0], pts[1])
    }

    private fun mapViewToBitmap(p: PointF): PointF {
        val pts = floatArrayOf(p.x, p.y)
        inverseMatrix.mapPoints(pts)
        return PointF(pts[0], pts[1])
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editMode) return false
        return when (currentTool) {
            EditTool.POINTS -> handlePointsTouch(event)
            EditTool.BRUSH_ADD, EditTool.BRUSH_ERASE -> handleBrushTouch(event)
            EditTool.NONE -> false
        }
    }

    // ---------- Chế độ kéo điểm ----------

    private fun handlePointsTouch(event: MotionEvent): Boolean {
        if (contourPoints.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val idx = findNearestHandleIndex(event.x, event.y)
                if (idx != -1) {
                    draggingIndex = idx
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingIndex != -1) {
                    val bitmapPoint = mapViewToBitmap(PointF(event.x, event.y))
                    clampToBitmapBounds(bitmapPoint)
                    contourPoints[draggingIndex] = bitmapPoint
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggingIndex != -1) {
                    selectionMask?.let { ContourUtils.rasterizePolygonIntoMask(it, contourPoints) }
                }
                draggingIndex = -1
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
            }
        }
        return super.onTouchEvent(event)
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

    // ---------- Chế độ vẽ brush (thêm / xóa vùng chọn) ----------

    private fun handleBrushTouch(event: MotionEvent): Boolean {
        val canvas = maskCanvas ?: return false
        val bitmapPoint = mapViewToBitmap(PointF(event.x, event.y))
        clampToBitmapBounds(bitmapPoint)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                drawBrushDot(canvas, bitmapPoint)
                lastBrushPoint = bitmapPoint
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val last = lastBrushPoint
                if (last != null) drawBrushLine(canvas, last, bitmapPoint) else drawBrushDot(canvas, bitmapPoint)
                lastBrushPoint = bitmapPoint
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastBrushPoint = null
                parent?.requestDisallowInterceptTouchEvent(false)
                retraceContourFromMask() // chỉ dò lại polygon khi kết thúc nét vẽ, tránh giật lag khi đang kéo
                return true
            }
        }
        return true
    }

    private fun drawBrushDot(canvas: Canvas, p: PointF) {
        val paint = if (currentTool == EditTool.BRUSH_ADD) brushAddPaint else brushErasePaint
        canvas.drawCircle(p.x, p.y, brushRadiusPx, paint)
    }

    private fun drawBrushLine(canvas: Canvas, from: PointF, to: PointF) {
        val paint = if (currentTool == EditTool.BRUSH_ADD) brushAddPaint else brushErasePaint
        paint.strokeWidth = brushRadiusPx * 2
        canvas.drawLine(from.x, from.y, to.x, to.y, paint)
        // vẽ thêm chấm tròn ở điểm cuối để nối liền mạch, tránh hở góc khi đổi hướng đột ngột
        canvas.drawCircle(to.x, to.y, brushRadiusPx, paint)
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
            val epsilon = kotlin.math.max(mask.width, mask.height) * 0.004f
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