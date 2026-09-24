package com.jetpack.stickify.domain.model

/**
 * Entity đại diện cho một project đã lưu.
 * Map từ class diagram «persistence» ProjectEntity.
 */
data class ProjectEntity(
    val id: String,
    val name: String,
    val type: ProjectType,
    val source: ProjectOrigin,
    val thumbnailPath: String,
    val exportedPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val contentJson: String? = null,
    val historyJson: String? = null
)
