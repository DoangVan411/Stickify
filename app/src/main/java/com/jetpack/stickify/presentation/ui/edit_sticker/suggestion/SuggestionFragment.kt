package com.jetpack.stickify.presentation.ui.edit_sticker.suggestion

import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.jetpack.stickify.R
import com.jetpack.stickify.domain.model.StickerStyle
import com.jetpack.stickify.presentation.ui.edit_sticker.StickerSharedViewModel
import androidx.fragment.app.activityViewModels


class SuggestionFragment : Fragment() {


    // Lấy instance của SharedViewModel từ Activity cha
    // Mọi thay đổi ở đây sẽ ngay lập tức được Activity biết đến
    private val sharedViewModel: StickerSharedViewModel by activityViewModels()

    private lateinit var suggestedStylesRow: LinearLayout

    // Lưu các View cục bộ để dễ dàng đổi màu khi chọn
    private val styleItemViews = mutableMapOf<StickerStyle, View>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_suggestion, container, false)
        suggestedStylesRow = view.findViewById(R.id.suggestedStylesRow)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
    }

    private fun setupObservers() {
        // Lắng nghe: Khi ảnh đã cắt xong (xử lý ở Background xong), bắt đầu vẽ View
        sharedViewModel.imagesReady.observe(viewLifecycleOwner) { isReady ->
            if (isReady && styleItemViews.isEmpty()) {
                buildSuggestedStylesRow()
            }
        }

        // Lắng nghe: Khi Style hiện hành thay đổi (do click ở đây HOẶC do Undo/Redo ở Activity)
        sharedViewModel.currentStyle.observe(viewLifecycleOwner) { selectedStyle ->
            updateSelectedStyleUi(selectedStyle)
        }
    }

    private fun buildSuggestedStylesRow() {
        suggestedStylesRow.removeAllViews()
        styleItemViews.clear()

        val inflater = LayoutInflater.from(requireContext())

        // Duyệt qua tất cả các Style (ORIGINAL, BORDER, CARTOON)
        for (style in StickerStyle.values()) {
            // Lấy ảnh Thumbnail đã được thu nhỏ từ ViewModel
            val bitmap = sharedViewModel.styleThumbBitmaps[style] ?: continue
            val itemView = inflater.inflate(R.layout.item_suggested_style, suggestedStylesRow, false)

            val ivThumb: ImageView = itemView.findViewById(R.id.ivStyleThumb)
            val tvLabel: TextView = itemView.findViewById(R.id.tvStyleLabel)

            ivThumb.setImageBitmap(bitmap)
            tvLabel.text = style.label

            // Gửi action chọn Style về lại ViewModel (Tham số true = add vào history)
            itemView.setOnClickListener {
                sharedViewModel.selectStyle(style, addToHistory = true)
            }

            styleItemViews[style] = itemView
            suggestedStylesRow.addView(itemView)
        }

        // Khôi phục trạng thái UI nếu Fragment được Show lại
        sharedViewModel.currentStyle.value?.let { updateSelectedStyleUi(it) }
    }

    private fun updateSelectedStyleUi(selected: StickerStyle) {
        for ((style, view) in styleItemViews) {
            val frame = (view as LinearLayout).getChildAt(0) as FrameLayout
            val text = view.getChildAt(1) as TextView

            // Đổi Frame thành viền xanh nhạt
            frame.setBackgroundResource(
                if (style == selected) R.drawable.bg_style_thumb_frame_selected else R.drawable.bg_style_thumb_frame
            )
            // Đổi chữ thành màu xanh đậm
            text.setTextColor(
                if (style == selected) Color.parseColor("#2196F3") else Color.parseColor("#666666")
            )
        }
    }

}