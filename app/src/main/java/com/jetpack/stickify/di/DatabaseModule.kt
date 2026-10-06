package com.jetpack.stickify.di

import android.content.Context
import androidx.room.Room
import com.jetpack.stickify.data.source.local.AppDatabase
import com.jetpack.stickify.data.source.local.dao.AssetDao
import com.jetpack.stickify.data.source.local.dao.ProjectDao
import com.jetpack.stickify.data.source.local.dao.StickerPackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module cung cấp Room Database và DAO.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "stickify_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideProjectDao(database: AppDatabase): ProjectDao {
        return database.projectDao()
    }
    @Provides
    fun provideStickerPackDao(database: AppDatabase): StickerPackDao {
        return database.stickerPackDao()
    }
    @Provides
    fun provideAssetDao(database: AppDatabase): AssetDao {
        return database.assetDao()
    }
    @Provides
    fun provideKeyboardDao(db: AppDatabase) = db.stickerDao()
}
