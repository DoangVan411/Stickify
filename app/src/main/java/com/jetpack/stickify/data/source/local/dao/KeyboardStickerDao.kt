package com.jetpack.stickify.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jetpack.stickify.domain.model.KeyboardStickerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyboardStickerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: KeyboardStickerEntity): Long

    @Query("SELECT * FROM keyboard_stickers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<KeyboardStickerEntity>>

    @Query("SELECT * FROM keyboard_stickers WHERE lastUsedAt > 0 ORDER BY lastUsedAt DESC LIMIT 30")
    fun observeRecent(): Flow<List<KeyboardStickerEntity>>

    @Query("SELECT fileName FROM keyboard_stickers WHERE sourceId = :sourceId")
    suspend fun fileNameBySource(sourceId: String): String?

    @Query("UPDATE keyboard_stickers SET lastUsedAt = :time WHERE id = :id")
    suspend fun markUsed(id: Long, time: Long)

    @Query("SELECT * FROM keyboard_stickers WHERE id = :id")
    suspend fun getById(id: Long): KeyboardStickerEntity?

    @Query("DELETE FROM keyboard_stickers WHERE id = :id")
    suspend fun delete(id: Long)
}
