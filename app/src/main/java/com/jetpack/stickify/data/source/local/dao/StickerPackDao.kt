package com.jetpack.stickify.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jetpack.stickify.domain.model.StickerPackEntity


@Dao
interface StickerPackDao {
    @Query("SELECT * FROM sticker_packs ORDER BY updatedAt DESC")
    suspend fun getAllPacks(): List<StickerPackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPack(pack: StickerPackEntity)

    @Query("SELECT * FROM sticker_packs WHERE id = :id LIMIT 1")
    suspend fun getPackById(id: String): StickerPackEntity?

    @Query("DELETE FROM sticker_packs WHERE id = :id")
    suspend fun deletePackById(id: String)
}
