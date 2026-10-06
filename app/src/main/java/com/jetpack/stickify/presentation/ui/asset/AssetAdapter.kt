package com.jetpack.stickify.presentation.ui.asset

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.jetpack.stickify.R
import com.jetpack.stickify.domain.model.AssetEntity

class AssetAdapter(
    private val assets: List<AssetEntity>,
    private val onAddClick: () -> Unit,
    private val onItemClick: (AssetEntity) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ADD = 0
        private const val TYPE_ITEM = 1
    }

    override fun getItemCount(): Int = assets.size + 1

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) TYPE_ADD else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_ADD) {
            val view = inflater.inflate(R.layout.item_asset_add, parent, false)
            AddViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_asset_card, parent, false)
            ItemViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AddViewHolder) {
            holder.bind(onAddClick)
        } else if (holder is ItemViewHolder) {
            holder.bind(assets[position - 1], onItemClick)
        }
    }

    class AddViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(onClick: () -> Unit) {
            itemView.setOnClickListener { onClick() }
        }
    }

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivThumb: ImageView = view.findViewById(R.id.ivAssetThumb)
        private val tvName: TextView = view.findViewById(R.id.tvAssetName)

        fun bind(asset: AssetEntity, onClick: (AssetEntity) -> Unit) {
            tvName.text = asset.title

            val resId = when (asset.path) {
                "bg_transparent" -> R.drawable.ic_placeholder_explore_1
                "bg_gradient_pastel" -> R.drawable.img_background
                "bg_holographic" -> R.drawable.img_decor_6
                "img_decor_1" -> R.drawable.img_decor_1
                "img_decor_2" -> R.drawable.img_decor_2
                "img_decor_3" -> R.drawable.img_decor_3
                "img_decor_4" -> R.drawable.img_decor_4
                "img_decor_5" -> R.drawable.img_decor_5
                "img_sticker_01" -> R.drawable.img_sticker_01
                "img_sticker_02" -> R.drawable.img_sticker_02
                "img_sticker_03" -> R.drawable.img_sticker_03
                else -> 0
            }

            if (resId != 0) {
            ivThumb.setImageResource(resId)
        } else {
            Glide.with(itemView.context)
                .load(asset.path)
                .placeholder(R.drawable.ic_sticker_cat)
                .into(ivThumb)
        }

            itemView.setOnClickListener { onClick(asset) }
        }
    }
}
