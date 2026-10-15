package com.jetpack.stickify.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jetpack.stickify.data.source.local.converter.GsonProvider

/**
 * Entity đại diện cho Template trong mục Khám phá (Room DB).
 */
@Entity(tableName = "sticker_templates")
data class StickerTemplateEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: ProjectType,
    val tags: List<String>,
    val thumbnailUrl: String,
    val contentJson: String = "",
    val isBookmarked: Boolean = false,
    val isAnimated: Boolean = false,
    val isPremium: Boolean = false
) {
    fun toDomain(): StickerTemplate {
        val parsedContent = if (contentJson.isNotBlank()) {
            try {
                GsonProvider.gson.fromJson(contentJson, ProjectContent::class.java)
            } catch (e: Exception) {
                null
            }
        } else null

        return StickerTemplate(
            id = id,
            title = title,
            type = type,
            tags = tags,
            thumbnailUrl = thumbnailUrl,
            isBookmarked = isBookmarked,
            content = parsedContent,
            isAnimated = isAnimated,
            isPremium = isPremium
        )
    }
}
