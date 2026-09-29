package com.jetpack.stickify.presentation.ui.edit_sticker.decor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.jetpack.stickify.R

class DecorAdapter(
    private val items: List<DecorItem>,
    private val onItemClick: (DecorItem) -> Unit
) : RecyclerView.Adapter<DecorAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivThumb: ImageView = itemView.findViewById(R.id.ivDecorGridThumb)

        fun bind(item: DecorItem) {
            ivThumb.setImageResource(item.resId)
            itemView.setOnClickListener {
                // Hiệu ứng nảy nhẹ khi bấm
                itemView.animate().scaleX(0.88f).scaleY(0.88f).setDuration(70).withEndAction {
                    itemView.animate().scaleX(1f).scaleY(1f).setDuration(70).start()
                    onItemClick(item)
                }.start()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_decor_grid, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
