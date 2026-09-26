package com.jetpack.stickify.domain.usecase
import android.graphics.Bitmap
import com.jetpack.stickify.domain.repository.StickerRepository
import javax.inject.Inject

class ApplyBorderUseCase @Inject constructor(
    private val repository: StickerRepository
) {
    suspend operator fun invoke(
        original: Bitmap,
        thickness: Int,
        distance: Int,
        color: Int
    ): Bitmap {
        return repository.applyCustomBorder(original, thickness, distance, color)
    }
}