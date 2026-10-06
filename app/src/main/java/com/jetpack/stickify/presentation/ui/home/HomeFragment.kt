package com.jetpack.stickify.presentation.ui.home

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.jetpack.stickify.R
import com.jetpack.stickify.databinding.FragmentHomeBinding
import com.jetpack.stickify.domain.model.ExploreCategory
import com.jetpack.stickify.presentation.ui.edit_sticker.StickerEditActivity
import com.jetpack.stickify.presentation.ui.home.adapter.ExploreCategoryAdapter
import com.jetpack.stickify.presentation.ui.home.adapter.ExploreTemplateAdapter
import com.jetpack.stickify.presentation.ui.home.adapter.HorizontalSpaceItemDecoration
import com.jetpack.stickify.presentation.ui.home.adapter.RecentAction
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
        setupDialogListeners()
        setupRecyclerViews()
        setupClickListeners()
        observeUiState()
    }

    private fun setupDialogListeners() {
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

        childFragmentManager.setFragmentResultListener(
            ConfirmDeleteProjectDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val projectId = bundle.getString(ConfirmDeleteProjectDialogFragment.EXTRA_PROJECT_ID)
            if (!projectId.isNullOrEmpty()) {
                viewModel.deleteProject(projectId)
                Toast.makeText(context, "Đã xóa dự án", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerViews() {
        // Recent Projects - Horizontal (vừa vặn 3 item trên màn hình)
        recentAdapter = RecentProjectAdapter(
            onItemClick = { projectId ->
                val intent = Intent(requireContext(), StickerEditActivity::class.java).apply {
                    putExtra(StickerEditActivity.EXTRA_PROJECT_ID, projectId)
                }
                startActivity(intent)
            },
            onActionClick = { project, action ->
                when (action) {
                    RecentAction.TOGGLE_FAVORITE -> {
                        viewModel.toggleBookmark(project.id, !project.isBookmarked)
                        val msg = if (!project.isBookmarked) "Đã thêm vào yêu thích" else "Đã xóa khỏi yêu thích"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    RecentAction.RENAME -> {
                        val dialog = EditNameDialogFragment.newInstance(project.id, project.name)
                        dialog.show(childFragmentManager, EditNameDialogFragment.TAG)
                    }
                    RecentAction.DELETE -> {
                        val dialog = ConfirmDeleteProjectDialogFragment.newInstance(project.id, project.name)
                        dialog.show(childFragmentManager, ConfirmDeleteProjectDialogFragment.TAG)
                    }
                }
            }
        )
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
            val intent = Intent(requireContext(), com.jetpack.stickify.presentation.ui.gallery.GalleryActivity::class.java)
            startActivity(intent)
        }
        binding.ivCreateGif.setOnClickListener {
            binding.cardCreateGif.performClick()
        }

        binding.cardCreateSticker.setOnClickListener {
            val intent = Intent(requireContext(), com.jetpack.stickify.presentation.ui.gallery.GalleryActivity::class.java)
            startActivity(intent)
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
        val hasRecent = state.recentProjects.isNotEmpty()
        binding.rvRecentProjects.isVisible = hasRecent
        binding.tvRecentEmpty.isVisible = !hasRecent
        binding.tvSeeAll.isVisible = hasRecent
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
