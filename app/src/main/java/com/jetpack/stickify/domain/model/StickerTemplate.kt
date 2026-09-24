package com.jetpack.stickify.domain.model

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
    val isAnimated: Boolean = false,
    val isPremium: Boolean = false
)
