package com.jetpack.stickify.presentation.ui.cutimage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * Dò biên từ mask nhị phân, rút gọn/làm mượt polygon, và các hàm chuyển đổi
 * qua lại giữa polygon <-> mask raster (ALPHA_8 Bitmap) để hỗ trợ cả chỉnh sửa
 * bằng cách kéo điểm (polygon) lẫn vẽ tự do bằng brush (raster mask).
 */
object ContourUtils {

    // ---------- Dò biên & làm mượt polygon (dùng cho chế độ kéo điểm + vẽ nét đứt) ----------

    fun traceLargestContour(mask: BooleanArray, width: Int, height: Int): List<PointF> {
        fun isForeground(x: Int, y: Int): Boolean {
            if (x < 0 || y < 0 || x >= width || y >= height) return false
            return mask[y * width + x]
        }

        var startX = -1
        var startY = -1
        outer@ for (y in 0 until height) {
            for (x in 0 until width) {
                if (isForeground(x, y)) { startX = x; startY = y; break@outer }
            }
        }
        if (startX == -1) return emptyList()

        val dx = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
        val dy = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)

        val contour = mutableListOf<PointF>()
        var cx = startX
        var cy = startY
        var backtrackDir = 6

        val maxSteps = width * height * 4
        var steps = 0
        do {
            contour.add(PointF(cx.toFloat(), cy.toFloat()))
            var found = false
            for (i in 0 until 8) {
                val dir = (backtrackDir + i) % 8
                val nx = cx + dx[dir]
                val ny = cy + dy[dir]
                if (isForeground(nx, ny)) {
                    cx = nx; cy = ny
                    backtrackDir = (dir + 5) % 8
                    found = true
                    break
                }
            }
            if (!found) break
            steps++
        } while ((cx != startX || cy != startY) && steps < maxSteps)

        return contour
    }

    fun simplify(points: List<PointF>, epsilon: Float): List<PointF> {
        if (points.size < 3) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true
        simplifyRecursive(points, 0, points.size - 1, epsilon, keep)
        return points.filterIndexed { i, _ -> keep[i] }
    }

    private fun simplifyRecursive(points: List<PointF>, first: Int, last: Int, epsilon: Float, keep: BooleanArray) {
        if (last <= first + 1) return
        var maxDist = 0f
        var maxIndex = first
        val p1 = points[first]
        val p2 = points[last]
        for (i in first + 1 until last) {
            val d = perpendicularDistance(points[i], p1, p2)
            if (d > maxDist) { maxDist = d; maxIndex = i }
        }
        if (maxDist > epsilon) {
            keep[maxIndex] = true
            simplifyRecursive(points, first, maxIndex, epsilon, keep)
            simplifyRecursive(points, maxIndex, last, epsilon, keep)
        }
    }

    private fun perpendicularDistance(p: PointF, a: PointF, b: PointF): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0f) return distance(p, a)
        val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq
        val projX = a.x + t * dx
        val projY = a.y + t * dy
        return distance(p, PointF(projX, projY))
    }

    private fun distance(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    fun buildSmoothClosedPath(points: List<PointF>): Path {
        val path = Path()
        if (points.size < 3) return path
        path.moveTo(points[0].x, points[0].y)
        val n = points.size
        for (i in 0 until n) {
            val p0 = points[(i - 1 + n) % n]
            val p1 = points[i]
            val p2 = points[(i + 1) % n]
            val p3 = points[(i + 2) % n]

            val cp1x = p1.x + (p2.x - p0.x) / 6f
            val cp1y = p1.y + (p2.y - p0.y) / 6f
            val cp2x = p2.x - (p3.x - p1.x) / 6f
            val cp2y = p2.y - (p3.y - p1.y) / 6f

            path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
        }
        path.close()
        return path
    }

    // ---------- Chuyển đổi qua lại polygon <-> mask raster ----------

    /** Tạo mask ALPHA_8 (255 = được chọn) trực tiếp từ mảng boolean của ML Kit. */
    fun createMaskFromBooleanArray(mask: BooleanArray, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            pixels[i] = if (mask[i]) 0xFF000000.toInt() else 0x00000000
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp
    }

    /** Đọc mask ALPHA_8 ra mảng boolean (ngưỡng alpha > 128) để dò biên lại sau khi user vẽ brush. */
    fun booleanArrayFromMask(mask: Bitmap): BooleanArray {
        val w = mask.width
        val h = mask.height
        val pixels = IntArray(w * h)
        mask.getPixels(pixels, 0, w, 0, 0, w, h)
        return BooleanArray(w * h) { i -> ((pixels[i] ushr 24) and 0xFF) > 128 }
    }

    /** Rasterize 1 polygon (đã kéo chỉnh) đè lên mask hiện có — dùng khi user chỉnh xong ở chế độ kéo điểm. */
    fun rasterizePolygonIntoMask(mask: Bitmap, points: List<PointF>) {
        val canvas = Canvas(mask)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        if (points.size < 3) return
        val path = buildSmoothClosedPath(points)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawPath(path, paint)
    }

    // ---------- Cắt ảnh cuối cùng theo mask (hỗ trợ hình dạng bất kỳ, nhiều vùng rời rạc) ----------

    /**
     * Cắt [source] theo [mask] (mask phải cùng kích thước với source, ALPHA_8).
     * Trả về bitmap đã crop theo bounding box của vùng được chọn, nền trong suốt.
     */
    fun cutoutBitmapFromMask(source: Bitmap, mask: Bitmap): Bitmap {
        require(source.width == mask.width && source.height == mask.height) {
            "Mask phải cùng kích thước với ảnh gốc (${source.width}x${source.height} vs ${mask.width}x${mask.height})"
        }

        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, null)
        val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawBitmap(mask, 0f, 0f, cutPaint)

        val bbox = computeOpaqueBoundingBox(mask) ?: return result
        return Bitmap.createBitmap(result, bbox.left, bbox.top, bbox.width(), bbox.height())
    }

    private fun computeOpaqueBoundingBox(mask: Bitmap): Rect? {
        val w = mask.width
        val h = mask.height
        val pixels = IntArray(w * h)
        mask.getPixels(pixels, 0, w, 0, 0, w, h)

        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                val alpha = (pixels[rowOffset + x] ushr 24) and 0xFF
                if (alpha > 10) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < minX || maxY < minY) return null
        return Rect(max(0, minX), max(0, minY), min(w, maxX + 1), min(h, maxY + 1))
    }
}