package com.jetpack.stickify.data.source.local

import android.content.Context
import android.graphics.Color
import com.jetpack.stickify.data.source.local.converter.GsonProvider
import com.jetpack.stickify.data.source.local.dao.ProjectDao
import com.jetpack.stickify.domain.model.AddLayerAction
import com.jetpack.stickify.domain.model.BorderStyle
import com.jetpack.stickify.domain.model.BuiltinAsset
import com.jetpack.stickify.domain.model.CanvasSpec
import com.jetpack.stickify.domain.model.ChangeBorderAction
import com.jetpack.stickify.domain.model.ChangeSpeedAction
import com.jetpack.stickify.domain.model.DecorationCategory
import com.jetpack.stickify.domain.model.DecorationLayer
import com.jetpack.stickify.domain.model.EditHistory
import com.jetpack.stickify.domain.model.Playback
import com.jetpack.stickify.domain.model.PlaybackSpeed
import com.jetpack.stickify.domain.model.ProjectContent
import com.jetpack.stickify.domain.model.ProjectEntity
import com.jetpack.stickify.domain.model.ProjectOrigin
import com.jetpack.stickify.domain.model.ProjectType
import com.jetpack.stickify.domain.model.Transform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service khởi tạo dữ liệu mẫu (Seed Data) sử dụng các tài nguyên thực tế có sẵn trong thư mục assets/.
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectDao: ProjectDao
) {

    suspend fun seedDatabaseIfEmpty() = withContext(Dispatchers.IO) {
        try {
            if (projectDao.getProjectCount() > 0) return@withContext

            val now = System.currentTimeMillis()

            // 1. Copy các file ảnh asset thực tế làm thumbnail xem trước
            val thumb1Path = copyAssetToThumbnail(context, "backgrounds/pack_default/bg_gradient_pastel.png", "sample_thumb_1.png")
            val thumb2Path = copyAssetToThumbnail(context, "stickers/pack_comic/ic_sample_01.png", "sample_thumb_2.png")
            val thumb3Path = copyAssetToThumbnail(context, "stickers/pack_comic/ic_sample_02.png", "sample_thumb_3.png")
            val thumb4Path = copyAssetToThumbnail(context, "backgrounds/pack_default/bg_holographic.png", "sample_thumb_4.png")

            // 2. Project 1: "Sticker Pastel" (Sử dụng bg_gradient_pastel và ic_sample_01 từ assets)
            val bgLayer1 = DecorationLayer(
                id = "layer_bg_1",
                transform = Transform(cx = 256f, cy = 256f, scale = 1f),
                visible = true,
                asset = BuiltinAsset(assetId = "bg_gradient_pastel.png", packId = "pack_default"),
                category = DecorationCategory.DRAWN
            )
            val stickerLayer1 = DecorationLayer(
                id = "layer_stk_1",
                transform = Transform(cx = 256f, cy = 256f, scale = 1f),
                visible = true,
                asset = BuiltinAsset(assetId = "ic_sample_01.png", packId = "pack_comic"),
                category = DecorationCategory.LABEL
            )
            val content1 = ProjectContent(
                canvas = CanvasSpec(width = 512, height = 512),
                layers = listOf(bgLayer1, stickerLayer1),
                border = BorderStyle(thickness = 8f, spacing = 2f, colorArgb = Color.WHITE)
            )
            val history1 = EditHistory(
                undo = listOf(
                    AddLayerAction(layer = bgLayer1, index = 0),
                    AddLayerAction(layer = stickerLayer1, index = 1)
                ),
                redo = emptyList()
            )
            val project1 = ProjectEntity(
                id = "sample_project_1",
                name = "Sticker Pastel",
                type = ProjectType.STICKER,
                origin = ProjectOrigin.CREATED,
                thumbnailPath = thumb1Path,
                createdAt = now,
                updatedAt = now,
                contentJson = GsonProvider.gson.toJson(content1),
                historyJson = GsonProvider.gson.toJson(history1)
            )

            // 3. Project 2: "Comic Style" (Sử dụng ic_sample_02 từ assets)
            val stickerLayer2 = DecorationLayer(
                id = "layer_stk_2",
                transform = Transform(cx = 256f, cy = 256f, scale = 1.2f),
                visible = true,
                asset = BuiltinAsset(assetId = "ic_sample_02.png", packId = "pack_comic"),
                category = DecorationCategory.DRAWN
            )
            val border2 = BorderStyle(thickness = 12f, spacing = 4f, colorArgb = Color.BLACK)
            val content2 = ProjectContent(
                canvas = CanvasSpec(width = 512, height = 512),
                layers = listOf(stickerLayer2),
                border = border2
            )
            val history2 = EditHistory(
                undo = listOf(
                    AddLayerAction(layer = stickerLayer2, index = 0),
                    ChangeBorderAction(before = null, after = border2)
                ),
                redo = emptyList()
            )
            val project2 = ProjectEntity(
                id = "sample_project_2",
                name = "Comic Style",
                type = ProjectType.STICKER,
                origin = ProjectOrigin.CREATED,
                thumbnailPath = thumb2Path,
                createdAt = now - 86400000L,
                updatedAt = now - 86400000L,
                contentJson = GsonProvider.gson.toJson(content2),
                historyJson = GsonProvider.gson.toJson(history2)
            )

            // 4. Project 3: "Comic Action" (GIF / Playback / Undo & Redo)
            val stickerLayer3 = DecorationLayer(
                id = "layer_stk_3",
                transform = Transform(cx = 256f, cy = 256f, scale = 1.3f, rotationDeg = 15f),
                visible = true,
                asset = BuiltinAsset(assetId = "ic_sample_03.png", packId = "pack_comic"),
                category = DecorationCategory.LABEL
            )
            val content3 = ProjectContent(
                canvas = CanvasSpec(width = 512, height = 512),
                layers = listOf(stickerLayer3),
                border = BorderStyle(thickness = 6f, spacing = 2f, colorArgb = Color.YELLOW),
                playback = Playback(speed = PlaybackSpeed.X2, loop = true)
            )
            val history3 = EditHistory(
                undo = listOf(
                    AddLayerAction(layer = stickerLayer3, index = 0),
                    ChangeSpeedAction(before = PlaybackSpeed.X1, after = PlaybackSpeed.X2)
                ),
                redo = listOf(
                    ChangeBorderAction(
                        before = BorderStyle(thickness = 6f, spacing = 2f, colorArgb = Color.YELLOW),
                        after = BorderStyle(thickness = 10f, spacing = 2f, colorArgb = Color.RED)
                    )
                )
            )
            val project3 = ProjectEntity(
                id = "sample_project_3",
                name = "Comic Action",
                type = ProjectType.GIF,
                origin = ProjectOrigin.TEMPLATE,
                templateId = "t1",
                thumbnailPath = thumb3Path,
                createdAt = now - 172800000L,
                updatedAt = now - 172800000L,
                contentJson = GsonProvider.gson.toJson(content3),
                historyJson = GsonProvider.gson.toJson(history3)
            )

            // 5. Project 4: "Holographic Vibe" (Animated Sticker)
            val holoLayer = DecorationLayer(
                id = "layer_holo_1",
                transform = Transform(cx = 256f, cy = 256f, scale = 1f),
                visible = true,
                asset = BuiltinAsset(assetId = "bg_holographic.png", packId = "pack_default"),
                category = DecorationCategory.DRAWN
            )
            val content4 = ProjectContent(
                canvas = CanvasSpec(width = 512, height = 512),
                layers = listOf(holoLayer),
                playback = Playback(speed = PlaybackSpeed.X4, loop = true)
            )
            val history4 = EditHistory(
                undo = listOf(AddLayerAction(layer = holoLayer, index = 0)),
                redo = emptyList()
            )
            val project4 = ProjectEntity(
                id = "sample_project_4",
                name = "Holographic Vibe",
                type = ProjectType.ANIMATED_STICKER,
                origin = ProjectOrigin.CREATED,
                thumbnailPath = thumb4Path,
                createdAt = now - 259200000L,
                updatedAt = now - 259200000L,
                contentJson = GsonProvider.gson.toJson(content4),
                historyJson = GsonProvider.gson.toJson(history4)
            )

            projectDao.insertProject(project1)
            projectDao.insertProject(project2)
            projectDao.insertProject(project3)
            projectDao.insertProject(project4)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun copyAssetToThumbnail(context: Context, assetPath: String, outputFileName: String): String {
        return try {
            val thumbnailsDir = File(context.filesDir, "thumbnails")
            if (!thumbnailsDir.exists()) {
                thumbnailsDir.mkdirs()
            }
            val outFile = File(thumbnailsDir, outputFileName)
            if (!outFile.exists()) {
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            outFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            "recent_1"
        }
    }
}
