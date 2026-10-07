package com.jetpack.stickify.presentation.ui.collection

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.jetpack.stickify.R
import com.jetpack.stickify.databinding.FragmentCollectionBinding
import com.jetpack.stickify.domain.model.StickerProject
import com.jetpack.stickify.presentation.ui.collection.adapter.FavoriteAction
import com.jetpack.stickify.presentation.ui.collection.adapter.FavoriteStickerAdapter
import com.jetpack.stickify.presentation.ui.collection.adapter.PackAction
import com.jetpack.stickify.presentation.ui.collection.adapter.StickerPackAdapter
import com.jetpack.stickify.presentation.ui.collection.create_package.ConfirmDeletePackDialogFragment
import com.jetpack.stickify.presentation.ui.collection.create_package.CreateStickerPackActivity
import com.jetpack.stickify.presentation.ui.collection.create_package.CustomMessageDialogFragment
import com.jetpack.stickify.presentation.ui.collection.favourite.SelectPacksDialogFragment
import com.jetpack.stickify.presentation.ui.home.ConfirmDeleteProjectDialogFragment
import com.jetpack.stickify.presentation.ui.home.EditNameDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment hiển thị màn hình Bộ sưu tập (Collection).
 * Bao gồm:
 * 1. Mục Yêu thích (Grid 3 cột)
 * 2. Mục Bộ sticker (Grid 2 cột với ô đầu tiên là Thêm bộ sticker mới)
 */
@AndroidEntryPoint
class CollectionFragment : Fragment() {

    private var _binding: FragmentCollectionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CollectionViewModel by viewModels()

    private lateinit var favoriteAdapter: FavoriteStickerAdapter
    private lateinit var packAdapter: StickerPackAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCollectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDialogListeners()
        setupRecyclerViews()
        setupClickListeners()
        observeUiState()
    }

    private fun setupDialogListeners() {
        // Delete pack result
        childFragmentManager.setFragmentResultListener(
            ConfirmDeletePackDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val packId = bundle.getString(ConfirmDeletePackDialogFragment.EXTRA_PACK_ID)
            if (!packId.isNullOrEmpty()) {
                viewModel.deletePack(packId)
                Toast.makeText(context, "Đã xóa bộ sticker", Toast.LENGTH_SHORT).show()
            }
        }

        // Rename favorite/project result
        childFragmentManager.setFragmentResultListener(
            EditNameDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val projectId = bundle.getString(EditNameDialogFragment.EXTRA_PROJECT_ID)
            val newName = bundle.getString(EditNameDialogFragment.EXTRA_NEW_NAME)
            if (!projectId.isNullOrEmpty() && !newName.isNullOrEmpty()) {
                viewModel.updateProjectName(projectId, newName)
                Toast.makeText(context, "Đã cập nhật tên dự án", Toast.LENGTH_SHORT).show()
            }
        }

        // Delete favorite/project result
        childFragmentManager.setFragmentResultListener(
            ConfirmDeleteProjectDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val projectId = bundle.getString(ConfirmDeleteProjectDialogFragment.EXTRA_PROJECT_ID)
            if (!projectId.isNullOrEmpty()) {
                viewModel.deleteProject(projectId)
                Toast.makeText(context, "Đã xóa khỏi yêu thích", Toast.LENGTH_SHORT).show()
            }
        }

        // Add sticker to packs result
        childFragmentManager.setFragmentResultListener(
            SelectPacksDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val packIds = bundle.getStringArrayList(SelectPacksDialogFragment.EXTRA_SELECTED_PACK_IDS) ?: emptyList()
            val sticker = tempSelectedStickerForPack
            if (sticker != null && packIds.isNotEmpty()) {
                for (packId in packIds) {
                    viewModel.addStickerToPack(packId, sticker.id) { _ -> }
                }
                Toast.makeText(context, "Đã thêm sticker vào gói", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadCollectionData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecyclerViews() {
        // 1. Grid 3 cột cho Yêu thích
        favoriteAdapter = FavoriteStickerAdapter(
            onItemClick = { sticker ->
                Toast.makeText(context, sticker.name, Toast.LENGTH_SHORT).show()
            },
            onActionClick = { sticker, action ->
                when (action) {
                    FavoriteAction.RENAME -> {
                        val dialog = EditNameDialogFragment.newInstance(sticker.id, sticker.name)
                        dialog.show(childFragmentManager, EditNameDialogFragment.TAG)
                    }
                    FavoriteAction.ADD_TO_PACK -> {
                        tempSelectedStickerForPack = sticker
                        val dialog = SelectPacksDialogFragment.newInstance()
                        dialog.show(childFragmentManager, SelectPacksDialogFragment.TAG)
                    }
                    FavoriteAction.DELETE -> {
                        val dialog = ConfirmDeleteProjectDialogFragment.newInstance(sticker.id, sticker.name)
                        dialog.show(childFragmentManager, ConfirmDeleteProjectDialogFragment.TAG)
                    }
                }
            }
        )

        binding.rvFavorites.apply {
            layoutManager = GridLayoutManager(context, 3)
            adapter = favoriteAdapter
            isNestedScrollingEnabled = false
        }

        // 2. Grid 2 cột cho Bộ sticker
        packAdapter = StickerPackAdapter(
            onCreatePackClick = {
                val intent = Intent(requireContext(), CreateStickerPackActivity::class.java)
                startActivity(intent)
            },
            onPackClick = { pack ->
                val intent = Intent(requireContext(), CreateStickerPackActivity::class.java).apply {
                    putExtra(CreateStickerPackActivity.EXTRA_PACK_ID, pack.id)
                }
                startActivity(intent)
            },
            onActionClick = { pack, action ->
                when (action) {
                    PackAction.EXPORT_TO_WHATSAPP -> {
                        viewModel.exportToWhatsApp(pack.id) { success, errorMsg ->
                            if (!success) {
                                val dialog = CustomMessageDialogFragment.newInstance(
                                    "Không thể thêm vào WhatsApp",
                                    errorMsg ?: "Đã xảy ra lỗi khi xuất bộ sticker sang WhatsApp."
                                )
                                dialog.show(childFragmentManager, CustomMessageDialogFragment.TAG)
                            } else {
                                val dialog = CustomMessageDialogFragment.newInstance( "Thêm thành công","Đã mở WhatsApp cho ${pack.name}")
                                dialog.show(childFragmentManager, CustomMessageDialogFragment.TAG)
                            }
                        }
                    }
                    PackAction.DELETE -> {
                        val dialog = ConfirmDeletePackDialogFragment.newInstance(pack.id, pack.name)
                        dialog.show(childFragmentManager, ConfirmDeletePackDialogFragment.TAG)
                    }
                }
            }
        )

        binding.rvStickerPacks.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = packAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupClickListeners() {
        binding.tvFavoritesSeeAll.setOnClickListener {
            Toast.makeText(context, getString(R.string.favorites) + " - " + getString(R.string.see_all), Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressCollection.isVisible = state.isLoading
                    binding.nestedScrollView.isVisible = !state.isLoading

                    if (!state.isLoading) {
                        favoriteAdapter.submitList(state.favorites)
                        packAdapter.submitList(state.stickerPacks)

                        val hasFavorites = state.favorites.isNotEmpty()
                        binding.rvFavorites.isVisible = hasFavorites
                        binding.tvFavoritesEmpty.isVisible = !hasFavorites
                        binding.tvFavoritesSeeAll.isVisible = hasFavorites
                    }
                }
            }
        }
    }

    companion object {
        var tempSelectedStickerForPack: StickerProject? = null
    }
}
