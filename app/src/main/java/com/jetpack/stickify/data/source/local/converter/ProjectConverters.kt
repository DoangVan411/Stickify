package com.jetpack.stickify.data.source.local.converter

import androidx.room.TypeConverter
import com.jetpack.stickify.domain.model.EditHistory
import com.jetpack.stickify.domain.model.ProjectContent
import com.jetpack.stickify.domain.model.ProjectOrigin
import com.jetpack.stickify.domain.model.ProjectType

/**
 * TypeConverter cho Room Database.
 * Sử dụng GsonProvider để serialize/deserialize các đối tượng phức tạp và đa hình.
 */
class ProjectConverters {

    @TypeConverter
    fun fromProjectType(type: ProjectType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toProjectType(value: String?): ProjectType? {
        return value?.let {
            try {
                ProjectType.valueOf(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    @TypeConverter
    fun fromProjectOrigin(origin: ProjectOrigin?): String? {
        return origin?.name
    }

    @TypeConverter
    fun toProjectOrigin(value: String?): ProjectOrigin? {
        return value?.let {
            try {
                ProjectOrigin.valueOf(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    @TypeConverter
    fun fromProjectContent(content: ProjectContent?): String? {
        if (content == null) return null
        return GsonProvider.gson.toJson(content)
    }

    @TypeConverter
    fun toProjectContent(json: String?): ProjectContent? {
        if (json.isNullOrBlank()) return null
        return try {
            GsonProvider.gson.fromJson(json, ProjectContent::class.java)
        } catch (e: Exception) {
            null
        }
    }

    @TypeConverter
    fun fromEditHistory(history: EditHistory?): String? {
        if (history == null) return null
        return GsonProvider.gson.toJson(history)
    }

    @TypeConverter
    fun toEditHistory(json: String?): EditHistory? {
        if (json.isNullOrBlank()) return null
        return try {
            GsonProvider.gson.fromJson(json, EditHistory::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
