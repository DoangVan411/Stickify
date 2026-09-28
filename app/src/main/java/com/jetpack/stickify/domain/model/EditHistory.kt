package com.jetpack.stickify.domain.model

/**
 * Lịch sử chỉnh sửa sticker (Undo / Redo).
 * Map từ class diagram EditHistory.
 */
data class EditHistory(
    val undo: List<EditAction> = emptyList(),
    val redo: List<EditAction> = emptyList()
) {
    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()
}
