package com.jetpack.stickify.presentation.ui.edit_sticker.effect

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jetpack.stickify.R
import com.jetpack.stickify.domain.model.StickerAnimationType

/**
 * Adapter hiển thị danh sách hiệu ứng animation trong grid.
 * Mỗi item hiển thị icon + tên hiệu ứng, highlight item đang chọn.
 */
class EffectAdapter(
    private val items: List<EffectItem>,
    private val onEffectSelected: (StickerAnimationType) -> Unit
) : RecyclerView.Adapter<EffectAdapter.EffectViewHolder>() {

    private var selectedPosition = 0 // Mặc định chọn "Không"

    data class EffectItem(
        val type: StickerAnimationType,
        val iconResId: Int,
        val name: String
    )

    inner class EffectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.ivEffectIcon)
        val tvName: TextView = itemView.findViewById(R.id.tvEffectName)
        val selectedIndicator: View = itemView.findViewById(R.id.selectedIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EffectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_effect_grid, parent, false)
        return EffectViewHolder(view)
    }

    override fun onBindViewHolder(holder: EffectViewHolder, position: Int) {
        val item = items[position]
        holder.ivIcon.setImageResource(item.iconResId)
        holder.tvName.text = item.name

        val isSelected = position == selectedPosition
        holder.selectedIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE

        if (isSelected) {
            holder.tvName.setTextColor(Color.parseColor("#1B85F3"))
            holder.ivIcon.setColorFilter(Color.parseColor("#1B85F3"))
        } else {
            holder.tvName.setTextColor(Color.parseColor("#444444"))
            holder.ivIcon.setColorFilter(Color.parseColor("#666666"))
        }

        holder.itemView.setOnClickListener {
            val prev = selectedPosition
            selectedPosition = holder.bindingAdapterPosition
            notifyItemChanged(prev)
            notifyItemChanged(selectedPosition)
            onEffectSelected(item.type)
        }
    }

    override fun getItemCount(): Int = items.size

    fun getSelectedType(): StickerAnimationType {
        return if (selectedPosition in items.indices) items[selectedPosition].type
        else StickerAnimationType.NONE
    }
}
