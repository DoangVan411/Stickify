package com.jetpack.stickify.domain.model

/**
 * Model cho bộ sticker (sticker pack) trong Bộ sưu tập.
 */
data class StickerPack(
    val id: String,
    val name: String,
    val itemCount: Int,
    val type: ProjectType = ProjectType.STICKER,
    val previewIcons: List<Int> = emptyList()
)
