package com.jetpack.stickify.presentation.ui.gallery

import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loader thumbnail đơn giản, đủ dùng cho grid ảnh/video mà không cần Glide/Coil.
 * Nếu app đã dùng sẵn Glide/Coil thì nên thay bằng thư viện đó để có cache đĩa +
 * quản lý vòng đời tốt hơn — class này chỉ là fallback nhẹ.
 */
object ThumbnailLoader {

    private val memoryCache: LruCache<Long, Bitmap> = run {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt() // dùng tối đa 1/8 heap
        object : LruCache<Long, Bitmap>(maxKb) {
            override fun sizeOf(key: Long, value: Bitmap) = value.byteCount / 1024
        }
    }

    /**
     * Load thumbnail cho [item] vào [target]. Trả về Job để caller có thể hủy khi
     * ViewHolder bị recycle (tránh gán nhầm ảnh do load bất đồng bộ chậm).
     */
    fun load(
        scope: CoroutineScope,
        context: Context,
        item: MediaItem,
        target: (Bitmap?) -> Unit
    ): Job {
        memoryCache.get(item.id)?.let {
            target(it)
            return Job().apply { complete() }
        }

        return scope.launch(Dispatchers.Main) {
            val bmp = withContext(Dispatchers.IO) {
                try {
                    decodeThumbnail(context, item)
                } catch (e: Exception) {
                    null
                }
            }
            bmp?.let { memoryCache.put(item.id, it) }
            target(bmp)
        }
    }

    private fun decodeThumbnail(context: Context, item: MediaItem): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(item.uri, Size(300, 300), null)
        } else {
            // Fallback cho API < 29.
            if (item.isVideo) {
                MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver, item.id, MediaStore.Video.Thumbnails.MINI_KIND, null
                )
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Thumbnails.getThumbnail(
                    context.contentResolver, item.id, MediaStore.Images.Thumbnails.MINI_KIND, null
                )
            }
        }
    }

    fun clearCache() = memoryCache.evictAll()
}