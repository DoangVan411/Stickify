package com.jetpack.stickify.domain.usecase

import com.jetpack.stickify.domain.model.ExploreCategory
import com.jetpack.stickify.domain.model.StickerTemplate
import com.jetpack.stickify.domain.repository.ProjectRepository
import javax.inject.Inject

/**
 * UseCase lấy danh sách template theo category cho mục Khám phá.
 */
class GetExploreTemplatesUseCase @Inject constructor(
    private val repository: ProjectRepository
) {
    suspend operator fun invoke(category: ExploreCategory): Result<List<StickerTemplate>> {
        return repository.getExploreTemplates(category)
    }
}
