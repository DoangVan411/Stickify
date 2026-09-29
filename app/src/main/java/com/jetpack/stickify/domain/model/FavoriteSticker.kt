package com.jetpack.stickify.domain.model

/**
 * Model cho sticker yêu thích trong Bộ sưu tập.
 */
data class FavoriteSticker(
    val id: String,
    val name: String,
    val thumbnailPath: String,
    val type: ProjectType = ProjectType.STICKER
)
