package com.jetpack.stickify.presentation.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetpack.stickify.domain.repository.ProjectRepository
import com.jetpack.stickify.domain.usecase.DeleteStickerPackUseCase
import com.jetpack.stickify.domain.usecase.ExportToWhatsAppUseCase
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
    private val getStickerPacksUseCase: GetStickerPacksUseCase,
    private val exportToWhatsAppUseCase: ExportToWhatsAppUseCase,
    private val deleteStickerPackUseCase: DeleteStickerPackUseCase,
    private val projectRepository: ProjectRepository
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

    fun exportToWhatsApp(packId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = exportToWhatsAppUseCase(packId)
            result.onSuccess {
                onResult(true, null)
            }.onFailure { e ->
                onResult(false, e.message ?: "Không thể xuất sang WhatsApp. Hãy đảm bảo WhatsApp đã được cài đặt trên thiết bị.")
            }
        }
    }

    fun deletePack(packId: String) {
        viewModelScope.launch {
            val result = deleteStickerPackUseCase(packId)
            result.onSuccess {
                loadCollectionData()
            }
        }
    }

    fun updateProjectName(projectId: String, newName: String) {
        viewModelScope.launch {
            val result = projectRepository.updateProjectName(projectId, newName)
            result.onSuccess {
                loadCollectionData()
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            val result = projectRepository.deleteProject(projectId)
            result.onSuccess {
                loadCollectionData()
            }
        }
    }

    fun addStickerToPack(packId: String, stickerId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = projectRepository.addStickerToPack(packId, stickerId)
            result.onSuccess {
                loadCollectionData()
                onResult(true)
            }.onFailure {
                onResult(false)
            }
        }
    }
}
