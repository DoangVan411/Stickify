package com.jetpack.stickify.presentation.ui.edit_sticker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.core.graphics.scale
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetpack.stickify.data.processor.StickerStyleProcessor
import com.jetpack.stickify.data.source.local.AssetLoader
import com.jetpack.stickify.domain.model.BorderStyle
import com.jetpack.stickify.domain.model.Layer
import com.jetpack.stickify.domain.model.Playback
import com.jetpack.stickify.domain.model.StickerAnimationType
import com.jetpack.stickify.domain.model.StickerProject
import com.jetpack.stickify.domain.model.StickerStyle
import com.jetpack.stickify.domain.model.SubjectLayer
import com.jetpack.stickify.domain.model.SubjectStyle
import com.jetpack.stickify.domain.usecase.ApplyBorderUseCase
import com.jetpack.stickify.domain.usecase.ExportGifUseCase
import com.jetpack.stickify.domain.usecase.ProcessStickerUseCase
import com.jetpack.stickify.domain.usecase.SaveStickerUseCase
import com.jetpack.stickify.presentation.ui.edit_gif.GifProjectHandler
import com.jetpack.stickify.presentation.ui.edit_sticker.decor.DecorModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class StickerSharedViewModel @Inject constructor(
    private val processStickerUseCase: ProcessStickerUseCase,
    private val saveStickerUseCase: SaveStickerUseCase,
    private val applyBorderUseCase: ApplyBorderUseCase,
    private val exportGifUseCase: ExportGifUseCase,
    val assetLoader: AssetLoader,
    private val gifProjectHandler: GifProjectHandler
) : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private var borderProcessJob: Job? = null

    val styleBitmaps = mutableMapOf<StickerStyle, Bitmap>()
    val styleThumbBitmaps = mutableMapOf<StickerStyle, Bitmap>()

    private val _imagesReady = MutableLiveData<Boolean>()
    val imagesReady: LiveData<Boolean> get() = _imagesReady

    private val _currentStyle = MutableLiveData(StickerStyle.ORIGINAL)

    val coreBitmaps = mutableMapOf<StickerStyle, Bitmap>()
    val currentStyle: LiveData<StickerStyle> get() = _currentStyle

    private val _saveSuccessEvent = MutableLiveData<String?>()
    val saveSuccessEvent: LiveData<String?> get() = _saveSuccessEvent

    private val history = mutableListOf(StickerStyle.ORIGINAL)
    private var historyIndex = 0

    private val _historyState = MutableLiveData<Pair<Boolean, Boolean>>()
    val historyState: LiveData<Pair<Boolean, Boolean>> get() = _historyState

    // Border configs
    val currentBorderThickness = MutableLiveData<Int>(30)
    val currentBorderDistance = MutableLiveData<Int>(20)
    val currentBorderColor = MutableLiveData<Int>(Color.WHITE)

    // Decor
    private val _decorList = MutableLiveData<List<DecorModel>>(emptyList())
    val decorList: LiveData<List<DecorModel>> get() = _decorList

    private val _addedDecorEvent = MutableLiveData<DecorModel?>()
    val addedDecorEvent: LiveData<DecorModel?> get() = _addedDecorEvent
    private val _drawDecorModeEvent = MutableLiveData<DecorModel?>()
    val drawDecorModeEvent: LiveData<DecorModel?> get() = _drawDecorModeEvent

    private var restoredProjectId: String? = null

    fun addDecor(resId: Int, id: String = UUID.randomUUID().toString()) {
        val newDecor = DecorModel(id = id, resId = resId)
        val current = _decorList.value.orEmpty().toMutableList()
        current.add(newDecor)
        _decorList.value = current
        _drawDecorModeEvent.value = null
        _addedDecorEvent.value = newDecor
    }

    fun addCustomDecor(bitmap: Bitmap) {
        val newDecor = DecorModel(customBitmap = bitmap)
        val current = _decorList.value.orEmpty().toMutableList()
        current.add(newDecor)
        _decorList.value = current
        _drawDecorModeEvent.value = null
        _addedDecorEvent.value = newDecor
    }

    fun selectDrawDecor(resId: Int) {
        _drawDecorModeEvent.value = if (resId != 0) DecorModel(resId = resId) else null
    }

    fun clearDrawDecor() {
        _drawDecorModeEvent.value = null
    }

    fun loadAndPrepareStyles(uriString: String) {
        if (styleBitmaps.isNotEmpty()) return

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val processed = processStickerUseCase(uriString)

                styleBitmaps[StickerStyle.ORIGINAL] = processed.original
                styleBitmaps[StickerStyle.BORDER] = processed.border
                styleBitmaps[StickerStyle.CARTOON] = processed.cartoon

                coreBitmaps[StickerStyle.ORIGINAL] = processed.original
                coreBitmaps[StickerStyle.CARTOON] = processed.cartoon

                styleThumbBitmaps[StickerStyle.ORIGINAL] = createCenterFitThumbnail(processed.original, 240)
                styleThumbBitmaps[StickerStyle.BORDER] = createCenterFitThumbnail(processed.border, 240)
                styleThumbBitmaps[StickerStyle.CARTOON] = createCenterFitThumbnail(processed.cartoon, 240)

                _imagesReady.value = true
                _currentStyle.value = StickerStyle.ORIGINAL

                updateHistoryState()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun restoreFromProject(
        context: Context,
        projectId: String,
        subjectLayer: SubjectLayer,
        borderStyle: BorderStyle?,
        playback: Playback?
    ) {
        if (restoredProjectId == projectId && styleBitmaps.isNotEmpty()) return
        restoredProjectId = projectId

        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cutoutPath = subjectLayer.cutoutPath
                val cutoutFile = if (!cutoutPath.isNullOrBlank()) {
                    File(context.filesDir, cutoutPath)
                } else null

                val originalCore: Bitmap? = if (cutoutFile != null && cutoutFile.exists()) {
                    BitmapFactory.decodeFile(cutoutFile.absolutePath)
                } else {
                    assetLoader.loadBitmap(subjectLayer.source)
                }

                if (originalCore == null) {
                    _isLoading.postValue(false)
                    return@launch
                }

                val cartoonPath = subjectLayer.cartoonCorePath
                val cartoonFile = if (!cartoonPath.isNullOrBlank()) {
                    File(context.filesDir, cartoonPath)
                } else null

                val cartoonCore: Bitmap = if (cartoonFile != null && cartoonFile.exists()) {
                    BitmapFactory.decodeFile(cartoonFile.absolutePath) ?: StickerStyleProcessor.cartoonify(originalCore)
                } else {
                    StickerStyleProcessor.cartoonify(originalCore)
                }

                coreBitmaps[StickerStyle.ORIGINAL] = originalCore
                coreBitmaps[StickerStyle.CARTOON] = cartoonCore

                styleBitmaps[StickerStyle.ORIGINAL] = originalCore
                styleBitmaps[StickerStyle.CARTOON] = cartoonCore

                val thickness = borderStyle?.thickness?.toInt() ?: 30
                val distance = borderStyle?.spacing?.toInt() ?: 20
                val color = borderStyle?.colorArgb ?: Color.WHITE

                currentBorderThickness.postValue(thickness)
                currentBorderDistance.postValue(distance)
                currentBorderColor.postValue(color)

                val borderBitmap = applyBorderUseCase(
                    original = originalCore,
                    thickness = thickness,
                    distance = distance,
                    color = color
                )
                styleBitmaps[StickerStyle.BORDER] = borderBitmap

                styleThumbBitmaps[StickerStyle.ORIGINAL] = createCenterFitThumbnail(originalCore, 240)
                styleThumbBitmaps[StickerStyle.BORDER] = createCenterFitThumbnail(borderBitmap, 240)
                styleThumbBitmaps[StickerStyle.CARTOON] = createCenterFitThumbnail(cartoonCore, 240)

                val restoredStyle = when (subjectLayer.style) {
                    SubjectStyle.OUTLINE -> StickerStyle.BORDER
                    SubjectStyle.CARTOON -> StickerStyle.CARTOON
                    SubjectStyle.ORIGINAL -> StickerStyle.ORIGINAL
                }

                val styledPath = subjectLayer.styledPath
                val styledFile = if (!styledPath.isNullOrBlank()) File(context.filesDir, styledPath) else null
                if (styledFile != null && styledFile.exists()) {
                    val styledBmp = BitmapFactory.decodeFile(styledFile.absolutePath)
                    if (styledBmp != null) {
                        styleBitmaps[restoredStyle] = styledBmp
                        styleThumbBitmaps[restoredStyle] = createCenterFitThumbnail(styledBmp, 240)
                    }
                }

                _currentStyle.postValue(restoredStyle)

                val restoredAnim = playback?.animationType ?: StickerAnimationType.NONE
                _currentAnimation.postValue(restoredAnim)

                _imagesReady.postValue(true)
                updateHistoryState()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    // Animation / Effect
    private val _currentAnimation = MutableLiveData(StickerAnimationType.NONE)
    val currentAnimation: LiveData<StickerAnimationType> get() = _currentAnimation

    fun setAnimation(animationType: StickerAnimationType) {
        _currentAnimation.value = animationType
    }

    fun saveCurrentSticker(overrideBitmap: Bitmap? = null, sourceId: String? = null) {
        val currentBitmap = overrideBitmap ?: styleBitmaps[_currentStyle.value ?: StickerStyle.ORIGINAL] ?: return

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val anim = _currentAnimation.value
                val isAnimated = anim != null && anim.isAnimated
                val savedUri = if (isAnimated) {
                    exportGifUseCase(currentBitmap, anim)
                } else {
                    saveStickerUseCase(currentBitmap)
                }

                _saveSuccessEvent.value = savedUri
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun saveGifProject(
        projectId: String,
        thumbBitmap: Bitmap?,
        outputFile: File,
        filesDir: File,
        videoUri: String? = null,
        startTrimMs: Long = 0L,
        endTrimMs: Long = 8000L,
        speedMultiplier: Float = 1.0f,
        customName: String? = null,
        layers: List<Layer> = emptyList(),
        canvasWidth: Int = 0,
        canvasHeight: Int = 0
    ): Result<StickerProject> {
        return gifProjectHandler.saveGifProject(
            projectId = projectId,
            thumbBitmap = thumbBitmap,
            outputFile = outputFile,
            filesDir = filesDir,
            videoUri = videoUri,
            startTrimMs = startTrimMs,
            endTrimMs = endTrimMs,
            speedMultiplier = speedMultiplier,
            customName = customName,
            layers = layers,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight
        )
    }

    suspend fun loadGifProject(projectId: String): Result<StickerProject?> {
        return gifProjectHandler.getProjectById(projectId)
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
        _historyState.postValue(Pair(historyIndex > 0, historyIndex < history.size - 1))
    }

    private fun createCenterFitThumbnail(source: Bitmap, maxSize: Int): Bitmap {
        return source.scale(maxSize, maxSize)
    }

    fun updateBorderConfig(
        thickness: Int? = null,
        distance: Int? = null,
        color: Int? = null
    ) {
        thickness?.let { currentBorderThickness.value = it }
        distance?.let { currentBorderDistance.value = it }
        color?.let { currentBorderColor.value = it }

        processNewBorderSticker()
    }

    private fun processNewBorderSticker() {
        borderProcessJob?.cancel()
        val currentSelectedStyle = _currentStyle.value ?: return

        val baseStyle = if (currentSelectedStyle == StickerStyle.CARTOON) {
            StickerStyle.CARTOON
        } else {
            StickerStyle.ORIGINAL
        }
        val coreImage = coreBitmaps[baseStyle] ?: return

        borderProcessJob = viewModelScope.launch {
            delay(300.milliseconds)
            _isLoading.value = true
            try {
                val thickness = currentBorderThickness.value ?: 30
                val distance = currentBorderDistance.value ?: 20
                val color = currentBorderColor.value ?: Color.WHITE

                val newBorderImage = applyBorderUseCase(
                    original = coreImage,
                    thickness = thickness,
                    distance = distance,
                    color = color
                )

                styleBitmaps[currentSelectedStyle] = newBorderImage
                _currentStyle.value = currentSelectedStyle
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
