package com.jetpack.stickify.domain.model

enum class PlaybackSpeed(val multiplier: Int) {
    X1(1),
    X2(2),
    X4(4),
    X6(6)
}

data class Playback(
    val speed: PlaybackSpeed = PlaybackSpeed.X1,
    val loop: Boolean = true,
    val animationType: StickerAnimationType = StickerAnimationType.NONE,
    val startTrimMs: Long = 0L,
    val endTrimMs: Long = 8000L,
    val videoUri: String? = null
)
