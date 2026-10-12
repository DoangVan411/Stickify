package com.jetpack.stickify.domain.usecase

import com.jetpack.stickify.domain.model.StickerTemplate
import com.jetpack.stickify.domain.repository.ProjectRepository
import javax.inject.Inject

class GetTemplateByIdUseCase @Inject constructor(
    private val repository: ProjectRepository
) {
    suspend operator fun invoke(id: String): Result<StickerTemplate?> {
        return repository.getTemplateById(id)
    }
}
