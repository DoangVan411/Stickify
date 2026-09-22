package com.jetpack.stickify.presentation.ui.gallery


import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val durationMs: Long, // chỉ có ý nghĩa nếu isVideo = true
    val dateAddedSeconds: Long
)