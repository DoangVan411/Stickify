package com.jetpack.stickify.data.repository

import com.jetpack.stickify.data.source.local.FakeProjectDataSource
import com.jetpack.stickify.domain.model.ExploreCategory
import com.jetpack.stickify.domain.model.FavoriteSticker
import com.jetpack.stickify.domain.model.ProjectEntity
import com.jetpack.stickify.domain.model.StickerPack
import com.jetpack.stickify.domain.model.StickerTemplate
import com.jetpack.stickify.domain.repository.ProjectRepository
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation của ProjectRepository sử dụng fake data.
 * Giả lập delay để mô phỏng network/database call.
 */
@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val fakeDataSource: FakeProjectDataSource
) : ProjectRepository {

    override suspend fun getRecentProjects(): Result<List<ProjectEntity>> {
        return try {
            delay(300) // Giả lập loading
            Result.success(fakeDataSource.getRecentProjects())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getExploreTemplates(category: ExploreCategory): Result<List<StickerTemplate>> {
        return try {
            delay(200) // Giả lập loading
            Result.success(fakeDataSource.getExploreTemplatesByCategory(category))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getFavoriteStickers(): Result<List<FavoriteSticker>> {
        return try {
            delay(200)
            Result.success(fakeDataSource.getFavoriteStickers())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getStickerPacks(): Result<List<StickerPack>> {
        return try {
            delay(200)
            Result.success(fakeDataSource.getStickerPacks())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
