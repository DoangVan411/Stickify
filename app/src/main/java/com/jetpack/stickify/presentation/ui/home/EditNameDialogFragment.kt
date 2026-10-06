package com.jetpack.stickify.presentation.ui.home

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.jetpack.stickify.R

class EditNameDialogFragment : DialogFragment() {

    private var projectId: String = ""
    private var oldName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectId = arguments?.getString(ARG_PROJECT_ID).orEmpty()
        oldName = arguments?.getString(ARG_OLD_NAME).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_edit_name, container, false)
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

        val etProjectName = view.findViewById<EditText>(R.id.etProjectName)
        val btnCancel = view.findViewById<View>(R.id.btnCancel)
        val btnConfirm = view.findViewById<View>(R.id.btnConfirm)

        // Điền tên cũ vào EditText
        etProjectName.setText(oldName)
        etProjectName.setSelection(oldName.length)

        btnCancel.setOnClickListener {
            dismiss()
        }

        btnConfirm.setOnClickListener {
            val newName = etProjectName.text?.toString()?.trim().orEmpty()
            if (newName.isBlank()) {
                Toast.makeText(requireContext(), "Tên dự án không được để trống", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    EXTRA_PROJECT_ID to projectId,
                    EXTRA_NEW_NAME to newName
                )
            )
            dismiss()
        }
    }

    companion object {
        const val TAG = "EditNameDialogFragment"
        const val REQUEST_KEY = "edit_name_request_key"
        const val EXTRA_PROJECT_ID = "extra_project_id"
        const val EXTRA_NEW_NAME = "extra_new_name"

        private const val ARG_PROJECT_ID = "arg_project_id"
        private const val ARG_OLD_NAME = "arg_old_name"

        fun newInstance(projectId: String, oldName: String): EditNameDialogFragment {
            return EditNameDialogFragment().apply {
                arguments = bundleOf(
                    ARG_PROJECT_ID to projectId,
                    ARG_OLD_NAME to oldName
                )
            }
        }
    }
}
