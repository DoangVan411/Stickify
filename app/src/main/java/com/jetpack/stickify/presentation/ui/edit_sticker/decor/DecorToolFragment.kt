package com.jetpack.stickify.presentation.ui.edit_sticker.decor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.jetpack.stickify.R

class DecorToolFragment : Fragment() {

    private var selectedDecorIndex: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_decor_tool, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectedDecorIndex = savedInstanceState?.getInt(KEY_SELECTED_DECOR_INDEX) ?: selectedDecorIndex
        setupDecorSelection(view)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_DECOR_INDEX, selectedDecorIndex)
    }

    private fun setupDecorSelection(root: View) {
        val itemIds = intArrayOf(
            R.id.decorItem1, R.id.decorItem2, R.id.decorItem3, R.id.decorItem4, R.id.decorItem5
        )
        val frameIds = intArrayOf(
            R.id.decorSelectedFrame1,
            R.id.decorSelectedFrame2,
            R.id.decorSelectedFrame3,
            R.id.decorSelectedFrame4,
            R.id.decorSelectedFrame5
        )

        fun updateSelectedUi(index: Int) {
            if (index !in frameIds.indices) return
            selectedDecorIndex = index
            frameIds.forEachIndexed { frameIndex, frameId ->
                root.findViewById<View>(frameId).visibility =
                    if (frameIndex == selectedDecorIndex) View.VISIBLE else View.GONE
            }
        }

        itemIds.forEachIndexed { index, itemId ->
            root.findViewById<View>(itemId).setOnClickListener {
                updateSelectedUi(index)
            }
        }

        updateSelectedUi(selectedDecorIndex)
    }

    companion object {
        private const val KEY_SELECTED_DECOR_INDEX = "selected_decor_index"
    }
}
