package com.jetpack.stickify.domain.usecase

import com.jetpack.stickify.domain.repository.KeyboardStickerRepository
import javax.inject.Inject

class AddStickerToKeyboardUseCase @Inject constructor(
    private val repository: KeyboardStickerRepository
) {
    suspend operator fun invoke(uriString: String, animated: Boolean, sourceId: String?) =
        repository.addFromUri(uriString, animated, sourceId)
}
