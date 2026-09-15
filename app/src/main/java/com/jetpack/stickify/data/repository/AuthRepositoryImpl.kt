package com.jetpack.stickify.data.repository

import com.jetpack.stickify.domain.model.User
import com.jetpack.stickify.domain.repository.AuthRepository
import kotlinx.coroutines.delay

class AuthRepositoryImpl : AuthRepository {
    override suspend fun login(username: String, password: String): Result<User> {
        // Giả lập gọi API
        delay(1000)
        return if (username == "admin" && password == "123456") {
            Result.success(User("1", "admin", "admin@stickify.com"))
        } else {
            Result.failure(Exception("Sai tài khoản hoặc mật khẩu"))
        }
    }
}
