package com.jetpack.stickify.data.source.local

import com.jetpack.stickify.domain.model.ExploreCategory
import com.jetpack.stickify.domain.model.FavoriteSticker
import com.jetpack.stickify.domain.model.ProjectEntity
import com.jetpack.stickify.domain.model.ProjectOrigin
import com.jetpack.stickify.domain.model.ProjectType
import com.jetpack.stickify.domain.model.StickerPack
import com.jetpack.stickify.domain.model.StickerTemplate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fake data source cung cấp mock data cho HomeScreen.
 * Sau này sẽ thay bằng Room DB hoặc Remote API.
 */
@Singleton
class FakeProjectDataSource @Inject constructor() {

    fun getRecentProjects(): List<ProjectEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            ProjectEntity(
                id = "1",
                name = "Happy run",
                type = ProjectType.STICKER,
                origin = ProjectOrigin.CREATED,
                thumbnailPath = "recent_1",
                createdAt = now,
                updatedAt = now
            ),
            ProjectEntity(
                id = "2",
                name = "Happy girl",
                type = ProjectType.STICKER,
                origin = ProjectOrigin.TEMPLATE,
                thumbnailPath = "recent_2",
                createdAt = now - 2 * 24 * 60 * 60 * 1000L,
                updatedAt = now - 2 * 24 * 60 * 60 * 1000L
            ),
            ProjectEntity(
                id = "3",
                name = "Cool dog",
                type = ProjectType.GIF,
                origin = ProjectOrigin.CREATED,
                thumbnailPath = "recent_3",
                createdAt = now - 14 * 24 * 60 * 60 * 1000L,
                updatedAt = now - 14 * 24 * 60 * 60 * 1000L
            ),
            ProjectEntity(
                id = "4",
                name = "Funny cat",
                type = ProjectType.ANIMATED_STICKER,
                origin = ProjectOrigin.TEMPLATE,
                thumbnailPath = "recent_4",
                createdAt = now - 30 * 24 * 60 * 60 * 1000L,
                updatedAt = now - 30 * 24 * 60 * 60 * 1000L
            )
        )
    }

    fun getExploreTemplates(): List<StickerTemplate> {
        return listOf(
            StickerTemplate(
                id = "t1",
                title = "Surprised!",
                type = ProjectType.GIF,
                tags = listOf("cute", "meme"),
                thumbnailUrl = "explore_1",
                isAnimated = true
            ),
            StickerTemplate(
                id = "t2",
                title = "Hi!",
                type = ProjectType.STICKER,
                tags = listOf("cute", "stickers"),
                thumbnailUrl = "explore_2"
            ),
            StickerTemplate(
                id = "t3",
                title = "Oke!",
                type = ProjectType.STICKER,
                tags = listOf("cute", "stickers"),
                thumbnailUrl = "explore_3"
            ),
            StickerTemplate(
                id = "t4",
                title = "Surprised!",
                type = ProjectType.GIF,
                tags = listOf("meme", "gifs"),
                thumbnailUrl = "explore_4",
                isAnimated = true
            ),
            StickerTemplate(
                id = "t5",
                title = "Hi!",
                type = ProjectType.STICKER,
                tags = listOf("cute", "stickers"),
                thumbnailUrl = "explore_5"
            ),
            StickerTemplate(
                id = "t6",
                title = "Oke!",
                type = ProjectType.STICKER,
                tags = listOf("meme", "stickers"),
                thumbnailUrl = "explore_6"
            ),
            StickerTemplate(
                id = "t7",
                title = "Surprised!",
                type = ProjectType.GIF,
                tags = listOf("meme", "gifs"),
                thumbnailUrl = "explore_7",
                isAnimated = true
            ),
            StickerTemplate(
                id = "t8",
                title = "Hi!",
                type = ProjectType.STICKER,
                tags = listOf("cute", "stickers"),
                thumbnailUrl = "explore_8"
            )
        )
    }

    fun getExploreTemplatesByCategory(category: ExploreCategory): List<StickerTemplate> {
        val all = getExploreTemplates()
        return when (category) {
            ExploreCategory.ALL -> all
            ExploreCategory.GIFS -> all.filter { it.type == ProjectType.GIF || it.isAnimated }
            ExploreCategory.STICKERS -> all.filter { it.type == ProjectType.STICKER && !it.isAnimated }
            ExploreCategory.CUTE -> all.filter { it.tags.contains("cute") }
            ExploreCategory.MEME -> all.filter { it.tags.contains("meme") }
        }
    }

    fun getFavoriteStickers(): List<FavoriteSticker> {
        return listOf(
            FavoriteSticker(id = "fav_1", name = "Happy run", thumbnailPath = "recent_1", type = ProjectType.STICKER),
            FavoriteSticker(id = "fav_2", name = "Happy girl", thumbnailPath = "recent_2", type = ProjectType.STICKER),
            FavoriteSticker(id = "fav_3", name = "Cool dog", thumbnailPath = "recent_3", type = ProjectType.STICKER),
            FavoriteSticker(id = "fav_4", name = "Cute kitten", thumbnailPath = "recent_1", type = ProjectType.STICKER),
            FavoriteSticker(id = "fav_5", name = "Sweet girl", thumbnailPath = "recent_2", type = ProjectType.STICKER),
            FavoriteSticker(id = "fav_6", name = "Puppy smile", thumbnailPath = "recent_3", type = ProjectType.STICKER)
        )
    }

    fun getStickerPacks(): List<StickerPack> {
        return listOf(
            StickerPack(
                id = "pack_1",
                name = "Vũ trụ dễ thương",
                itemCount = 24,
                type = ProjectType.STICKER
            ),
            StickerPack(
                id = "pack_2",
                name = "Biểu cảm hài hước",
                itemCount = 18,
                type = ProjectType.STICKER
            ),
            StickerPack(
                id = "pack_3",
                name = "Động vật siêu quậy",
                itemCount = 12,
                type = ProjectType.STICKER
            )
        )
    }
}
