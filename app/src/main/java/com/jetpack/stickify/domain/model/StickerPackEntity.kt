package com.jetpack.stickify.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sticker_packs")
data class StickerPackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val author: String,
    val trayImagePath: String,
    val createdAt: Long,
    val updatedAt: Long,
    val stickerIdsJson: String // Lưu danh sách ID dưới dạng chuỗi JSON
)