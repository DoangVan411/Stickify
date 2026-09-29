package com.jetpack.stickify.domain.model

sealed interface EditAction {
    fun inverse(): EditAction
}

data class AddLayerAction(
    val layer: Layer,
    val index: Int
) : EditAction {
    override fun inverse(): EditAction = RemoveLayerAction(layer = layer, index = index)
}

data class RemoveLayerAction(
    val layer: Layer,
    val index: Int
) : EditAction {
    override fun inverse(): EditAction = AddLayerAction(layer = layer, index = index)
}

data class UpdateLayerAction(
    val before: Layer,
    val after: Layer
) : EditAction {
    override fun inverse(): EditAction = UpdateLayerAction(before = after, after = before)
}

data class ReorderLayerAction(
    val layerId: String,
    val from: Int,
    val to: Int
) : EditAction {
    override fun inverse(): EditAction = ReorderLayerAction(layerId = layerId, from = to, to = from)
}

data class ChangeBorderAction(
    val before: BorderStyle?,
    val after: BorderStyle?
) : EditAction {
    override fun inverse(): EditAction = ChangeBorderAction(before = after, after = before)
}

data class ChangeSpeedAction(
    val before: PlaybackSpeed,
    val after: PlaybackSpeed
) : EditAction {
    override fun inverse(): EditAction = ChangeSpeedAction(before = after, after = before)
}
