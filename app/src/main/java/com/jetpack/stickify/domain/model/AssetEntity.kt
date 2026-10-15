package com.jetpack.stickify.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey val id: String,
    val category: String, // "BACKGROUND", "DECORATION", "LABEL"
    val title: String,
    val path: String, // file path, asset path, or drawable name
    val isCustom: Boolean = false,
    val createdAt: Long
)
