package com.jetpack.stickify.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jetpack.stickify.data.source.local.converter.ProjectConverters
import com.jetpack.stickify.data.source.local.dao.AssetDao
import com.jetpack.stickify.data.source.local.dao.KeyboardStickerDao
import com.jetpack.stickify.data.source.local.dao.ProjectDao
import com.jetpack.stickify.data.source.local.dao.StickerPackDao
import com.jetpack.stickify.data.source.local.dao.TemplateDao
import com.jetpack.stickify.domain.model.AssetEntity
import com.jetpack.stickify.domain.model.KeyboardStickerEntity
import com.jetpack.stickify.domain.model.ProjectEntity
import com.jetpack.stickify.domain.model.StickerPackEntity
import com.jetpack.stickify.domain.model.StickerTemplateEntity

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sticker_templates ADD COLUMN contentJson TEXT NOT NULL DEFAULT ''")
        db.execSQL("DELETE FROM sticker_templates")
    }
}

/**
 * AppDatabase quản lý lưu trữ dữ liệu bền vững của ứng dụng Stickify.
 */
@Database(
    entities = [
        ProjectEntity::class,
        StickerPackEntity::class,
        AssetEntity::class,
        KeyboardStickerEntity::class,
        StickerTemplateEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(ProjectConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun stickerPackDao(): StickerPackDao
    abstract fun assetDao(): AssetDao
    abstract fun stickerDao(): KeyboardStickerDao
    abstract fun templateDao(): TemplateDao
}
