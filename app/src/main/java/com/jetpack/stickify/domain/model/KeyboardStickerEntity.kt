package com.jetpack.stickify.domain.model
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "keyboard_stickers",
    indices = [Index(value = ["sourceId"], unique = true)]
)
data class KeyboardStickerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: String?,        // projectId, để sửa lại project thì ghi đè chứ không nhân đôi
    val fileName: String,
    val mimeType: String,         // image/png | image/gif
    val isAnimated: Boolean,
    val createdAt: Long,
    val lastUsedAt: Long = 0
)