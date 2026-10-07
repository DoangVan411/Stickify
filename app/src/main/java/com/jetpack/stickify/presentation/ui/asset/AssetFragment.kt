package com.jetpack.stickify.presentation.ui.asset

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jetpack.stickify.R
import com.jetpack.stickify.domain.model.AssetEntity
import com.jetpack.stickify.presentation.ui.cut_image.CutoutActivity
import com.jetpack.stickify.presentation.ui.home.HomeActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AssetFragment : Fragment() {

    private val viewModel: AssetViewModel by viewModels()

    private var targetAddCategory: String = "BACKGROUND"

    private lateinit var rvBackgrounds: RecyclerView
    private lateinit var rvDecorations: RecyclerView
    private lateinit var rvLabels: RecyclerView
    private lateinit var tvDecorSeeAll: TextView
    private lateinit var tvLabelSeeAll: TextView

    private val cutoutLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val croppedUri = result.data?.getParcelableExtra<Uri>(CutoutActivity.EXTRA_CROPPED_IMAGE_URI)
                ?: result.data?.data
            if (croppedUri != null) {
                val title = when (targetAddCategory) {
                    "BACKGROUND" -> "Nền mới"
                    "DECORATION" -> "Trang trí mới"
                    else -> "Nhãn mới"
                }
                viewModel.addCustomAsset(targetAddCategory, title, croppedUri.toString()) { success ->
                    if (success) {
                        Toast.makeText(context, "Thêm tài nguyên thành công", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Lỗi khi thêm tài nguyên", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val intent = Intent(requireContext(), CutoutActivity::class.java).apply {
                putExtra(CutoutActivity.EXTRA_IMAGE_URI, uri)
                putExtra(CutoutActivity.EXTRA_IS_ASSET_MODE, true)
                putExtra(CutoutActivity.EXTRA_ASSET_CATEGORY, targetAddCategory)
            }
            cutoutLauncher.launch(intent)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_asset, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        observeUiState()
        setupClickListeners()
        setupDragAndDrop(view)
    }

    private fun initViews(view: View) {
        rvBackgrounds = view.findViewById(R.id.rvBackgrounds)
        rvDecorations = view.findViewById(R.id.rvDecorations)
        rvLabels = view.findViewById(R.id.rvLabels)
        tvDecorSeeAll = view.findViewById(R.id.tvDecorSeeAll)
        tvLabelSeeAll = view.findViewById(R.id.tvLabelSeeAll)
    }

    private fun setupClickListeners() {
        tvDecorSeeAll.setOnClickListener {
            Toast.makeText(context, "Tất cả trang trí", Toast.LENGTH_SHORT).show()
        }
        tvLabelSeeAll.setOnClickListener {
            Toast.makeText(context, "Tất cả nhãn", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startDragAsset(asset: AssetEntity, view: View): Boolean {
        val clipData = ClipData.newPlainText("asset_id", asset.id)
        val shadow = View.DragShadowBuilder(view)
        view.startDragAndDrop(clipData, shadow, asset, 0)
        return true
    }

    private fun setupDragAndDrop(view: View) {
        val layoutDeleteTarget = view.findViewById<View>(R.id.layoutDeleteTarget) ?: return

        view.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    (activity as? HomeActivity)?.setBottomBarVisible(false)
                    layoutDeleteTarget.visibility = View.VISIBLE
                    layoutDeleteTarget.alpha = 0f
                    layoutDeleteTarget.animate().alpha(1f).setDuration(200).start()
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    (activity as? HomeActivity)?.setBottomBarVisible(true)
                    layoutDeleteTarget.animate().alpha(0f).setDuration(200).withEndAction {
                        layoutDeleteTarget.visibility = View.GONE
                    }.start()
                    true
                }
                else -> true
            }
        }

        layoutDeleteTarget.setOnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_ENTERED -> {
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                }
                DragEvent.ACTION_DROP -> {
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                    val asset = event.localState as? AssetEntity
                    if (asset != null) {
                        showConfirmDeleteDialog(asset)
                    }
                }
            }
            true
        }
    }

    private fun showConfirmDeleteDialog(asset: AssetEntity) {
        val dialog = ConfirmDeleteAssetDialogFragment.newInstance(asset.title)
        dialog.onConfirmDelete = {
            viewModel.deleteAsset(asset.id) { success ->
                if (success) {
                    Toast.makeText(context, "Đã xóa tài nguyên", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Lỗi khi xóa tài nguyên", Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show(childFragmentManager, ConfirmDeleteAssetDialogFragment.TAG)
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // 1. Backgrounds
                    val bgAdapter = AssetAdapter(state.backgrounds,
                        onAddClick = {
                            targetAddCategory = "BACKGROUND"
                            pickImageLauncher.launch("image/*")
                        },
                        onItemClick = { asset ->
                            Toast.makeText(context, "Đã chọn nền: ${asset.title}", Toast.LENGTH_SHORT).show()
                        },
                        onItemLongClick = { asset, v ->
                            startDragAsset(asset, v)
                        }
                    )
                    rvBackgrounds.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                    rvBackgrounds.adapter = bgAdapter

                    // 2. Decorations
                    val decAdapter = AssetAdapter(state.decorations,
                        onAddClick = {
                            targetAddCategory = "DECORATION"
                            pickImageLauncher.launch("image/*")
                        },
                        onItemClick = { asset ->
                            Toast.makeText(context, "Đã chọn trang trí: ${asset.title}", Toast.LENGTH_SHORT).show()
                        },
                        onItemLongClick = { asset, v ->
                            startDragAsset(asset, v)
                        }
                    )
                    rvDecorations.layoutManager = GridLayoutManager(context, 3)
                    rvDecorations.isNestedScrollingEnabled = false
                    rvDecorations.adapter = decAdapter

                    // 3. Labels
                    val lblAdapter = AssetAdapter(state.labels,
                        onAddClick = {
                            targetAddCategory = "LABEL"
                            pickImageLauncher.launch("image/*")
                        },
                        onItemClick = { asset ->
                            Toast.makeText(context, "Đã chọn nhãn: ${asset.title}", Toast.LENGTH_SHORT).show()
                        },
                        onItemLongClick = { asset, v ->
                            startDragAsset(asset, v)
                        }
                    )
                    rvLabels.layoutManager = GridLayoutManager(context, 3)
                    rvLabels.isNestedScrollingEnabled = false
                    rvLabels.adapter = lblAdapter
                }
            }
        }
    }
}
