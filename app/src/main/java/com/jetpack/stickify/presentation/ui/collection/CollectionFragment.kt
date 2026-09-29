package com.jetpack.stickify.presentation.ui.collection

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
import com.jetpack.stickify.presentation.ui.collection.adapter.FavoriteStickerAdapter
import com.jetpack.stickify.presentation.ui.collection.adapter.StickerPackAdapter
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
        setupRecyclerViews()
        setupClickListeners()
        observeUiState()
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
            onMoreClick = { sticker ->
                Toast.makeText(context, getString(R.string.more_options) + ": " + sticker.name, Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, getString(R.string.create_new_pack), Toast.LENGTH_SHORT).show()
            },
            onPackClick = { pack ->
                Toast.makeText(context, pack.name, Toast.LENGTH_SHORT).show()
            },
            onMoreClick = { pack ->
                Toast.makeText(context, getString(R.string.more_options) + ": " + pack.name, Toast.LENGTH_SHORT).show()
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
                    favoriteAdapter.submitList(state.favorites)
                    packAdapter.submitList(state.stickerPacks)
                }
            }
        }
    }
}
