package com.jetpack.stickify.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jetpack.stickify.data.source.local.converter.ProjectConverters
import com.jetpack.stickify.data.source.local.dao.ProjectDao
import com.jetpack.stickify.data.source.local.dao.StickerPackDao
import com.jetpack.stickify.domain.model.ProjectEntity
import com.jetpack.stickify.domain.model.StickerPackEntity

/**
 * AppDatabase quản lý lưu trữ dữ liệu bền vững của ứng dụng Stickify.
 */
@Database(entities = [ProjectEntity::class, StickerPackEntity::class], version = 2, exportSchema = false)
@TypeConverters(ProjectConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun stickerPackDao(): StickerPackDao
}
