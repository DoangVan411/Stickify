package com.jetpack.stickify.domain.usecase

import com.jetpack.stickify.domain.model.ProcessedStickers
import com.jetpack.stickify.domain.repository.StickerRepository
import javax.inject.Inject

class ProcessStickerUseCase @Inject constructor(private val repository: StickerRepository) {
    suspend operator fun invoke(uriString: String): ProcessedStickers {
        return repository.processStickers(uriString)
    }
}