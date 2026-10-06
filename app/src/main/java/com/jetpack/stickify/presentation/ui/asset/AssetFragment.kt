package com.jetpack.stickify.presentation.ui.asset

import android.os.Bundle
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

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val path = uri.toString()
            val title = when (targetAddCategory) {
                "BACKGROUND" -> "Nền mới"
                "DECORATION" -> "Trang trí mới"
                else -> "Nhãn mới"
            }
            viewModel.addCustomAsset(targetAddCategory, title, path) { success ->
                if (success) {
                    Toast.makeText(context, "Thêm tài nguyên thành công", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Lỗi khi thêm tài nguyên", Toast.LENGTH_SHORT).show()
                }
            }
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
