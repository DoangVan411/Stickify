package com.jetpack.stickify.domain.model

data class StickerProject(
    val id: String,
    val name: String,
    val type: ProjectType,
    val origin: ProjectOrigin,
    val templateId: String? = null,
    val thumbnailPath: String? = null,
    val exportedPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val content: ProjectContent = ProjectContent(),
    val history: EditHistory = EditHistory()
)
