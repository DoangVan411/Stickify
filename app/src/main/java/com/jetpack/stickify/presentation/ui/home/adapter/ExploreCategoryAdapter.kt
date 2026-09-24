package com.jetpack.stickify.presentation.ui.home.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jetpack.stickify.R
import com.jetpack.stickify.domain.model.ExploreCategory

/**
 * Adapter cho category chips trong mục Khám phá.
 */
class ExploreCategoryAdapter(
    private val onCategorySelected: (ExploreCategory) -> Unit = {}
) : ListAdapter<ExploreCategory, ExploreCategoryAdapter.ViewHolder>(DiffCallback()) {

    private var selectedCategory: ExploreCategory = ExploreCategory.ALL

    fun setSelectedCategory(category: ExploreCategory) {
        val oldSelected = selectedCategory
        selectedCategory = category
        // Notify old and new positions
        val oldPos = currentList.indexOf(oldSelected)
        val newPos = currentList.indexOf(category)
        if (oldPos >= 0) notifyItemChanged(oldPos)
        if (newPos >= 0) notifyItemChanged(newPos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_explore_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCategory: TextView = itemView.findViewById(R.id.tvCategoryName)

        fun bind(category: ExploreCategory) {
            tvCategory.text = category.displayName

            val isSelected = category == selectedCategory
            if (isSelected) {
                tvCategory.setBackgroundResource(R.drawable.bg_category_selected)
                tvCategory.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.white))
            } else {
                tvCategory.setBackgroundResource(R.drawable.bg_category_unselected)
                tvCategory.setTextColor(ContextCompat.getColor(itemView.context, R.color.colorTextSecondary))
            }

            itemView.setOnClickListener {
                onCategorySelected(category)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ExploreCategory>() {
        override fun areItemsTheSame(oldItem: ExploreCategory, newItem: ExploreCategory): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: ExploreCategory, newItem: ExploreCategory): Boolean {
            return oldItem == newItem
        }
    }
}
