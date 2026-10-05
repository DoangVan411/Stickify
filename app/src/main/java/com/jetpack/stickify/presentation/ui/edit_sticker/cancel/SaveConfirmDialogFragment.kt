package com.jetpack.stickify.presentation.ui.edit_sticker.cancel

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.jetpack.stickify.databinding.DialogSaveConfirmBinding

class SaveConfirmDialogFragment : DialogFragment() {

    private var _binding: DialogSaveConfirmBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSaveConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // Ép Window của Dialog khớp width với màn hình tại đây
        dialog?.window?.apply {
            // Lấy chiều rộng màn hình thiết bị
            val displayMetrics = requireContext().resources.displayMetrics
            val width = (displayMetrics.widthPixels * 0.88).toInt() // 88% chiều rộng màn hình

            // Đặt kích thước cho Dialog Window
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Nút LƯU: Gửi Action SAVE và đóng Dialog
        binding.btnSave.setOnClickListener {
            sendResult(ACTION_SAVE)
            dismiss()
        }

        // Nút BỎ THAY ĐỔI: Gửi Action DISCARD và đóng Dialog
        binding.btnDiscard.setOnClickListener {
            sendResult(ACTION_DISCARD)
            dismiss()
        }

    }

    private fun sendResult(action: String) {
        setFragmentResult(
            REQUEST_KEY,
            bundleOf(EXTRA_ACTION to action)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Tránh Memory Leak với ViewBinding
    }

    companion object {
        const val TAG = "SaveConfirmDialogFragment"
        const val REQUEST_KEY = "save_confirm_request_key"
        const val EXTRA_ACTION = "extra_action"

        const val ACTION_SAVE = "ACTION_SAVE"
        const val ACTION_DISCARD = "ACTION_DISCARD"
        const val ACTION_CANCEL = "ACTION_CANCEL"

        fun newInstance(): SaveConfirmDialogFragment {
            return SaveConfirmDialogFragment()
        }
    }
}