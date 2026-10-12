package com.jetpack.stickify.data.source.local

import android.content.Context
import android.graphics.Color
import com.jetpack.stickify.data.source.local.converter.GsonProvider
import com.jetpack.stickify.data.source.local.dao.ProjectDao
import com.jetpack.stickify.data.source.local.dao.TemplateDao
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
import com.jetpack.stickify.domain.model.StickerAnimationType
import com.jetpack.stickify.domain.model.StickerTemplateEntity
import com.jetpack.stickify.domain.model.SubjectLayer
import com.jetpack.stickify.domain.model.TextLayer
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
    private val projectDao: ProjectDao,
    private val templateDao: TemplateDao
) {

    suspend fun seedDatabaseIfEmpty() = withContext(Dispatchers.IO) {
        try {
            if (templateDao.getTemplateCount() == 0) {
                val placeholderSubject = SubjectLayer(
                    id = "layer_subject_placeholder",
                    transform = Transform(cx = 256f, cy = 256f, scale = 1f),
                    visible = true,
                    source = BuiltinAsset(assetId = "ic_sample_01.png", packId = "pack_comic"),
                    isTemplatePlaceholder = true
                )

                val t1Content = ProjectContent(
                    canvas = CanvasSpec(width = 512, height = 512),
                    layers = listOf(
                        DecorationLayer(
                            id = "layer_bg_1",
                            transform = Transform(cx = 256f, cy = 256f, scale = 1f),
                            asset = BuiltinAsset(assetId = "bg_gradient_pastel.png", packId = "pack_default")
                        ),
                        placeholderSubject,
                        TextLayer(
                            id = "layer_txt_1",
                            transform = Transform(cx = 256f, cy = 420f, scale = 1f),
                            content = "SURPRISED!",
                            colorArgb = Color.parseColor("#FFD54F")
                        )
                    ),
                    playback = Playback(speed = PlaybackSpeed.X2, loop = true, animationType = StickerAnimationType.BOUNCE)
                )

                val t2Content = ProjectContent(
                    canvas = CanvasSpec(width = 512, height = 512),
                    layers = listOf(
                        placeholderSubject,
                        DecorationLayer(
                            id = "layer_dec_2",
                            transform = Transform(cx = 380f, cy = 150f, scale = 1f),
                            asset = BuiltinAsset(assetId = "heart.png", packId = "pack_default")
                        ),
                        TextLayer(
                            id = "layer_txt_2",
                            transform = Transform(cx = 256f, cy = 430f, scale = 1f),
                            content = "Hi There!",
                            colorArgb = Color.parseColor("#2196F3")
                        )
                    ),
                    border = BorderStyle(thickness = 10f, spacing = 2f, colorArgb = Color.WHITE)
                )

                val t3Content = ProjectContent(
                    canvas = CanvasSpec(width = 512, height = 512),
                    layers = listOf(
                        placeholderSubject,
                        DecorationLayer(
                            id = "layer_dec_3",
                            transform = Transform(cx = 120f, cy = 120f, scale = 1f),
                            asset = BuiltinAsset(assetId = "ic_sample_02.png", packId = "pack_comic")
                        ),
                        TextLayer(
                            id = "layer_txt_3",
                            transform = Transform(cx = 256f, cy = 440f, scale = 1.1f),
                            content = "OKE!",
                            colorArgb = Color.parseColor("#4CAF50")
                        )
                    )
                )

                val t4Content = ProjectContent(
                    canvas = CanvasSpec(width = 512, height = 512),
                    layers = listOf(
                        DecorationLayer(
                            id = "layer_bg_4",
                            transform = Transform(cx = 256f, cy = 256f, scale = 1f),
                            asset = BuiltinAsset(assetId = "bg_holographic.png", packId = "pack_default")
                        ),
                        placeholderSubject
                    ),
                    playback = Playback(speed = PlaybackSpeed.X2, loop = true, animationType = StickerAnimationType.SPIN)
                )

                val t5Content = ProjectContent(
                    canvas = CanvasSpec(width = 512, height = 512),
                    layers = listOf(
                        placeholderSubject,
                        TextLayer(
                            id = "layer_txt_5",
                            transform = Transform(cx = 256f, cy = 100f, scale = 1.2f),
                            content = "Hello Friend!",
                            colorArgb = Color.parseColor("#E91E63")
                        )
                    ),
                    border = BorderStyle(thickness = 8f, spacing = 2f, colorArgb = Color.WHITE)
                )

                val t6Content = ProjectContent(
                    canvas = CanvasSpec(width = 512, height = 512),
                    layers = listOf(
                        placeholderSubject,
                        DecorationLayer(
                            id = "layer_dec_6",
                            transform = Transform(cx = 400f, cy = 400f, scale = 0.9f),
                            asset = BuiltinAsset(assetId = "ic_sample_03.png", packId = "pack_comic")
                        )
                    )
                )

                val t7Content = ProjectContent(
                    canvas = CanvasSpec(width = 512, height = 512),
                    layers = listOf(
                        placeholderSubject,
                        TextLayer(
                            id = "layer_txt_7",
                            transform = Transform(cx = 256f, cy = 450f, scale = 1.3f),
                            content = "WOW!",
                            colorArgb = Color.parseColor("#FF9800")
                        )
                    ),
                    playback = Playback(speed = PlaybackSpeed.X4, loop = true, animationType = StickerAnimationType.PULSE)
                )

                val t8Content = ProjectContent(
                    canvas = CanvasSpec(width = 512, height = 512),
                    layers = listOf(
                        placeholderSubject,
                        TextLayer(
                            id = "layer_txt_8",
                            transform = Transform(cx = 256f, cy = 80f, scale = 1f),
                            content = "Cute Sticker",
                            colorArgb = Color.parseColor("#9C27B0")
                        )
                    )
                )

                val templates = listOf(
                    StickerTemplateEntity(
                        id = "t1",
                        title = "Surprised!",
                        type = ProjectType.GIF,
                        tags = listOf("cute", "meme"),
                        thumbnailUrl = "backgrounds/pack_default/bg_gradient_pastel.png",
                        contentJson = GsonProvider.gson.toJson(t1Content),
                        isAnimated = true
                    ),
                    StickerTemplateEntity(
                        id = "t2",
                        title = "Hi!",
                        type = ProjectType.STICKER,
                        tags = listOf("cute", "stickers"),
                        thumbnailUrl = "stickers/pack_comic/ic_sample_01.png",
                        contentJson = GsonProvider.gson.toJson(t2Content)
                    ),
                    StickerTemplateEntity(
                        id = "t3",
                        title = "Oke!",
                        type = ProjectType.STICKER,
                        tags = listOf("cute", "stickers"),
                        thumbnailUrl = "stickers/pack_comic/ic_sample_02.png",
                        contentJson = GsonProvider.gson.toJson(t3Content)
                    ),
                    StickerTemplateEntity(
                        id = "t4",
                        title = "Surprised!",
                        type = ProjectType.GIF,
                        tags = listOf("meme", "gifs"),
                        thumbnailUrl = "backgrounds/pack_default/bg_holographic.png",
                        contentJson = GsonProvider.gson.toJson(t4Content),
                        isAnimated = true
                    ),
                    StickerTemplateEntity(
                        id = "t5",
                        title = "Hi!",
                        type = ProjectType.STICKER,
                        tags = listOf("cute", "stickers"),
                        thumbnailUrl = "stickers/pack_comic/ic_sample_03.png",
                        contentJson = GsonProvider.gson.toJson(t5Content)
                    ),
                    StickerTemplateEntity(
                        id = "t6",
                        title = "Oke!",
                        type = ProjectType.STICKER,
                        tags = listOf("meme", "stickers"),
                        thumbnailUrl = "backgrounds/pack_default/bg_gradient_pastel.png",
                        contentJson = GsonProvider.gson.toJson(t6Content)
                    ),
                    StickerTemplateEntity(
                        id = "t7",
                        title = "Surprised!",
                        type = ProjectType.GIF,
                        tags = listOf("meme", "gifs"),
                        thumbnailUrl = "stickers/pack_comic/ic_sample_01.png",
                        contentJson = GsonProvider.gson.toJson(t7Content),
                        isAnimated = true
                    ),
                    StickerTemplateEntity(
                        id = "t8",
                        title = "Hi!",
                        type = ProjectType.STICKER,
                        tags = listOf("cute", "stickers"),
                        thumbnailUrl = "backgrounds/pack_default/bg_holographic.png",
                        contentJson = GsonProvider.gson.toJson(t8Content)
                    )
                )
                templateDao.insertTemplates(templates)
            }

            if (projectDao.getProjectCount() > 0) return@withContext

            val now = System.currentTimeMillis()

            val thumb1Path = copyAssetToThumbnail(context, "backgrounds/pack_default/bg_gradient_pastel.png", "sample_thumb_1.png")
            val thumb2Path = copyAssetToThumbnail(context, "stickers/pack_comic/ic_sample_01.png", "sample_thumb_2.png")
            val thumb3Path = copyAssetToThumbnail(context, "stickers/pack_comic/ic_sample_02.png", "sample_thumb_3.png")
            val thumb4Path = copyAssetToThumbnail(context, "backgrounds/pack_default/bg_holographic.png", "sample_thumb_4.png")

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
