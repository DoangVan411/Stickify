package com.jetpack.stickify.presentation.ui.edit_sticker.utils

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.jetpack.stickify.R

class PromptErrorDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "PromptErrorDialog"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_MESSAGE = "arg_message"

        fun newInstance(title: String, message: String): PromptErrorDialogFragment {
            val args = Bundle().apply {
                putString(ARG_TITLE, title)
                putString(ARG_MESSAGE, message)
            }
            return PromptErrorDialogFragment().apply { arguments = args }
        }
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
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        return inflater.inflate(R.layout.dialog_prompt_error, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTitle = view.findViewById<TextView>(R.id.tvErrorTitle)
        val tvMessage = view.findViewById<TextView>(R.id.tvErrorMessage)
        val btnOk = view.findViewById<Button>(R.id.btnErrorOk)

        tvTitle.text = arguments?.getString(ARG_TITLE) ?: "Lỗi"
        tvMessage.text = arguments?.getString(ARG_MESSAGE) ?: "Đã có lỗi xảy ra."

        btnOk.setOnClickListener {
            dismissAllowingStateLoss() // Đóng Dialog an toàn
        }
    }
}