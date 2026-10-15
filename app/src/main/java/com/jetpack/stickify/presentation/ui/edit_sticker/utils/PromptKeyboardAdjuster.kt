package com.jetpack.stickify.presentation.ui.edit_sticker.utils

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager

class PromptKeyboardAdjuster(
    private val rootView: View,
    private val targetView: View,
    private val focusView: View? = null,
    private val bottomMarginDp: Int = 10
) {
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    fun attach() {
        val marginPx = (bottomMarginDp * rootView.resources.displayMetrics.density).toInt()

        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)

            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            val isKeyboardVisible = keypadHeight > screenHeight * 0.15

            if (isKeyboardVisible) {
                val location = IntArray(2)
                targetView.getLocationOnScreen(location)

                val originalBottom = location[1] + targetView.height - targetView.translationY.toInt()
                val keyboardTop = rect.bottom
                val targetBottom = keyboardTop - marginPx

                if (originalBottom > targetBottom) {
                    val neededTranslationY = (targetBottom - originalBottom).toFloat()
                    targetView.animate()
                        .translationY(neededTranslationY)
                        .setDuration(100)
                        .start()
                } else if (targetView.translationY != 0f) {
                    targetView.animate().translationY(0f).setDuration(100).start()
                }
            } else {
                if (targetView.translationY != 0f) {
                    targetView.animate()
                        .translationY(0f)
                        .setDuration(150)
                        .start()
                }
            }
        }

        rootView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
        setupClickOutsideToHideKeyboard()
    }

    private fun setupClickOutsideToHideKeyboard() {
        if (focusView == null) return
        rootView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && focusView.hasFocus()) {
                focusView.clearFocus()
                val imm = rootView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(focusView.windowToken, 0)
            }
            false
        }
    }

    fun detach() {
        globalLayoutListener?.let { listener ->
            rootView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
        globalLayoutListener = null
    }
}