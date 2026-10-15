package com.jetpack.stickify.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity đại diện cho một project đã lưu (persistence).
 * Map từ class diagram «persistence» ProjectEntity.
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: ProjectType,
    val origin: ProjectOrigin,
    val templateId: String? = null,
    val thumbnailPath: String,
    val exportedPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isBookmarked: Boolean = false,
    val contentJson: String? = null,
    val historyJson: String? = null
)
