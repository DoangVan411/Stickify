package com.jetpack.stickify.domain.model

data class Transform(
    val cx: Float = 0f,
    val cy: Float = 0f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
    val flipX: Boolean = false,
    val opacity: Float = 1f
)
