package com.jetpack.stickify.presentation.ui.edit_sticker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetpack.stickify.data.source.local.AssetLoader
import com.jetpack.stickify.domain.model.*
import com.jetpack.stickify.domain.usecase.GetProjectByIdUseCase
import com.jetpack.stickify.domain.usecase.SaveProjectUseCase
import com.jetpack.stickify.domain.usecase.UpdateProjectThumbnailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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
 * thực thi EditAction, Undo/Redo, quản lý Decorations và cập nhật thumbnail/lưu dự án.
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

    // Danh sách decoration vẽ trang trí (Drawn)
    private val _drawnDecorations = MutableStateFlow(
        listOf(
            BuiltinAsset("heart.png", "pack_default"),
            BuiltinAsset("img_decorate_01.png", "pack_default")
        )
    )
    val drawnDecorations: StateFlow<List<BuiltinAsset>> = _drawnDecorations.asStateFlow()

    // Danh sách nhãn (Labels / Stickers)
    private val _labelDecorations = MutableStateFlow(
        listOf(
            BuiltinAsset("ic_sample_01.png", "pack_comic"),
            BuiltinAsset("ic_sample_02.png", "pack_comic"),
            BuiltinAsset("ic_sample_03.png", "pack_comic"),
            BuiltinAsset("ic_sample_04.png", "pack_comic"),
            BuiltinAsset("heart.png", "pack_more"),
            BuiltinAsset("img_decorate_01.png", "pack_more")
        )
    )
    val labelDecorations: StateFlow<List<BuiltinAsset>> = _labelDecorations.asStateFlow()

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
                                thumbnailPath = project.thumbnailPath ?: "recent_1",
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

    fun initNewProject(projectId: String, imageUriString: String) {
        val subjectLayer = SubjectLayer(
            id = "layer_subject_main",
            transform = Transform(cx = 256f, cy = 256f, scale = 1f),
            visible = true,
            source = CustomAsset(imageUriString),
            styledPath = imageUriString
        )
        val newContent = ProjectContent(
            canvas = CanvasSpec(width = 512, height = 512),
            layers = listOf(subjectLayer)
        )
        val newHistory = EditHistory(
            undo = listOf(AddLayerAction(layer = subjectLayer, index = 0)),
            redo = emptyList()
        )
        val newSession = EditorSession(content = newContent, history = newHistory)
        _uiState.update {
            it.copy(
                projectId = projectId,
                projectName = "Sticker ${System.currentTimeMillis() % 1000}",
                editorSession = newSession,
                canUndo = newSession.history.canUndo,
                canRedo = newSession.history.canRedo
            )
        }
    }

    fun performAction(action: EditAction) {
        val currentSession = _uiState.value.editorSession
        currentSession.perform(action)
        val updatedSession = EditorSession(
            content = currentSession.content,
            history = currentSession.history
        )
        _uiState.update {
            it.copy(
                editorSession = updatedSession,
                canUndo = updatedSession.history.canUndo,
                canRedo = updatedSession.history.canRedo
            )
        }
    }

    fun addDecorationLayer(assetRef: AssetRef) {
        val currentSession = _uiState.value.editorSession
        val newLayerId = "layer_dec_${System.currentTimeMillis()}"
        val newLayer = DecorationLayer(
            id = newLayerId,
            transform = Transform(cx = 256f, cy = 256f, scale = 1f),
            visible = true,
            asset = assetRef,
            category = DecorationCategory.DRAWN
        )
        val action = AddLayerAction(layer = newLayer, index = currentSession.content.layers.size)
        performAction(action)
    }

    fun addCustomDecoration(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val customDir = File(context.filesDir, "custom_decorations")
                if (!customDir.exists()) customDir.mkdirs()
                val fileName = "custom_${System.currentTimeMillis()}.png"
                val file = File(customDir, fileName)
                FileOutputStream(file).use { output ->
                    inputStream.copyTo(output)
                }

                val relativePath = "custom_decorations/$fileName"
                val customAsset = CustomAsset(relativePath)

                withContext(Dispatchers.Main) {
                    addDecorationLayer(customAsset)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    fun saveProjectWithDetails(
        projectId: String,
        borderThickness: Float,
        borderDistance: Float,
        borderColor: Int,
        animationType: StickerAnimationType
    ) {
        val currentState = _uiState.value
        val speed = when (animationType) {
            StickerAnimationType.NONE -> PlaybackSpeed.X1
            StickerAnimationType.BOUNCE -> PlaybackSpeed.X1
            StickerAnimationType.SHAKE -> PlaybackSpeed.X2
            StickerAnimationType.SPIN -> PlaybackSpeed.X2
            StickerAnimationType.PULSE -> PlaybackSpeed.X4
            StickerAnimationType.WOBBLE -> PlaybackSpeed.X4
        }
        val updatedContent = currentState.editorSession.content.copy(
            border = BorderStyle(thickness = borderThickness, spacing = borderDistance, colorArgb = borderColor),
            playback = Playback(speed = speed, loop = true)
        )
        val updatedSession = EditorSession(content = updatedContent, history = currentState.editorSession.history)

        viewModelScope.launch {
            _uiState.update { it.copy(projectId = projectId, isLoading = true, editorSession = updatedSession) }

            val result = saveProjectUseCase(
                projectId = projectId,
                projectName = currentState.projectName,
                projectType = currentState.projectType,
                origin = currentState.origin,
                thumbnailPath = currentState.thumbnailPath,
                editorSession = updatedSession
            )

            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, isSaveSuccess = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun saveProject() {
        val currentState = _uiState.value
        val pId = currentState.projectId ?: "proj_${System.currentTimeMillis()}"
        saveProjectWithDetails(
            projectId = pId,
            borderThickness = currentState.editorSession.content.border?.thickness ?: 30f,
            borderDistance = currentState.editorSession.content.border?.spacing ?: 20f,
            borderColor = currentState.editorSession.content.border?.colorArgb ?: Color.WHITE,
            animationType = StickerAnimationType.NONE
        )
    }

    fun updateBorder(thickness: Float, distance: Float, colorArgb: Int) {
        val currentSession = _uiState.value.editorSession
        val oldBorder = currentSession.content.border
        val newBorder = BorderStyle(thickness = thickness, spacing = distance, colorArgb = colorArgb)
        val action = ChangeBorderAction(before = oldBorder, after = newBorder)
        performAction(action)
    }

    fun updateAnimation(animationType: StickerAnimationType) {
        val currentSession = _uiState.value.editorSession
        val oldPlayback = currentSession.content.playback
        val newSpeed = when (animationType) {
            StickerAnimationType.NONE -> PlaybackSpeed.X1
            StickerAnimationType.BOUNCE -> PlaybackSpeed.X1
            StickerAnimationType.SHAKE -> PlaybackSpeed.X2
            StickerAnimationType.SPIN -> PlaybackSpeed.X2
            StickerAnimationType.PULSE -> PlaybackSpeed.X4
            StickerAnimationType.WOBBLE -> PlaybackSpeed.X4
        }
        val action = ChangeSpeedAction(before = oldPlayback?.speed ?: PlaybackSpeed.X1, after = newSpeed)
        performAction(action)
    }

    fun addBuiltinDecoration(assetId: String, packId: String = "pack_default", category: DecorationCategory = DecorationCategory.LABEL) {
        val currentSession = _uiState.value.editorSession
        val newLayerId = "layer_dec_${System.currentTimeMillis()}"
        val asset = BuiltinAsset(assetId = assetId, packId = packId)
        val newLayer = DecorationLayer(
            id = newLayerId,
            transform = Transform(cx = 256f, cy = 256f, scale = 1f),
            visible = true,
            asset = asset,
            category = category
        )
        val action = AddLayerAction(layer = newLayer, index = currentSession.content.layers.size)
        performAction(action)
    }
    fun addTextLayer(content: String, color: Int, align: TextAlign) {
        val currentSession = _uiState.value.editorSession
        val newLayerId = "layer_text_${System.currentTimeMillis()}"
        val newLayer = TextLayer(
            id = newLayerId,
            transform = Transform(cx = 256f, cy = 256f, scale = 1f),
            visible = true,
            content = content,
            colorArgb = color,
            align = align
        )
        val action = AddLayerAction(layer = newLayer, index = currentSession.content.layers.size)
        performAction(action)
    }

    fun updateTextLayer(layerId: String, newContent: String, newColor: Int, newAlign: TextAlign) {
        val currentSession = _uiState.value.editorSession
        val oldLayer = currentSession.content.layers.find { it.id == layerId } as? TextLayer ?: return

        // Dùng hàm copy() của Kotlin data class để cập nhật nội dung mới
        // nhưng vẫn giữ nguyên toàn bộ Transform (cx, cy, scale, rotationDeg) cũ.
        val newLayer = oldLayer.copy(
            content = newContent,
            colorArgb = newColor,
            align = newAlign
        )

        val action = UpdateLayerAction(before = oldLayer, after = newLayer)
        performAction(action)
    }
}
