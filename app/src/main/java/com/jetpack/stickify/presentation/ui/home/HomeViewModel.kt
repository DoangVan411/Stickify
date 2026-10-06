package com.jetpack.stickify.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetpack.stickify.domain.model.ExploreCategory
import com.jetpack.stickify.domain.repository.ProjectRepository
import com.jetpack.stickify.domain.usecase.GetExploreTemplatesUseCase
import com.jetpack.stickify.domain.usecase.GetRecentProjectsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
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
    private val getExploreTemplatesUseCase: GetExploreTemplatesUseCase,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeRecentProjects()
        loadExploreTemplates()
    }

    private fun observeRecentProjects() {
        viewModelScope.launch {
            // Đảm bảo dữ liệu mẫu ban đầu được khởi tạo từ Seeder nếu DB rỗng
            getRecentProjectsUseCase()

            // Lắng nghe dữ liệu theo thời gian thực để đưa vào RecentProjectAdapter
            getRecentProjectsUseCase.getFlow()
                .catch { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
                .collectLatest { projects ->
                    // Thêm isLoading = false để ẩn ProgressBar trên HomeFragment
                    _uiState.update { it.copy(recentProjects = projects, isLoading = false) }
                }
        }
    }

    private fun loadExploreTemplates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

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

        // Bật lại trạng thái loading khi đổi tab Category
        _uiState.update { it.copy(selectedCategory = category, isLoading = true) }

        viewModelScope.launch {
            val result = getExploreTemplatesUseCase(category)
            result.onSuccess { templates ->
                // Tắt loading sau khi load xong danh sách Template theo thể loại mới
                _uiState.update { it.copy(exploreTemplates = templates, isLoading = false) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.message, isLoading = false) }
            }
        }
    }

    fun toggleBookmark(projectId: String, isBookmarked: Boolean) {
        viewModelScope.launch {
            projectRepository.toggleBookmark(projectId, isBookmarked)
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)
        }
    }

    fun updateProjectName(projectId: String, newName: String) {
        viewModelScope.launch {
            projectRepository.updateProjectName(projectId, newName)
        }
    }

    fun refresh() {
        loadExploreTemplates()
    }
}