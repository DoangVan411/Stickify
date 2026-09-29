package com.jetpack.stickify.domain.usecase

import android.graphics.Bitmap
import com.jetpack.stickify.domain.model.StickerAnimationType
import com.jetpack.stickify.domain.repository.StickerRepository
import javax.inject.Inject

/**
 * UseCase xuất sticker dưới dạng GIF có animation.
 * Nhận bitmap gốc và loại hiệu ứng, trả về URI string của file GIF đã lưu.
 */
class ExportGifUseCase @Inject constructor(
    private val repository: StickerRepository
) {
    suspend operator fun invoke(
        bitmap: Bitmap,
        animationType: StickerAnimationType
    ): String {
        return repository.saveAnimatedGif(bitmap, animationType)
    }
}

