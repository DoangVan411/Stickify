package com.jetpack.stickify.domain.model
import android.graphics.Bitmap

// Chứa kết quả sau khi xử lý ảnh
data class ProcessedStickers(
    val original: Bitmap,
    val border: Bitmap,
    val cartoon: Bitmap
)