package com.jetpack.stickify.domain.usecase

import com.jetpack.stickify.domain.model.AssetEntity
import com.jetpack.stickify.domain.repository.AssetRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAssetsUseCase @Inject constructor(
    private val repository: AssetRepository
) {
    suspend fun get(category: String): Result<List<AssetEntity>> {
        repository.seedDefaultAssetsIfNeeded()
        return repository.getAssetsByCategory(category)
    }

    fun getFlow(category: String): Flow<List<AssetEntity>> {
        return repository.getAssetsByCategoryFlow(category)
    }
}
