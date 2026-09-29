package com.jetpack.stickify.presentation.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetpack.stickify.domain.usecase.GetFavoritesUseCase
import com.jetpack.stickify.domain.usecase.GetStickerPacksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel cho màn hình Bộ sưu tập (Collection).
 */
@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val getFavoritesUseCase: GetFavoritesUseCase,
    private val getStickerPacksUseCase: GetStickerPacksUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionUiState(isLoading = true))
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()

    init {
        loadCollectionData()
    }

    fun loadCollectionData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val favoritesResult = getFavoritesUseCase()
            val packsResult = getStickerPacksUseCase()

            val favorites = favoritesResult.getOrDefault(emptyList())
            val packs = packsResult.getOrDefault(emptyList())

            _uiState.update {
                it.copy(
                    isLoading = false,
                    favorites = favorites,
                    stickerPacks = packs
                )
            }
        }
    }
}
