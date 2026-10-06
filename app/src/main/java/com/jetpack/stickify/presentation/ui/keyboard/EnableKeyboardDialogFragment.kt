package com.jetpack.stickify.presentation.ui.keyboard

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.jetpack.stickify.R

class EnableKeyboardDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_enable_keyboard, container, false)
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

        view.findViewById<View>(R.id.btnLater).setOnClickListener {
            dismiss()
        }

        view.findViewById<View>(R.id.btnOpenSettings).setOnClickListener {
            dismiss()
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        }
    }

    companion object {
        const val TAG = "EnableKeyboardDialogFragment"

        fun newInstance(): EnableKeyboardDialogFragment {
            return EnableKeyboardDialogFragment()
        }
    }
}
