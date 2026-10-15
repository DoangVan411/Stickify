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
                    source = BuiltinAsset(assetId = "st_cat.png", packId = "pack_comic"),
                    isTemplatePlaceholder = true
                )

                // Template 1 (4+ items: bg, placeholder, decor 1, text)
                val t1Content = ProjectContent(
                    canvas = CanvasSpec(width = 512, height = 512), layers = listOf(
                        DecorationLayer(
                            id = "t1_bg",
                            transform = Transform(cx = 256f, cy = 256f, scale = 1f),
                            asset = BuiltinAsset(
                                assetId = "bg_gradient_pastel.png", packId = "pack_default"
                            )
                        ), DecorationLayer(
                            id = "t1_dec1", transform = Transform(
                                cx = 110f, cy = 110f, scale = 0.8f, rotationDeg = -15f
                            ), asset = BuiltinAsset(
                                assetId = "dec_thug_life.png", packId = "pack_default"
                            )
                        ), placeholderSubject, DecorationLayer(
                            id = "t1_dec2",
                            transform = Transform(
                                cx = 410f, cy = 120f, scale = 1f, rotationDeg = 20f
                            ),
                            asset = BuiltinAsset(assetId = "st_heart.png", packId = "pack_comic")
                        ), TextLayer(
                            id = "t1_txt",
                            transform = Transform(cx = 256f, cy = 440f, scale = 1f),
                            content = "SURPRISED!",
                            colorArgb = Color.parseColor("#FFD54F")
                        )
                    ), playback = Playback(
                        speed = PlaybackSpeed.X2,
                        loop = true,
                        animationType = StickerAnimationType.BOUNCE
                    )
                )

                // Template 2 (4+ items)
                val t2Content =
                    """
                        {"aiAnimations":[],"border":{"colorArgb":-1,"spacing":0.0,"thickness":10.0},"canvas":{"height":512,"width":512},"layers":[{"type":"DecorationLayer","asset":{"type":"BuiltinAsset","assetId":"bg_holographic.png","packId":"pack_default"},"category":"DRAWN","id":"t6_bg","transform":{"cx":382.09378,"cy":219.0642,"flipX":false,"opacity":1.0,"rotationDeg":-90.45343,"scale":3.387455},"visible":true},{"type":"SubjectLayer","cartoonCorePath":"","cutoutPath":"","id":"layer_subject_placeholder","isTemplatePlaceholder":true,"mediaKind":"IMAGE","source":{"type":"BuiltinAsset","assetId":"st_cat.png","packId":"pack_comic"},"strokeColorArgb":0,"strokeWidthRatio":0.0,"style":"ORIGINAL","styledPath":"projects/proj_1791802133593/subject_styled_1791811100913.png","transform":{"cx":374.43753,"cy":294.5418,"flipX":false,"opacity":1.0,"rotationDeg":10.220959,"scale":0.7322034},"visible":true},{"type":"DecorationLayer","asset":{"type":"BuiltinAsset","assetId":"st_banana.png","packId":"pack_comic"},"category":"DRAWN","id":"t6_dec1","transform":{"cx":444.21902,"cy":258.44623,"flipX":false,"opacity":1.0,"rotationDeg":-28.341497,"scale":0.83033615},"visible":true},{"type":"TextLayer","align":"CENTER","bold":false,"boxWidthRatio":1.0,"colorArgb":-26624,"content":"Nice!","fontId":"","fontSizeRatio":1.0,"id":"t6_txt","italic":false,"strokeColorArgb":0,"strokeWidthRatio":0.0,"transform":{"cx":553.8124,"cy":394.07108,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1791802161319_5217.png"},"category":"CUSTOM","id":"layer_dec_616d8248-5216-4b1d-97df-105bb3871877","transform":{"cx":53.890594,"cy":198.18755,"flipX":false,"opacity":1.0,"rotationDeg":-16.4372,"scale":0.8244844},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1791802170710_6864.png"},"category":"CUSTOM","id":"layer_dec_027baa87-74f6-407d-bf09-871576f75ff6","transform":{"cx":341.1563,"cy":122.06776,"flipX":false,"opacity":1.0,"rotationDeg":0.8028698,"scale":0.56966},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1791802276075_1142.png"},"category":"DRAWN","id":"9684ef25-7c1f-4ca5-8606-a66a523ec1f9","transform":{"cx":575.84375,"cy":123.32639,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.3970938},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1791802276075_1142.png"},"category":"DRAWN","id":"7fe09e51-c731-4773-bbc8-aacc0dc3b127","transform":{"cx":587.4,"cy":219.54245,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.35211915},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1791802276075_1142.png"},"category":"DRAWN","id":"59efd690-c11e-4e2e-af97-5fd556d02cfd","transform":{"cx":581.64685,"cy":298.5625,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.4831649},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1791802276075_1142.png"},"category":"DRAWN","id":"d3a4a175-53b8-4365-8bcb-fbe1749c0642","transform":{"cx":549.65,"cy":384.01257,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.39671347},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1791802276075_1142.png"},"category":"DRAWN","id":"28275732-3597-4abe-a8b8-f6250e0a3714","transform":{"cx":492.0672,"cy":449.8578,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.49018943},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1791802276075_1142.png"},"category":"DRAWN","id":"ce787355-7523-4d64-ab6d-66e714c6208c","transform":{"cx":413.1625,"cy":504.16943,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.44947544},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1791802276765_6614.png"},"category":"DRAWN","id":"903b9e4d-19dd-46e0-b00f-52712e656d3f","transform":{"cx":399.75,"cy":149.6632,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.3435274},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1791802276765_6614.png"},"category":"DRAWN","id":"8589fc40-8364-4f11-84f5-5c19a4e2cf5f","transform":{"cx":297.75,"cy":181.69775,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.35047156},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1791802276765_6614.png"},"category":"DRAWN","id":"4a6ff078-934d-41f5-821e-c65525629804","transform":{"cx":230.21564,"cy":217.9815,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.4315003},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1791802276765_6614.png"},"category":"DRAWN","id":"e30903ee-ec06-4256-a441-0fd1ff9f1dd2","transform":{"cx":164.21875,"cy":279.6257,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.47661346},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1791802276765_6614.png"},"category":"DRAWN","id":"aeedcd6e-6f05-47c7-96e7-05d555f2e6ab","transform":{"cx":136.62813,"cy":358.62006,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.40351173},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1791802276765_6614.png"},"category":"DRAWN","id":"5569e34a-0b7a-4653-ba23-98e5cc38462d","transform":{"cx":182.77814,"cy":447.9339,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.40014824},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1791802276765_6614.png"},"category":"DRAWN","id":"3a907023-40f4-4817-ac4d-38211da47c19","transform":{"cx":254.61874,"cy":500.96408,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.46897754},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1791802276765_6614.png"},"category":"DRAWN","id":"458c1a23-2bbd-4f2c-ad6d-e98662987e82","transform":{"cx":337.1797,"cy":525.66925,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.47969884},"visible":true}],"playback":{"animationType":"NONE","loop":true,"speed":"X1"}}
                    """.trimIndent()

                // Template 3 (4+ items)
                val t3Content = ProjectContent(
                    canvas = CanvasSpec(width = 512, height = 512), layers = listOf(
                        DecorationLayer(
                            id = "t3_dec1",
                            transform = Transform(cx = 256f, cy = 256f, scale = 1.2f),
                            asset = BuiltinAsset(assetId = "st_p_cake.png", packId = "pack_more")
                        ), placeholderSubject, DecorationLayer(
                            id = "t3_dec2", transform = Transform(
                                cx = 80f, cy = 80f, scale = 0.8f, rotationDeg = -10f
                            ), asset = BuiltinAsset(
                                assetId = "dec_sparkle.png", packId = "pack_default"
                            )
                        ), TextLayer(
                            id = "t3_txt",
                            transform = Transform(cx = 256f, cy = 440f, scale = 1.1f),
                            content = "OK!",
                            colorArgb = Color.parseColor("#4CAF50")
                        )
                    ), border = BorderStyle(thickness = 12f, spacing = 4f, colorArgb = Color.BLACK)
                )

                // Template 4 (4+ items)
                val t4Content = ProjectContent(
                    canvas = CanvasSpec(width = 512, height = 512), layers = listOf(
                        DecorationLayer(
                            id = "t4_bg",
                            transform = Transform(cx = 256f, cy = 256f, scale = 1f),
                            asset = BuiltinAsset(
                                assetId = "bg_gradient_pastel.png", packId = "pack_default"
                            )
                        ), placeholderSubject, DecorationLayer(
                            id = "t4_dec1", transform = Transform(
                                cx = 390f, cy = 380f, scale = 1f, rotationDeg = 10f
                            ), asset = BuiltinAsset(
                                assetId = "dec_halo.png", packId = "pack_default"
                            )
                        ), DecorationLayer(
                            id = "t4_dec2",
                            transform = Transform(
                                cx = 120f, cy = 100f, scale = 0.9f, rotationDeg = -15f
                            ),
                            asset = BuiltinAsset(assetId = "st_cake.png", packId = "pack_more")
                        ), TextLayer(
                            id = "t4_txt",
                            transform = Transform(cx = 256f, cy = 440f, scale = 1f),
                            content = "OMG!",
                            colorArgb = Color.parseColor("#E91E63")
                        )
                    ), playback = Playback(
                        speed = PlaybackSpeed.X2,
                        loop = true,
                        animationType = StickerAnimationType.SPIN
                    )
                )

                // Template 5 (4+ items)
                val t5Content = ProjectContent(
                    canvas = CanvasSpec(width = 512, height = 512), layers = listOf(
                        DecorationLayer(
                            id = "t5_dec1",
                            transform = Transform(cx = 110f, cy = 110f, scale = 1f),
                            asset = BuiltinAsset(assetId = "dec_crown.png", packId = "pack_more")
                        ), placeholderSubject, DecorationLayer(
                            id = "t5_dec2",
                            transform = Transform(
                                cx = 400f, cy = 400f, scale = 0.9f, rotationDeg = -12f
                            ),
                            asset = BuiltinAsset(assetId = "st_cake.png", packId = "pack_more")
                        ), TextLayer(
                            id = "t5_txt",
                            transform = Transform(cx = 256f, cy = 440f, scale = 1.2f),
                            content = "Awesome!",
                            colorArgb = Color.parseColor("#9C27B0")
                        )
                    ), border = BorderStyle(thickness = 8f, spacing = 2f, colorArgb = Color.WHITE)
                )

                // Template 6 (4+ items)
                val t6Content = ProjectContent(
                    canvas = CanvasSpec(width = 512, height = 512), layers = listOf(
                        DecorationLayer(
                            id = "t6_bg",
                            transform = Transform(cx = 256f, cy = 256f, scale = 1f),
                            asset = BuiltinAsset(
                                assetId = "bg_holographic.png", packId = "pack_default"
                            )
                        ), placeholderSubject, DecorationLayer(
                            id = "t6_dec1",
                            transform = Transform(cx = 90f, cy = 420f, scale = 1.1f),
                            asset = BuiltinAsset(assetId = "st_banana.png", packId = "pack_comic")
                        ), DecorationLayer(
                            id = "t6_dec2",
                            transform = Transform(
                                cx = 420f, cy = 90f, scale = 0.8f, rotationDeg = 15f
                            ),
                            asset = BuiltinAsset(assetId = "st_heart.png", packId = "pack_comic")
                        ), TextLayer(
                            id = "t6_txt",
                            transform = Transform(cx = 256f, cy = 440f, scale = 1f),
                            content = "Nice!",
                            colorArgb = Color.parseColor("#FF9800")
                        )
                    )
                )

                // Template 7 (4+ items)
                val t7Content = ProjectContent(
                    canvas = CanvasSpec(width = 512, height = 512), layers = listOf(
                        placeholderSubject, DecorationLayer(
                            id = "t7_dec1", transform = Transform(
                                cx = 410f, cy = 90f, scale = 0.85f, rotationDeg = 25f
                            ), asset = BuiltinAsset(
                                assetId = "dec_thug_life.png", packId = "pack_default"
                            )
                        ), DecorationLayer(
                            id = "t7_dec2", transform = Transform(
                                cx = 90f, cy = 410f, scale = 0.85f, rotationDeg = -25f
                            ), asset = BuiltinAsset(
                                assetId = "dec_heart.png", packId = "pack_default"
                            )
                        ), DecorationLayer(
                            id = "t7_dec3",
                            transform = Transform(cx = 256f, cy = 80f, scale = 1f),
                            asset = BuiltinAsset(assetId = "dec_crown.png", packId = "pack_more")
                        ), TextLayer(
                            id = "t7_txt",
                            transform = Transform(cx = 256f, cy = 440f, scale = 1.3f),
                            content = "Wow!",
                            colorArgb = Color.parseColor("#00BCD4")
                        )
                    ), playback = Playback(
                        speed = PlaybackSpeed.X4,
                        loop = true,
                        animationType = StickerAnimationType.PULSE
                    )
                )

                // Template 8 (4+ items)
                val t8Content = ProjectContent(
                    canvas = CanvasSpec(width = 512, height = 512), layers = listOf(
                        DecorationLayer(
                            id = "t8_bg",
                            transform = Transform(cx = 256f, cy = 256f, scale = 1f),
                            asset = BuiltinAsset(
                                assetId = "bg_gradient_pastel.png", packId = "pack_default"
                            )
                        ), placeholderSubject, DecorationLayer(
                            id = "t8_dec1", transform = Transform(
                                cx = 390f, cy = 390f, scale = 1f, rotationDeg = 10f
                            ), asset = BuiltinAsset(
                                assetId = "dec_sparkle.png", packId = "pack_default"
                            )
                        ), DecorationLayer(
                            id = "t8_dec2",
                            transform = Transform(
                                cx = 100f, cy = 100f, scale = 0.8f, rotationDeg = -10f
                            ),
                            asset = BuiltinAsset(assetId = "st_heart.png", packId = "pack_comic")
                        ), TextLayer(
                            id = "t8_txt",
                            transform = Transform(cx = 256f, cy = 440f, scale = 1f),
                            content = "Sweet!",
                            colorArgb = Color.parseColor("#E91E63")
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
                    ), StickerTemplateEntity(
                        id = "t2",
                        title = "Hi!",
                        type = ProjectType.STICKER,
                        tags = listOf("cute", "stickers"),
                        thumbnailUrl = "thumbnail/thumb_proj_1791818356308.png",
                        contentJson = t2Content
                    ), StickerTemplateEntity(
                        id = "t3",
                        title = "Oke!",
                        type = ProjectType.STICKER,
                        tags = listOf("cute", "stickers"),
                        thumbnailUrl = "stickers/pack_comic/st_dog.png",
                        contentJson = GsonProvider.gson.toJson(t3Content)
                    ), StickerTemplateEntity(
                        id = "t4",
                        title = "Surprised!",
                        type = ProjectType.GIF,
                        tags = listOf("meme", "gifs"),
                        thumbnailUrl = "backgrounds/pack_default/bg_holographic.png",
                        contentJson = GsonProvider.gson.toJson(t4Content),
                        isAnimated = true
                    ), StickerTemplateEntity(
                        id = "t5",
                        title = "Hi!",
                        type = ProjectType.STICKER,
                        tags = listOf("cute", "stickers"),
                        thumbnailUrl = "stickers/pack_more/st_plant.png",
                        contentJson = GsonProvider.gson.toJson(t5Content)
                    ), StickerTemplateEntity(
                        id = "t6",
                        title = "Oke!",
                        type = ProjectType.STICKER,
                        tags = listOf("meme", "stickers"),
                        thumbnailUrl = "stickers/pack_comic/st_girl.png",
                        contentJson = GsonProvider.gson.toJson(t6Content)
                    ), StickerTemplateEntity(
                        id = "t7",
                        title = "Surprised!",
                        type = ProjectType.GIF,
                        tags = listOf("meme", "gifs"),
                        thumbnailUrl = "stickers/pack_more/st_p_cake.png",
                        contentJson = GsonProvider.gson.toJson(t7Content),
                        isAnimated = true
                    ), StickerTemplateEntity(
                        id = "t8",
                        title = "Hi!",
                        type = ProjectType.STICKER,
                        tags = listOf("cute", "stickers"),
                        thumbnailUrl = "stickers/pack_more/st_cake.png",
                        contentJson = GsonProvider.gson.toJson(t8Content)
                    )
                )
                templateDao.insertTemplates(templates)
            }

            if (projectDao.getProjectCount() > 0) return@withContext

            val now = System.currentTimeMillis()

            val thumb1Path = copyAssetToThumbnail(
                context, "backgrounds/pack_default/bg_gradient_pastel.png", "sample_thumb_1.png"
            )
            val thumb2Path = copyAssetToThumbnail(
                context, "stickers/pack_comic/st_plant.png", "sample_thumb_2.png"
            )
            val thumb3Path = copyAssetToThumbnail(
                context, "stickers/pack_comic/st_star.png", "sample_thumb_3.png"
            )
            val thumb4Path = copyAssetToThumbnail(
                context, "backgrounds/pack_default/bg_holographic.png", "sample_thumb_4.png"
            )

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
                asset = BuiltinAsset(assetId = "st_plant.png", packId = "pack_comic"),
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
                ), redo = emptyList()
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
                asset = BuiltinAsset(assetId = "st_star.png", packId = "pack_comic"),
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
                ), redo = emptyList()
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
                asset = BuiltinAsset(assetId = "st_universe.png", packId = "pack_comic"),
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
                ), redo = listOf(
                    ChangeBorderAction(
                        before = BorderStyle(
                            thickness = 6f, spacing = 2f, colorArgb = Color.YELLOW
                        ), after = BorderStyle(thickness = 10f, spacing = 2f, colorArgb = Color.RED)
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
                undo = listOf(AddLayerAction(layer = holoLayer, index = 0)), redo = emptyList()
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

    private fun copyAssetToThumbnail(
        context: Context, assetPath: String, outputFileName: String
    ): String {
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
