package com.jetpack.stickify.domain.model


import android.graphics.Bitmap

/** Sticker động do AI tạo: các frame đã giải mã sẵn từ MP4 (nền đã được tách nếu bật keyer). */
class AiAnimation(
    val frames: List<Bitmap>,
    val frameDelayMs: Int
)

/** Các bước của quá trình tạo sticker AI, domain không biết gì về Socket.IO hay MediaMetadataRetriever. */
sealed interface AiStickerStep {
    data class Progress(val percent: Int, val message: String) : AiStickerStep
    data class Done(val animation: AiAnimation) : AiStickerStep
    data class Failed(val message: String) : AiStickerStep
}