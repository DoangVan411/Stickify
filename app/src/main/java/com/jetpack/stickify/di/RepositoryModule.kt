package com.jetpack.stickify.di

import com.jetpack.stickify.data.repository.ProjectRepositoryImpl
import com.jetpack.stickify.data.repository.StickerRepositoryImpl
import com.jetpack.stickify.domain.repository.ProjectRepository
import com.jetpack.stickify.domain.repository.StickerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module để bind repository interfaces với implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProjectRepository(
        impl: ProjectRepositoryImpl
    ): ProjectRepository

    @Binds
    abstract fun bindStickerRepository(
        implementation: StickerRepositoryImpl
    ): StickerRepository
}