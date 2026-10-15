package com.jetpack.stickify.data.repository

import android.content.Context
import android.net.Uri
import com.jetpack.stickify.data.source.local.dao.KeyboardStickerDao
import com.jetpack.stickify.domain.model.KeyboardStickerEntity
import com.jetpack.stickify.domain.repository.KeyboardStickerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyboardStickerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: KeyboardStickerDao
) : KeyboardStickerRepository {

    override fun observeAll(): Flow<List<KeyboardStickerEntity>> = dao.observeAll()

    override fun observeRecent(): Flow<List<KeyboardStickerEntity>> = dao.observeRecent()

    override suspend fun addFromUri(uriString: String, animated: Boolean, sourceId: String?): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val isGif = uriString.endsWith(".gif", ignoreCase = true) ||
                    uri.path?.endsWith(".gif", ignoreCase = true) == true ||
                    (runCatching { context.contentResolver.getType(uri) }.getOrNull() == "image/gif")

            val mimeType = when {
                isGif -> "image/gif"
                animated -> "image/webp"
                else -> "image/png"
            }
            val extension = when {
                isGif -> "gif"
                animated -> "webp"
                else -> "png"
            }
            val fileName = "keyboard_sticker_${UUID.randomUUID()}.$extension"

            val targetFile = File(context.filesDir, "keyboard_stickers").apply {
                if (!exists()) mkdirs()
            }.let { File(it, fileName) }

            val finalAnimated = animated || isGif

            val inputStream = try {
                when {
                    uri.scheme == null || uri.scheme == "file" -> {
                        val filePath = uri.path ?: uriString
                        val file = File(filePath)
                        if (file.exists()) file.inputStream() else context.contentResolver.openInputStream(uri)
                    }
                    uri.scheme == "content" -> {
                        try {
                            context.contentResolver.openInputStream(uri)
                        } catch (sec: Exception) {
                            val local = resolveLocalFile(uri)
                            if (local != null && local.exists()) local.inputStream() else throw sec
                        }
                    }
                    else -> context.contentResolver.openInputStream(uri)
                }
            } catch (e: Exception) {
                val local = resolveLocalFile(uri)
                if (local != null && local.exists()) {
                    local.inputStream()
                } else {
                    android.util.Log.e("KeyboardStickerRepo", "Failed to open stream for $uriString: ${e.message}", e)
                    null
                }
            }

            inputStream?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Cannot open input stream from uri: $uriString"))

            val entity = KeyboardStickerEntity(
                sourceId = sourceId,
                fileName = targetFile.absolutePath,
                mimeType = mimeType,
                isAnimated = finalAnimated,
                createdAt = System.currentTimeMillis()
            )

            val id = dao.insert(entity)
            Result.success(id)
        } catch (e: Exception) {
            android.util.Log.e("KeyboardStickerRepo", "Error adding sticker: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun resolveLocalFile(uri: Uri): File? {
        val path = uri.path ?: return null
        val filename = File(path).name
        val candidates = listOf(
            File(context.filesDir, path.removePrefix("/")),
            File(context.filesDir, "stickers/$filename"),
            File(context.filesDir, "keyboard_stickers/$filename"),
            File(context.cacheDir, path.removePrefix("/")),
            File(context.cacheDir, "cutouts/$filename"),
            File(context.cacheDir, filename),
            File(path)
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
    }

    override suspend fun markUsed(id: Long) = withContext(Dispatchers.IO) {
        dao.markUsed(id, System.currentTimeMillis())
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val entity = dao.getById(id)
        if (entity != null) {
            try {
                File(entity.fileName).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            dao.delete(id)
        }
    }
}
