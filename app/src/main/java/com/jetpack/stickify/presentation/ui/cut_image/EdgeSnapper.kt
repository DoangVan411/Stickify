package com.jetpack.stickify.presentation.ui.cut_image


import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Bản đồ cường độ biên (0..255) cùng kích thước ảnh gốc. */
class EdgeMap(val width: Int, val height: Int, private val data: ByteArray) {
    fun at(x: Float, y: Float): Int {
        val xi = x.roundToInt().coerceIn(0, width - 1)
        val yi = y.roundToInt().coerceIn(0, height - 1)
        return data[yi * width + xi].toInt() and 0xFF
    }
}

/**
 * "Bám biên": nhận nét vẽ tay của user, rồi hút từng điểm vào đường biên mạnh nhất
 * (theo gradient Sobel của ảnh) nằm gần đó, theo phương vuông góc với nét vẽ.
 */
object EdgeSnapper {

    private const val RESAMPLE_STEP = 3f          // px ảnh gốc giữa 2 điểm sau khi resample
    private const val MIN_EDGE_STRENGTH = 28      // biên yếu hơn mức này -> giữ nguyên nét tay
    private const val DISTANCE_PENALTY = 70f      // càng lớn càng ưu tiên biên gần nét vẽ
    private const val MEDIAN_HALF = 3             // lọc median offset để loại điểm nhảy sai
    private const val SMOOTH_HALF = 2             // làm mượt offset

    /** Tính edge map bằng Sobel trên độ sáng. Nặng -> gọi ở background thread. */
    fun computeEdgeMap(bitmap: Bitmap): EdgeMap {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)

        val lum = IntArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            lum[i] = (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
        }

        val out = ByteArray(w * h)
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                val tl = lum[i - w - 1]; val t = lum[i - w]; val tr = lum[i - w + 1]
                val l = lum[i - 1];                          val r = lum[i + 1]
                val bl = lum[i + w - 1]; val b = lum[i + w]; val br = lum[i + w + 1]
                val gx = (tr + 2 * r + br) - (tl + 2 * l + bl)
                val gy = (bl + 2 * b + br) - (tl + 2 * t + tr)
                val mag = (abs(gx) + abs(gy)) * 255 / 400
                out[i] = min(255, mag).toByte()
            }
        }
        return EdgeMap(w, h, out)
    }

    /**
     * @param raw    các điểm nét vẽ (tọa độ ảnh gốc)
     * @param radius bán kính tìm biên (px ảnh gốc)
     */
    fun snapStroke(raw: List<PointF>, edge: EdgeMap, radius: Float): List<PointF> {
        val pts = resample(raw, RESAMPLE_STEP)
        val n = pts.size
        if (n < 5) return pts
        val r = max(1, radius.roundToInt())

        // Pháp tuyến tại mỗi điểm
        val nx = FloatArray(n)
        val ny = FloatArray(n)
        for (i in 0 until n) {
            val a = pts[max(0, i - 2)]
            val b = pts[min(n - 1, i + 2)]
            val tx = b.x - a.x
            val ty = b.y - a.y
            val len = hypot(tx, ty)
            if (len > 1e-3f) {
                nx[i] = -ty / len
                ny[i] = tx / len
            }
        }

        // Tìm offset tốt nhất dọc pháp tuyến
        val offsets = FloatArray(n)
        for (i in 0 until n) {
            if (nx[i] == 0f && ny[i] == 0f) continue
            var bestK = 0
            var bestScore = Float.NEGATIVE_INFINITY
            var bestEdge = 0
            for (k in -r..r) {
                val e = edge.at(pts[i].x + nx[i] * k, pts[i].y + ny[i] * k)
                val score = e - DISTANCE_PENALTY * abs(k) / r
                if (score > bestScore) {
                    bestScore = score; bestK = k; bestEdge = e
                }
            }
            offsets[i] = if (bestEdge >= MIN_EDGE_STRENGTH) bestK.toFloat() else 0f
        }

        val smooth = movingAverage(medianFilter(offsets, MEDIAN_HALF), SMOOTH_HALF)
        return List(n) { i -> PointF(pts[i].x + nx[i] * smooth[i], pts[i].y + ny[i] * smooth[i]) }
    }

    private fun resample(raw: List<PointF>, step: Float): List<PointF> {
        if (raw.isEmpty()) return emptyList()
        val out = ArrayList<PointF>()
        var last = PointF(raw[0].x, raw[0].y)
        out.add(last)
        for (i in 1 until raw.size) {
            val cur = raw[i]
            var d = hypot(cur.x - last.x, cur.y - last.y)
            while (d >= step) {
                val t = step / d
                last = PointF(last.x + (cur.x - last.x) * t, last.y + (cur.y - last.y) * t)
                out.add(last)
                d = hypot(cur.x - last.x, cur.y - last.y)
            }
        }
        val end = raw.last()
        if (hypot(end.x - last.x, end.y - last.y) > 0.5f) out.add(PointF(end.x, end.y))
        return out
    }

    private fun medianFilter(src: FloatArray, half: Int): FloatArray {
        val n = src.size
        val out = FloatArray(n)
        val window = FloatArray(half * 2 + 1)
        for (i in 0 until n) {
            var c = 0
            for (j in i - half..i + half) window[c++] = src[j.coerceIn(0, n - 1)]
            window.sort()
            out[i] = window[window.size / 2]
        }
        return out
    }

    private fun movingAverage(src: FloatArray, half: Int): FloatArray {
        val n = src.size
        val out = FloatArray(n)
        for (i in 0 until n) {
            var sum = 0f
            var c = 0
            for (j in i - half..i + half) { sum += src[j.coerceIn(0, n - 1)]; c++ }
            out[i] = sum / c
        }
        return out
    }
}