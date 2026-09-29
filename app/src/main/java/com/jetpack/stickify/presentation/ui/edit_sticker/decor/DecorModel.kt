package com.jetpack.stickify.presentation.ui.edit_sticker.decor

import android.graphics.Bitmap
import java.util.UUID

/**
 * Model đại diện cho một chi tiết trang trí (Decor / Label) trên sticker canvas.
 */
data class DecorModel(
    val id: String = UUID.randomUUID().toString(),
    val resId: Int = 0,
    val customBitmap: Bitmap? = null,
    var cx: Float = 0f,
    var cy: Float = 0f,
    var scale: Float = 1f,
    var rotationDeg: Float = 0f,
    var flipX: Boolean = false
) {
    fun deepCopy(): DecorModel {
        return copy(
            id = id,
            resId = resId,
            customBitmap = customBitmap,
            cx = cx,
            cy = cy,
            scale = scale,
            rotationDeg = rotationDeg,
            flipX = flipX
        )
    }
}

data class DecorItem(
    val id: String,
    val name: String,
    val resId: Int,
    val isImportAction: Boolean = false
)
