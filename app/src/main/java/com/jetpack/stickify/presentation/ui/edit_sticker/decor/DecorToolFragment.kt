package com.jetpack.stickify.presentation.ui.edit_sticker.decor

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

class DecorToolFragment : Fragment() {

    private val sharedViewModel: StickerSharedViewModel by activityViewModels()

    // 1. Danh sách "Vẽ trang trí" (4 items hàng đầu)
    private val drawnDecorItems = listOf(
        DecorItem("decor_1", "Lấp lánh", R.drawable.img_decor_1),
        DecorItem("decor_2", "Trái tim", R.drawable.img_decor_2),
        DecorItem("decor_3", "Hoa", R.drawable.img_decor_3),
        DecorItem("decor_4", "Vương miện", R.drawable.img_decor_4)
    )

    // 2. Danh sách "Nhãn" (Gồm nút thêm ảnh + các nhãn dán như YEAH, OMG)
    private val labelItems = listOf(
        DecorItem("import", "Thêm ảnh", R.drawable.img_import, isImportAction = true),
        DecorItem("decor_5", "Yeah", R.drawable.img_decor_5),
        DecorItem("decor_6", "OMG", R.drawable.img_decor_6),
        DecorItem("decor_7", "Yeah 2", R.drawable.img_decor_7),
        DecorItem("decor_8", "OMG 2", R.drawable.img_decor_8),
        DecorItem("decor_5_2", "Yeah 3", R.drawable.img_decor_5),
        DecorItem("decor_6_2", "OMG 3", R.drawable.img_decor_6),
        DecorItem("decor_7_2", "Yeah 4", R.drawable.img_decor_7)
    )

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Cài đặt Grid cho Vẽ trang trí
        val rvDrawnDecor: RecyclerView = view.findViewById(R.id.rvDrawnDecor)
        rvDrawnDecor.layoutManager = GridLayoutManager(requireContext(), 4)
        rvDrawnDecor.adapter = DecorAdapter(drawnDecorItems) { item ->
            sharedViewModel.selectDrawDecor(item.resId)
        }

        // 2. Cài đặt Grid cho Nhãn
        val rvLabels: RecyclerView = view.findViewById(R.id.rvLabels)
        rvLabels.layoutManager = GridLayoutManager(requireContext(), 4)
        rvLabels.adapter = DecorAdapter(labelItems) { item ->
            if (item.isImportAction) {
                pickImageLauncher.launch("image/*")
            } else {
                sharedViewModel.addDecor(item.resId, item.id)
            }
        }
    }
}
