package com.jetpack.stickify.domain.repository


import android.graphics.Bitmap
import com.jetpack.stickify.domain.model.AiStickerStep
import kotlinx.coroutines.flow.Flow

interface AiStickerRepository {
    /** Gửi ảnh + prompt lên backend, phát Progress... rồi Done/Failed. Huỷ collect = huỷ job + ngắt socket. */
    fun generate(source: Bitmap, prompt: String): Flow<AiStickerStep>

    /** Ghi các frame ra file WebP động, trả về Uri (string) để mở bằng StickerPreviewActivity. */
    suspend fun exportAnimated(frames: List<Bitmap>, frameDelayMs: Int): String
}