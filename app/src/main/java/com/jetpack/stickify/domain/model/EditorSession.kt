package com.jetpack.stickify.domain.model

/**
 * Quản lý phiên làm việc chỉnh sửa Sticker.
 * Map từ class diagram EditorSession.
 */
data class EditorSession(
    var content: ProjectContent,
    var history: EditHistory = EditHistory()
) {
    fun perform(action: EditAction) {
        content = content.apply(action)
        history = history.copy(
            undo = history.undo + action,
            redo = emptyList()
        )
    }

    fun undo() {
        if (!history.canUndo) return
        val lastAction = history.undo.last()
        val inverseAction = lastAction.inverse()
        content = content.apply(inverseAction)
        history = history.copy(
            undo = history.undo.dropLast(1),
            redo = history.redo + lastAction
        )
    }

    fun redo() {
        if (!history.canRedo) return
        val actionToRedo = history.redo.last()
        content = content.apply(actionToRedo)
        history = history.copy(
            undo = history.undo + actionToRedo,
            redo = history.redo.dropLast(1)
        )
    }
}
