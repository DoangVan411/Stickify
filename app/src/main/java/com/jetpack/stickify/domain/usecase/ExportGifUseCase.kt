package com.jetpack.stickify.domain.usecase

import android.graphics.Bitmap
import com.jetpack.stickify.domain.model.StickerAnimationType
import com.jetpack.stickify.domain.repository.StickerRepository
import javax.inject.Inject

/**
 * UseCase xuất sticker dưới dạng WebP có animation.
 */
class ExportGifUseCase @Inject constructor(
    private val repository: StickerRepository
) {
    suspend operator fun invoke(
        bitmap: Bitmap,
        animationType: StickerAnimationType
    ): String {
        return repository.saveAnimatedSticker(bitmap, animationType)
    }
}
