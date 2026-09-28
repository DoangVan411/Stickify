package com.jetpack.stickify.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.jetpack.stickify.data.mapper.ProjectMapper
import com.jetpack.stickify.data.source.local.DatabaseSeeder
import com.jetpack.stickify.data.source.local.FakeProjectDataSource
import com.jetpack.stickify.data.source.local.dao.ProjectDao
import com.jetpack.stickify.domain.model.ExploreCategory
import com.jetpack.stickify.domain.model.ProjectEntity
import com.jetpack.stickify.domain.model.StickerProject
import com.jetpack.stickify.domain.model.StickerTemplate
import com.jetpack.stickify.domain.repository.ProjectRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation của ProjectRepository kết hợp Room Database, Storage, Seeder và Fake DataSource cho Templates.
 */
@Singleton
class ProjectRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectDao: ProjectDao,
    private val mapper: ProjectMapper,
    private val fakeDataSource: FakeProjectDataSource,
    private val databaseSeeder: DatabaseSeeder
) : ProjectRepository {

    override fun getAllProjects(): Flow<List<StickerProject>> {
        return projectDao.getAllProjects().map { entities ->
            entities.map { mapper.toDomain(it) }
        }
    }

    override fun getProjectById(id: String): Flow<StickerProject?> {
        return projectDao.getProjectById(id).map { entity ->
            entity?.let { mapper.toDomain(it) }
        }
    }

    override suspend fun saveProject(project: StickerProject): Result<Unit> {
        return try {
            val entity = mapper.toEntity(project)
            projectDao.insertProject(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun insertProject(project: ProjectEntity) {
        projectDao.insertProject(project)
    }

    override suspend fun updateProject(project: ProjectEntity) {
        projectDao.updateProject(project)
    }

    override suspend fun updateProjectThumbnail(projectId: String, bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val thumbnailsDir = File(context.filesDir, "thumbnails")
            if (!thumbnailsDir.exists()) {
                thumbnailsDir.mkdirs()
            }
            val thumbFile = File(thumbnailsDir, "thumb_$projectId.png")
            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            val newPath = thumbFile.absolutePath
            val now = System.currentTimeMillis()
            projectDao.updateThumbnailAndTimestamp(projectId, newPath, now)
            Result.success(newPath)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun getRecentProjects(): Result<List<ProjectEntity>> {
        return try {
            // Tự động seed dữ liệu mẫu khi chạy lần đầu nếu database trống
            databaseSeeder.seedDatabaseIfEmpty()
            val dbProjects = projectDao.getAllProjects().firstOrNull() ?: emptyList()
            Result.success(dbProjects)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getRecentProjectsFlow(): Flow<List<ProjectEntity>> {
        return projectDao.getAllProjects()
    }

    override suspend fun getExploreTemplates(category: ExploreCategory): Result<List<StickerTemplate>> {
        return try {
            Result.success(fakeDataSource.getExploreTemplatesByCategory(category))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
