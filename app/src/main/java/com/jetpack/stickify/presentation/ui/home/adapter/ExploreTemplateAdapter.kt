package com.jetpack.stickify.presentation.ui.home.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.jetpack.stickify.R
import com.jetpack.stickify.domain.model.ProjectType
import com.jetpack.stickify.domain.model.StickerTemplate
import java.io.File

/**
 * Adapter cho grid templates trong mục Khám phá.
 */
class ExploreTemplateAdapter(
    private val onItemClick: (StickerTemplate) -> Unit = {},
    private val onFavoriteClick: (StickerTemplate) -> Unit = {}
) : ListAdapter<StickerTemplate, ExploreTemplateAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_explore_template, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.ivExploreThumbnail)
        private val tvBadge: TextView = itemView.findViewById(R.id.tvBadge)
        private val tvName: TextView = itemView.findViewById(R.id.tvExploreName)
        private val ivFavorite: ImageView = itemView.findViewById(R.id.ivFavorite)

        fun bind(template: StickerTemplate) {
            tvName.text = template.title

            // Badge GIF / STICKER
            when (template.type) {
                ProjectType.GIF, ProjectType.ANIMATED_STICKER -> {
                    tvBadge.text = "GIF"
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_gif)
                    tvBadge.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.white))
                    tvBadge.visibility = View.VISIBLE
                }
                ProjectType.STICKER -> {
                    tvBadge.text = "STICKER"
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_sticker)
                    tvBadge.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.white))
                    tvBadge.visibility = View.VISIBLE
                }
            }

            val thumbUrl = template.thumbnailUrl
            val glideModel: Any = when {
                thumbUrl.startsWith("http://") || thumbUrl.startsWith("https://") || thumbUrl.startsWith("file://") || thumbUrl.startsWith("content://") -> {
                    thumbUrl
                }
                thumbUrl.startsWith("/") -> {
                    File(thumbUrl)
                }
                else -> {
                    "file:///android_asset/$thumbUrl"
                }
            }

            Glide.with(itemView.context)
                .load(glideModel)
                .placeholder(R.drawable.ic_sticker_cat)
                .error(R.drawable.ic_sticker_cat)
                .into(ivThumbnail)

            ivFavorite.setOnClickListener { onFavoriteClick(template) }
            itemView.setOnClickListener { onItemClick(template) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<StickerTemplate>() {
        override fun areItemsTheSame(oldItem: StickerTemplate, newItem: StickerTemplate): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: StickerTemplate, newItem: StickerTemplate): Boolean {
            return oldItem == newItem
        }
    }
}
