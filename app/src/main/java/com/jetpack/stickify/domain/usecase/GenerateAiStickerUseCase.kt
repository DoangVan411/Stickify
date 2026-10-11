package com.jetpack.stickify.domain.usecase

import android.graphics.Bitmap
import com.jetpack.stickify.domain.model.AiStickerStep
import com.jetpack.stickify.domain.repository.AiStickerRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GenerateAiStickerUseCase @Inject constructor(
    private val repository: AiStickerRepository
) {
    operator fun invoke(source: Bitmap, prompt: String): Flow<AiStickerStep> =
        repository.generate(source, prompt.trim())
}

class ExportAiAnimationUseCase @Inject constructor(
    private val repository: AiStickerRepository
) {
    suspend operator fun invoke(frames: List<Bitmap>, frameDelayMs: Int): String =
        repository.exportAnimated(frames, frameDelayMs)
}