package com.jetpack.stickify.domain.usecase

import com.jetpack.stickify.domain.model.ProjectEntity
import com.jetpack.stickify.domain.repository.ProjectRepository
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
}
