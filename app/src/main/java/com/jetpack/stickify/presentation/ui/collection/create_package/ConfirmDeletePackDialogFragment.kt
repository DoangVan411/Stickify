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
import androidx.fragment.app.setFragmentResult
import com.jetpack.stickify.R

class ConfirmDeletePackDialogFragment : DialogFragment() {

    private var packId: String = ""
    private var packName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packId = arguments?.getString(ARG_PACK_ID).orEmpty()
        packName = arguments?.getString(ARG_PACK_NAME).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_confirm_delete, container, false)
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
        view.findViewById<TextView>(R.id.tvConfirmMessage).text = "Bạn có chắc chắn muốn xóa bộ sticker \"$packName\" không?"

        view.findViewById<View>(R.id.btnCancelDelete).setOnClickListener {
            dismiss()
        }

        view.findViewById<View>(R.id.btnConfirmDelete).setOnClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(EXTRA_PACK_ID to packId)
            )
            dismiss()
        }
    }

    companion object {
        const val TAG = "ConfirmDeletePackDialogFragment"
        const val REQUEST_KEY = "confirm_delete_pack_request_key"
        const val EXTRA_PACK_ID = "extra_pack_id"

        private const val ARG_PACK_ID = "arg_pack_id"
        private const val ARG_PACK_NAME = "arg_pack_name"

        fun newInstance(packId: String, packName: String): ConfirmDeletePackDialogFragment {
            return ConfirmDeletePackDialogFragment().apply {
                arguments = bundleOf(
                    ARG_PACK_ID to packId,
                    ARG_PACK_NAME to packName
                )
            }
        }
    }
}