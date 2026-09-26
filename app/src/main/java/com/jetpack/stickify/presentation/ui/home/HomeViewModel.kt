package com.jetpack.stickify.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetpack.stickify.domain.model.ExploreCategory
import com.jetpack.stickify.domain.usecase.GetExploreTemplatesUseCase
import com.jetpack.stickify.domain.usecase.GetRecentProjectsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel cho HomeScreen.
 * Quản lý state thông qua StateFlow theo pattern UDF.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getRecentProjectsUseCase: GetRecentProjectsUseCase,
    private val getExploreTemplatesUseCase: GetExploreTemplatesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Load recent projects
            val recentResult = getRecentProjectsUseCase()
            recentResult.onSuccess { projects ->
                _uiState.update { it.copy(recentProjects = projects) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.message) }
            }

            // Load explore templates
            val exploreResult = getExploreTemplatesUseCase(_uiState.value.selectedCategory)
            exploreResult.onSuccess { templates ->
                _uiState.update { it.copy(exploreTemplates = templates, isLoading = false) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.message, isLoading = false) }
            }
        }
    }

    fun onCategorySelected(category: ExploreCategory) {
        if (category == _uiState.value.selectedCategory) return

        _uiState.update { it.copy(selectedCategory = category) }

        viewModelScope.launch {
            val result = getExploreTemplatesUseCase(category)
            result.onSuccess { templates ->
                _uiState.update { it.copy(exploreTemplates = templates) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.message) }
            }
        }
    }

    fun refresh() {
        loadData()
    }
}
