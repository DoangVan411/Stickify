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

    override suspend fun seedDefaultAssetsIfNeeded() = withContext(Dispatchers.IO) {
        try {
            if (assetDao.getAssetCount() > 0) return@withContext
            val now = System.currentTimeMillis()

            // 1. Nền (Backgrounds)
            val defaultBackgrounds = listOf(
                AssetEntity("bg_transparent", "BACKGROUND", "Trong suốt", "bg_transparent", false, now - 1),
                AssetEntity("bg_gradient_pastel", "BACKGROUND", "Gradient pastel", "bg_gradient_pastel", false, now - 2),
                AssetEntity("bg_holographic", "BACKGROUND", "Holographic", "bg_holographic", false, now - 3)
            )

            // 2. Trang trí (Decorations)
            val defaultDecors = listOf(
                AssetEntity("dec_thug_life", "DECORATION", "Kính thug life", "img_decor_1", false, now - 1),
                AssetEntity("dec_heart", "DECORATION", "Trái tim hồng", "img_decor_2", false, now - 2),
                AssetEntity("dec_sparkle", "DECORATION", "Sao lấp lánh", "img_decor_3", false, now - 3),
                AssetEntity("dec_halo", "DECORATION", "Hào quang", "img_decor_4", false, now - 4),
                AssetEntity("dec_crown", "DECORATION", "Vương miện", "img_decor_5", false, now - 5)
            )

            // 3. Nhãn (Labels)
            val defaultLabels = listOf(
                AssetEntity("lbl_omg", "LABEL", "OMG!", "img_sticker_01", false, now - 1),
                AssetEntity("lbl_wow", "LABEL", "WOW!", "img_sticker_02", false, now - 2),
                AssetEntity("lbl_yeah", "LABEL", "YEAH!", "img_sticker_03", false, now - 3),
                AssetEntity("lbl_heart", "LABEL", "Trái tim hồng", "img_decor_2", false, now - 4),
                AssetEntity("lbl_sparkle", "LABEL", "Sao lấp lánh", "img_decor_3", false, now - 5)
            )

            (defaultBackgrounds + defaultDecors + defaultLabels).forEach {
                assetDao.insertAsset(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
