package com.jetpack.stickify.presentation.ui.home

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

class ConfirmDeleteProjectDialogFragment : DialogFragment() {

    private var projectId: String = ""
    private var projectName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectId = arguments?.getString(ARG_PROJECT_ID).orEmpty()
        projectName = arguments?.getString(ARG_PROJECT_NAME).orEmpty()
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
        view.findViewById<TextView>(R.id.tvConfirmMessage).text = "Bạn có chắc chắn muốn xóa dự án \"$projectName\" không?"

        view.findViewById<View>(R.id.btnCancelDelete).setOnClickListener {
            dismiss()
        }

        view.findViewById<View>(R.id.btnConfirmDelete).setOnClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(EXTRA_PROJECT_ID to projectId)
            )
            dismiss()
        }
    }

    companion object {
        const val TAG = "ConfirmDeleteProjectDialogFragment"
        const val REQUEST_KEY = "confirm_delete_project_request_key"
        const val EXTRA_PROJECT_ID = "extra_project_id"

        private const val ARG_PROJECT_ID = "arg_project_id"
        private const val ARG_PROJECT_NAME = "arg_project_name"

        fun newInstance(projectId: String, projectName: String): ConfirmDeleteProjectDialogFragment {
            return ConfirmDeleteProjectDialogFragment().apply {
                arguments = bundleOf(
                    ARG_PROJECT_ID to projectId,
                    ARG_PROJECT_NAME to projectName
                )
            }
        }
    }
}
