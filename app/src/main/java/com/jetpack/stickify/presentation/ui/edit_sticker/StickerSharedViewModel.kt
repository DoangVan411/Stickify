package com.jetpack.stickify.presentation.ui.edit_sticker

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.graphics.Bitmap
import android.graphics.Color
import com.jetpack.stickify.domain.model.StickerStyle
import com.jetpack.stickify.domain.usecase.ProcessStickerUseCase
import com.jetpack.stickify.domain.usecase.SaveStickerUseCase
import androidx.core.graphics.scale
import com.jetpack.stickify.domain.usecase.ApplyBorderUseCase

@HiltViewModel // Sử dụng Hilt để Inject
class StickerSharedViewModel @Inject constructor(
    private val processStickerUseCase: ProcessStickerUseCase,
    private val saveStickerUseCase: SaveStickerUseCase,
    private val applyBorderUseCase: ApplyBorderUseCase
) : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    // Lưu trữ ảnh trong RAM cho phiên làm việc
    val styleBitmaps = mutableMapOf<StickerStyle, Bitmap>()
    val styleThumbBitmaps = mutableMapOf<StickerStyle, Bitmap>()

    private val _imagesReady = MutableLiveData<Boolean>()
    val imagesReady: LiveData<Boolean> get() = _imagesReady

    private val _currentStyle = MutableLiveData(StickerStyle.ORIGINAL)

    private val coreBitmaps = mutableMapOf<StickerStyle, Bitmap>()
    val currentStyle: LiveData<StickerStyle> get() = _currentStyle

    // Bắn sự kiện lưu thành công
    private val _saveSuccessEvent = MutableLiveData<String?>()
    val saveSuccessEvent: LiveData<String?> get() = _saveSuccessEvent

    // Lịch sử Undo/Redo là logic của Presentation (UI state), nên giữ ở đây
    private val history = mutableListOf(StickerStyle.ORIGINAL)
    private var historyIndex = 0

    private val _historyState = MutableLiveData<Pair<Boolean, Boolean>>()
    val historyState: LiveData<Pair<Boolean, Boolean>> get() = _historyState


    //border
    val currentBorderThickness = MutableLiveData<Int>(30)
    val currentBorderDistance = MutableLiveData<Int>(20)
    val currentBorderColor = MutableLiveData<Int>(Color.WHITE)

    fun loadAndPrepareStyles(uriString: String) {
        if (styleBitmaps.isNotEmpty()) return

        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Gọi Use Case (Domain Layer)
                val processed = processStickerUseCase(uriString)

                styleBitmaps[StickerStyle.ORIGINAL] = processed.original
                styleBitmaps[StickerStyle.BORDER] = processed.border
                styleBitmaps[StickerStyle.CARTOON] = processed.cartoon

                coreBitmaps[StickerStyle.ORIGINAL] = processed.original
                coreBitmaps[StickerStyle.CARTOON] = processed.cartoon


                // Tạo Thumbnails (Logic UI có thể giữ lại đây hoặc tách ra UseCase tuỳ độ nghiêm ngặt)
                styleThumbBitmaps[StickerStyle.ORIGINAL] =
                    createCenterFitThumbnail(processed.original, 240)
                styleThumbBitmaps[StickerStyle.BORDER] =
                    createCenterFitThumbnail(processed.border, 240)
                styleThumbBitmaps[StickerStyle.CARTOON] =
                    createCenterFitThumbnail(processed.cartoon, 240)

                _imagesReady.value = true
                _currentStyle.value = StickerStyle.ORIGINAL

                updateHistoryState()
            } catch (e: Exception) {
                // TODO: Xử lý lỗi (Hiển thị toast, etc.)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveCurrentSticker() {
        val currentBitmap = styleBitmaps[_currentStyle.value ?: StickerStyle.ORIGINAL] ?: return

        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Gọi Use Case lưu ảnh
                val savedUri = saveStickerUseCase(currentBitmap)
                _saveSuccessEvent.value = savedUri
            } catch (e: Exception) {
                // Handle Error
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectStyle(style: StickerStyle, addToHistory: Boolean = true) {
        if (_currentStyle.value == style) return
        _currentStyle.value = style



        if (addToHistory) {
            while (history.size > historyIndex + 1) history.removeAt(history.size - 1)
            history.add(style)
            historyIndex = history.size - 1
            updateHistoryState()
        }
    }

    fun moveHistory(delta: Int) {
        val newIndex = historyIndex + delta
        if (newIndex !in history.indices) return
        historyIndex = newIndex
        selectStyle(history[historyIndex], addToHistory = false)
        updateHistoryState()
    }

    private fun updateHistoryState() {
        _historyState.value = Pair(historyIndex > 0, historyIndex < history.size - 1)
    }

    private fun createCenterFitThumbnail(source: Bitmap, maxSize: Int): Bitmap {
        // Logic scale ảnh thumbnail...
        return source.scale(240, 240) // Ví dụ
    }

    fun updateBorderConfig(
        thickness: Int? = null,
        distance: Int? = null,
        color: Int? = null
    ) {
        thickness?.let { currentBorderThickness.value = it }
        distance?.let { currentBorderDistance.value = it }
        color?.let { currentBorderColor.value = it }

        // Gọi Background Thread xử lý lại ảnh gốc với tham số viền mới
        processNewBorderSticker()
    }

    private fun processNewBorderSticker() {
        val currentSelectedStyle = _currentStyle.value ?: return

        // 1. Xác định LÕI SẠCH dựa trên cái đang chọn
        // Nếu đang chọn CARTOON -> Lấy lõi CARTOON. Còn ORIGINAL hay BORDER -> Lấy lõi ORIGINAL.
        val baseStyle = if (currentSelectedStyle == StickerStyle.CARTOON) {
            StickerStyle.CARTOON
        } else {
            StickerStyle.ORIGINAL
        }
        // 2. Lấy bức ảnh sạch chưa có viền ra
        val coreImage = coreBitmaps[baseStyle] ?: return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val thickness = currentBorderThickness.value ?: 30
                val distance = currentBorderDistance.value ?: 20
                val color = currentBorderColor.value ?: Color.WHITE

                // 2. Gọi UseCase (Thread Default/IO đã được xử lý an toàn ở tầng Data)
                val newBorderImage = applyBorderUseCase(
                    original = coreImage,
                    thickness = thickness,
                    distance = distance,
                    color = color
                )

                // 4. BƯỚC QUYẾT ĐỊNH: Ghi đè bức ảnh đã vẽ viền vào ĐÚNG cái tab mà User đang đứng
                styleBitmaps[currentSelectedStyle] = newBorderImage

                // 5. Trigger (kích hoạt) lại LiveData để Activity tự động vẽ lại ảnh MÀ KHÔNG BỊ ĐỔI TAB
                _currentStyle.value = currentSelectedStyle
            } catch (e: Exception) {
                // handle error
            } finally {
                _isLoading.value = false
            }
        }
    }
}