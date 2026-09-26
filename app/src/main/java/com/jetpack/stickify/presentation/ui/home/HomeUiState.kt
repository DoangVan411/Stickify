package com.jetpack.stickify.presentation.ui.home

import com.jetpack.stickify.domain.model.ExploreCategory
import com.jetpack.stickify.domain.model.ProjectEntity
import com.jetpack.stickify.domain.model.StickerTemplate

/**
 * UI State cho HomeScreen.
 * Sử dụng pattern Unidirectional Data Flow.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val recentProjects: List<ProjectEntity> = emptyList(),
    val exploreTemplates: List<StickerTemplate> = emptyList(),
    val selectedCategory: ExploreCategory = ExploreCategory.ALL,
    val error: String? = null
)
