package com.jetpack.stickify.data.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jetpack.stickify.data.mapper.ProjectMapper
import com.jetpack.stickify.data.source.local.DatabaseSeeder
import com.jetpack.stickify.data.source.local.FakeProjectDataSource
import com.jetpack.stickify.data.source.local.dao.ProjectDao
import com.jetpack.stickify.data.source.local.dao.StickerPackDao
import com.jetpack.stickify.domain.model.ExploreCategory
import com.jetpack.stickify.domain.model.ProjectEntity
import com.jetpack.stickify.domain.model.StickerPack
import com.jetpack.stickify.domain.model.StickerPackEntity
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
    private val stickerPackDao: StickerPackDao,
    private val fakeDataSource: FakeProjectDataSource,
    private val databaseSeeder: DatabaseSeeder
) : ProjectRepository {
    private val gson = Gson()

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

    override suspend fun getFavoriteStickers(): Result<List<StickerProject>> = withContext(Dispatchers.IO) {
        try {
            val entities = projectDao.getFavoriteProjects()
            val domainModels = entities.map { mapper.toDomain(it) }
            Result.success(domainModels)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun toggleBookmark(projectId: String, isBookmarked: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            projectDao.updateBookmarkStatus(projectId, isBookmarked)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteProject(projectId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            projectDao.deleteProjectById(projectId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateProjectName(projectId: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            projectDao.updateProjectName(projectId, newName, now)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getStickerPacks(): Result<List<StickerPack>> = withContext(Dispatchers.IO) {
        try {
            val packEntities = stickerPackDao.getAllPacks()
            val resultPacks = packEntities.map { entity ->
                // Parse IDs
                val listType = object : TypeToken<List<String>>() {}.type
                val stickerIds: List<String> = gson.fromJson(entity.stickerIdsJson, listType) ?: emptyList()

                // Fetch projects in pack
                val projectEntities = if (stickerIds.isNotEmpty()) {
                    projectDao.getProjectsByIds(stickerIds)
                } else {
                    emptyList()
                }
                val entityMap = projectEntities.associateBy { it.id }
                val domainProjects = stickerIds.mapNotNull { id ->
                    entityMap[id]?.let { mapper.toDomain(it) }
                }

                StickerPack(
                    id = entity.id,
                    name = entity.name,
                    author = entity.author,
                    trayImagePath = entity.trayImagePath,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    stickers = domainProjects
                )
            }
            Result.success(resultPacks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPackById(packId: String): Result<StickerPack?> = withContext(Dispatchers.IO) {
        try {
            val entity = stickerPackDao.getPackById(packId) ?: return@withContext Result.success(null)
            val listType = object : TypeToken<List<String>>() {}.type
            val stickerIds: List<String> = gson.fromJson(entity.stickerIdsJson, listType) ?: emptyList()

            val projectEntities = if (stickerIds.isNotEmpty()) {
                projectDao.getProjectsByIds(stickerIds)
            } else {
                emptyList()
            }
            val entityMap = projectEntities.associateBy { it.id }
            val domainProjects = stickerIds.mapNotNull { id ->
                entityMap[id]?.let { mapper.toDomain(it) }
            }

            val pack = StickerPack(
                id = entity.id,
                name = entity.name,
                author = entity.author,
                trayImagePath = entity.trayImagePath,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                stickers = domainProjects
            )
            Result.success(pack)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createStickerPack(pack: StickerPack): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val stickerIds = pack.stickers.map { it.id }
            val stickerIdsJson = gson.toJson(stickerIds)
            val entity = StickerPackEntity(
                id = pack.id,
                name = pack.name,
                author = pack.author,
                trayImagePath = pack.trayImagePath,
                createdAt = pack.createdAt,
                updatedAt = pack.updatedAt,
                stickerIdsJson = stickerIdsJson
            )
            stickerPackDao.insertPack(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteStickerPack(packId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            stickerPackDao.deletePackById(packId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addStickerToPack(packId: String, stickerId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = stickerPackDao.getPackById(packId) ?: return@withContext Result.failure(Exception("Pack not found"))
            val listType = object : TypeToken<List<String>>() {}.type
            val currentIds: MutableList<String> = gson.fromJson(entity.stickerIdsJson, listType) ?: mutableListOf()
            if (!currentIds.contains(stickerId)) {
                currentIds.add(0, stickerId)
                val newJson = gson.toJson(currentIds)
                val updatedEntity = entity.copy(stickerIdsJson = newJson, updatedAt = System.currentTimeMillis())
                stickerPackDao.insertPack(updatedEntity)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exportToWhatsApp(packId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val packEntity = stickerPackDao.getPackById(packId)
            val packName = packEntity?.name ?: "Sticker Pack"

            val intent = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
                putExtra("sticker_pack_id", packId)
                putExtra("sticker_pack_authority", "com.jetpack.stickify.stickercontentprovider")
                putExtra("sticker_pack_name", packName)
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val packageManager = context.packageManager
            val resolveInfo = packageManager.resolveActivity(intent, 0)
            if (resolveInfo == null) {
                intent.setPackage("com.whatsapp.w4b")
                val resolveInfoW4B = packageManager.resolveActivity(intent, 0)
                if (resolveInfoW4B == null) {
                    return@withContext Result.failure(Exception("WhatsApp chưa được cài đặt trên thiết bị"))
                }
            }

            withContext(Dispatchers.Main) {
                context.startActivity(intent)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
