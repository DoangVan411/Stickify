package com.jetpack.stickify.presentation.ui.cut_image


import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Xử lý ảnh: đọc EXIF orientation, xoay bitmap về đúng chiều "nhìn thấy",
 * lưu bitmap ra file và trả về content:// Uri (qua FileProvider) để truyền
 * sang Activity kế tiếp.
 *
 * QUAN TRỌNG: ML Kit Subject Segmentation KHÔNG tự đọc EXIF của ảnh gallery.
 * Ta phải tự xoay ảnh về đúng chiều trước khi feed vào InputImage.fromBitmap(bitmap, 0).
 */
object ImageUtils {

    /** Đọc ảnh từ Uri, decode và xoay theo đúng EXIF orientation. */
    fun loadUprightBitmap(context: Context, uri: Uri, maxDimension: Int = 2048): Bitmap {
        val rawBitmap = decodeBitmap(context, uri, maxDimension)
        val orientation = readExifOrientation(context, uri)
        return applyExifOrientation(rawBitmap, orientation)
    }

    private fun decodeBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap {
        // Bước 1: chỉ đọc kích thước để tính inSampleSize, tránh OOM với ảnh lớn.
        val boundsOptions = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(context, uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, boundsOptions) }

        var sampleSize = 1
        val (w, h) = boundsOptions.outWidth to boundsOptions.outHeight
        while (w / sampleSize > maxDimension || h / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return openStream(context, uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: throw IllegalArgumentException("Không đọc được ảnh từ Uri: $uri")
    }

    private fun openStream(context: Context, uri: Uri): InputStream? =
        context.contentResolver.openInputStream(uri)

    private fun readExifOrientation(context: Context, uri: Uri): Int {
        return try {
            openStream(context, uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Lưu bitmap thành PNG (giữ alpha) trong cache dir và trả về content Uri qua FileProvider. */
    fun saveBitmapAndGetUri(context: Context, bitmap: Bitmap, fileName: String = "cutout_${System.currentTimeMillis()}.png"): Uri {
        val cutoutDir = File(context.cacheDir, "cutouts").apply { mkdirs() }
        val file = File(cutoutDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        // authorities phải khớp với khai báo trong AndroidManifest.xml, xem ghi chú cuối file.
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
