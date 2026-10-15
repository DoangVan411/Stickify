package com.jetpack.stickify.data.repository

import com.jetpack.stickify.data.source.local.dao.AssetDao
import com.jetpack.stickify.domain.model.AssetEntity
import com.jetpack.stickify.domain.repository.AssetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetRepositoryImpl @Inject constructor(
    private val assetDao: AssetDao
) : AssetRepository {

    override suspend fun getAssetsByCategory(category: String): Result<List<AssetEntity>> = withContext(Dispatchers.IO) {
        try {
            seedDefaultAssetsIfNeeded()
            val list = assetDao.getAssetsByCategory(category)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getAssetsByCategoryFlow(category: String): Flow<List<AssetEntity>> {
        return assetDao.getAssetsByCategoryFlow(category)
    }

    override suspend fun saveAsset(asset: AssetEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            assetDao.insertAsset(asset)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAsset(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            assetDao.deleteAssetById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun seedDefaultAssetsIfNeeded() = withContext(Dispatchers.IO) {
        try {
            if (assetDao.getAssetCount() > 0) return@withContext

            val baseTime = 1000L
            var timeIndex = 0

            // 1. Nền (Backgrounds)
            val defaultBackgrounds = listOf(
                AssetEntity("bg_transparent", "BACKGROUND", "Trong suốt", "backgrounds/pack_default/bg_transparent.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("bg_gradient_pastel", "BACKGROUND", "Gradient pastel", "backgrounds/pack_default/bg_gradient_pastel.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("bg_holographic", "BACKGROUND", "Holographic", "backgrounds/pack_default/bg_holographic.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("bg_gradient_stone", "BACKGROUND", "Đá gradient", "backgrounds/pack_default/bg_gradient_stone.png", false, baseTime + (++timeIndex * 1000L))
            )

            // 2. Trang trí (Decorations / Drawn Decor)
            val defaultDecors = listOf(

                AssetEntity("dec_thug_life", "DECORATION", "Kính thug life", "decorations/pack_default/dec_thug_life.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("dec_heart", "DECORATION", "Trái tim hồng", "decorations/pack_default/dec_heart.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("dec_sparkle", "DECORATION", "Sao lấp lánh", "decorations/pack_default/dec_sparkle.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("dec_halo", "DECORATION", "Hào quang", "decorations/pack_default/dec_halo.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("dec_crown", "DECORATION", "Vương miện", "decorations/pack_default/dec_crown.png", false, baseTime + (++timeIndex * 1000L))
            )

            // 3. Nhãn (Labels / Stickers from pack_comic, pack_more, pack_template)
            val defaultLabels = listOf(
                // Drawables


                // pack_comic
                AssetEntity("comic_cat", "LABEL", "Mèo", "stickers/pack_comic/st_cat.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("comic_dog", "LABEL", "Chó", "stickers/pack_comic/st_dog.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("comic_girl", "LABEL", "Cô gái", "stickers/pack_comic/st_girl.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("comic_alien", "LABEL", "Alien", "stickers/pack_comic/st_alien.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("comic_heart", "LABEL", "Trái tim", "stickers/pack_comic/st_heart.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("comic_banana", "LABEL", "Chuối", "stickers/pack_comic/st_banana.png", false, baseTime + (++timeIndex * 1000L)),

                // pack_more
                AssetEntity("more_cake", "LABEL", "Bánh kem", "stickers/pack_more/st_cake.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("more_star", "LABEL", "Ngôi sao", "stickers/pack_more/st_star.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("more_plant", "LABEL", "Cây cảnh", "stickers/pack_more/st_plant.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("more_p_cake", "LABEL", "Bánh sinh nhật", "stickers/pack_more/st_p_cake.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("more_universe", "LABEL", "Vũ trụ", "stickers/pack_more/st_universe.png", false, baseTime + (++timeIndex * 1000L)),

                // pack_template
                AssetEntity("tpl_crown", "LABEL", "Vương miện", "stickers/pack_template/crown.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("tpl_heart", "LABEL", "Trái tim", "stickers/pack_template/heart.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("tpl_rainbow", "LABEL", "Cầu vồng", "stickers/pack_template/rainbow.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("tpl_red_bow", "LABEL", "Nơ đỏ", "stickers/pack_template/red_bow.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("tpl_starbursts", "LABEL", "Pháo hoa", "stickers/pack_template/starbursts.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("tpl_angel_wings", "LABEL", "Cánh thiên thần", "stickers/pack_template/angel_wings.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("tpl_light_streak", "LABEL", "Tia sáng", "stickers/pack_template/light_streak.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("tpl_vietnam_flag", "LABEL", "Cờ VN", "stickers/pack_template/vietnam_flag.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("tpl_y2k_sparkles", "LABEL", "Y2K", "stickers/pack_template/y2k_sparkles.png", false, baseTime + (++timeIndex * 1000L)),
                AssetEntity("tpl_smiling_cloud", "LABEL", "Mây cười", "stickers/pack_template/smiling_cloud.png", false, baseTime + (++timeIndex * 1000L))
            )

            (defaultBackgrounds + defaultDecors + defaultLabels).forEach {
                assetDao.insertAsset(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
