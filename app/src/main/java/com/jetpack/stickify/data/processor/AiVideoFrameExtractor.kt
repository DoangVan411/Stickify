package com.jetpack.stickify.data.processor

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import javax.inject.Inject
import kotlin.math.max

/**
 * Lấy frame trực tiếp từ MP4, BỎ QUA bước tách nền (giữ nguyên nền trắng).
 * Tối ưu cực đại tốc độ và RAM.
 */
class AiVideoFrameExtractor @Inject constructor() {

    suspend fun extract(file: File): List<Bitmap> {
        val startedAt = SystemClock.elapsedRealtime()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val count = (durationMs * FPS / 1000).toInt().coerceIn(1, MAX_FRAMES)

            val frames = ArrayList<Bitmap>(count)

            for (i in 0 until count) {
                currentCoroutineContext().ensureActive()
                val timeUs = i * 1_000_000L / FPS

                // Trích xuất trực tiếp ảnh ở kích thước nhỏ (480px) từ Native nếu Android 8.1+
                val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, MAX_SIDE, MAX_SIDE)
                } else {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                }

                if (raw == null) {
                    Log.w(TAG, "frame $i = null")
                    continue
                }

                val scaled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) raw else scaleDown(raw)
                frames.add(scaled)

                // Thu hồi bitmap gốc nếu có tạo bản copy thu nhỏ
                if (scaled !== raw) raw.recycle()
            }

            Log.d(TAG, "Xong: ${frames.size} frame (Không tách nền), mất ${SystemClock.elapsedRealtime() - startedAt}ms")
            return frames
        } finally {
            retriever.release()
        }
    }

    private fun scaleDown(src: Bitmap): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= MAX_SIDE) return src
        val k = MAX_SIDE.toFloat() / longest
        return Bitmap.createScaledBitmap(src, (src.width * k).toInt().coerceAtLeast(1), (src.height * k).toInt().coerceAtLeast(1), true)
    }

    companion object {
        private const val TAG = "AiSticker"
        const val FPS = 12
        const val FRAME_DELAY_MS = 1000 / FPS
        private const val MAX_FRAMES = 60
        private const val MAX_SIDE = 480

        // Đã xóa cờ KEY_BACKGROUND vì không cần dùng nữa
    }
}