package com.jetpack.stickify.presentation.ui.home.adapter

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * ItemDecoration để tạo khoảng cách đồng đều giữa các item trong danh sách cuộn ngang.
 */
class HorizontalSpaceItemDecoration(private val spacePx: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position != RecyclerView.NO_POSITION && position < state.itemCount - 1) {
            outRect.right = spacePx
        } else {
            outRect.right = 0
        }
    }
}
