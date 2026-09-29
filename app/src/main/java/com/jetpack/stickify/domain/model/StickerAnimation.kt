package com.jetpack.stickify.domain.model

/**
 * Các loại hiệu ứng animation có thể áp dụng lên sticker.
 * Mỗi hiệu ứng sẽ tạo ra chuyển động khác nhau khi xuất GIF.
 *
 * @param label Tên hiển thị tiếng Việt
 * @param totalFrames Số frame trong 1 vòng lặp animation
 * @param frameDelayMs Thời gian mỗi frame (ms) khi xuất GIF
 */
enum class StickerAnimationType(
    val label: String,
    val totalFrames: Int,
    val frameDelayMs: Int
) {
    /** Không có hiệu ứng – xuất PNG tĩnh */
    NONE("Không", 1, 0),

    /** Nhún lên xuống */
    BOUNCE("Nhún nhảy", 20, 50),

    /** Rung ngang qua lại */
    SHAKE("Rung lắc", 16, 40),

    /** Xoay tròn 360° */
    SPIN("Xoay tròn", 24, 42),

    /** Phóng to / thu nhỏ nhịp nhàng */
    PULSE("Co giãn", 20, 50),

    /** Lắc lư nghiêng trái phải */
    WOBBLE("Lắc lư", 20, 50);

    val isAnimated: Boolean get() = this != NONE
}
