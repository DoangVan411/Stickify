package com.jetpack.stickify.presentation.ui.collection.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jetpack.stickify.R
import com.jetpack.stickify.domain.model.FavoriteSticker

/**
 * Adapter cho danh sách sticker yêu thích (Grid 3 cột) trong màn hình Bộ sưu tập.
 */
class FavoriteStickerAdapter(
    private val onItemClick: (FavoriteSticker) -> Unit = {},
    private val onMoreClick: (FavoriteSticker) -> Unit = {}
) : ListAdapter<FavoriteSticker, FavoriteStickerAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite_sticker, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.ivFavoriteThumbnail)
        private val tvName: TextView = itemView.findViewById(R.id.tvFavoriteName)
        private val ivMore: ImageView = itemView.findViewById(R.id.ivFavoriteMore)

        fun bind(sticker: FavoriteSticker) {
            tvName.text = sticker.name

            val drawableRes = when (sticker.thumbnailPath) {
                "recent_1" -> R.drawable.ic_sticker_cat
                "recent_2" -> R.drawable.ic_sticker_girl
                "recent_3" -> R.drawable.ic_sticker_dog
                else -> R.drawable.ic_sticker_cat
            }
            ivThumbnail.setImageResource(drawableRes)

            itemView.setOnClickListener { onItemClick(sticker) }
            ivMore.setOnClickListener { onMoreClick(sticker) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<FavoriteSticker>() {
        override fun areItemsTheSame(oldItem: FavoriteSticker, newItem: FavoriteSticker): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FavoriteSticker, newItem: FavoriteSticker): Boolean {
            return oldItem == newItem
        }
    }
}
