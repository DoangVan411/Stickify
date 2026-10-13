package com.jetpack.stickify.presentation.ui.edit_gif

import android.graphics.Bitmap
import com.jetpack.stickify.domain.model.EditHistory
import com.jetpack.stickify.domain.model.ProjectContent
import com.jetpack.stickify.domain.model.ProjectOrigin
import com.jetpack.stickify.domain.model.ProjectType
import com.jetpack.stickify.domain.model.StickerProject
import com.jetpack.stickify.domain.usecase.SaveProjectUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

import com.jetpack.stickify.domain.model.Layer
import com.jetpack.stickify.domain.usecase.GetProjectByIdUseCase
import kotlinx.coroutines.flow.firstOrNull

/**
 * Handler xử lý logic lưu trữ file thumbnail và lưu StickerProject vào Room Database.
 * Được gọi thông qua StickerSharedViewModel để đảm bảo tuân thủ Clean Architecture / MVVM.
 */
@Singleton
class GifProjectHandler @Inject constructor(
    private val saveProjectUseCase: SaveProjectUseCase,
    private val getProjectByIdUseCase: GetProjectByIdUseCase
) {

    /**
     * Lấy dự án theo ID để phục hồi dữ liệu khi chỉnh sửa lại.
     */
    suspend fun getProjectById(projectId: String): Result<StickerProject?> = withContext(Dispatchers.IO) {
        try {
            val project = getProjectByIdUseCase.execute(projectId).firstOrNull()
            Result.success(project)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lưu ảnh thumbnail và lưu entity StickerProject vào database qua SaveProjectUseCase.
     */
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
    ): Result<StickerProject> = withContext(Dispatchers.IO) {
        try {
            // 1. Lưu thumbnail PNG
            val thumbnailsDir = File(filesDir, "thumbnails").apply { if (!exists()) mkdirs() }
            val thumbFile = File(thumbnailsDir, "thumb_$projectId.png")
            if (thumbBitmap != null) {
                FileOutputStream(thumbFile).use { out ->
                    thumbBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
            }

            // 2. Tạo đối tượng StickerProject kèm playback metadata & layers
            val now = System.currentTimeMillis()
            val projectName = customName ?: ("GIF " + SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(now)))
            val playback = com.jetpack.stickify.domain.model.Playback(
                speed = when (speedMultiplier.toInt()) {
                    2 -> com.jetpack.stickify.domain.model.PlaybackSpeed.X2
                    4 -> com.jetpack.stickify.domain.model.PlaybackSpeed.X4
                    6 -> com.jetpack.stickify.domain.model.PlaybackSpeed.X6
                    else -> com.jetpack.stickify.domain.model.PlaybackSpeed.X1
                },
                loop = true,
                startTrimMs = startTrimMs,
                endTrimMs = endTrimMs,
                videoUri = videoUri
            )
            val canvas = if (canvasWidth > 0 && canvasHeight > 0) {
                com.jetpack.stickify.domain.model.CanvasSpec(canvasWidth, canvasHeight)
            } else {
                com.jetpack.stickify.domain.model.CanvasSpec()
            }
            val project = StickerProject(
                id = projectId,
                name = projectName,
                type = ProjectType.GIF,
                origin = ProjectOrigin.CREATED,
                thumbnailPath = thumbFile.absolutePath,
                exportedPath = outputFile.absolutePath,
                createdAt = now,
                updatedAt = now,
                content = ProjectContent(canvas = canvas, playback = playback, layers = layers),
                history = EditHistory(),
                isBookmarked = false
            )

            // 3. Lưu vào database qua UseCase
            val saveResult = saveProjectUseCase.save(project)
            if (saveResult.isSuccess) {
                Result.success(project)
            } else {
                Result.failure(saveResult.exceptionOrNull() ?: Exception("Lỗi khi lưu dự án GIF"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
