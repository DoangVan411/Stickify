package com.jetpack.stickify.domain.usecase

import com.jetpack.stickify.domain.repository.ProjectRepository
import javax.inject.Inject

class ExportToWhatsAppUseCase @Inject constructor(
    private val repository: ProjectRepository
) {
    suspend operator fun invoke(packId: String): Result<Unit> {
        return repository.exportToWhatsApp(packId)
    }
}
