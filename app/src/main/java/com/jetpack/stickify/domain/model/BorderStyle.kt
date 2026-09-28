package com.jetpack.stickify.domain.model

/**
 * Kiểu đường viền cho Sticker.
 * Map từ class diagram BorderStyle.
 */
data class BorderStyle(
    val thickness: Float = 0f,
    val spacing: Float = 0f,
    val colorArgb: Int = 0
)
