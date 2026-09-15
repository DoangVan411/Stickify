package com.jetpack.stickify.domain.repository

import com.jetpack.stickify.domain.model.User

interface AuthRepository {
    suspend fun login(username: String, password: String): Result<User>
}
