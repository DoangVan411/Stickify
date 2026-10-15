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
import com.jetpack.stickify.domain.model.ProjectType
import com.jetpack.stickify.domain.model.StickerPack

enum class PackAction {
    DELETE,
    EXPORT_TO_WHATSAPP
}

class StickerPackAdapter(
    private val onCreatePackClick: () -> Unit = {},
    private val onPackClick: (StickerPack) -> Unit = {},
    private val onActionClick: (StickerPack, PackAction) -> Unit = { _, _ -> }
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

        // Khởi tạo List chứa 6 ImageView preview
        private val previewViews: List<ImageView> = listOf(
            itemView.findViewById(R.id.ivPreview1),
            itemView.findViewById(R.id.ivPreview2),
            itemView.findViewById(R.id.ivPreview3),
            itemView.findViewById(R.id.ivPreview4),
            itemView.findViewById(R.id.ivPreview5),
            itemView.findViewById(R.id.ivPreview6)
        )

        fun bind(pack: StickerPack) {
            tvPackName.text = pack.name

            val countText = if (pack.type == ProjectType.GIF) {
                itemView.context.getString(R.string.gifs_count, pack.itemCount)
            } else {
                itemView.context.getString(R.string.stickers_count, pack.itemCount)
            }
            tvPackCount.text = countText

            // Xử lý load 6 ảnh preview cho bộ sticker
            for (i in previewViews.indices) {
                val iv = previewViews[i]
                val sticker = pack.stickers.getOrNull(i)

                if (sticker?.thumbnailPath != null) {
                    Glide.with(itemView.context)
                        .load(sticker.thumbnailPath)
                        .into(iv)
                } else {
                    // Cần clear ảnh trên ImageView bị tái sử dụng để tránh lỗi hiển thị nhầm
                    Glide.with(itemView.context).clear(iv)
                    iv.setImageDrawable(null)
                }
            }

            itemView.setOnClickListener { onPackClick(pack) }

            ivMore.setOnClickListener { view ->
                showPopupMenu(view, pack)
            }
        }

        private fun showPopupMenu(anchorView: View, pack: StickerPack) {
            val context = anchorView.context
            val inflater = LayoutInflater.from(context)
            val popupView = inflater.inflate(R.layout.popup_pack_menu, null)

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

            val btnWhatsApp = popupView.findViewById<View>(R.id.btnWhatsApp)
            val btnDeletePack = popupView.findViewById<View>(R.id.btnDeletePack)

            btnWhatsApp.setOnClickListener {
                popupWindow.dismiss()
                onActionClick(pack, PackAction.EXPORT_TO_WHATSAPP)
            }

            btnDeletePack.setOnClickListener {
                popupWindow.dismiss()
                onActionClick(pack, PackAction.DELETE)
            }

            popupWindow.showAsDropDown(anchorView, -120, 0)
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