package com.jetpack.stickify.presentation.ui.edit_sticker.border

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jetpack.stickify.R

class ColorPickerAdapter(
    private val colors: List<Int>,
    private var selectedColor: Int,
    private val onColorSelected: (Int) -> Unit
) : RecyclerView.Adapter<ColorPickerAdapter.ColorViewHolder>() {

    inner class ColorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val vColorFill: View = view.findViewById(R.id.vColorFill)
        val vSelectionBorder: View = view.findViewById(R.id.vSelectionBorder)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_color_swatch, parent, false)
        return ColorViewHolder(view)
    }

    override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
        val color = colors[position]

        // Đổi màu hình tròn
        holder.vColorFill.backgroundTintList = ColorStateList.valueOf(color)

        // Hiện viền xanh nếu đang được chọn
        holder.vSelectionBorder.visibility = if (color == selectedColor) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            if (selectedColor != color) {
                val oldSelectedColor = selectedColor
                selectedColor = color
                // Update UI lại các item bị ảnh hưởng
                notifyItemChanged(colors.indexOf(oldSelectedColor))
                notifyItemChanged(position)

                onColorSelected(color) // Bắn event ra ngoài
            }
        }
    }

    override fun getItemCount() = colors.size
}