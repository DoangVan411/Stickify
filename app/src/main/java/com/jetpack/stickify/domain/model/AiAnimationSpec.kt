package com.jetpack.stickify.domain.model

/**
 * Thông tin lưu trữ animation AI trong ProjectContent (JSON).
 *
 * @param relativeDir Đường dẫn tương đối lưu các frame PNG (vd: "projects/<id>/ai_animations/anim_0")
 * @param frameCount Số lượng frame ảnh
 * @param frameDelayMs Thời gian trễ giữa các frame (ms)
 * @param spanBefore Undo depth của editViewModel trước khi AI áp dụng
 * @param spanAfter Undo depth của editViewModel sau khi AI áp dụng
 */
data class AiAnimationSpec(
    val relativeDir: String,
    val frameCount: Int,
    val frameDelayMs: Int,
    val spanBefore: Int,
    val spanAfter: Int
)
