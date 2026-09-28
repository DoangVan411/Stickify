package com.jetpack.stickify.domain.model

/**
 * Sticker project đang hoạt động trong editor.
 * Map từ class diagram StickerProject.
 */
data class StickerProject(
    val id: String,
    val name: String,
    val type: ProjectType,
    val origin: ProjectOrigin,
    val templateId: String? = null,
    val thumbnailPath: String,
    val exportedPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val content: ProjectContent,
    val history: EditHistory = EditHistory()
)
