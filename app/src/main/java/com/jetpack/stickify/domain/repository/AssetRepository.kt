package com.jetpack.stickify.domain.repository

import com.jetpack.stickify.domain.model.AssetEntity
import kotlinx.coroutines.flow.Flow

interface AssetRepository {
    suspend fun getAssetsByCategory(category: String): Result<List<AssetEntity>>
    fun getAssetsByCategoryFlow(category: String): Flow<List<AssetEntity>>
    suspend fun saveAsset(asset: AssetEntity): Result<Unit>
    suspend fun seedDefaultAssetsIfNeeded()
}
