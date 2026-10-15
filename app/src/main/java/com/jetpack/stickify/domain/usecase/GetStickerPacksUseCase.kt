package com.jetpack.stickify.domain.usecase

import com.jetpack.stickify.domain.model.StickerPack
import com.jetpack.stickify.domain.repository.ProjectRepository
import javax.inject.Inject

/**
 * Use case lấy danh sách các bộ sticker (sticker packs) cho màn hình Bộ sưu tập.
 */
class GetStickerPacksUseCase @Inject constructor(
    private val repository: ProjectRepository
) {
    suspend operator fun invoke(): Result<List<StickerPack>> {
        return repository.getStickerPacks()
    }
}
