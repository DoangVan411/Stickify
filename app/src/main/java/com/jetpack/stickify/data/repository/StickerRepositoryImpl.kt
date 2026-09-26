package com.jetpack.stickify.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.jetpack.stickify.data.processor.StickerStyleProcessor
import com.jetpack.stickify.domain.model.ProcessedStickers
import com.jetpack.stickify.domain.repository.StickerRepository
import com.jetpack.stickify.presentation.ui.cut_image.ImageUtils // Utility cũ của bạn
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StickerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context // Được inject bằng Hilt
) : StickerRepository {

    override suspend fun processStickers(imageUriString: String): ProcessedStickers = withContext(Dispatchers.IO) {
        val uri = Uri.parse(imageUriString)
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }

        val original = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IllegalArgumentException("Không đọc được ảnh")

        // Xử lý bằng thuật toán của bạn
        val border = StickerStyleProcessor.addPerfectStickerBorderWithShadow(original, Color.WHITE)
        val cartoon = StickerStyleProcessor.cartoonify(original)

        return@withContext ProcessedStickers(original, border, cartoon)
    }

    override suspend fun saveSticker(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val uri = ImageUtils.saveBitmapAndGetUri(
            context,
            bitmap,
            "sticker_${System.currentTimeMillis()}.png"
        )
        return@withContext uri.toString()
    }
    override suspend fun applyCustomBorder(
        original: Bitmap,
        thickness: Int,
        distance: Int,
        color: Int
    ): Bitmap = withContext(Dispatchers.Default) {
        return@withContext StickerStyleProcessor.addCustomStickerBorder(
            source = original,
            borderColor = color,
            borderThickness = thickness.toFloat(),
            distancePadding = distance.toFloat()
        )
    }
}