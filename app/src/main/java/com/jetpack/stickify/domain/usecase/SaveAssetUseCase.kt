package com.jetpack.stickify.domain.usecase

import com.jetpack.stickify.domain.model.AssetEntity
import com.jetpack.stickify.domain.repository.AssetRepository
import javax.inject.Inject

class SaveAssetUseCase @Inject constructor(
    private val repository: AssetRepository
) {
    suspend operator fun invoke(asset: AssetEntity): Result<Unit> {
        return repository.saveAsset(asset)
    }
}
