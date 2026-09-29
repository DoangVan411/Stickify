package com.jetpack.stickify.domain.usecase

import com.jetpack.stickify.domain.model.FavoriteSticker
import com.jetpack.stickify.domain.repository.ProjectRepository
import javax.inject.Inject

/**
 * Use case lấy danh sách sticker yêu thích cho màn hình Bộ sưu tập.
 */
class GetFavoritesUseCase @Inject constructor(
    private val repository: ProjectRepository
) {
    suspend operator fun invoke(): Result<List<FavoriteSticker>> {
        return repository.getFavoriteStickers()
    }
}
