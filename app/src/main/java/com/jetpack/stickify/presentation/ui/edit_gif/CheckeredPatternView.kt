package com.jetpack.stickify.presentation.ui.edit_gif

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * View vẽ nền caro (checkered pattern) làm phông nền cho khung chỉnh sửa ảnh/GIF/video.
 */
class CheckeredPatternView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val CHECKER_TILE_DP = 10f
    }

    private val checkerPaint: Paint by lazy { buildCheckerPaint() }

    private fun buildCheckerPaint(): Paint {
        val tilePx = (CHECKER_TILE_DP * resources.displayMetrics.density).toInt().coerceAtLeast(4)
        val tile = Bitmap.createBitmap(tilePx * 2, tilePx * 2, Bitmap.Config.ARGB_8888)
        val tileCanvas = Canvas(tile)
        val light = Paint().apply { color = Color.parseColor("#FFFFFF") }
        val dark = Paint().apply { color = Color.parseColor("#E4E4E4") }
        tileCanvas.drawRect(0f, 0f, tilePx.toFloat(), tilePx.toFloat(), light)
        tileCanvas.drawRect(tilePx.toFloat(), 0f, (tilePx * 2).toFloat(), tilePx.toFloat(), dark)
        tileCanvas.drawRect(0f, tilePx.toFloat(), tilePx.toFloat(), (tilePx * 2).toFloat(), dark)
        tileCanvas.drawRect(tilePx.toFloat(), tilePx.toFloat(), (tilePx * 2).toFloat(), (tilePx * 2).toFloat(), light)

        return Paint().apply {
            shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), checkerPaint)
    }
}
