package com.jetpack.stickify.domain.repository

import com.jetpack.stickify.domain.model.KeyboardStickerEntity
import kotlinx.coroutines.flow.Flow

interface KeyboardStickerRepository {
    fun observeAll(): Flow<List<KeyboardStickerEntity>>
    fun observeRecent(): Flow<List<KeyboardStickerEntity>>
    suspend fun addFromUri(uriString: String, animated: Boolean, sourceId: String?): Result<Long>
    suspend fun markUsed(id: Long)
    suspend fun delete(id: Long)
}
