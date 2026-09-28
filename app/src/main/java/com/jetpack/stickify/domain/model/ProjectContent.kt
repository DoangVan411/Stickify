package com.jetpack.stickify.domain.model

/**
 * Nội dung của một project (canvas, layers, border, playback).
 * Map từ class diagram ProjectContent.
 */
data class ProjectContent(
    val canvas: CanvasSpec = CanvasSpec(),
    val layers: List<Layer> = emptyList(),
    val border: BorderStyle? = null,
    val playback: Playback? = null
) {
    fun apply(action: EditAction): ProjectContent {
        return when (action) {
            is AddLayerAction -> {
                val newLayers = layers.toMutableList().apply {
                    val targetIndex = action.index.coerceIn(0, size)
                    add(targetIndex, action.layer)
                }
                copy(layers = newLayers)
            }
            is RemoveLayerAction -> {
                val newLayers = layers.filterNot { it.id == action.layer.id }
                copy(layers = newLayers)
            }
            is UpdateLayerAction -> {
                val newLayers = layers.map { layer ->
                    if (layer.id == action.before.id) action.after else layer
                }
                copy(layers = newLayers)
            }
            is ReorderLayerAction -> {
                val layerToMove = layers.find { it.id == action.layerId } ?: return this
                val newLayers = layers.filterNot { it.id == action.layerId }.toMutableList()
                val targetIndex = action.to.coerceIn(0, newLayers.size)
                newLayers.add(targetIndex, layerToMove)
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
