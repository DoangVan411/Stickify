package com.jetpack.stickify.domain.usecase

import com.jetpack.stickify.domain.model.ProjectEntity
import com.jetpack.stickify.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * UseCase lấy danh sách project gần đây.
 */
class GetRecentProjectsUseCase @Inject constructor(
    private val repository: ProjectRepository
) {
    suspend operator fun invoke(): Result<List<ProjectEntity>> {
        return repository.getRecentProjects()
    }

    fun getFlow(): Flow<List<ProjectEntity>> {
        return repository.getRecentProjectsFlow()
    }
}
