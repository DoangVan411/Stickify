package com.jetpack.stickify.presentation.ui.asset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetpack.stickify.domain.model.AssetEntity
import com.jetpack.stickify.domain.repository.AssetRepository
import com.jetpack.stickify.domain.usecase.GetAssetsUseCase
import com.jetpack.stickify.domain.usecase.SaveAssetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AssetUiState(
    val backgrounds: List<AssetEntity> = emptyList(),
    val decorations: List<AssetEntity> = emptyList(),
    val labels: List<AssetEntity> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class AssetViewModel @Inject constructor(
    private val getAssetsUseCase: GetAssetsUseCase,
    private val saveAssetUseCase: SaveAssetUseCase,
    private val assetRepository: AssetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetUiState())
    val uiState: StateFlow<AssetUiState> = _uiState.asStateFlow()

    init {
        loadAssets()
    }

    fun loadAssets() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val bgResult = getAssetsUseCase.get("BACKGROUND")
            val decResult = getAssetsUseCase.get("DECORATION")
            val lblResult = getAssetsUseCase.get("LABEL")

            _uiState.update {
                it.copy(
                    isLoading = false,
                    backgrounds = bgResult.getOrDefault(emptyList()),
                    decorations = decResult.getOrDefault(emptyList()),
                    labels = lblResult.getOrDefault(emptyList())
                )
            }
        }
    }

    fun addCustomAsset(category: String, title: String, path: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val asset = AssetEntity(
                id = UUID.randomUUID().toString(),
                category = category,
                title = title,
                path = path,
                isCustom = true,
                createdAt = System.currentTimeMillis()
            )
            val result = saveAssetUseCase(asset)
            if (result.isSuccess) {
                loadAssets()
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }

    fun deleteAsset(assetId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = assetRepository.deleteAsset(assetId)
            if (result.isSuccess) {
                loadAssets()
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }
}
