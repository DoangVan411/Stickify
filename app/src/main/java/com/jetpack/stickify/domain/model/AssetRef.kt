package com.jetpack.stickify.domain.model

sealed interface AssetRef {
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
}
