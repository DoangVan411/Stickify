package com.jetpack.stickify.domain.repository

import android.graphics.Bitmap
import com.jetpack.stickify.domain.model.ExploreCategory
import com.jetpack.stickify.domain.model.ProjectEntity
import com.jetpack.stickify.domain.model.StickerProject
import com.jetpack.stickify.domain.model.StickerTemplate
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface cho project operations theo Clean Architecture.
 */
interface ProjectRepository {
    fun getAllProjects(): Flow<List<StickerProject>>
    fun getProjectById(id: String): Flow<StickerProject?>
    suspend fun saveProject(project: StickerProject): Result<Unit>
    suspend fun insertProject(project: ProjectEntity)
    suspend fun updateProject(project: ProjectEntity)
    suspend fun updateProjectThumbnail(projectId: String, bitmap: Bitmap): Result<String>
    suspend fun getRecentProjects(): Result<List<ProjectEntity>>
    fun getRecentProjectsFlow(): Flow<List<ProjectEntity>>
    suspend fun getExploreTemplates(category: ExploreCategory): Result<List<StickerTemplate>>
}
