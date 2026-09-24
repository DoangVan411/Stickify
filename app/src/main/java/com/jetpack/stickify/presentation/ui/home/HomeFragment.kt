package com.jetpack.stickify.presentation.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.jetpack.stickify.R
import com.jetpack.stickify.databinding.FragmentHomeBinding
import com.jetpack.stickify.domain.model.ExploreCategory
import com.jetpack.stickify.presentation.ui.home.adapter.ExploreCategoryAdapter
import com.jetpack.stickify.presentation.ui.home.adapter.ExploreTemplateAdapter
import com.jetpack.stickify.presentation.ui.home.adapter.HorizontalSpaceItemDecoration
import com.jetpack.stickify.presentation.ui.home.adapter.RecentProjectAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment chính hiển thị HomeScreen.
 * Bao gồm: Header, Feature Cards, Recent, Explore.
 */
@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    private lateinit var recentAdapter: RecentProjectAdapter
    private lateinit var categoryAdapter: ExploreCategoryAdapter
    private lateinit var exploreAdapter: ExploreTemplateAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupClickListeners()
        observeUiState()
    }

    private fun setupRecyclerViews() {
        // Recent Projects - Horizontal (vừa vặn 3 item trên màn hình)
        recentAdapter = RecentProjectAdapter { project ->
            // TODO: Navigate to project editor
        }
        val spacePx = resources.getDimensionPixelSize(R.dimen.spacing_sm)
        binding.rvRecentProjects.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = recentAdapter
            addItemDecoration(HorizontalSpaceItemDecoration(spacePx))
        }

        // Category Chips - Horizontal
        categoryAdapter = ExploreCategoryAdapter { category ->
            viewModel.onCategorySelected(category)
        }
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }
        categoryAdapter.submitList(ExploreCategory.entries.toList())

        // Explore Templates - Grid (2 columns)
        exploreAdapter = ExploreTemplateAdapter(
            onItemClick = { template ->
                // TODO: Navigate to template preview
            },
            onFavoriteClick = { template ->
                // TODO: Toggle favorite
            }
        )
        binding.rvExploreTemplates.apply {
            layoutManager = GridLayoutManager(context, 3)
            adapter = exploreAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupClickListeners() {
        binding.cardCreateGif.setOnClickListener {
            // TODO: Navigate to GIF editor
        }
        binding.ivCreateGif.setOnClickListener {
            binding.cardCreateGif.performClick()
        }

        binding.cardCreateSticker.setOnClickListener {
            // TODO: Navigate to Sticker editor
        }
        binding.ivCreateSticker.setOnClickListener {
            binding.cardCreateSticker.performClick()
        }

        binding.tvSeeAll.setOnClickListener {
            // TODO: Navigate to all projects
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUi(state)
                }
            }
        }
    }

    private fun updateUi(state: HomeUiState) {
        // Loading
        binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        // Recent Projects
        recentAdapter.submitList(state.recentProjects)

        // Category selection
        categoryAdapter.setSelectedCategory(state.selectedCategory)

        // Explore Templates
        exploreAdapter.submitList(state.exploreTemplates)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
