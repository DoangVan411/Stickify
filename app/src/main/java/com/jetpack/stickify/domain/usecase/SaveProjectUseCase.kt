package com.jetpack.stickify.domain.usecase

import com.jetpack.stickify.domain.model.EditorSession
import com.jetpack.stickify.domain.model.ProjectOrigin
import com.jetpack.stickify.domain.model.ProjectType
import com.jetpack.stickify.domain.model.StickerProject
import com.jetpack.stickify.domain.repository.ProjectRepository
import javax.inject.Inject

/**
 * UseCase lưu trạng thái hiện tại của EditorSession xuống Room Database.
 */
class SaveProjectUseCase @Inject constructor(
    private val repository: ProjectRepository
) {
    suspend operator fun invoke(
        projectId: String,
        projectName: String,
        projectType: ProjectType = ProjectType.STICKER,
        origin: ProjectOrigin = ProjectOrigin.CREATED,
        thumbnailPath: String,
        editorSession: EditorSession
    ): Result<Unit> {
        val now = System.currentTimeMillis()
        val project = StickerProject(
            id = projectId,
            name = projectName,
            type = projectType,
            origin = origin,
            thumbnailPath = thumbnailPath,
            createdAt = now,
            updatedAt = now,
            content = editorSession.content,
            history = editorSession.history
        )
        return repository.saveProject(project)
    }

    suspend fun save(project: StickerProject): Result<Unit> {
        return repository.saveProject(project)
    }
}
