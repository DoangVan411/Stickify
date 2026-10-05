package com.jetpack.stickify.domain.model

data class StickerPack(
    val id: String,
    val name: String,
    val author: String = "Stickify",
    val trayImagePath: String = "", // Ảnh đại diện khay sticker (Yêu cầu bắt buộc của WhatsApp)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val stickers: List<StickerProject> = emptyList() // Quan hệ 0..* như trong UML
) {
    val itemCount: Int
        get() = stickers.size

    val type: ProjectType
        get() = stickers.firstOrNull()?.type ?: ProjectType.STICKER
}
