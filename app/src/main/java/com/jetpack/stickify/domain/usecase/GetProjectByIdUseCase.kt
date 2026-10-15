package com.jetpack.stickify.domain.usecase

import com.jetpack.stickify.domain.model.EditorSession
import com.jetpack.stickify.domain.model.StickerProject
import com.jetpack.stickify.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * UseCase lấy StickerProject theo ID từ Repository và phục hồi EditorSession.
 */
class GetProjectByIdUseCase @Inject constructor(
    private val repository: ProjectRepository
) {
    fun execute(id: String): Flow<StickerProject?> {
        return repository.getProjectById(id)
    }

    fun getEditorSession(id: String): Flow<EditorSession?> {
        return repository.getProjectById(id).map { project ->
            project?.let {
                EditorSession(
                    content = it.content,
                    history = it.history
                )
            }
        }
    }
}
