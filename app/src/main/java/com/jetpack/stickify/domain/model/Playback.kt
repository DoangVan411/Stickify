package com.jetpack.stickify.domain.model

/**
 * Cấu hình phát cho sticker động / GIF.
 * Map từ class diagram Playback và PlaybackSpeed.
 */
data class Playback(
    val speed: PlaybackSpeed = PlaybackSpeed.X1,
    val loop: Boolean = true
)

enum class PlaybackSpeed(val multiplier: Int) {
    X1(1),
    X2(2),
    X4(4),
    X6(6)
}
