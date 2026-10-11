package com.jetpack.stickify.presentation.ui.edit_sticker.prompt_sticker

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetpack.stickify.domain.model.AiAnimation
import com.jetpack.stickify.domain.model.AiStickerStep
import com.jetpack.stickify.domain.usecase.ExportAiAnimationUseCase
import com.jetpack.stickify.domain.usecase.GenerateAiStickerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiStickerUiState(
    val isGenerating: Boolean = false,
    val percent: Int = 0,
    val message: String = "",
    /** Sticker động đang hiển thị (null = quay về ảnh tĩnh). */
    val animation: AiAnimation? = null,
    val canUndoAi: Boolean = false,
    val error: String? = null
)

sealed interface AiStickerEvent {
    data class Exported(val uri: String) : AiStickerEvent
    data class ExportFailed(val message: String) : AiStickerEvent
}

/**
 * Khoảng "undo depth" của editViewModel mà 1 lần áp dụng AI đã chiếm:
 *  - before: depth ngay trước khi xóa Text/Decor
 *  - after : depth ngay sau khi xóa (before == after nếu lúc đó không có layer nào để xóa)
 */
data class EditSpan(val before: Int, val after: Int) {
    val clearedLayers: Boolean get() = after > before
}

data class AiEntryData(
    val span: EditSpan,
    val animation: AiAnimation
)

/** Kết quả khi AI xử lý Undo/Redo. Activity dựa vào đây để biết có phải bước tiếp editViewModel không. */
enum class AiHistoryResult {
    /** AI không liên quan -> để editViewModel tự xử lý. */
    NOT_HANDLED,

    /** AI đã tự xử lý xong (lúc đó không có layer nào bị xóa kèm theo). */
    HANDLED,

    /** AI đã xử lý VÀ Activity phải gọi thêm editViewModel.undo()/redo() để khôi phục/xóa lại nhóm layer đi kèm. */
    HANDLED_AND_STEP_EDIT
}

/**
 * Giữ kết quả AI qua xoay màn hình + lịch sử Undo/Redo của kết quả AI.
 *
 * Mỗi kết quả AI chiếm một [EditSpan] trong lịch sử của editViewModel: "xóa Text/Decor cũ" và "AI xuất hiện"
 * là MỘT bước duy nhất.
 *
 *  - Undo: nếu chưa có chỉnh sửa nào SAU AI (editDepth <= span.after) thì gỡ AI; nếu AI đã xóa layer thì
 *    Activity gọi thêm editViewModel.undo() (nhóm xóa là 1 ActionGroup) để layer cũ quay lại cùng lúc.
 *    Ngược lại (đã thêm text/decor sau AI) để editViewModel undo như bình thường.
 *  - Redo: áp dụng lại AI nếu editDepth == span.before; nếu AI đã xóa layer thì Activity gọi thêm
 *    editViewModel.redo() để xóa lại nhóm layer đó. Ngược lại để editViewModel redo.
 */
@HiltViewModel
class AiStickerViewModel @Inject constructor(
    private val generateAiSticker: GenerateAiStickerUseCase,
    private val exportAiAnimation: ExportAiAnimationUseCase
) : ViewModel() {

    private class Entry(val span: EditSpan, val animation: AiAnimation)

    private val applied = ArrayList<Entry>()
    private val undone = ArrayList<Entry>()
    private var lastDepth = 0
    private var expectEditRedo = false
    private var job: Job? = null

    private val _uiState = MutableStateFlow(AiStickerUiState())
    val uiState: StateFlow<AiStickerUiState> = _uiState.asStateFlow()

    private val _events = Channel<AiStickerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val frameDelayMs: Int get() = _uiState.value.animation?.frameDelayMs ?: 83

    fun getAppliedEntries(): List<AiEntryData> = applied.map { AiEntryData(it.span, it.animation) }

    fun getUndoneEntriesCount(): Int = undone.size

    /**
     * Khôi phục lịch sử AI khi load project từ DB.
     * Hàm idempotent: nếu [applied] đã có dữ liệu (vd: đã load rồi hoặc xoay màn hình) thì bỏ qua.
     */
    fun restore(entries: List<Pair<EditSpan, AiAnimation>>, currentEditDepth: Int) {
        if (applied.isNotEmpty()) return
        if (entries.isEmpty()) return

        applied.clear()
        undone.clear()
        for ((span, anim) in entries) {
            applied.add(Entry(span, anim))
        }
        lastDepth = currentEditDepth
        publish()
    }

    /**
     * @param onApply được gọi ĐÚNG lúc AI trả kết quả (trên main thread) để xóa Text/Decor cũ trong editViewModel
     *  và trả về [EditSpan] tương ứng. Lambda chỉ nên capture editViewModel (không capture Activity)
     *  vì job sống qua xoay màn hình.
     */
    fun generate(source: Bitmap, prompt: String, onApply: () -> EditSpan) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, percent = 0, message = "", error = null) }
            try {
                generateAiSticker(source, prompt).collect { step ->
                    when (step) {
                        is AiStickerStep.Progress -> _uiState.update { it.copy(percent = step.percent, message = step.message) }
                        is AiStickerStep.Failed -> {
                            Log.e("AiSticker", "Failed: ${step.message}")
                            _uiState.update { it.copy(error = step.message) }
                        }
                        is AiStickerStep.Done -> {
                            Log.d("AiSticker", "Done: ${step.animation.frames.size} frame, ${step.animation.frames.firstOrNull()?.width}x${step.animation.frames.firstOrNull()?.height}")
                            val span = onApply()
                            applied += Entry(span, step.animation)
                            undone.clear()
                            lastDepth = span.after
                            publish()
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e("AiSticker", "generate() crashed", e)
                _uiState.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
            } finally {
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    fun cancel() { job?.cancel() }

    fun consumeError() = _uiState.update { it.copy(error = null) }

    fun undo(editDepth: Int): AiHistoryResult {
        val top = applied.lastOrNull() ?: return AiHistoryResult.NOT_HANDLED
        if (editDepth > top.span.after) return AiHistoryResult.NOT_HANDLED
        applied.removeAt(applied.lastIndex)
        undone += top
        publish()
        return if (top.span.clearedLayers) AiHistoryResult.HANDLED_AND_STEP_EDIT else AiHistoryResult.HANDLED
    }

    fun redo(editDepth: Int): AiHistoryResult {
        val top = undone.lastOrNull() ?: return AiHistoryResult.NOT_HANDLED
        if (top.span.before != editDepth) return AiHistoryResult.NOT_HANDLED
        undone.removeAt(undone.lastIndex)
        applied += top
        publish()
        return if (top.span.clearedLayers) AiHistoryResult.HANDLED_AND_STEP_EDIT else AiHistoryResult.HANDLED
    }

    fun canRedoAi(editDepth: Int): Boolean = undone.lastOrNull()?.span?.before == editDepth

    fun expectEditRedo() { expectEditRedo = true }

    fun onEditDepthChanged(depth: Int) {
        if (depth == lastDepth) return
        if (depth > lastDepth && !expectEditRedo && undone.isNotEmpty()) {
            undone.clear()
            publish()
        }
        expectEditRedo = false
        lastDepth = depth
    }

    fun exportAnimated(frames: List<Bitmap>, frameDelayMs: Int) {
        viewModelScope.launch {
            try {
                _events.send(AiStickerEvent.Exported(exportAiAnimation(frames, frameDelayMs)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(AiStickerEvent.ExportFailed(e.message ?: "Xuất sticker động thất bại"))
            } finally {
                frames.forEach { if (!it.isRecycled) it.recycle() }
            }
        }
    }

    private fun publish() {
        _uiState.update { it.copy(animation = applied.lastOrNull()?.animation, canUndoAi = applied.isNotEmpty()) }
    }
}
