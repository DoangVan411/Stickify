package com.jetpack.stickify.di


import com.jetpack.stickify.data.repository.AiStickerRepositoryImpl
import com.jetpack.stickify.domain.repository.AiStickerRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun bindAiStickerRepository(impl: AiStickerRepositoryImpl): AiStickerRepository

    companion object {
        // Emulator: 10.0.2.2 | Máy thật: IP LAN của PC, hoặc `adb reverse tcp:8080 tcp:8080` rồi dùng http://localhost:8080
        @Provides
        @Named("aiStickerBaseUrl")
        fun provideAiStickerBaseUrl(): String = "https://stickify-backend-814946056318.us-central1.run.app"
    }
}