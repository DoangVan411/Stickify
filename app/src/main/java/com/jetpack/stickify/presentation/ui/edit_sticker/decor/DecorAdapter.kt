package com.jetpack.stickify.presentation.ui.edit_sticker.decor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.jetpack.stickify.R

class DecorAdapter(
    private val items: List<DecorItem>,
    private val isSelectable: Boolean = true,
    private val onItemClick: (DecorItem) -> Unit
) : RecyclerView.Adapter<DecorAdapter.ViewHolder>() {

    private var selectedPosition: Int = RecyclerView.NO_POSITION

    fun setSelectedPosition(position: Int) {
        if (!isSelectable) return
        val prev = selectedPosition
        selectedPosition = position
        if (prev != RecyclerView.NO_POSITION) notifyItemChanged(prev)
        if (selectedPosition != RecyclerView.NO_POSITION) notifyItemChanged(selectedPosition)
    }

    fun clearSelection() {
        if (!isSelectable) return
        val prev = selectedPosition
        selectedPosition = RecyclerView.NO_POSITION
        if (prev != RecyclerView.NO_POSITION) notifyItemChanged(prev)
    }

    fun getSelectedPosition(): Int = if (isSelectable) selectedPosition else RecyclerView.NO_POSITION

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivThumb: ImageView = itemView.findViewById(R.id.ivDecorGridThumb)
        val selectedIndicator: View? = itemView.findViewById(R.id.selectedIndicator)

        fun bind(item: DecorItem, isSelected: Boolean) {
            ivThumb.setImageResource(item.resId)
            selectedIndicator?.visibility = if (isSelectable && isSelected) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                val currentPos = bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    if (isSelectable && !item.isImportAction) {
                        val wasSelected = (selectedPosition == currentPos)
                        if (wasSelected) {
                            clearSelection()
                        } else {
                            setSelectedPosition(currentPos)
                        }
                    }

                    // Gọi callback click NGAY LẬP TỨC để ViewModel nhận được sự kiện chọn cọ vẽ
                    onItemClick(item)

                    // Hiệu ứng nảy nhẹ khi bấm
                    itemView.animate().scaleX(0.88f).scaleY(0.88f).setDuration(70).withEndAction {
                        itemView.animate().scaleX(1f).scaleY(1f).setDuration(70).start()
                    }.start()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_decor_grid, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], isSelectable && position == selectedPosition)
    }

    override fun getItemCount(): Int = items.size
}
