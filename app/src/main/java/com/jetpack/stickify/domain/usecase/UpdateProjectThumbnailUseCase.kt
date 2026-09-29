package com.jetpack.stickify.domain.usecase

import android.graphics.Bitmap
import com.jetpack.stickify.domain.repository.ProjectRepository
import javax.inject.Inject

/**
 * UseCase lưu ảnh thumbnail xuống bộ nhớ trong và cập nhật thumbnailPath cùng updatedAt cho Project trong Room DB.
 */
class UpdateProjectThumbnailUseCase @Inject constructor(
    private val repository: ProjectRepository
) {
    suspend operator fun invoke(projectId: String, bitmap: Bitmap): Result<String> {
        return repository.updateProjectThumbnail(projectId, bitmap)
    }
}
