package com.jetpack.stickify.domain.model

class EditorSession(
    var content: ProjectContent = ProjectContent(),
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
        val action = history.undo.last()
        content = content.apply(action.inverse())
        history = history.copy(
            undo = history.undo.dropLast(1),
            redo = history.redo + action
        )
    }

    fun redo() {
        if (!history.canRedo) return
        val action = history.redo.last()
        content = content.apply(action)
        history = history.copy(
            undo = history.undo + action,
            redo = history.redo.dropLast(1)
        )
    }
}
