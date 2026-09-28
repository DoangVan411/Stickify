package com.jetpack.stickify.presentation.ui.edit_sticker

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetpack.stickify.data.source.local.AssetLoader
import com.jetpack.stickify.domain.model.EditAction
import com.jetpack.stickify.domain.model.EditorSession
import com.jetpack.stickify.domain.model.ProjectContent
import com.jetpack.stickify.domain.model.ProjectOrigin
import com.jetpack.stickify.domain.model.ProjectType
import com.jetpack.stickify.domain.usecase.GetProjectByIdUseCase
import com.jetpack.stickify.domain.usecase.SaveProjectUseCase
import com.jetpack.stickify.domain.usecase.UpdateProjectThumbnailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * UI State cho màn hình StickerEdit.
 */
data class StickerEditUiState(
    val isLoading: Boolean = false,
    val projectId: String? = null,
    val projectName: String = "Untitled",
    val projectType: ProjectType = ProjectType.STICKER,
    val origin: ProjectOrigin = ProjectOrigin.CREATED,
    val thumbnailPath: String = "recent_1",
    val editorSession: EditorSession = EditorSession(content = ProjectContent()),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val error: String? = null,
    val isSaveSuccess: Boolean = false
)

/**
 * ViewModel quản lý logic khôi phục EditorSession (bao gồm ProjectContent và EditHistory),
 * thực thi EditAction, Undo/Redo, và cập nhật thumbnail/lưu dự án.
 */
@HiltViewModel
class StickerEditViewModel @Inject constructor(
    private val getProjectByIdUseCase: GetProjectByIdUseCase,
    private val saveProjectUseCase: SaveProjectUseCase,
    private val updateProjectThumbnailUseCase: UpdateProjectThumbnailUseCase,
    val assetLoader: AssetLoader
) : ViewModel() {

    private val _uiState = MutableStateFlow(StickerEditUiState())
    val uiState: StateFlow<StickerEditUiState> = _uiState.asStateFlow()

    fun loadProject(projectId: String) {
        if (projectId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, projectId = projectId) }

            getProjectByIdUseCase.execute(projectId)
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collectLatest { project ->
                    if (project != null) {
                        val session = EditorSession(
                            content = project.content,
                            history = project.history
                        )
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                projectId = project.id,
                                projectName = project.name,
                                projectType = project.type,
                                origin = project.origin,
                                thumbnailPath = project.thumbnailPath,
                                editorSession = session,
                                canUndo = session.history.canUndo,
                                canRedo = session.history.canRedo
                            )
                        }
                    } else {
                        val newSession = EditorSession(content = ProjectContent())
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                projectId = projectId,
                                editorSession = newSession,
                                canUndo = false,
                                canRedo = false
                            )
                        }
                    }
                }
        }
    }

    fun performAction(action: EditAction) {
        val currentSession = _uiState.value.editorSession
        currentSession.perform(action)
        _uiState.update {
            it.copy(
                editorSession = currentSession,
                canUndo = currentSession.history.canUndo,
                canRedo = currentSession.history.canRedo
            )
        }
    }

    fun undo() {
        val currentSession = _uiState.value.editorSession
        if (currentSession.history.canUndo) {
            currentSession.undo()
            _uiState.update {
                it.copy(
                    editorSession = currentSession,
                    canUndo = currentSession.history.canUndo,
                    canRedo = currentSession.history.canRedo
                )
            }
        }
    }

    fun redo() {
        val currentSession = _uiState.value.editorSession
        if (currentSession.history.canRedo) {
            currentSession.redo()
            _uiState.update {
                it.copy(
                    editorSession = currentSession,
                    canUndo = currentSession.history.canUndo,
                    canRedo = currentSession.history.canRedo
                )
            }
        }
    }

    fun captureAndSaveThumbnail(projectId: String, bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                try {
                    val result = updateProjectThumbnailUseCase(projectId, bitmap)
                    result.onSuccess { newPath ->
                        _uiState.update { it.copy(thumbnailPath = newPath) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    fun saveProject() {
        val currentState = _uiState.value
        val pId = currentState.projectId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val result = saveProjectUseCase(
                projectId = pId,
                projectName = currentState.projectName,
                projectType = currentState.projectType,
                origin = currentState.origin,
                thumbnailPath = currentState.thumbnailPath,
                editorSession = currentState.editorSession
            )

            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, isSaveSuccess = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
