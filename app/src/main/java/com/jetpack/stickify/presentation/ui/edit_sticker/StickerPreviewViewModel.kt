package com.jetpack.stickify.presentation.ui.edit_sticker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetpack.stickify.domain.repository.ProjectRepository
import com.jetpack.stickify.domain.usecase.AddStickerToKeyboardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StickerPreviewViewModel @Inject constructor(
    private val addStickerToKeyboardUseCase: AddStickerToKeyboardUseCase,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    fun addToKeyboard(uriString: String, isAnimated: Boolean, sourceId: String?, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = addStickerToKeyboardUseCase(uriString, isAnimated, sourceId)
            onResult(result.isSuccess)
        }
    }

    fun toggleBookmark(projectId: String, isBookmarked: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = projectRepository.toggleBookmark(projectId, isBookmarked)
            onResult(result.isSuccess)
        }
    }

    fun addStickerToPack(packId: String, stickerId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = projectRepository.addStickerToPack(packId, stickerId)
            onResult(result.isSuccess)
        }
    }
}
