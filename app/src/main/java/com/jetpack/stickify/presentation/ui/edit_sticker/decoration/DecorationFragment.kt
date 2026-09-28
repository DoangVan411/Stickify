package com.jetpack.stickify.presentation.ui.edit_sticker.decoration


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.jetpack.stickify.presentation.ui.edit_sticker.StickerEditViewModel
import kotlinx.coroutines.flow.collectLatest
import com.jetpack.stickify.databinding.FragmentDecorationBinding


class DecorationFragment : Fragment() {

    private var _binding: FragmentDecorationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StickerEditViewModel by activityViewModels()

    private lateinit var drawnAdapter: DecorationAdapter
    private lateinit var labelAdapter: DecorationAdapter

    // Launcher mở Bộ sưu tập chọn ảnh custom
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.addCustomDecoration(requireContext(), it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDecorationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        observeData()
    }

    private fun setupRecyclerViews() {
        // Danh sách Vẽ Trang Trí (Không có nút Add)
        drawnAdapter = DecorationAdapter(
            showAddButton = false,
            onAddCustomClick = {},
            onItemClick = { assetRef -> viewModel.addDecorationLayer(assetRef) }
        )
        binding.rvDrawnDecorations.adapter = drawnAdapter

        // Danh sách Nhãn (Có nút Add Custom ở vị trí đầu tiên)
        labelAdapter = DecorationAdapter(
            showAddButton = true,
            onAddCustomClick = { pickImageLauncher.launch("image/*") },
            onItemClick = { assetRef -> viewModel.addDecorationLayer(assetRef) }
        )
        binding.rvLabelDecorations.adapter = labelAdapter
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.drawnDecorations.collectLatest { list ->
                drawnAdapter.submitList(list)
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.labelDecorations.collectLatest { list ->
                labelAdapter.submitList(list)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}