package com.jetpack.stickify.domain.model

import java.util.UUID

/**
 * Template cho sticker/GIF trong mục Khám phá.
 * Map từ class diagram StickerTemplate.
 */
data class StickerTemplate(
    val id: String,
    val title: String,
    val type: ProjectType,
    val tags: List<String> = emptyList(),
    val thumbnailUrl: String,
    val isBookmarked: Boolean = false,
    val content: ProjectContent? = null,
    val isAnimated: Boolean = false,
    val isPremium: Boolean = false
) {
    fun instantiate(
        userSubject: SubjectLayer,
        now: Long = System.currentTimeMillis()
    ): StickerProject {
        val initialContent = content ?: ProjectContent(
            layers = listOf(userSubject)
        )
        return StickerProject(
            id = UUID.randomUUID().toString(),
            name = title,
            type = type,
            origin = ProjectOrigin.TEMPLATE,
            templateId = id,
            thumbnailPath = thumbnailUrl,
            createdAt = now,
            updatedAt = now,
            content = initialContent,
            history = EditHistory()
        )
    }
}
