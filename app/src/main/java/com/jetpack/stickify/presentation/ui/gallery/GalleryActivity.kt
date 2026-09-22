package com.jetpack.stickify.presentation.ui.gallery


import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jetpack.stickify.R
import com.jetpack.stickify.databinding.ActivityGalleryBinding
import com.jetpack.stickify.presentation.ui.cutimage.CutoutActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Màn hình lưới 3 cột hiển thị TOÀN BỘ ảnh + video trên máy (MediaStore),
 * sắp xếp mới nhất trước. Chọn 1 ảnh -> mở CutoutActivity với Uri ảnh đó.
 * Chọn video -> hiện tại chỉ báo Cutout không hỗ trợ video (tùy bạn mở rộng sau).
 */
class GalleryActivity : AppCompatActivity() {

    companion object {
        private const val GRID_SPAN_COUNT = 4
    }

    private lateinit var adapter: MediaGridAdapter

    private lateinit var galleryBinding : ActivityGalleryBinding

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            showGallery()
            loadMedia()
        } else {
            showPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        galleryBinding = DataBindingUtil.setContentView(this, R.layout.activity_gallery)


        adapter = MediaGridAdapter(lifecycleScope) { item -> onMediaSelected(item) }
        galleryBinding.recyclerView.layoutManager = GridLayoutManager(this, GRID_SPAN_COUNT)
        galleryBinding.recyclerView.adapter = adapter

        galleryBinding.btnGrantPermission.setOnClickListener { requestPermissionOrOpenSettings() }
        galleryBinding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        if (hasAllPermissions()) {
            loadMedia()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun hasAllPermissions(): Boolean =
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun requestPermissionOrOpenSettings() {
        val shouldShowRationale = requiredPermissions.any {
            shouldShowRequestPermissionRationale(it)
        }
        if (!shouldShowRationale && !hasAllPermissions()) {
            // Người dùng đã từ chối vĩnh viễn ("Don't ask again") -> mở Settings.
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun showGallery() {
        galleryBinding.permissionDeniedGroup.visibility = View.GONE
        galleryBinding.recyclerView.visibility = View.VISIBLE
    }

    private fun showPermissionDenied() {
        galleryBinding.recyclerView.visibility = View.GONE
        galleryBinding.loadingAnimation.visibility = View.GONE
        galleryBinding.permissionDeniedGroup.visibility = View.VISIBLE
    }

    private fun loadMedia() {
        showGallery()
        galleryBinding.loadingAnimation.visibility = View.VISIBLE
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { queryAllMedia() }
            galleryBinding.loadingAnimation.visibility = View.GONE
            adapter.submitList(items)
        }
    }

    /** Truy vấn ảnh + video từ MediaStore, gộp lại và sắp xếp theo ngày thêm mới nhất. */
    private fun queryAllMedia(): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        result.addAll(queryImages())
        result.addAll(queryVideos())
        return result.sortedByDescending { it.dateAddedSeconds }
    }

    private fun queryImages(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                items.add(MediaItem(id, uri, isVideo = false, durationMs = 0L, dateAddedSeconds = cursor.getLong(dateCol)))
            }
        }
        return items
    }

    private fun queryVideos(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                items.add(
                    MediaItem(
                        id, uri, isVideo = true,
                        durationMs = cursor.getLong(durationCol),
                        dateAddedSeconds = cursor.getLong(dateCol)
                    )
                )
            }
        }
        return items
    }

    private fun onMediaSelected(item: MediaItem) {
        if (item.isVideo) {
            // Cutout hiện chỉ xử lý ảnh tĩnh. Mở rộng sau nếu cần cắt frame từ video.
            Toast.makeText(this, "Chức năng cắt hiện chỉ áp dụng cho ảnh", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, CutoutActivity::class.java).apply {
            putExtra(CutoutActivity.EXTRA_IMAGE_URI, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }
}