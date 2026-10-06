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
            val mimeType = if (animated) "image/webp" else "image/png"
            val extension = if (animated) "webp" else "png"
            val fileName = "keyboard_sticker_${UUID.randomUUID()}.$extension"

            val targetFile = File(context.filesDir, "keyboard_stickers").apply {
                if (!exists()) mkdirs()
            }.let { File(it, fileName) }

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Cannot open input stream from uri"))

            val entity = KeyboardStickerEntity(
                sourceId = sourceId,
                fileName = targetFile.absolutePath,
                mimeType = mimeType,
                isAnimated = animated,
                createdAt = System.currentTimeMillis()
            )

            val id = dao.insert(entity)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
