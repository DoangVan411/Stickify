package com.jetpack.stickify.domain.repository

import android.graphics.Bitmap
import com.jetpack.stickify.domain.model.ProcessedStickers


interface StickerRepository {
    // Nhận vào URI dưới dạng String để giảm phụ thuộc vào framework
    suspend fun processStickers(imageUriString: String): ProcessedStickers

    suspend fun saveSticker(bitmap: Bitmap): String // Trả về Uri String đã lưu

    // Hàm mới xử lý riêng cho việc vẽ lại viền tuỳ chỉnh
    suspend fun applyCustomBorder(
        original: Bitmap,
        thickness: Int,
        distance: Int,
        color: Int
    ): Bitmap
}