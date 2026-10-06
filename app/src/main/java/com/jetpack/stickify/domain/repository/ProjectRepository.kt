package com.jetpack.stickify.domain.repository

import android.graphics.Bitmap
import com.jetpack.stickify.domain.model.ExploreCategory
import com.jetpack.stickify.domain.model.FavoriteSticker
import com.jetpack.stickify.domain.model.ProjectEntity
import com.jetpack.stickify.domain.model.StickerPack
import com.jetpack.stickify.domain.model.StickerProject
import com.jetpack.stickify.domain.model.StickerTemplate
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface cho project operations.
 * Domain layer chỉ biết interface này, không biết implementation.
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

    // Favorites
    suspend fun getFavoriteStickers(): Result<List<StickerProject>>
    suspend fun toggleBookmark(projectId: String, isBookmarked: Boolean): Result<Unit>
    suspend fun deleteProject(projectId: String): Result<Unit>
    suspend fun updateProjectName(projectId: String, newName: String): Result<Unit>

    // Sticker Packs
    suspend fun getStickerPacks(): Result<List<StickerPack>>
    suspend fun getPackById(packId: String): Result<StickerPack?>
    suspend fun createStickerPack(pack: StickerPack): Result<Unit>
    suspend fun deleteStickerPack(packId: String): Result<Unit>
    suspend fun addStickerToPack(packId: String, stickerId: String): Result<Unit>

    // WhatsApp Export
    suspend fun exportToWhatsApp(packId: String): Result<Unit>
}
