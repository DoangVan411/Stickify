package com.jetpack.stickify.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jetpack.stickify.domain.model.StickerTemplateEntity

@Dao
interface TemplateDao {

    @Query("SELECT * FROM sticker_templates")
    suspend fun getAllTemplates(): List<StickerTemplateEntity>

    @Query("SELECT * FROM sticker_templates WHERE id = :id")
    suspend fun getTemplateById(id: String): StickerTemplateEntity?

    @Query("SELECT COUNT(*) FROM sticker_templates")
    suspend fun getTemplateCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(templates: List<StickerTemplateEntity>)

    @Query("UPDATE sticker_templates SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun updateBookmarkStatus(id: String, isBookmarked: Boolean)

    @Query("DELETE FROM sticker_templates")
    suspend fun clearAllTemplates()
}
