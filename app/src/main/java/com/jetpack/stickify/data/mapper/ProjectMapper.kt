package com.jetpack.stickify.data.mapper

import com.jetpack.stickify.data.source.local.converter.GsonProvider
import com.jetpack.stickify.domain.model.EditHistory
import com.jetpack.stickify.domain.model.ProjectContent
import com.jetpack.stickify.domain.model.ProjectEntity
import com.jetpack.stickify.domain.model.StickerProject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper chuyển đổi giữa ProjectEntity (Persistence Layer) và StickerProject (Domain Model).
 */
@Singleton
class ProjectMapper @Inject constructor() {

    fun toDomain(entity: ProjectEntity): StickerProject {
        val content = if (!entity.contentJson.isNullOrBlank()) {
            try {
                GsonProvider.gson.fromJson(entity.contentJson, ProjectContent::class.java) ?: ProjectContent()
            } catch (e: Exception) {
                ProjectContent()
            }
        } else {
            ProjectContent()
        }

        val history = if (!entity.historyJson.isNullOrBlank()) {
            try {
                GsonProvider.gson.fromJson(entity.historyJson, EditHistory::class.java) ?: EditHistory()
            } catch (e: Exception) {
                EditHistory()
            }
        } else {
            EditHistory()
        }

        return StickerProject(
            id = entity.id,
            name = entity.name,
            type = entity.type,
            origin = entity.origin,
            templateId = entity.templateId,
            thumbnailPath = entity.thumbnailPath,
            exportedPath = entity.exportedPath,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            content = content,
            history = history,
            isBookmarked = entity.isBookmarked
        )
    }

    fun toEntity(domain: StickerProject): ProjectEntity {
        val contentJson = GsonProvider.gson.toJson(domain.content)
        val historyJson = GsonProvider.gson.toJson(domain.history)

        return ProjectEntity(
            id = domain.id,
            name = domain.name,
            type = domain.type,
            origin = domain.origin,
            templateId = domain.templateId,
            thumbnailPath = domain.thumbnailPath ?: "",
            exportedPath = domain.exportedPath,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            contentJson = contentJson,
            historyJson = historyJson,
            isBookmarked = domain.isBookmarked
        )
    }
}
