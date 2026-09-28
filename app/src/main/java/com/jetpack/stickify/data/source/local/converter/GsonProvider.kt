package com.jetpack.stickify.data.source.local.converter

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.jetpack.stickify.domain.model.*

/**
 * Single source of truth để tạo Gson instance cấu hình polymorphism cho AssetRef, Layer, và EditAction.
 * Đảm bảo đăng ký đầy đủ tất cả các lớp con để Room TypeConverter deserialize chính xác không bị rỗng/lỗi.
 */
object GsonProvider {

    val gson: Gson by lazy {
        val assetRefAdapter = RuntimeTypeAdapterFactory.of(AssetRef::class.java, "type")
            .registerSubtype(BuiltinAsset::class.java, "BuiltinAsset")
            .registerSubtype(CustomAsset::class.java, "CustomAsset")
            .registerSubtype(RemoteAsset::class.java, "RemoteAsset")

        val layerAdapter = RuntimeTypeAdapterFactory.of(Layer::class.java, "type")
            .registerSubtype(SubjectLayer::class.java, "SubjectLayer")
            .registerSubtype(TextLayer::class.java, "TextLayer")
            .registerSubtype(DecorationLayer::class.java, "DecorationLayer")
            .registerSubtype(EffectLayer::class.java, "EffectLayer")

        val editActionAdapter = RuntimeTypeAdapterFactory.of(EditAction::class.java, "type")
            .registerSubtype(AddLayerAction::class.java, "AddLayerAction")
            .registerSubtype(RemoveLayerAction::class.java, "RemoveLayerAction")
            .registerSubtype(UpdateLayerAction::class.java, "UpdateLayerAction")
            .registerSubtype(ReorderLayerAction::class.java, "ReorderLayerAction")
            .registerSubtype(ChangeBorderAction::class.java, "ChangeBorderAction")
            .registerSubtype(ChangeSpeedAction::class.java, "ChangeSpeedAction")

        GsonBuilder()
            .registerTypeAdapterFactory(assetRefAdapter)
            .registerTypeAdapterFactory(layerAdapter)
            .registerTypeAdapterFactory(editActionAdapter)
            .create()
    }
}
