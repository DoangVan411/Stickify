package com.jetpack.stickify.presentation.ui.edit_sticker.decoration

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.jetpack.stickify.R
import com.jetpack.stickify.databinding.ItemDecorationBinding
import com.jetpack.stickify.domain.model.AssetRef
import com.jetpack.stickify.domain.model.BuiltinAsset

class DecorationAdapter(
    private val showAddButton: Boolean = false,
    private val onAddCustomClick: () -> Unit,
    private val onItemClick: (AssetRef) -> Unit
) : ListAdapter<BuiltinAsset, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val TYPE_ADD_BUTTON = 0
        private const val TYPE_ITEM = 1

        private val DiffCallback = object : DiffUtil.ItemCallback<BuiltinAsset>() {
            override fun areItemsTheSame(oldItem: BuiltinAsset, newItem: BuiltinAsset): Boolean =
                oldItem.assetId == newItem.assetId && oldItem.packId == newItem.packId

            override fun areContentsTheSame(oldItem: BuiltinAsset, newItem: BuiltinAsset): Boolean =
                oldItem == newItem
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (showAddButton && position == 0) TYPE_ADD_BUTTON else TYPE_ITEM
    }

    override fun getItemCount(): Int {
        val baseCount = super.getItemCount()
        return if (showAddButton) baseCount + 1 else baseCount
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = ItemDecorationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return if (viewType == TYPE_ADD_BUTTON) {
            AddButtonViewHolder(binding)
        } else {
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AddButtonViewHolder) {
            holder.bind()
        } else if (holder is ItemViewHolder) {
            val realPosition = if (showAddButton) position - 1 else position
            if (realPosition in 0 until super.getItemCount()) {
                holder.bind(getItem(realPosition))
            }
        }
    }

    inner class ItemViewHolder(private val binding: ItemDecorationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BuiltinAsset) {
            val assetUri = "file:///android_asset/stickers/${item.packId}/${item.assetId}"

            Glide.with(binding.imgDecoration)
                .load(assetUri)
                .into(binding.imgDecoration)

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    inner class AddButtonViewHolder(private val binding: ItemDecorationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            binding.root.setBackgroundResource(R.drawable.bg_add_custom_border)
            binding.imgDecoration.setImageResource(R.drawable.ic_add)
            binding.root.setOnClickListener { onAddCustomClick() }
        }
    }
}
