package com.jetpack.stickify.presentation.ui.edit_sticker.text

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.jetpack.stickify.R

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [TextToolFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class TextToolFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private var selectedDecorIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_text_tool, container, false)
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
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment TextToolFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            TextToolFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}