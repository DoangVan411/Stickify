package com.jetpack.stickify.presentation.ui.gallery


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jetpack.stickify.R
import kotlinx.coroutines.Job
import java.util.concurrent.TimeUnit

class MediaGridAdapter(
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onItemClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<MediaGridAdapter.MediaViewHolder>() {

    private val items = mutableListOf<MediaItem>()

    fun submitList(newItems: List<MediaItem>) {
        val diff = DiffUtil.calculateDiff(MediaDiffCallback(items, newItems))
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_media, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun onViewRecycled(holder: MediaViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelPendingLoad()
    }

    override fun getItemCount(): Int = items.size

    inner class MediaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        private var loadJob: Job? = null

        fun bind(item: MediaItem) {
            cancelPendingLoad()
            ivThumbnail.setImageDrawable(null) // tránh nháy ảnh cũ khi recycle

            if (item.isVideo) {
                tvDuration.visibility = View.VISIBLE
                tvDuration.text = formatDuration(item.durationMs)
            } else {
                tvDuration.visibility = View.GONE
            }

            loadJob = ThumbnailLoader.load(lifecycleScope, itemView.context, item) { bitmap ->
                // Đảm bảo view chưa bị recycle sang item khác trong lúc chờ load.
                if (bindingAdapterPosition != RecyclerView.NO_POSITION && items.getOrNull(bindingAdapterPosition)?.id == item.id) {
                    ivThumbnail.setImageBitmap(bitmap)
                }
            }

            itemView.setOnClickListener { onItemClick(item) }
        }

        fun cancelPendingLoad() {
            loadJob?.cancel()
            loadJob = null
        }

        private fun formatDuration(durationMs: Long): String {
            val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
    }

    private class MediaDiffCallback(
        private val old: List<MediaItem>,
        private val new: List<MediaItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = old.size
        override fun getNewListSize() = new.size
        override fun areItemsTheSame(oldPos: Int, newPos: Int) = old[oldPos].id == new[newPos].id
        override fun areContentsTheSame(oldPos: Int, newPos: Int) = old[oldPos] == new[newPos]
    }
}