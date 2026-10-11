package com.jetpack.stickify.presentation.ui.edit_sticker

import com.jetpack.stickify.presentation.ui.edit_sticker.text.TextFont
import com.jetpack.stickify.presentation.ui.edit_sticker.text.TextStyleSpec
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetpack.stickify.data.source.local.AssetLoader
import com.jetpack.stickify.domain.model.*
import com.jetpack.stickify.domain.usecase.GetProjectByIdUseCase
import com.jetpack.stickify.domain.usecase.SaveProjectUseCase
import com.jetpack.stickify.domain.usecase.UpdateProjectThumbnailUseCase
import com.jetpack.stickify.presentation.ui.edit_sticker.prompt_sticker.AiEntryData
import com.jetpack.stickify.presentation.ui.edit_sticker.prompt_sticker.EditSpan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
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

    /** Số bước trong stack Undo; AiStickerViewModel dùng để đồng bộ Undo/Redo của kết quả AI với các chỉnh sửa layer. */
    val undoDepth: Int get() = _uiState.value.editorSession.history.undo.size

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

    fun loadProject(
        context: Context? = null,
        projectId: String,
        onProjectLoaded: (SubjectLayer?, BorderStyle?, Playback?, List<Pair<EditSpan, AiAnimation>>) -> Unit = { _, _, _, _ -> }
    ) {
        if (projectId.isBlank()) return
        if (loadedProjectId == projectId) return
        loadedProjectId = projectId

        val appContext = context?.applicationContext

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, projectId = projectId, loadFailed = false) }
            try {
                val project = getProjectByIdUseCase.execute(projectId).firstOrNull()
                if (project == null) {
                    loadedProjectId = null
                    _uiState.update {
                        it.copy(isLoading = false, loadFailed = true, error = "Không tìm thấy project")
                    }
                    return@launch
                }
                isPersisted = true

                val restoredAi = if (appContext != null && project.content.aiAnimations.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        loadAiAnimations(appContext, project.content.aiAnimations)
                    }
                } else emptyList()

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

                val subjectLayer = project.content.layers.filterIsInstance<SubjectLayer>().firstOrNull()
                onProjectLoaded(subjectLayer, project.content.border, project.content.playback, restoredAi)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadedProjectId = null
                _uiState.update { it.copy(isLoading = false, loadFailed = true, error = e.message ?: "Lỗi tải project") }
            }
        }
    }

    private fun loadAiAnimations(
        context: Context,
        specs: List<AiAnimationSpec>
    ): List<Pair<EditSpan, AiAnimation>> {
        val result = mutableListOf<Pair<EditSpan, AiAnimation>>()

        for (spec in specs) {
            val dir = File(context.filesDir, spec.relativeDir)
            if (!dir.exists() || !dir.isDirectory) continue

            val frameFiles = dir.listFiles { f -> f.isFile && f.name.endsWith(".png") }?.sortedBy { it.name } ?: continue
            if (frameFiles.isEmpty()) continue

            val frames = mutableListOf<Bitmap>()
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }

            for (file in frameFiles) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath, options)
                if (bmp != null) {
                    frames.add(bmp)
                }
            }

            if (frames.isNotEmpty()) {
                val span = EditSpan(spec.spanBefore, spec.spanAfter)
                val anim = AiAnimation(frames, spec.frameDelayMs)
                result.add(Pair(span, anim))
            }
        }

        return result
    }

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
                cutoutPath = relativePath,
                styledPath = relativePath
            )
            val newContent = ProjectContent(
                canvas = CanvasSpec(width = 512, height = 512),
                layers = listOf(subjectLayer)
            )
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

    private fun writeCoreBitmap(context: Context, projectId: String, fileName: String, bitmap: Bitmap): String? = runCatching {
        val dir = File(context.filesDir, "projects/$projectId").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        "projects/$projectId/$fileName"
    }.getOrNull()

    private fun writeStyledBitmap(context: Context, projectId: String, bitmap: Bitmap): String? = runCatching {
        val dir = File(context.filesDir, "projects/$projectId").apply { mkdirs() }
        val name = "subject_styled_${System.currentTimeMillis()}.png"
        val file = File(dir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        dir.listFiles { f -> f.name.startsWith("subject_styled_") && f.name != name }?.forEach { it.delete() }
        "projects/$projectId/$name"
    }.getOrNull()

    private data class ActionGroup(val start: Int, val count: Int)
    private val groups = mutableListOf<ActionGroup>()

    private fun performInternal(session: EditorSession, action: EditAction) {
        val before = session.history.undo.size
        groups.removeAll { it.start >= before }
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

    private fun saveAiAnimations(
        context: Context,
        projectId: String,
        entries: List<AiEntryData>
    ): List<AiAnimationSpec> {
        val baseDir = File(context.filesDir, "projects/$projectId/ai_animations").apply { mkdirs() }
        val activeDirs = mutableSetOf<String>()
        val specs = mutableListOf<AiAnimationSpec>()

        entries.forEachIndexed { index, entry ->
            val dirName = "anim_$index"
            val animDir = File(baseDir, dirName).apply { mkdirs() }
            activeDirs.add(dirName)

            // Dọn dẹp frame cũ trong thư mục này trước khi ghi
            animDir.listFiles()?.forEach { it.delete() }

            val frames = entry.animation.frames
            frames.forEachIndexed { fIdx, frameBmp ->
                val frameFile = File(animDir, String.format(Locale.US, "frame_%03d.png", fIdx))
                FileOutputStream(frameFile).use { out ->
                    frameBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }

            val relativePath = "projects/$projectId/ai_animations/$dirName"
            specs.add(
                AiAnimationSpec(
                    relativeDir = relativePath,
                    frameCount = frames.size,
                    frameDelayMs = entry.animation.frameDelayMs,
                    spanBefore = entry.span.before,
                    spanAfter = entry.span.after
                )
            )
        }

        // Dọn dẹp các thư mục animation cũ không còn được tham chiếu
        baseDir.listFiles { f -> f.isDirectory && f.name !in activeDirs }?.forEach { oldDir ->
            oldDir.deleteRecursively()
        }

        return specs
    }

    /**
     * Lưu project.
     * @param border null = giữ nguyên viền đang có trong project.
     * @param animationType StickerAnimationType thực tế đang chọn.
     * @param selectedStyle StickerStyle đang chọn (ORIGINAL, BORDER, CARTOON).
     * @param originalCoreBitmap Bitmap gốc chưa viền.
     * @param cartoonCoreBitmap Bitmap cartoon chưa viền.
     * @param styledBitmap Bitmap đã xử lý style cuối cùng.
     * @param thumbnailBitmap Thumbnail preview.
     */
    fun saveProjectWithDetails(
        context: Context?,
        projectId: String,
        border: BorderStyle?,
        animationType: StickerAnimationType,
        selectedStyle: StickerStyle = StickerStyle.ORIGINAL,
        originalCoreBitmap: Bitmap? = null,
        cartoonCoreBitmap: Bitmap? = null,
        styledBitmap: Bitmap? = null,
        thumbnailBitmap: Bitmap? = null,
        aiEntries: List<AiEntryData> = emptyList(),
        hasUndoneAi: Boolean = false
    ) {
        val appContext = context?.applicationContext
        val state = _uiState.value
        val session = state.editorSession

        viewModelScope.launch {
            withContext(NonCancellable) {
                _uiState.update { it.copy(projectId = projectId, isLoading = true, isSaveSuccess = false) }
                try {
                    var content = session.content

                    // 1a. Ghi core ORIGINAL & CARTOON ra đĩa nếu có
                    var cutoutRel: String? = null
                    var cartoonRel: String? = null
                    if (originalCoreBitmap != null && appContext != null) {
                        cutoutRel = withContext(Dispatchers.IO) { writeCoreBitmap(appContext, projectId, "subject_cutout.png", originalCoreBitmap) }
                    }
                    if (cartoonCoreBitmap != null && appContext != null) {
                        cartoonRel = withContext(Dispatchers.IO) { writeCoreBitmap(appContext, projectId, "subject_cartoon.png", cartoonCoreBitmap) }
                    }

                    // 1b. Ghi ảnh styled (đã viền/cartoon...)
                    var styledRel: String? = null
                    if (styledBitmap != null && appContext != null) {
                        styledRel = withContext(Dispatchers.IO) { writeStyledBitmap(appContext, projectId, styledBitmap) }
                    }

                    val subjectStyle = when (selectedStyle) {
                        StickerStyle.ORIGINAL -> SubjectStyle.ORIGINAL
                        StickerStyle.BORDER -> SubjectStyle.OUTLINE
                        StickerStyle.CARTOON -> SubjectStyle.CARTOON
                    }

                    var replaced = false
                    content = content.copy(
                        layers = content.layers.map { layer: Layer ->
                            if (!replaced && layer is SubjectLayer) {
                                replaced = true
                                layer.copy(
                                    style = subjectStyle,
                                    cutoutPath = cutoutRel ?: layer.cutoutPath,
                                    cartoonCorePath = cartoonRel ?: layer.cartoonCorePath,
                                    styledPath = styledRel ?: layer.styledPath
                                )
                            } else layer
                        }
                    )

                    // 1c. Ghi các animation AI đang applied ra đĩa
                    if (appContext != null) {
                        val specs = withContext(Dispatchers.IO) {
                            saveAiAnimations(appContext, projectId, aiEntries)
                        }
                        content = content.copy(aiAnimations = specs)
                    }

                    // 1d. Nhất quán lịch sử
                    var historyToSave = session.history
                    if (hasUndoneAi) {
                        historyToSave = historyToSave.copy(redo = emptyList())
                    }

                    // 2. Viền & Playback: Ghi TRỰC TIẾP vào content.copy mà KHÔNG gọi working.perform(...)
                    val newPlayback = Playback(
                        speed = speedFor(animationType),
                        loop = true,
                        animationType = animationType
                    )
                    content = content.copy(
                        border = border ?: content.border,
                        playback = newPlayback
                    )

                    val finalSession = EditorSession(content = content, history = historyToSave)

                    // 3. Tính toán ProjectType mới
                    val currentType = state.projectType
                    val hasAi = aiEntries.isNotEmpty()
                    val hasEffect = animationType.isAnimated

                    val newProjectType = when {
                        currentType == ProjectType.GIF -> ProjectType.GIF
                        hasAi || hasEffect -> ProjectType.ANIMATED_STICKER
                        else -> ProjectType.STICKER
                    }

                    // 4. Lưu DB
                    val result = saveProjectUseCase(
                        projectId = projectId,
                        projectName = state.projectName,
                        projectType = newProjectType,
                        origin = state.origin,
                        thumbnailPath = _uiState.value.thumbnailPath,
                        editorSession = finalSession
                    )

                    result.onSuccess {
                        isPersisted = true
                        loadedProjectId = projectId

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
                                projectType = newProjectType,
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
        saveProjectWithDetails(context = null, projectId = pId, border = null, animationType = StickerAnimationType.NONE)
    }

    fun updateBorder(thickness: Float, distance: Float, colorArgb: Int) {
        val currentSession = _uiState.value.editorSession
        val oldBorder = currentSession.content.border
        val newBorder = BorderStyle(thickness = thickness, spacing = distance, colorArgb = colorArgb)
        val action = ChangeBorderAction(before = oldBorder, after = newBorder)
        performAction(action)
    }

    fun setEffect(type: StickerAnimationType) {
        val currentSession = _uiState.value.editorSession
        val currentType = currentSession.content.playback?.animationType ?: StickerAnimationType.NONE
        if (currentType == type) return
        val action = ChangeEffectAction(before = currentType, after = type)
        performAction(action)
    }

    fun updateAnimation(animationType: StickerAnimationType) {
        setEffect(animationType)
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

    fun clearAllDecorAndTextWithUndo() {
        val session = _uiState.value.editorSession
        val layersToRemove = session.content.layers.filter { it !is SubjectLayer }
        if (layersToRemove.isEmpty()) return

        val startUndoSize = session.history.undo.size

        for (layer in layersToRemove) {
            val currentIndex = session.content.layers.indexOfFirst { it.id == layer.id }
            if (currentIndex >= 0) {
                performInternal(
                    session,
                    RemoveLayerAction(layer = session.content.layers[currentIndex], index = currentIndex)
                )
            }
        }

        if (layersToRemove.size > 1) {
            groups.add(ActionGroup(startUndoSize, layersToRemove.size))
        }

        publish(session)
    }

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

        val newLayer = oldLayer.copy(
            content = newContent,
            fontId = style.encode(),
            colorArgb = newColor,
            bold = style.font == TextFont.BOLD,
            align = newAlign
        )

        if (newLayer == oldLayer) return

        val action = UpdateLayerAction(before = oldLayer, after = newLayer)
        performAction(action)
    }
}
