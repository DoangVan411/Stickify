package com.jetpack.stickify.presentation.ui.edit_sticker.decor

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.jetpack.stickify.R

class MaxDecorLimitDialogFragment : DialogFragment() {

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
        view.findViewById<TextView>(R.id.tvDialogTitle).text = "Giới hạn trang trí"
        view.findViewById<TextView>(R.id.tvDialogMessage).text =
            "Bạn đã đạt giới hạn trang trí tối đa để đảm bảo hiệu suất và bộ nhớ của ứng dụng hoạt động ổn định. Không thể vẽ hoặc thêm trang trí mới."
        view.findViewById<View>(R.id.btnDialogOk).setOnClickListener {
            dismiss()
        }
    }

    companion object {
        const val TAG = "MaxDecorLimitDialogFragment"

        fun newInstance(): MaxDecorLimitDialogFragment {
            return MaxDecorLimitDialogFragment()
        }
    }
}
