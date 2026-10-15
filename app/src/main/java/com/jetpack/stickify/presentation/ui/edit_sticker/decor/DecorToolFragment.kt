package com.jetpack.stickify.presentation.ui.edit_sticker.decor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jetpack.stickify.R
import com.jetpack.stickify.presentation.ui.edit_sticker.StickerSharedViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class DecorToolFragment : Fragment() {

    private val sharedViewModel: StickerSharedViewModel by activityViewModels()

    // Launcher mở thư viện ảnh khi nhấn vào nút Nhãn thêm ảnh (Dashed card)
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    sharedViewModel.addCustomDecor(bitmap)
                } else {
                    Toast.makeText(context, "Không thể đọc ảnh", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi tải ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_decor_tool, container, false)
    }

    private var drawnAdapter: DecorAdapter? = null
    private var labelAdapter: DecorAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Cài đặt Grid cho Vẽ trang trí
        val rvDrawnDecor: RecyclerView = view.findViewById(R.id.rvDrawnDecor)
        rvDrawnDecor.layoutManager = GridLayoutManager(requireContext(), 4)
        drawnAdapter = DecorAdapter(emptyList(), isSelectable = true) { item ->
            if (drawnAdapter?.getSelectedPosition() != RecyclerView.NO_POSITION) {
                if (item.resId != 0) {
                    sharedViewModel.selectDrawDecor(resId = item.resId)
                } else {
                    val bitmap = item.bitmap ?: loadBitmapFromPath(requireContext(), item.path)
                    if (bitmap != null) {
                        sharedViewModel.selectDrawDecor(bitmap = bitmap)
                    } else {
                        sharedViewModel.clearDrawDecor()
                    }
                }
            } else {
                sharedViewModel.clearDrawDecor()
            }
        }
        rvDrawnDecor.adapter = drawnAdapter

        // 2. Cài đặt Grid cho Nhãn
        val rvLabels: RecyclerView = view.findViewById(R.id.rvLabels)
        rvLabels.layoutManager = GridLayoutManager(requireContext(), 4)
        labelAdapter = DecorAdapter(emptyList(), isSelectable = false) { item ->
            drawnAdapter?.clearSelection()
            sharedViewModel.clearDrawDecor()
            if (item.isImportAction) {
                pickImageLauncher.launch("image/*")
            } else if (item.resId != 0) {
                sharedViewModel.addDecor(resId = item.resId, id = item.id)
            } else {
                val bitmap = item.bitmap ?: loadBitmapFromPath(requireContext(), item.path)
                if (bitmap != null) {
                    sharedViewModel.addDecor(bitmap = bitmap, id = item.id)
                } else {
                    Toast.makeText(context, "Không thể tải nhãn", Toast.LENGTH_SHORT).show()
                }
            }
        }
        rvLabels.adapter = labelAdapter

        // Lấy danh sách trang trí và nhãn từ bảng Asset qua ViewModel
        sharedViewModel.decorationAssets.observe(viewLifecycleOwner) { assets ->
            val context = context ?: return@observe
            val items = assets.map { asset ->
                val resId = context.resources.getIdentifier(asset.path, "drawable", context.packageName)
                DecorItem(
                    id = asset.id,
                    name = asset.title,
                    resId = resId,
                    path = asset.path
                )
            }
            drawnAdapter?.updateItems(items)
        }

        sharedViewModel.labelAssets.observe(viewLifecycleOwner) { assets ->
            val context = context ?: return@observe
            val importItem = DecorItem("import", "Thêm ảnh", R.drawable.img_import, isImportAction = true)
            val items = mutableListOf(importItem)
            items.addAll(assets.map { asset ->
                val resId = context.resources.getIdentifier(asset.path, "drawable", context.packageName)
                DecorItem(
                    id = asset.id,
                    name = asset.title,
                    resId = resId,
                    path = asset.path
                )
            })
            labelAdapter?.updateItems(items)
        }

        sharedViewModel.loadAssets()
    }

    private fun loadBitmapFromPath(context: Context, path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        return try {
            context.assets.open(path).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (_: Exception) {
            try {
                val file = File(path)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else {
                    val uri = Uri.parse(path)
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
