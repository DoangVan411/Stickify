package com.jetpack.stickify.domain.model

data class ProjectContent(
    val canvas: CanvasSpec = CanvasSpec(),
    val layers: List<Layer> = emptyList(),
    val border: BorderStyle? = null,
    val playback: Playback? = null
) {
    fun apply(action: EditAction): ProjectContent {
        return when (action) {
            is AddLayerAction -> {
                val newLayers = layers.toMutableList()
                val targetIndex = action.index.coerceIn(0, newLayers.size)
                newLayers.add(targetIndex, action.layer)
                copy(layers = newLayers)
            }
            is RemoveLayerAction -> {
                val newLayers = layers.filterNot { it.id == action.layer.id }
                copy(layers = newLayers)
            }
            is UpdateLayerAction -> {
                val newLayers = layers.map { if (it.id == action.before.id) action.after else it }
                copy(layers = newLayers)
            }
            is ReorderLayerAction -> {
                val newLayers = layers.toMutableList()
                val item = newLayers.firstOrNull { it.id == action.layerId }
                if (item != null) {
                    newLayers.remove(item)
                    val targetIndex = action.to.coerceIn(0, newLayers.size)
                    newLayers.add(targetIndex, item)
                }
                copy(layers = newLayers)
            }
            is ChangeBorderAction -> {
                copy(border = action.after)
            }
            is ChangeSpeedAction -> {
                val currentPlayback = playback ?: Playback()
                copy(playback = currentPlayback.copy(speed = action.after))
            }
        }
    }
}
