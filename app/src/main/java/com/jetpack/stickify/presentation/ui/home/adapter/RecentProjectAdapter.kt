package com.jetpack.stickify.presentation.ui.home.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jetpack.stickify.R
import com.jetpack.stickify.domain.model.ProjectEntity
import com.jetpack.stickify.domain.model.ProjectType

/**
 * Adapter cho danh sách project gần đây (horizontal RecyclerView).
 * Item được thiết kế dạng card tương đồng với item khám phá (explore)
 * và tự động co giãn kích thước để màn hình hiển thị vừa vặn 3 item.
 */
class RecentProjectAdapter(
    private val onItemClick: (ProjectEntity) -> Unit = {},
    private val onMoreClick: (ProjectEntity) -> Unit = {}
) : ListAdapter<ProjectEntity, RecentProjectAdapter.ViewHolder>(DiffCallback()) {

    private var calculatedItemWidth: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_project, parent, false)

        // Tính toán kích thước để vừa vặn đúng 3 item trên chiều ngang màn hình
        if (calculatedItemWidth == 0) {
            val displayMetrics = parent.context.resources.displayMetrics
            val totalWidth = parent.measuredWidth.takeIf { it > 0 } ?: displayMetrics.widthPixels
            val spacePx = (8 * displayMetrics.density).toInt()
            val paddingHorizontal = parent.paddingStart + parent.paddingEnd
            val availableWidth = totalWidth - paddingHorizontal - (2 * spacePx)
            if (availableWidth > 0) {
                calculatedItemWidth = availableWidth / 3
            }
        }

        if (calculatedItemWidth > 0) {
            view.layoutParams = view.layoutParams.apply {
                width = calculatedItemWidth
            }
        }

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.ivRecentThumbnail)
        private val tvBadge: TextView? = itemView.findViewById(R.id.tvRecentBadge)
        private val tvName: TextView = itemView.findViewById(R.id.tvRecentName)
        private val tvTime: TextView = itemView.findViewById(R.id.tvRecentTime)
        private val ivMore: ImageView = itemView.findViewById(R.id.ivRecentMore)

        fun bind(project: ProjectEntity) {
            tvName.text = project.name
            tvTime.text = getRelativeTime(project.updatedAt)

            // Badge GIF / STICKER tương đồng với Explore
            tvBadge?.let { badge ->
                when (project.type) {
                    ProjectType.GIF, ProjectType.ANIMATED_STICKER -> {
                        badge.text = "GIF"
                        badge.setBackgroundResource(R.drawable.bg_badge_gif)
                        badge.setTextColor(androidx.core.content.ContextCompat.getColor(itemView.context, android.R.color.white))
                        badge.visibility = View.VISIBLE
                    }
                    ProjectType.STICKER -> {
                        badge.text = "STICKER"
                        badge.setBackgroundResource(R.drawable.bg_badge_sticker)
                        badge.setTextColor(androidx.core.content.ContextCompat.getColor(itemView.context, android.R.color.white))
                        badge.visibility = View.VISIBLE
                    }
                }
            }

            // Sử dụng placeholder drawable dựa trên thumbnailPath
            val drawableRes = getPlaceholderDrawable(project.thumbnailPath)
            ivThumbnail.setImageResource(drawableRes)

            ivMore.setOnClickListener { onMoreClick(project) }
            itemView.setOnClickListener { onItemClick(project) }
        }

        private fun getRelativeTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            val minutes = diff / (60 * 1000)
            val hours = diff / (60 * 60 * 1000)
            val days = diff / (24 * 60 * 60 * 1000)
            val weeks = days / 7
            val months = days / 30

            return when {
                minutes < 1 -> "vừa xong"
                minutes < 60 -> "$minutes phút trước"
                hours < 24 -> "$hours giờ trước"
                days < 7 -> "$days ngày trước"
                weeks < 4 -> "$weeks tuần trước"
                else -> "$months tháng trước"
            }
        }

        private fun getPlaceholderDrawable(thumbnailPath: String): Int {
            return when (thumbnailPath) {
                "recent_1" -> R.drawable.ic_placeholder_sticker_1
                "recent_2" -> R.drawable.ic_placeholder_sticker_2
                "recent_3" -> R.drawable.ic_placeholder_sticker_3
                "recent_4" -> R.drawable.ic_placeholder_sticker_4
                else -> R.drawable.ic_placeholder_sticker_1
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ProjectEntity>() {
        override fun areItemsTheSame(oldItem: ProjectEntity, newItem: ProjectEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ProjectEntity, newItem: ProjectEntity): Boolean {
            return oldItem == newItem
        }
    }
}
