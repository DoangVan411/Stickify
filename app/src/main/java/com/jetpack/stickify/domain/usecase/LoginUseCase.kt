package com.jetpack.stickify.domain.usecase

import com.jetpack.stickify.domain.model.User
import com.jetpack.stickify.domain.repository.AuthRepository

class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(username: String, password: String): Result<User> {
        return repository.login(username, password)
    }
}
