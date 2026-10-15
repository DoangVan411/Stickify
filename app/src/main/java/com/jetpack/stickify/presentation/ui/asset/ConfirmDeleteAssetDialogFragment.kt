package com.jetpack.stickify.presentation.ui.asset

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.jetpack.stickify.R

class ConfirmDeleteAssetDialogFragment : DialogFragment() {

    var onConfirmDelete: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_confirm_delete_asset, container, false)
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

        val titleName = arguments?.getString(ARG_TITLE).orEmpty()
        if (titleName.isNotEmpty()) {
            view.findViewById<TextView>(R.id.tvConfirmMessage)?.text =
                "Bạn có chắc chắn muốn xóa tài nguyên \"$titleName\" không?"
        }

        view.findViewById<View>(R.id.btnCancelDelete).setOnClickListener {
            dismiss()
        }

        view.findViewById<View>(R.id.btnConfirmDelete).setOnClickListener {
            dismiss()
            onConfirmDelete?.invoke()
        }
    }

    companion object {
        const val TAG = "ConfirmDeleteAssetDialogFragment"
        private const val ARG_TITLE = "arg_title"

        fun newInstance(assetTitle: String): ConfirmDeleteAssetDialogFragment {
            return ConfirmDeleteAssetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, assetTitle)
                }
            }
        }
    }
}
