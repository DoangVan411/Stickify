package com.jetpack.stickify.presentation.ui.collection.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.jetpack.stickify.R
import com.jetpack.stickify.domain.model.StickerProject

enum class FavoriteAction {
    RENAME,
    ADD_TO_PACK,
    DELETE
}

class FavoriteStickerAdapter(
    private val onItemClick: (StickerProject) -> Unit = {},
    private val onActionClick: (StickerProject, FavoriteAction) -> Unit = { _, _ -> }
) : ListAdapter<StickerProject, FavoriteStickerAdapter.ViewHolder>(DiffCallback()) {

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

        fun bind(sticker: StickerProject) {
            tvName.text = sticker.name

            // Load ảnh thực tế từ thumbnailPath bằng Glide
            Glide.with(itemView.context)
                .load(sticker.thumbnailPath)
                .placeholder(R.drawable.ic_sticker_cat) // Ảnh mặc định trong lúc chờ
                .error(R.drawable.ic_sticker_cat)
                .into(ivThumbnail)

            itemView.setOnClickListener { onItemClick(sticker) }

            ivMore.setOnClickListener { view ->
                showPopupMenu(view, sticker)
            }
        }

        private fun showPopupMenu(anchorView: View, sticker: StickerProject) {
            val context = anchorView.context
            val inflater = LayoutInflater.from(context)
            val popupView = inflater.inflate(R.layout.popup_favorite_menu, null)

            val popupWindow = PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            ).apply {
                elevation = 5f * context.resources.displayMetrics.density
                isOutsideTouchable = true
                isFocusable = true
            }

            val btnRenameFav = popupView.findViewById<View>(R.id.btnRenameFav)
            val btnAddToPack = popupView.findViewById<View>(R.id.btnAddToPack)
            val btnDeleteFav = popupView.findViewById<View>(R.id.btnDeleteFav)

            btnRenameFav.setOnClickListener {
                popupWindow.dismiss()
                onActionClick(sticker, FavoriteAction.RENAME)
            }

            btnAddToPack.setOnClickListener {
                popupWindow.dismiss()
                onActionClick(sticker, FavoriteAction.ADD_TO_PACK)
            }

            btnDeleteFav.setOnClickListener {
                popupWindow.dismiss()
                onActionClick(sticker, FavoriteAction.DELETE)
            }

            popupWindow.showAsDropDown(anchorView, -120, 0)
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<StickerProject>() {
        override fun areItemsTheSame(oldItem: StickerProject, newItem: StickerProject): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: StickerProject, newItem: StickerProject): Boolean {
            return oldItem == newItem
        }
    }
}
