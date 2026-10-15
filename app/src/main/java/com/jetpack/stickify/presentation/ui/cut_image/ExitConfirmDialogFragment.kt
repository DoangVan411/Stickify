package com.jetpack.stickify.presentation.ui.cut_image

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.jetpack.stickify.R

class ExitConfirmDialogFragment : DialogFragment() {

    var onConfirmExit: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_confirm_exit, container, false)
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

        view.findViewById<View>(R.id.btnCancelExit).setOnClickListener {
            dismiss()
        }

        view.findViewById<View>(R.id.btnConfirmExit).setOnClickListener {
            dismiss()
            onConfirmExit?.invoke()
        }
    }

    companion object {
        const val TAG = "ExitConfirmDialogFragment"

        fun newInstance(onExit: () -> Unit): ExitConfirmDialogFragment {
            return ExitConfirmDialogFragment().apply {
                onConfirmExit = onExit
            }
        }
    }
}
