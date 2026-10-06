package com.jetpack.stickify.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jetpack.stickify.domain.model.AssetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets WHERE category = :category ORDER BY createdAt DESC")
    suspend fun getAssetsByCategory(category: String): List<AssetEntity>

    @Query("SELECT * FROM assets WHERE category = :category ORDER BY createdAt DESC")
    fun getAssetsByCategoryFlow(category: String): Flow<List<AssetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: AssetEntity)

    @Query("SELECT COUNT(*) FROM assets")
    suspend fun getAssetCount(): Int
}
