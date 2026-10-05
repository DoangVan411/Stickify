package com.jetpack.stickify.presentation.ui.edit_sticker

import com.jetpack.stickify.presentation.ui.edit_sticker.text.TextFont
import com.jetpack.stickify.presentation.ui.edit_sticker.text.TextStyleSpec
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
import kotlinx.coroutines.CancellationException
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
    val isSaveSuccess: Boolean = false,
    /** Load project thất bại (không tìm thấy / lỗi DB) -> Activity nên đóng màn hình. */
    val loadFailed: Boolean = false,
    /** Tăng mỗi lần session thay đổi, đảm bảo StateFlow luôn emit dù EditorSession so sánh bằng nhau. */
    val revision: Long = 0L,
    /** Layer vừa được thêm -> Activity chọn nó trên view rồi gọi consumeAddedLayer(). */
    val lastAddedLayerId: String? = null
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

    /** Id project đã được load/khởi tạo; chặn việc reset session khi Activity bị tạo lại (xoay màn hình). */
    private var loadedProjectId: String? = null

    /** true khi project đã tồn tại trong DB (đã load từ DB hoặc đã lưu ít nhất 1 lần). */
    var isPersisted: Boolean = false
        private set

    private fun publish(session: EditorSession) {
        // Luôn tạo instance mới + tăng revision để StateFlow chắc chắn emit.
        val fresh = EditorSession(content = session.content, history = session.history)
        _uiState.update {
            it.copy(
                editorSession = fresh,
                canUndo = fresh.history.canUndo,
                canRedo = fresh.history.canRedo,
                revision = it.revision + 1
            )
        }
    }

    fun consumeAddedLayer() {
        _uiState.update { it.copy(lastAddedLayerId = null) }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    fun loadProject(projectId: String) {
        if (projectId.isBlank()) return
        // Đã load rồi (xoay màn hình) -> KHÔNG load lại, nếu không sẽ mất các chỉnh sửa chưa lưu + lịch sử undo/redo.
        if (loadedProjectId == projectId) return
        loadedProjectId = projectId

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, projectId = projectId, loadFailed = false) }
            try {
                // Chỉ lấy MỘT lần. Trước đây dùng collectLatest trên Flow của Room: mỗi lần DB đổi
                // (kể cả khi cập nhật thumbnail ở onStop) session bị ghi đè bằng bản trong DB
                // => mất chỉnh sửa chưa lưu và mất lịch sử undo/redo.
                val project = getProjectByIdUseCase.execute(projectId).firstOrNull()
                if (project == null) {
                    loadedProjectId = null
                    _uiState.update {
                        it.copy(isLoading = false, loadFailed = true, error = "Không tìm thấy project")
                    }
                    return@launch
                }
                isPersisted = true
                val session = EditorSession(content = project.content, history = project.history)
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
                        canRedo = session.history.canRedo,
                        revision = it.revision + 1
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadedProjectId = null
                _uiState.update { it.copy(isLoading = false, loadFailed = true, error = e.message ?: "Lỗi tải project") }
            }
        }
    }

    /**
     * Khởi tạo project mới từ ảnh cắt. Ảnh được COPY vào bộ nhớ riêng của app
     * (filesDir/projects/<id>/...) và layer chỉ lưu đường dẫn tương đối, vì Uri tạm / cache
     * sẽ mất hiệu lực khi mở lại project => đây là lý do project cũ mở ra bị trống.
     */
    fun initNewProject(context: Context, projectId: String, imageUriString: String) {
        if (loadedProjectId == projectId) return
        loadedProjectId = projectId
        _uiState.update { it.copy(projectId = projectId) }

        val appContext = context.applicationContext
        viewModelScope.launch {
            val relativePath = withContext(Dispatchers.IO) {
                copyToPrivateStorage(appContext, projectId, Uri.parse(imageUriString))
            }
            if (relativePath == null) {
                loadedProjectId = null
                _uiState.update { it.copy(error = "Không thể lưu ảnh vào project") }
                return@launch
            }

            val subjectLayer = SubjectLayer(
                id = "layer_subject_main",
                transform = Transform(cx = 256f, cy = 256f, scale = 1f),
                visible = true,
                source = CustomAsset(relativePath),
                styledPath = relativePath
            )
            val newContent = ProjectContent(
                canvas = CanvasSpec(width = 512, height = 512),
                layers = listOf(subjectLayer)
            )
            // Layer chủ thể là NỀN của project => không đưa vào lịch sử,
            // nếu không người dùng có thể Undo và xóa mất chủ thể.
            val newHistory = EditHistory(undo = emptyList(), redo = emptyList())
            val newSession = EditorSession(content = newContent, history = newHistory)
            _uiState.update {
                it.copy(
                    projectId = projectId,
                    projectName = "Sticker ${System.currentTimeMillis() % 1000}",
                    editorSession = newSession,
                    canUndo = false,
                    canRedo = false,
                    revision = it.revision + 1
                )
            }
        }
    }

    private fun copyToPrivateStorage(context: Context, projectId: String, uri: Uri): String? = runCatching {
        val dir = File(context.filesDir, "projects/$projectId").apply { mkdirs() }
        val file = File(dir, "subject_source.png")
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { ins -> FileOutputStream(file).use { out -> ins.copyTo(out) } }
        "projects/$projectId/subject_source.png"
    }.getOrNull()

    private fun writeStyledBitmap(context: Context, projectId: String, bitmap: Bitmap): String? = runCatching {
        val dir = File(context.filesDir, "projects/$projectId").apply { mkdirs() }
        // Tên file duy nhất mỗi lần lưu để cache bitmap trong ZoomableStickerView không bị cũ.
        val name = "subject_styled_${System.currentTimeMillis()}.png"
        val file = File(dir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        dir.listFiles { f -> f.name.startsWith("subject_styled_") && f.name != name }?.forEach { it.delete() }
        "projects/$projectId/$name"
    }.getOrNull()

    /** Nhiều action liên tiếp được Undo/Redo cùng lúc (vd: 1 nét vẽ brush gồm nhiều con dấu). Chỉ tồn tại trong RAM. */
    private data class ActionGroup(val start: Int, val count: Int)
    private val groups = mutableListOf<ActionGroup>()

    private fun performInternal(session: EditorSession, action: EditAction) {
        val before = session.history.undo.size
        groups.removeAll { it.start >= before } // các nhóm nằm trong nhánh redo đã bị loại bỏ
        session.perform(action)
    }

    fun performAction(action: EditAction) {
        val currentSession = _uiState.value.editorSession
        performInternal(currentSession, action)
        publish(currentSession)
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
        _uiState.update { it.copy(lastAddedLayerId = newLayerId) }
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
        if (!currentSession.history.canUndo) return
        val size = currentSession.history.undo.size
        val n = groups.find { it.start + it.count == size }?.count ?: 1
        repeat(n) { if (currentSession.history.canUndo) currentSession.undo() }
        publish(currentSession)
    }

    fun redo() {
        val currentSession = _uiState.value.editorSession
        if (!currentSession.history.canRedo) return
        val size = currentSession.history.undo.size
        val n = groups.find { it.start == size }?.count ?: 1
        repeat(n) { if (currentSession.history.canRedo) currentSession.redo() }
        publish(currentSession)
    }

    // ---------- Thao tác trên layer decor/chữ từ ZoomableStickerView ----------

    private fun Transform.copyWith(
        cx: Float = this.cx,
        cy: Float = this.cy,
        scale: Float = this.scale,
        rotationDeg: Float = this.rotationDeg
    ) = Transform(
        cx = cx, cy = cy, scale = scale,
        rotationDeg = rotationDeg, flipX = this.flipX, opacity = this.opacity
    )

    private fun Layer.withTransform(t: Transform): Layer? = when (this) {
        is DecorationLayer -> copy(transform = t)
        is TextLayer -> copy(transform = t)
        is SubjectLayer -> copy(transform = t)
        else -> null
    }

    /** Gọi khi người dùng kéo/xoay/phóng xong 1 layer. */
    fun updateLayerTransform(layerId: String, cx: Float, cy: Float, scale: Float, rotationDeg: Float) {
        val before = _uiState.value.editorSession.content.layers.find { it.id == layerId } ?: return
        val after = before.withTransform(before.transform.copyWith(cx, cy, scale, rotationDeg)) ?: return
        performAction(UpdateLayerAction(before = before, after = after))
    }

    fun removeLayer(layerId: String) {
        val layers = _uiState.value.editorSession.content.layers
        val index = layers.indexOfFirst { it.id == layerId }
        if (index < 0) return
        performAction(RemoveLayerAction(layer = layers[index], index = index))
    }

    fun duplicateLayer(sourceId: String, newId: String, cx: Float, cy: Float) {
        val layers = _uiState.value.editorSession.content.layers
        val src = layers.find { it.id == sourceId } ?: return
        val t = src.transform.copyWith(cx = cx, cy = cy)
        val copy: Layer = when (src) {
            is DecorationLayer -> src.copy(id = newId, transform = t)
            is TextLayer -> src.copy(id = newId, transform = t)
            else -> return
        }
        performAction(AddLayerAction(layer = copy, index = layers.size))
        _uiState.update { it.copy(lastAddedLayerId = newId) }
    }

    private fun saveDecorBitmap(context: Context, bitmap: Bitmap): String? = runCatching {
        val dir = File(context.filesDir, "custom_decorations").apply { mkdirs() }
        val name = "custom_${System.currentTimeMillis()}_${(0..9999).random()}.png"
        FileOutputStream(File(dir, name)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        "custom_decorations/$name"
    }.getOrNull()

    /** Thêm 1 decor (từ DecorToolFragment) vào project ở giữa canvas. Bitmap được lưu ra filesDir. */
    fun addDecorationFromBitmap(context: Context, bitmap: Bitmap, layerId: String) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val rel = withContext(Dispatchers.IO) { saveDecorBitmap(appContext, bitmap) }
            if (rel == null) {
                _uiState.update { it.copy(error = "Không thể lưu decor") }
                return@launch
            }
            val content = _uiState.value.editorSession.content
            val layer = DecorationLayer(
                id = layerId,
                transform = Transform(
                    cx = content.canvas.width / 2f,
                    cy = content.canvas.height / 2f,
                    scale = 1f
                ),
                visible = true,
                asset = CustomAsset(rel),
                category = DecorationCategory.CUSTOM
            )
            performAction(AddLayerAction(layer = layer, index = content.layers.size))
            _uiState.update { it.copy(lastAddedLayerId = layerId) }
        }
    }

    /** Thêm các con dấu của 1 nét vẽ brush. Cả nét vẽ được Undo/Redo như 1 thao tác. */
    fun addStampLayers(context: Context, bitmap: Bitmap, stamps: List<ZoomableStickerView.StampSpec>) {
        if (stamps.isEmpty()) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            val rel = withContext(Dispatchers.IO) { saveDecorBitmap(appContext, bitmap) }
            if (rel == null) {
                _uiState.update { it.copy(error = "Không thể lưu decor") }
                return@launch
            }
            val session = _uiState.value.editorSession
            val start = session.history.undo.size
            for (st in stamps) {
                val layer = DecorationLayer(
                    id = st.id,
                    transform = Transform(cx = st.cx, cy = st.cy, scale = st.scale),
                    visible = true,
                    asset = CustomAsset(rel),
                    category = DecorationCategory.DRAWN
                )
                performInternal(session, AddLayerAction(layer = layer, index = session.content.layers.size))
            }
            if (stamps.size > 1) groups.add(ActionGroup(start, stamps.size))
            publish(session)
        }
    }

    fun captureAndSaveThumbnail(projectId: String, bitmap: Bitmap) {
        // Project chưa có trong DB (chưa lưu / bị hủy) thì không cập nhật thumbnail.
        if (!isPersisted) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
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

    private fun speedFor(animationType: StickerAnimationType): PlaybackSpeed = when (animationType) {
        StickerAnimationType.NONE -> PlaybackSpeed.X1
        StickerAnimationType.BOUNCE -> PlaybackSpeed.X1
        StickerAnimationType.SHAKE -> PlaybackSpeed.X2
        StickerAnimationType.SPIN -> PlaybackSpeed.X2
        StickerAnimationType.PULSE -> PlaybackSpeed.X4
        StickerAnimationType.WOBBLE -> PlaybackSpeed.X4
    }

    /**
     * Lưu project.
     * @param border null = giữ nguyên viền đang có trong project.
     * @param animationType null = giữ nguyên playback đang có.
     * @param styledBitmap ảnh chủ thể cuối cùng (đã viền/cartoon/decor...) -> ghi ra filesDir và gắn vào SubjectLayer.
     * @param thumbnailBitmap thumbnail, được cập nhật SAU khi lưu project để không bị ghi đè bởi thumbnailPath cũ.
     */
    fun saveProjectWithDetails(
        context: Context?,
        projectId: String,
        border: BorderStyle?,
        animationType: StickerAnimationType?,
        styledBitmap: Bitmap? = null,
        thumbnailBitmap: Bitmap? = null
    ) {
        val appContext = context?.applicationContext
        val state = _uiState.value
        val session = state.editorSession

        viewModelScope.launch {
            // Activity finish() => viewModelScope bị hủy; NonCancellable để lưu DB luôn chạy xong.
            withContext(NonCancellable) {
                _uiState.update { it.copy(projectId = projectId, isLoading = true, isSaveSuccess = false) }
                try {
                    var content = session.content

                    // 1. Gắn ảnh chủ thể đã xử lý vào SubjectLayer (không đưa vào lịch sử Undo)
                    if (styledBitmap != null && appContext != null) {
                        val rel = withContext(Dispatchers.IO) { writeStyledBitmap(appContext, projectId, styledBitmap) }
                        if (rel != null) {
                            var replaced = false
                            content = content.copy(
                                layers = content.layers.map { layer: Layer ->
                                    if (!replaced && layer is SubjectLayer) {
                                        replaced = true
                                        layer.copy(styledPath = rel)
                                    } else layer
                                }
                            )
                        }
                    }

                    // 2. Viền / playback: ghi nhận qua EditAction để lịch sử khớp với nội dung
                    val working = EditorSession(content = content, history = session.history)
                    if (border != null && content.border != border) {
                        working.perform(ChangeBorderAction(before = content.border, after = border))
                    }
                    val newSpeed = animationType?.let { speedFor(it) }
                    if (newSpeed != null && content.playback?.speed != newSpeed) {
                        working.perform(
                            ChangeSpeedAction(before = content.playback?.speed ?: PlaybackSpeed.X1, after = newSpeed)
                        )
                    }
                    // Phòng trường hợp action.apply() không xử lý border/playback đang null
                    var finalContent = working.content
                    if (border != null && finalContent.border != border) {
                        finalContent = finalContent.copy(border = border)
                    }
                    if (newSpeed != null && finalContent.playback?.speed != newSpeed) {
                        finalContent = finalContent.copy(
                            playback = Playback(speed = newSpeed, loop = finalContent.playback?.loop ?: true)
                        )
                    }
                    val finalSession = EditorSession(content = finalContent, history = working.history)

                    // 3. Lưu DB
                    val result = saveProjectUseCase(
                        projectId = projectId,
                        projectName = state.projectName,
                        projectType = state.projectType,
                        origin = state.origin,
                        thumbnailPath = _uiState.value.thumbnailPath,
                        editorSession = finalSession
                    )

                    result.onSuccess {
                        isPersisted = true
                        loadedProjectId = projectId

                        // 4. Thumbnail SAU khi project đã có trong DB
                        if (thumbnailBitmap != null) {
                            withContext(Dispatchers.IO) {
                                updateProjectThumbnailUseCase(projectId, thumbnailBitmap)
                            }.onSuccess { newPath ->
                                _uiState.update { it.copy(thumbnailPath = newPath) }
                            }
                        }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isSaveSuccess = true,
                                editorSession = finalSession,
                                canUndo = finalSession.history.canUndo,
                                canRedo = finalSession.history.canRedo,
                                revision = it.revision + 1
                            )
                        }
                    }.onFailure { e ->
                        _uiState.update { it.copy(isLoading = false, error = e.message ?: "Lưu project thất bại") }
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Lưu project thất bại") }
                } finally {
                    if (thumbnailBitmap != null && !thumbnailBitmap.isRecycled) thumbnailBitmap.recycle()
                }
            }
        }
    }

    fun saveProject() {
        val pId = _uiState.value.projectId ?: "proj_${System.currentTimeMillis()}"
        saveProjectWithDetails(context = null, projectId = pId, border = null, animationType = null)
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
        _uiState.update { it.copy(lastAddedLayerId = newLayerId) }
    }
    /**
     * @param color  màu người dùng chọn (màu chữ / nền / viền tùy kiểu).
     * @param style  font + kiểu nền, được mã hóa vào TextLayer.fontId nên lưu DB + undo/redo được.
     */
    fun addTextLayer(
        content: String,
        color: Int,
        align: TextAlign,
        style: TextStyleSpec = TextStyleSpec.DEFAULT
    ) {
        val currentSession = _uiState.value.editorSession
        val newLayerId = "layer_text_${System.currentTimeMillis()}"
        val newLayer = TextLayer(
            id = newLayerId,
            transform = Transform(cx = 256f, cy = 256f, scale = 1f),
            visible = true,
            content = content,
            fontId = style.encode(),
            colorArgb = color,
            bold = style.font == TextFont.BOLD,
            align = align
        )
        val action = AddLayerAction(layer = newLayer, index = currentSession.content.layers.size)
        performAction(action)
        _uiState.update { it.copy(lastAddedLayerId = newLayerId) }
    }

    fun updateTextLayer(
        layerId: String,
        newContent: String,
        newColor: Int,
        newAlign: TextAlign,
        newStyle: TextStyleSpec? = null
    ) {
        val currentSession = _uiState.value.editorSession
        val oldLayer = currentSession.content.layers.find { it.id == layerId } as? TextLayer ?: return

        val style = newStyle ?: TextStyleSpec.decode(oldLayer.fontId)

        // copy() giữ nguyên toàn bộ Transform (cx, cy, scale, rotationDeg) cũ.
        val newLayer = oldLayer.copy(
            content = newContent,
            fontId = style.encode(),
            colorArgb = newColor,
            bold = style.font == TextFont.BOLD,
            align = newAlign
        )

        // Không có gì đổi thì không tạo thêm 1 bước trong lịch sử undo
        if (newLayer == oldLayer) return

        val action = UpdateLayerAction(before = oldLayer, after = newLayer)
        performAction(action)
    }
}