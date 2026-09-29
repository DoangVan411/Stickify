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
import com.jetpack.stickify.domain.model.ProjectType
import com.jetpack.stickify.domain.model.StickerPack

/**
 * Adapter cho danh sách bộ sticker (Grid 2 cột).
 * Vị trí đầu tiên (position 0) là ô nét đứt [+] Thêm bộ sticker mới.
 */
class StickerPackAdapter(
    private val onCreatePackClick: () -> Unit = {},
    private val onPackClick: (StickerPack) -> Unit = {},
    private val onMoreClick: (StickerPack) -> Unit = {}
) : ListAdapter<StickerPack, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val VIEW_TYPE_CREATE = 0
        private const val VIEW_TYPE_PACK = 1
    }

    override fun getItemCount(): Int {
        return currentList.size + 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) VIEW_TYPE_CREATE else VIEW_TYPE_PACK
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_CREATE) {
            val view = inflater.inflate(R.layout.item_create_pack, parent, false)
            CreatePackViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_sticker_pack, parent, false)
            PackViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is CreatePackViewHolder) {
            holder.bind()
        } else if (holder is PackViewHolder) {
            val pack = getItem(position - 1)
            holder.bind(pack)
        }
    }

    inner class CreatePackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind() {
            itemView.setOnClickListener { onCreatePackClick() }
        }
    }

    inner class PackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPackName: TextView = itemView.findViewById(R.id.tvPackName)
        private val tvPackCount: TextView = itemView.findViewById(R.id.tvPackCount)
        private val ivMore: ImageView = itemView.findViewById(R.id.ivPackMore)

        fun bind(pack: StickerPack) {
            tvPackName.text = pack.name

            val countText = if (pack.type == ProjectType.GIF) {
                itemView.context.getString(R.string.gifs_count, pack.itemCount)
            } else {
                itemView.context.getString(R.string.stickers_count, pack.itemCount)
            }
            tvPackCount.text = countText

            itemView.setOnClickListener { onPackClick(pack) }
            ivMore.setOnClickListener { onMoreClick(pack) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<StickerPack>() {
        override fun areItemsTheSame(oldItem: StickerPack, newItem: StickerPack): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: StickerPack, newItem: StickerPack): Boolean {
            return oldItem == newItem
        }
    }
}
