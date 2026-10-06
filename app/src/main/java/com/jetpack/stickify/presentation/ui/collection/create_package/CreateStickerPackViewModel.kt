package com.jetpack.stickify.presentation.ui.collection.create_package

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetpack.stickify.domain.model.StickerPack
import com.jetpack.stickify.domain.model.StickerProject
import com.jetpack.stickify.domain.repository.ProjectRepository
import com.jetpack.stickify.domain.usecase.CreateStickerPackUseCase
import com.jetpack.stickify.domain.usecase.GetFavoritesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CreateStickerPackUiState(
    val availableStickers: List<StickerProject> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class CreateStickerPackViewModel @Inject constructor(
    private val createStickerPackUseCase: CreateStickerPackUseCase,
    private val getFavoritesUseCase: GetFavoritesUseCase,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateStickerPackUiState())
    val uiState: StateFlow<CreateStickerPackUiState> = _uiState.asStateFlow()

    var editingPackId: String? = null
    private var originalCreatedAt: Long = System.currentTimeMillis()

    init {
        loadAvailableStickers()
    }

    fun loadAvailableStickers(onLoaded: (List<StickerProject>) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = getFavoritesUseCase()
            result.onSuccess { stickers ->
                _uiState.update { it.copy(availableStickers = stickers, isLoading = false) }
                onLoaded(stickers)
            }.onFailure {
                _uiState.update { it.copy(isLoading = false) }
                onLoaded(emptyList())
            }
        }
    }

    fun loadPack(packId: String, onLoaded: (StickerPack) -> Unit) {
        editingPackId = packId
        viewModelScope.launch {
            val result = projectRepository.getPackById(packId)
            result.onSuccess { pack ->
                if (pack != null) {
                    originalCreatedAt = pack.createdAt
                    onLoaded(pack)
                }
            }
        }
    }

    fun savePack(name: String, author: String, stickers: List<StickerProject>, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            if (name.isBlank()) {
                onResult(false, "Tên bộ sticker không được để trống")
                return@launch
            }
            if (stickers.isEmpty()) {
                onResult(false, "Vui lòng chọn ít nhất 1 sticker")
                return@launch
            }

            val now = System.currentTimeMillis()
            val pack = StickerPack(
                id = editingPackId ?: UUID.randomUUID().toString(),
                name = name.trim(),
                author = author.trim().ifBlank { "No_name" },
                trayImagePath = stickers.firstOrNull()?.thumbnailPath.orEmpty(),
                createdAt = if (editingPackId != null) originalCreatedAt else now,
                updatedAt = now,
                stickers = stickers
            )
            val result = createStickerPackUseCase(pack)
            result.onSuccess {
                onResult(true, null)
            }.onFailure { e ->
                onResult(false, e.message ?: "Lỗi khi lưu bộ sticker")
            }
        }
    }
}
