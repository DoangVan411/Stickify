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

    /**
     * Chạy Subject Segmentation trên bitmap ĐÃ được xoay đúng chiều (xem ImageUtils).
     * Dùng rotationDegrees = 0 vì ảnh đã upright.
     */
    suspend fun segment(bitmap: Bitmap): SegmentationMaskResult = suspendCancellableCoroutine { cont ->
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundConfidenceMask()
            .build()
        val segmenter = SubjectSegmentation.getClient(options)

        val inputImage = InputImage.fromBitmap(bitmap, 0)

        segmenter.process(inputImage)
            .addOnSuccessListener { result ->
                val w = bitmap.width
                val h = bitmap.height
                val maskBuffer = result.foregroundConfidenceMask // FloatBuffer, kích thước w*h
                val mask = BooleanArray(w * h)
                if (maskBuffer != null) {
                    maskBuffer.rewind()
                    for (i in 0 until w * h) {
                        mask[i] = maskBuffer.get(i) > CONFIDENCE_THRESHOLD
                    }
                }
                segmenter.close()
                if (cont.isActive) cont.resume(SegmentationMaskResult(w, h, mask))
            }
            .addOnFailureListener { e ->
                segmenter.close()
                if (cont.isActive) cont.resumeWithException(e)
            }

        cont.invokeOnCancellation { segmenter.close() }
    }
}