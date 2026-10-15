package com.jetpack.stickify.data.gif

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.min

/**
 * Chuẩn bị ảnh gửi Veo.
 * LỖI CŨ: Bitmap.compress(JPEG) lên ảnh nền trong suốt => nền biến thành MÀU ĐEN.
 * Ở đây: dán sticker lên nền TRẮNG, khung dọc 9:16 (khớp VIDEO_ASPECT_RATIO của server) rồi mới nén JPEG.
 */
class AiImageEncoder @Inject constructor() {

    fun encode(source: Bitmap): ByteArray {
        val out = Bitmap.createBitmap(TARGET_W, TARGET_H, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(out)
            canvas.drawColor(Color.WHITE)
            val scale = min(TARGET_W * FILL / source.width, TARGET_H * FILL / source.height)
            val w = source.width * scale
            val h = source.height * scale
            val dst = RectF((TARGET_W - w) / 2f, (TARGET_H - h) / 2f, (TARGET_W + w) / 2f, (TARGET_H + h) / 2f)
            canvas.drawBitmap(source, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

            val bos = ByteArrayOutputStream()
            out.compress(Bitmap.CompressFormat.JPEG, 80, bos)
            return bos.toByteArray()
        } finally {
            out.recycle()
        }
    }

    private companion object {
        const val TARGET_W = 720
        const val TARGET_H = 1280
        const val FILL = 0.8f
    }
}