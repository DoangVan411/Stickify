package com.jetpack.stickify.domain.model

/**
 * Tham chiếu tài nguyên (mẫu có sẵn, tùy chỉnh, từ internet).
 * Map từ class diagram I AssetRef.
 */
sealed interface AssetRef

data class BuiltinAsset(
    val assetId: String,
    val packId: String
) : AssetRef

data class CustomAsset(
    val relativePath: String
) : AssetRef

data class RemoteAsset(
    val url: String
) : AssetRef
