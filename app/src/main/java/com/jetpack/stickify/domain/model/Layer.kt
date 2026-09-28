package com.jetpack.stickify.domain.model

/**
 * Interface cho các Layer trong Sticker.
 * Map từ class diagram I Layer.
 */
sealed interface Layer {
    val id: String
    val transform: Transform
    val visible: Boolean
}

data class SubjectLayer(
    override val id: String,
    override val transform: Transform = Transform(),
    override val visible: Boolean = true,
    val source: AssetRef,
    val mediaKind: MediaKind,
    val cutoutPath: String? = null,
    val style: SubjectStyle = SubjectStyle.ORIGINAL,
    val styledPath: String? = null,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val isTemplatePlaceholder: Boolean = false
) : Layer

data class DecorationLayer(
    override val id: String,
    override val transform: Transform = Transform(),
    override val visible: Boolean = true,
    val asset: AssetRef,
    val category: DecorationCategory
) : Layer

data class EffectLayer(
    override val id: String,
    override val transform: Transform = Transform(),
    override val visible: Boolean = true,
    val effectId: String,
    val params: Map<String, Float> = emptyMap(),
    val targetLayerId: String,
    val startMs: Long = 0L,
    val durationMs: Long = 0L
) : Layer

data class TextLayer(
    override val id: String,
    override val transform: Transform = Transform(),
    override val visible: Boolean = true,
    val content: String,
    val fontId: String? = null,
    val fontSizeRatio: Float = 1f,
    val colorArgb: Int = 0,
    val strokeColorArgb: Int = 0,
    val strokeWidthRatio: Float = 0f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val align: TextAlign = TextAlign.CENTER,
    val boxWidthRatio: Float = 1f
) : Layer
