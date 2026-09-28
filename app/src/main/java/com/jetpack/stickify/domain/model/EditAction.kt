package com.jetpack.stickify.domain.model

/**
 * Các hành động chỉnh sửa trên Project.
 * Map từ class diagram I EditAction.
 */
sealed interface EditAction {
    fun inverse(): EditAction
}

data class AddLayerAction(
    val layer: Layer,
    val index: Int
) : EditAction {
    override fun inverse(): EditAction = RemoveLayerAction(layer, index)
}

data class RemoveLayerAction(
    val layer: Layer,
    val index: Int
) : EditAction {
    override fun inverse(): EditAction = AddLayerAction(layer, index)
}

data class UpdateLayerAction(
    val before: Layer,
    val after: Layer
) : EditAction {
    override fun inverse(): EditAction = UpdateLayerAction(before, after)
}

data class ReorderLayerAction(
    val layerId: String,
    val from: Int,
    val to: Int
) : EditAction {
    override fun inverse(): EditAction = ReorderLayerAction(layerId, to, from)
}

data class ChangeBorderAction(
    val before: BorderStyle?,
    val after: BorderStyle?
) : EditAction {
    override fun inverse(): EditAction = ChangeBorderAction(after, before)
}

data class ChangeSpeedAction(
    val before: PlaybackSpeed,
    val after: PlaybackSpeed
) : EditAction {
    override fun inverse(): EditAction = ChangeSpeedAction(after, before)
}
