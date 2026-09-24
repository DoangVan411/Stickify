package com.jetpack.stickify.domain.repository

import com.jetpack.stickify.domain.model.ExploreCategory
import com.jetpack.stickify.domain.model.ProjectEntity
import com.jetpack.stickify.domain.model.StickerTemplate

/**
 * Repository interface cho project operations.
 * Domain layer chỉ biết interface này, không biết implementation.
 */
interface ProjectRepository {
    suspend fun getRecentProjects(): Result<List<ProjectEntity>>
    suspend fun getExploreTemplates(category: ExploreCategory): Result<List<StickerTemplate>>
}
