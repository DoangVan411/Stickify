package com.jetpack.stickify.domain.usecase

import android.graphics.Bitmap
import com.jetpack.stickify.domain.repository.StickerRepository
import jakarta.inject.Inject

class SaveStickerUseCase @Inject constructor(private val repository: StickerRepository) {
    suspend operator fun invoke(bitmap: Bitmap): String {
        return repository.saveSticker(bitmap)
    }
}