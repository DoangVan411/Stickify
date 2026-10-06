package com.jetpack.stickify.domain.usecase

import com.jetpack.stickify.domain.model.StickerPack
import com.jetpack.stickify.domain.repository.ProjectRepository
import javax.inject.Inject

class CreateStickerPackUseCase @Inject constructor(
    private val repository: ProjectRepository
) {
    suspend operator fun invoke(pack: StickerPack): Result<Unit> {
        return repository.createStickerPack(pack)
    }
}
