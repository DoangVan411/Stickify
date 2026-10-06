package com.jetpack.stickify.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.jetpack.stickify.data.gif.AnimatedWebPEncoder
import com.jetpack.stickify.data.gif.StickerAnimationRenderer
import com.jetpack.stickify.data.processor.StickerStyleProcessor
import com.jetpack.stickify.domain.model.ProcessedStickers
import com.jetpack.stickify.domain.model.StickerAnimationType
import com.jetpack.stickify.domain.repository.StickerRepository
import com.jetpack.stickify.presentation.ui.cut_image.ImageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StickerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : StickerRepository {

    override suspend fun processStickers(imageUriString: String): ProcessedStickers = withContext(Dispatchers.IO) {
        val uri = Uri.parse(imageUriString)
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }

        val original = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IllegalArgumentException("Không đọc được ảnh")

        val border = StickerStyleProcessor.addPerfectStickerBorderWithShadow(original, Color.WHITE)
        val cartoon = StickerStyleProcessor.cartoonify(original)

        return@withContext ProcessedStickers(original, border, cartoon)
    }

    override suspend fun saveSticker(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val transparentBitmap = ImageUtils.makeBlackBackgroundTransparent(bitmap)
        val uri = ImageUtils.saveBitmapAndGetUri(
            context,
            transparentBitmap,
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

    override suspend fun saveAnimatedSticker(
        bitmap: Bitmap,
        animationType: StickerAnimationType
    ): String = withContext(Dispatchers.IO) {
        val transparentBitmap = ImageUtils.makeBlackBackgroundTransparent(bitmap)
        val frames = StickerAnimationRenderer.renderFrames(transparentBitmap, animationType)
        val file = File(context.cacheDir, "sticker_${System.currentTimeMillis()}.webp")
        AnimatedWebPEncoder.encode(frames, animationType.frameDelayMs, file)
        return@withContext Uri.fromFile(file).toString()
    }
}
