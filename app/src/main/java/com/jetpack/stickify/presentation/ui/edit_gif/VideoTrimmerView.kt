package com.jetpack.stickify.presentation.ui.edit_gif

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Custom View cho thanh cắt video (Video Trimmer):
 * - Dải thumbnail các khung hình video
 * - Khung chọn có viền vàng và 2 tai kéo (handles) 2 bên
 * - Vùng không chọn bị phủ màu tối mờ
 * - Hỗ trợ kéo handle trái/phải và trượt cả khung chọn
 */
class VideoTrimmerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnTrimChangeListener {
        fun onTrimRangeChanged(startMs: Long, endMs: Long)
        fun onTrimHandleTouch(isDragging: Boolean, currentMs: Long)
    }

    var listener: OnTrimChangeListener? = null

    var videoDurationMs: Long = 10000L
        set(value) {
            field = max(1000L, value)
            endTrimMs = min(field, startTrimMs + defaultDurationMs)
            invalidate()
        }

    var defaultDurationMs: Long = 8000L
    var minTrimDurationMs: Long = 1000L
    var maxTrimDurationMs: Long = 15000L

    var startTrimMs: Long = 0L
        private set
    var endTrimMs: Long = 8000L
        private set

    private var thumbnails: List<Bitmap> = emptyList()

    private val density = resources.displayMetrics.density
    private val handleWidth = 14f * density
    private val borderWidth = 3f * density
    private val cornerRadius = 8f * density
    private val touchRadius = 24f * density

    private val yellowColor = Color.parseColor("#FFB300")
    private val dimOverlayColor = Color.parseColor("#99000000")

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = yellowColor
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = yellowColor
        style = Paint.Style.FILL
    }

    private val handleGripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66000000")
        strokeWidth = 1.5f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = dimOverlayColor
        style = Paint.Style.FILL
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val clipPath = Path()

    private enum class DragTarget {
        NONE, LEFT_HANDLE, RIGHT_HANDLE, WINDOW
    }

    private var currentDrag = DragTarget.NONE
    private var lastTouchX = 0f

    fun setThumbnails(bitmaps: List<Bitmap>) {
        this.thumbnails = bitmaps
        invalidate()
    }

    fun setTrimRange(startMs: Long, endMs: Long) {
        this.startTrimMs = startMs.coerceIn(0L, videoDurationMs - minTrimDurationMs)
        this.endTrimMs = endMs.coerceIn(startTrimMs + minTrimDurationMs, videoDurationMs)
        invalidate()
        listener?.onTrimRangeChanged(this.startTrimMs, this.endTrimMs)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val innerLeft = handleWidth
        val innerRight = w - handleWidth
        val innerWidth = innerRight - innerLeft
        if (innerWidth <= 0) return

        // 1. Vẽ dải thumbnail bo góc
        clipPath.reset()
        clipPath.addRoundRect(
            RectF(innerLeft, 0f, innerRight, h),
            cornerRadius,
            cornerRadius,
            Path.Direction.CW
        )
        canvas.save()
        canvas.clipPath(clipPath)

        if (thumbnails.isNotEmpty()) {
            val thumbWidth = innerWidth / thumbnails.size.toFloat()
            for (i in thumbnails.indices) {
                val bmp = thumbnails[i]
                val left = innerLeft + i * thumbWidth
                val right = left + thumbWidth
                val srcRect = Rect(0, 0, bmp.width, bmp.height)
                val dstRect = RectF(left, 0f, right, h)
                canvas.drawBitmap(bmp, srcRect, dstRect, thumbPaint)
            }
        } else {
            // Nền tối nếu chưa có thumbnail
            val darkPaint = Paint().apply { color = Color.parseColor("#333333") }
            canvas.drawRect(innerLeft, 0f, innerRight, h, darkPaint)
        }
        canvas.restore()

        // 2. Tính vị trí pixel của 2 handle
        val leftX = innerLeft + (startTrimMs.toFloat() / videoDurationMs) * innerWidth
        val rightX = innerLeft + (endTrimMs.toFloat() / videoDurationMs) * innerWidth

        // 3. Phủ mờ vùng không chọn
        if (leftX > innerLeft) {
            canvas.drawRect(innerLeft, 0f, leftX, h, dimPaint)
        }
        if (rightX < innerRight) {
            canvas.drawRect(rightX, 0f, innerRight, h, dimPaint)
        }

        // 4. Viền trên và dưới của khung chọn
        canvas.drawLine(leftX, borderWidth / 2f, rightX, borderWidth / 2f, borderPaint)
        canvas.drawLine(leftX, h - borderWidth / 2f, rightX, h - borderWidth / 2f, borderPaint)

        // 5. Vẽ handle trái
        val leftHandleRect = RectF(leftX - handleWidth, 0f, leftX, h)
        canvas.drawRoundRect(leftHandleRect, 6f * density, 6f * density, handlePaint)
        // Nét gạch biểu thị tay cầm
        canvas.drawLine(
            leftX - handleWidth / 2f,
            h * 0.35f,
            leftX - handleWidth / 2f,
            h * 0.65f,
            handleGripPaint
        )

        // 6. Vẽ handle phải
        val rightHandleRect = RectF(rightX, 0f, rightX + handleWidth, h)
        canvas.drawRoundRect(rightHandleRect, 6f * density, 6f * density, handlePaint)
        // Nét gạch biểu thị tay cầm
        canvas.drawLine(
            rightX + handleWidth / 2f,
            h * 0.35f,
            rightX + handleWidth / 2f,
            h * 0.65f,
            handleGripPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val innerLeft = handleWidth
        val innerRight = w - handleWidth
        val innerWidth = innerRight - innerLeft
        if (innerWidth <= 0) return false

        val x = event.x
        val leftX = innerLeft + (startTrimMs.toFloat() / videoDurationMs) * innerWidth
        val rightX = innerLeft + (endTrimMs.toFloat() / videoDurationMs) * innerWidth

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x
                currentDrag = when {
                    abs(x - (leftX - handleWidth / 2f)) <= touchRadius -> DragTarget.LEFT_HANDLE
                    abs(x - (rightX + handleWidth / 2f)) <= touchRadius -> DragTarget.RIGHT_HANDLE
                    x in leftX..rightX -> DragTarget.WINDOW
                    else -> DragTarget.NONE
                }

                if (currentDrag != DragTarget.NONE) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val currentMs = if (currentDrag == DragTarget.RIGHT_HANDLE) endTrimMs else startTrimMs
                    listener?.onTrimHandleTouch(true, currentMs)
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (currentDrag == DragTarget.NONE) return false
                val dx = x - lastTouchX
                lastTouchX = x
                val dMs = (dx / innerWidth * videoDurationMs).toLong()

                when (currentDrag) {
                    DragTarget.LEFT_HANDLE -> {
                        val newStart = (startTrimMs + dMs)
                            .coerceIn(0L, endTrimMs - minTrimDurationMs)
                            .coerceAtLeast(endTrimMs - maxTrimDurationMs)
                        if (newStart != startTrimMs) {
                            startTrimMs = newStart
                            invalidate()
                            listener?.onTrimRangeChanged(startTrimMs, endTrimMs)
                            listener?.onTrimHandleTouch(true, startTrimMs)
                        }
                    }

                    DragTarget.RIGHT_HANDLE -> {
                        val newEnd = (endTrimMs + dMs)
                            .coerceIn(startTrimMs + minTrimDurationMs, videoDurationMs)
                            .coerceAtMost(startTrimMs + maxTrimDurationMs)
                        if (newEnd != endTrimMs) {
                            endTrimMs = newEnd
                            invalidate()
                            listener?.onTrimRangeChanged(startTrimMs, endTrimMs)
                            listener?.onTrimHandleTouch(true, endTrimMs)
                        }
                    }

                    DragTarget.WINDOW -> {
                        val windowLen = endTrimMs - startTrimMs
                        var newStart = startTrimMs + dMs
                        var newEnd = endTrimMs + dMs
                        if (newStart < 0) {
                            newStart = 0
                            newEnd = windowLen
                        }
                        if (newEnd > videoDurationMs) {
                            newEnd = videoDurationMs
                            newStart = videoDurationMs - windowLen
                        }
                        if (newStart != startTrimMs || newEnd != endTrimMs) {
                            startTrimMs = newStart
                            endTrimMs = newEnd
                            invalidate()
                            listener?.onTrimRangeChanged(startTrimMs, endTrimMs)
                            listener?.onTrimHandleTouch(true, startTrimMs)
                        }
                    }

                    DragTarget.NONE -> {}
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (currentDrag != DragTarget.NONE) {
                    val currentMs = if (currentDrag == DragTarget.RIGHT_HANDLE) endTrimMs else startTrimMs
                    listener?.onTrimHandleTouch(false, currentMs)
                    currentDrag = DragTarget.NONE
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
