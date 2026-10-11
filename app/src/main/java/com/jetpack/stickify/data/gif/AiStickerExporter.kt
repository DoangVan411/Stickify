package com.jetpack.stickify.data.gif


import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Ghi các frame đã ghép (video + chữ/decor) ra WebP động bằng AnimatedWebPEncoder có sẵn. */
class AiStickerExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun export(frames: List<Bitmap>, frameDelayMs: Int): String = withContext(Dispatchers.IO) {
        require(frames.isNotEmpty()) { "Không có frame để xuất" }
        val dir = File(context.filesDir, "ai_stickers").apply { mkdirs() }
        val file = File(dir, "ai_sticker_${System.currentTimeMillis()}.webp")
        // Lossy 80: 48 frame lossless 512x512 có thể nặng hàng chục MB
        AnimatedWebPEncoder.encode(frames, frameDelayMs, file, lossless = false, quality = 80)
        Uri.fromFile(file).toString()
    }
}