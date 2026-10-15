package com.jetpack.stickify.presentation.ui.edit_sticker

import android.graphics.Bitmap
import android.view.View
import androidx.core.view.drawToBitmap

/**
 * Extension function để chụp View hiện tại thành Bitmap ARGB_8888.
 */
fun View.captureToBitmap(): Bitmap {
    return this.drawToBitmap(Bitmap.Config.ARGB_8888)
}
