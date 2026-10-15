package com.jetpack.stickify.data.source.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.jetpack.stickify.domain.model.AssetRef
import com.jetpack.stickify.domain.model.BuiltinAsset
import com.jetpack.stickify.domain.model.CustomAsset
import com.jetpack.stickify.domain.model.RemoteAsset
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility Helper đọc Bitmap tài nguyên từ assets/ (stickers, backgrounds, decorations, builtins),
 * bộ nhớ trong filesDir (CustomAsset), và URL (RemoteAsset).
 */
@Singleton
class AssetLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun loadBitmap(assetRef: AssetRef): Bitmap? = withContext(Dispatchers.IO) {
        return@withContext try {
            when (assetRef) {
                is BuiltinAsset -> loadBuiltinAsset(assetRef)
                is CustomAsset -> loadCustomAsset(assetRef)
                is RemoteAsset -> loadRemoteAsset(assetRef)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun loadBuiltinAsset(builtinAsset: BuiltinAsset): Bitmap? {
        val possiblePaths = listOf(
            "stickers/${builtinAsset.packId}/${builtinAsset.assetId}",
            "backgrounds/${builtinAsset.packId}/${builtinAsset.assetId}",
            "decorations/${builtinAsset.packId}/${builtinAsset.assetId}",
            "builtins/${builtinAsset.packId}/${builtinAsset.assetId}",
            "${builtinAsset.packId}/${builtinAsset.assetId}",
            builtinAsset.assetId
        )

        for (path in possiblePaths) {
            var inputStream: InputStream? = null
            try {
                inputStream = context.assets.open(path)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    return bitmap
                }
            } catch (e: Exception) {
                // Thử đường dẫn tiếp theo
            } finally {
                try {
                    inputStream?.close()
                } catch (_: Exception) {
                }
            }
        }

        val resNames = listOf(
            builtinAsset.assetId.substringBeforeLast("."),
            "img_${builtinAsset.assetId.substringBeforeLast(".")}",
            builtinAsset.assetId
        )
        for (resName in resNames) {
            val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
            if (resId != 0) {
                val bmp = BitmapFactory.decodeResource(context.resources, resId)
                if (bmp != null) return bmp
            }
        }

        return null
    }

    private fun loadCustomAsset(customAsset: CustomAsset): Bitmap? {
        val path = customAsset.relativePath
        if (path.isBlank()) return null

        if (path.startsWith("content://") || path.startsWith("file://")) {
            return try {
                val uri = Uri.parse(path)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        val directFile = File(path)
        if (directFile.exists()) {
            val bmp = BitmapFactory.decodeFile(directFile.absolutePath)
            if (bmp != null) return bmp
        }

        val appFile = File(context.filesDir, path)
        if (appFile.exists()) {
            val bmp = BitmapFactory.decodeFile(appFile.absolutePath)
            if (bmp != null) return bmp
        }

        return null
    }

    private fun loadRemoteAsset(remoteAsset: RemoteAsset): Bitmap? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(remoteAsset.url)
            connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()
            val input = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
