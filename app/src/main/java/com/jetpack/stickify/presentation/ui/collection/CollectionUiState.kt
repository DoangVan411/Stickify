package com.jetpack.stickify.presentation.ui.collection

import com.jetpack.stickify.domain.model.FavoriteSticker
import com.jetpack.stickify.domain.model.StickerPack

/**
 * UI State cho màn hình Bộ sưu tập (Collection).
 */
data class CollectionUiState(
    val isLoading: Boolean = false,
    val favorites: List<FavoriteSticker> = emptyList(),
    val stickerPacks: List<StickerPack> = emptyList(),
    val errorMessage: String? = null
)
