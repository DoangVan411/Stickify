package com.jetpack.stickify.presentation.ui.collection.create_package

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.jetpack.stickify.R

class CustomMessageDialogFragment : DialogFragment() {

    private var titleText: String = ""
    private var messageText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        titleText = arguments?.getString(ARG_TITLE) ?: "Thông báo"
        messageText = arguments?.getString(ARG_MESSAGE) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_custom_message, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            val displayMetrics = requireContext().resources.displayMetrics
            val width = (displayMetrics.widthPixels * 0.88).toInt()
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.tvDialogTitle).text = titleText
        view.findViewById<TextView>(R.id.tvDialogMessage).text = messageText
        view.findViewById<View>(R.id.btnDialogOk).setOnClickListener {
            dismiss()
        }
    }

    companion object {
        const val TAG = "CustomMessageDialogFragment"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_MESSAGE = "arg_message"

        fun newInstance(title: String, message: String): CustomMessageDialogFragment {
            return CustomMessageDialogFragment().apply {
                arguments = bundleOf(
                    ARG_TITLE to title,
                    ARG_MESSAGE to message
                )
            }
        }
    }
}