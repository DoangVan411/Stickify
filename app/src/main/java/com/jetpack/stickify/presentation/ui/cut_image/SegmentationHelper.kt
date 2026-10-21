package com.jetpack.stickify.presentation.ui.cut_image

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Kết quả segmentation đã "phẳng hóa" thành mảng boolean dễ xử lý. */
data class SegmentationMaskResult(
    val width: Int,
    val height: Int,
    /** true = pixel thuộc chủ thể (foreground) */
    val foregroundMask: BooleanArray
)

object SegmentationHelper {

    private const val CONFIDENCE_THRESHOLD = 0.5f

    // Giới hạn kích thước tối đa đưa vào ML Kit.
    // Model AI bên trong ML Kit chỉ hoạt động ở độ phân giải ~512-1024px.
    private const val MAX_SEGMENTATION_DIMENSION = 1024

    /**
     * Chạy Subject Segmentation trên bitmap ĐÃ được xoay đúng chiều.
     * Tự động scale down bản tạm để tiết kiệm Native RAM và map kết quả về kích thước ban đầu.
     */
    suspend fun segment(bitmap: Bitmap): SegmentationMaskResult = suspendCancellableCoroutine { cont ->
        val origW = bitmap.width
        val origH = bitmap.height

        // 1. Kiểm tra và scale down bitmap tạm nếu quá lớn trước khi đưa vào ML Kit
        val maxDim = maxOf(origW, origH)
        val needsScale = maxDim > MAX_SEGMENTATION_DIMENSION

        val processedBitmap = if (needsScale) {
            val scale = MAX_SEGMENTATION_DIMENSION.toFloat() / maxDim
            Bitmap.createScaledBitmap(
                bitmap,
                (origW * scale).toInt(),
                (origH * scale).toInt(),
                true
            )
        } else {
            bitmap
        }

        val procW = processedBitmap.width
        val procH = processedBitmap.height

        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundConfidenceMask()
            .build()
        val segmenter = SubjectSegmentation.getClient(options)

        val inputImage = InputImage.fromBitmap(processedBitmap, 0)

        segmenter.process(inputImage)
            .addOnSuccessListener { result ->
                try {
                    val maskBuffer = result.foregroundConfidenceMask // FloatBuffer kích thước procW * procH
                    val smallMask = BooleanArray(procW * procH)

                    if (maskBuffer != null) {
                        maskBuffer.rewind()
                        // Đọc mảng Float dạng Bulk Copy (chạy ở cấp C++) thay vì loop get(i) từng phần tử
                        val floatArray = FloatArray(procW * procH)
                        maskBuffer.get(floatArray)

                        for (i in 0 until procW * procH) {
                            smallMask[i] = floatArray[i] > CONFIDENCE_THRESHOLD
                        }
                    }

                    // 2. Map mask trở lại kích thước gốc bằng thuật toán Nearest Neighbor nếu có scale
                    val finalMask = if (needsScale) {
                        scaleMaskNearestNeighbor(smallMask, procW, procH, origW, origH)
                    } else {
                        smallMask
                    }

                    if (cont.isActive) cont.resume(SegmentationMaskResult(origW, origH, finalMask))
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                } finally {
                    segmenter.close()
                    // Recycle bitmap bản tạm ngay khi hoàn thành
                    if (needsScale && processedBitmap != bitmap && !processedBitmap.isRecycled) {
                        processedBitmap.recycle()
                    }
                }
            }
            .addOnFailureListener { e ->
                segmenter.close()
                if (needsScale && processedBitmap != bitmap && !processedBitmap.isRecycled) {
                    processedBitmap.recycle()
                }
                if (cont.isActive) cont.resumeWithException(e)
            }

        cont.invokeOnCancellation {
            segmenter.close()
            if (needsScale && processedBitmap != bitmap && !processedBitmap.isRecycled) {
                processedBitmap.recycle()
            }
        }
    }

    /**
     * Upscale mảng BooleanMask từ kích thước nhỏ về kích thước ảnh gốc bằng Nearest Neighbor.
     * Xử lý trực tiếp trên mảng 1D primitives, không tạo Bitmap/Canvas nên chỉ tốn < 2ms và không tốn RAM.
     */
    private fun scaleMaskNearestNeighbor(
        srcMask: BooleanArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int
    ): BooleanArray {
        val dstMask = BooleanArray(dstW * dstH)
        val xRatio = srcW.toFloat() / dstW
        val yRatio = srcH.toFloat() / dstH

        for (y in 0 until dstH) {
            val srcY = (y * yRatio).toInt().coerceIn(0, srcH - 1)
            val dstRowOffset = y * dstW
            val srcRowOffset = srcY * srcW

            for (x in 0 until dstW) {
                val srcX = (x * xRatio).toInt().coerceIn(0, srcW - 1)
                dstMask[dstRowOffset + x] = srcMask[srcRowOffset + srcX]
            }
        }
        return dstMask
    }
}