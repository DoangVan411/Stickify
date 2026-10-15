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
                val t1Content = """
                    {"aiAnimations":[],"border":{"colorArgb":-1,"spacing":0.0,"thickness":11.0},"canvas":{"height":512,"width":512},"layers":[{"type":"SubjectLayer","cartoonCorePath":"","cutoutPath":"","id":"layer_subject_main","isTemplatePlaceholder":true,"mediaKind":"IMAGE","source":{"type":"CustomAsset","relativePath":"projects/proj_1792056656126/subject_source.png"},"strokeColorArgb":0,"strokeWidthRatio":0.0,"style":"ORIGINAL","styledPath":"projects/proj_1792056656126/subject_styled_1792056784667.png","transform":{"cx":259.90625,"cy":258.24826,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056686530_6508.png"},"category":"CUSTOM","id":"layer_dec_6d1250a6-7fc3-4109-a382-1ea4ba6fd54d","transform":{"cx":98.65624,"cy":100.54864,"flipX":false,"opacity":1.0,"rotationDeg":-27.978464,"scale":1.2778145},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056691849_3135.png"},"category":"CUSTOM","id":"layer_dec_8ea308fd-0213-4777-822c-0ec4ef711285","transform":{"cx":90.531235,"cy":353.63882,"flipX":false,"opacity":1.0,"rotationDeg":-23.825956,"scale":1.0912219},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056700605_2450.png"},"category":"CUSTOM","id":"layer_dec_7ba03181-694c-4660-aca1-35673827a2b6","transform":{"cx":370.84372,"cy":119.49821,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056719765_9183.png"},"category":"DRAWN","id":"ee12215d-ea37-4c5e-a094-7cd1eeafe9d1","transform":{"cx":316.78125,"cy":402.75348,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.47296026},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056723016_5122.png"},"category":"DRAWN","id":"56f9839e-604f-45f4-88c1-9b093f9dac0a","transform":{"cx":105.21875,"cy":185.63542,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.43445387},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056725606_4872.png"},"category":"DRAWN","id":"e3e9eb45-33a0-4912-b350-da0dd0e40915","transform":{"cx":242.25,"cy":432.62326,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.38885647},"visible":true},{"type":"TextLayer","align":"CENTER","bold":true,"boxWidthRatio":1.0,"colorArgb":-27392,"content":"Hmmmm!!!","fontId":"bold|fill","fontSizeRatio":1.0,"id":"layer_text_1792056755189","italic":false,"strokeColorArgb":0,"strokeWidthRatio":0.0,"transform":{"cx":250.37498,"cy":464.1249,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true}],"playback":{"animationType":"BOUNCE","endTrimMs":8000,"loop":true,"speed":"X1","startTrimMs":0}}
                """.trimIndent()
                // Template 2 (4+ items)
                val t2Content =
                    """
                    {"aiAnimations":[],"border":{"colorArgb":-1,"spacing":20.0,"thickness":30.0},"canvas":{"height":512,"width":512},"layers":[{"type":"SubjectLayer","cartoonCorePath":"","cutoutPath":"","id":"layer_subject_main","isTemplatePlaceholder":true,"mediaKind":"IMAGE","source":{"type":"CustomAsset","relativePath":"projects/proj_1792060333205/subject_source.png"},"strokeColorArgb":0,"strokeWidthRatio":0.0,"style":"ORIGINAL","styledPath":"projects/proj_1792060333205/subject_styled_1792060453543.png","transform":{"cx":256.0,"cy":256.0,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true},{"type":"DecorationLayer","asset":{"type":"BuiltinAsset","assetId":"dec_heart.png","packId":"pack_default"},"category":"DRAWN","id":"3321927b-d535-492f-bbbb-4ea151668bfa","transform":{"cx":334.4375,"cy":117.54514,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.4611402},"visible":true},{"type":"DecorationLayer","asset":{"type":"BuiltinAsset","assetId":"dec_heart.png","packId":"pack_default"},"category":"DRAWN","id":"b18cfb9f-86cf-4c21-8681-3ee76c379003","transform":{"cx":408.6026,"cy":112.99729,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.4260514},"visible":true},{"type":"DecorationLayer","asset":{"type":"BuiltinAsset","assetId":"dec_heart.png","packId":"pack_default"},"category":"DRAWN","id":"9ece64a2-005f-47d6-84e1-e7e5c041eecd","transform":{"cx":443.7202,"cy":181.63054,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.37226695},"visible":true},{"type":"DecorationLayer","asset":{"type":"BuiltinAsset","assetId":"dec_heart.png","packId":"pack_default"},"category":"DRAWN","id":"2b9a4bf1-5833-4ff7-b5c8-78f30249816e","transform":{"cx":439.9469,"cy":258.11624,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.41766578},"visible":true},{"type":"DecorationLayer","asset":{"type":"BuiltinAsset","assetId":"dec_heart.png","packId":"pack_default"},"category":"DRAWN","id":"79cfd56a-408e-4b67-9f87-5cf4a06e0347","transform":{"cx":381.8797,"cy":338.72293,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.37226918},"visible":true},{"type":"DecorationLayer","asset":{"type":"BuiltinAsset","assetId":"dec_heart.png","packId":"pack_default"},"category":"DRAWN","id":"17be6559-3ce6-4592-ab96-ae72ed4af26e","transform":{"cx":324.6797,"cy":403.30267,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.3458803},"visible":true},{"type":"DecorationLayer","asset":{"type":"BuiltinAsset","assetId":"dec_heart.png","packId":"pack_default"},"category":"DRAWN","id":"6fc06a79-468f-449d-bb05-6810a1a79723","transform":{"cx":273.1508,"cy":469.88727,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.38906437},"visible":true},{"type":"DecorationLayer","asset":{"type":"BuiltinAsset","assetId":"dec_heart.png","packId":"pack_default"},"category":"DRAWN","id":"b1e774ab-4626-4f13-85db-524e11d5c503","transform":{"cx":236.0,"cy":139.38542,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.40143836},"visible":true},{"type":"DecorationLayer","asset":{"type":"BuiltinAsset","assetId":"dec_heart.png","packId":"pack_default"},"category":"DRAWN","id":"4b9e14fc-2312-4c12-8780-377b5344c019","transform":{"cx":160.98438,"cy":114.32047,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.428124},"visible":true},{"type":"DecorationLayer","asset":{"type":"BuiltinAsset","assetId":"dec_heart.png","packId":"pack_default"},"category":"DRAWN","id":"9eccb2f4-ba0f-4298-b7fa-a34f1a7ef96f","transform":{"cx":85.9375,"cy":159.84142,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.4675113},"visible":true},{"type":"DecorationLayer","asset":{"type":"BuiltinAsset","assetId":"dec_heart.png","packId":"pack_default"},"category":"DRAWN","id":"f69b8c51-0fce-4f7d-8582-fd35d25af3c9","transform":{"cx":37.464066,"cy":236.87978,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.3657319},"visible":true},{"type":"DecorationLayer","asset":{"type":"BuiltinAsset","assetId":"dec_heart.png","packId":"pack_default"},"category":"DRAWN","id":"4915ea7c-6c1b-4809-a34e-1828fef18329","transform":{"cx":35.01407,"cy":345.40347,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.48995385},"visible":true},{"type":"DecorationLayer","asset":{"type":"BuiltinAsset","assetId":"dec_heart.png","packId":"pack_default"},"category":"DRAWN","id":"12560f12-f314-4442-9807-ef8ce3b9aa82","transform":{"cx":103.66876,"cy":424.491,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.48304325},"visible":true},{"type":"DecorationLayer","asset":{"type":"BuiltinAsset","assetId":"dec_heart.png","packId":"pack_default"},"category":"DRAWN","id":"12a9f2b9-02ae-4d8e-8d57-fd78a54ad42d","transform":{"cx":188.53282,"cy":470.13077,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.49636206},"visible":true},{"type":"DecorationLayer","asset":{"type":"BuiltinAsset","assetId":"dec_crown.png","packId":"pack_default"},"category":"DRAWN","id":"e87c0c1a-ae69-4350-b6e0-71df28a8630c","transform":{"cx":260.21875,"cy":121.078125,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.40542027},"visible":true},{"type":"DecorationLayer","asset":{"type":"BuiltinAsset","assetId":"smiling_cloud.png","packId":"pack_template"},"category":"LABEL","id":"layer_dec_1792060450632","transform":{"cx":235.21873,"cy":49.15972,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true}],"playback":{"animationType":"NONE","endTrimMs":8000,"loop":true,"speed":"X1","startTrimMs":0}}                    
                    """.trimIndent()

                // Template 3 (4+ items)
                val t3Content = """
                    {"aiAnimations":[],"border":{"colorArgb":-1,"spacing":20.0,"thickness":30.0},"canvas":{"height":512,"width":512},"layers":[{"type":"SubjectLayer","cartoonCorePath":"","cutoutPath":"","id":"layer_subject_main","isTemplatePlaceholder":true,"mediaKind":"IMAGE","source":{"type":"CustomAsset","relativePath":"projects/proj_1792055837389/subject_source.png"},"strokeColorArgb":0,"strokeWidthRatio":0.0,"style":"ORIGINAL","styledPath":"projects/proj_1792055837389/subject_styled_1792055933639.png","transform":{"cx":255.84375,"cy":256.64236,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792055843476_8142.png"},"category":"CUSTOM","id":"layer_dec_3daaf3c0-c989-4f02-8a51-2da3055f7023","transform":{"cx":381.0,"cy":133.63022,"flipX":false,"opacity":1.0,"rotationDeg":-7.913979,"scale":0.57839},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792055848745_131.png"},"category":"CUSTOM","id":"layer_dec_b84e0d5e-b852-4b96-9096-61507caefe29","transform":{"cx":85.53125,"cy":166.06944,"flipX":false,"opacity":1.0,"rotationDeg":-14.298462,"scale":1.147763},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792055852895_8957.png"},"category":"CUSTOM","id":"layer_dec_9e6e0137-d060-4886-b2f6-ba8f7f505b79","transform":{"cx":388.5,"cy":358.7777,"flipX":false,"opacity":1.0,"rotationDeg":5.608744,"scale":0.7855075},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792055864140_5283.png"},"category":"CUSTOM","id":"layer_dec_ea1ae7d6-161d-45e0-a6c1-746364e8990f","transform":{"cx":129.59373,"cy":415.62674,"flipX":false,"opacity":1.0,"rotationDeg":5.2785068,"scale":0.53862816},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792055890170_8895.png"},"category":"CUSTOM","id":"layer_dec_7d7efa38-20a7-4fd7-8f78-4214d6fa4219","transform":{"cx":303.34378,"cy":97.01571,"flipX":false,"opacity":1.0,"rotationDeg":0.11644999,"scale":0.5685274},"visible":true},{"type":"TextLayer","align":"CENTER","bold":true,"boxWidthRatio":1.0,"colorArgb":-27392,"content":"Hehe","fontId":"bold|fill","fontSizeRatio":1.0,"id":"layer_text_1792055906149","italic":false,"strokeColorArgb":0,"strokeWidthRatio":0.0,"transform":{"cx":256.7813,"cy":459.3074,"flipX":false,"opacity":1.0,"rotationDeg":-1.8116568,"scale":1.1122267},"visible":true}],"playback":{"animationType":"NONE","endTrimMs":8000,"loop":true,"speed":"X1","startTrimMs":0}}
                """.trimIndent()

                // Template 4 (4+ items)
                val t4Content = """
                    {"aiAnimations":[],"border":{"colorArgb":-1,"spacing":0.0,"thickness":11.0},"canvas":{"height":512,"width":512},"layers":[{"type":"SubjectLayer","cartoonCorePath":"","cutoutPath":"","id":"layer_subject_main","isTemplatePlaceholder":true,"mediaKind":"IMAGE","source":{"type":"CustomAsset","relativePath":"projects/proj_1792056656126/subject_source.png"},"strokeColorArgb":0,"strokeWidthRatio":0.0,"style":"ORIGINAL","styledPath":"projects/proj_1792056656126/subject_styled_1792056784667.png","transform":{"cx":259.90625,"cy":258.24826,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056686530_6508.png"},"category":"CUSTOM","id":"layer_dec_6d1250a6-7fc3-4109-a382-1ea4ba6fd54d","transform":{"cx":98.65624,"cy":100.54864,"flipX":false,"opacity":1.0,"rotationDeg":-27.978464,"scale":1.2778145},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056691849_3135.png"},"category":"CUSTOM","id":"layer_dec_8ea308fd-0213-4777-822c-0ec4ef711285","transform":{"cx":90.531235,"cy":353.63882,"flipX":false,"opacity":1.0,"rotationDeg":-23.825956,"scale":1.0912219},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056700605_2450.png"},"category":"CUSTOM","id":"layer_dec_7ba03181-694c-4660-aca1-35673827a2b6","transform":{"cx":370.84372,"cy":119.49821,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056719765_9183.png"},"category":"DRAWN","id":"ee12215d-ea37-4c5e-a094-7cd1eeafe9d1","transform":{"cx":316.78125,"cy":402.75348,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.47296026},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056723016_5122.png"},"category":"DRAWN","id":"56f9839e-604f-45f4-88c1-9b093f9dac0a","transform":{"cx":105.21875,"cy":185.63542,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.43445387},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056725606_4872.png"},"category":"DRAWN","id":"e3e9eb45-33a0-4912-b350-da0dd0e40915","transform":{"cx":242.25,"cy":432.62326,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.38885647},"visible":true},{"type":"TextLayer","align":"CENTER","bold":true,"boxWidthRatio":1.0,"colorArgb":-27392,"content":"Hmmmm!!!","fontId":"bold|fill","fontSizeRatio":1.0,"id":"layer_text_1792056755189","italic":false,"strokeColorArgb":0,"strokeWidthRatio":0.0,"transform":{"cx":250.37498,"cy":464.1249,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true}],"playback":{"animationType":"BOUNCE","endTrimMs":8000,"loop":true,"speed":"X1","startTrimMs":0}}
                """.trimIndent()

                // Template 5 (4+ items)
                val t5Content = """
                    {"aiAnimations":[],"border":{"colorArgb":-1,"spacing":20.0,"thickness":30.0},"canvas":{"height":512,"width":512},"layers":[{"type":"SubjectLayer","cartoonCorePath":"","cutoutPath":"","id":"layer_subject_main","isTemplatePlaceholder":true,"mediaKind":"IMAGE","source":{"type":"CustomAsset","relativePath":"projects/proj_1792055954665/subject_source.png"},"strokeColorArgb":0,"strokeWidthRatio":0.0,"style":"ORIGINAL","styledPath":"projects/proj_1792055954665/subject_styled_1792056040241.png","transform":{"cx":258.03125,"cy":257.9271,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792055961202_4349.png"},"category":"CUSTOM","id":"layer_dec_fcdb979e-bfd6-4a98-bcf7-c7e75de87e72","transform":{"cx":111.62501,"cy":127.52778,"flipX":false,"opacity":1.0,"rotationDeg":-6.05713,"scale":0.4643003},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792055964702_9606.png"},"category":"CUSTOM","id":"layer_dec_eb265526-3fa4-420a-9871-20d3aecca6f1","transform":{"cx":311.46875,"cy":104.40275,"flipX":false,"opacity":1.0,"rotationDeg":6.0928874,"scale":0.55291206},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792055967606_6883.png"},"category":"CUSTOM","id":"layer_dec_d8a1cb59-7269-4f53-808e-b9010db8e848","transform":{"cx":149.4375,"cy":377.40622,"flipX":false,"opacity":1.0,"rotationDeg":-10.832601,"scale":0.5301872},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792055971420_9322.png"},"category":"CUSTOM","id":"layer_dec_d5815a74-145e-4b68-a831-574cc4e000ea","transform":{"cx":374.28125,"cy":377.40634,"flipX":false,"opacity":1.0,"rotationDeg":4.591217,"scale":0.45900562},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792055977729_189.png"},"category":"CUSTOM","id":"layer_dec_9aa9e916-faaf-4d79-9d88-90e8e622b079","transform":{"cx":252.25003,"cy":431.0434,"flipX":false,"opacity":1.0,"rotationDeg":-0.59240896,"scale":0.48373464},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792055985339_9083.png"},"category":"CUSTOM","id":"layer_dec_03a1c379-bb7a-4998-a1d6-23b6f5155f9c","transform":{"cx":211.15625,"cy":110.18403,"flipX":false,"opacity":1.0,"rotationDeg":-12.470652,"scale":0.41418195},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056007063_7712.png"},"category":"DRAWN","id":"8c23234f-fbde-4fbf-9c21-8bfc3a4078ff","transform":{"cx":387.25,"cy":184.99306,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.3803911},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056007063_7712.png"},"category":"DRAWN","id":"f4bfb5d4-707a-4ff3-8fd2-b7a5ca43c2fa","transform":{"cx":431.6398,"cy":245.88216,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.38273346},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056007929_235.png"},"category":"DRAWN","id":"109a3f1f-2f4d-4176-845e-99785bbe4c8b","transform":{"cx":428.65625,"cy":328.88196,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.37692022},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056007929_235.png"},"category":"DRAWN","id":"5ffeed48-9b02-4350-8f83-fa7ab4ab87ed","transform":{"cx":414.83215,"cy":403.21036,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.41762125},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056008771_6678.png"},"category":"DRAWN","id":"8ee103b7-f84e-49da-9db6-f2e89d2d8045","transform":{"cx":97.5625,"cy":199.76736,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.38636294},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056008771_6678.png"},"category":"DRAWN","id":"e79ac511-6b4b-43db-a738-3cba66e0a22d","transform":{"cx":92.39906,"cy":279.22058,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.42647535},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056010279_3603.png"},"category":"DRAWN","id":"83d5dcf9-7cf7-428d-8216-91535d456281","transform":{"cx":318.5,"cy":449.00348,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.4083489},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056010921_379.png"},"category":"DRAWN","id":"31cf3b15-2717-499b-a99b-81fe707e82c7","transform":{"cx":181.15625,"cy":453.5,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.44860867},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056013860_3973.png"},"category":"CUSTOM","id":"layer_dec_f487adaa-54c9-4ac3-bf5c-3aee582424b1","transform":{"cx":403.65634,"cy":233.51738,"flipX":false,"opacity":1.0,"rotationDeg":0.6405525,"scale":0.61640245},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056032933_4888.png"},"category":"CUSTOM","id":"layer_dec_5ec1b1d7-1868-4478-b127-cf4de57f6e87","transform":{"cx":178.96873,"cy":59.116333,"flipX":false,"opacity":1.0,"rotationDeg":-5.0792413,"scale":0.46478042},"visible":true}],"playback":{"animationType":"NONE","endTrimMs":8000,"loop":true,"speed":"X1","startTrimMs":0}}
                """.trimIndent()

                // Template 6 (4+ items)
                val t6Content = """
                    {"aiAnimations":[],"border":{"colorArgb":-1,"spacing":20.0,"thickness":30.0},"canvas":{"height":512,"width":512},"layers":[{"type":"SubjectLayer","cartoonCorePath":"","cutoutPath":"","id":"layer_subject_main","isTemplatePlaceholder":true,"mediaKind":"IMAGE","source":{"type":"CustomAsset","relativePath":"projects/proj_1792056072085/subject_source.png"},"strokeColorArgb":0,"strokeWidthRatio":0.0,"style":"ORIGINAL","styledPath":"projects/proj_1792056072085/subject_styled_1792056131116.png","transform":{"cx":256.3125,"cy":256.32117,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056086557_4687.png"},"category":"CUSTOM","id":"layer_dec_8b8efd15-fc49-4a92-bb12-eec926567e8b","transform":{"cx":373.65625,"cy":126.243095,"flipX":false,"opacity":1.0,"rotationDeg":-1.7030473,"scale":0.53548235},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056086557_4687.png"},"category":"CUSTOM","id":"6fe4684a-a035-4f34-ae44-c4f5fa40aa94","transform":{"cx":379.58334,"cy":257.36105,"flipX":false,"opacity":1.0,"rotationDeg":-1.7439452,"scale":0.53847885},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056086557_4687.png"},"category":"CUSTOM","id":"9c2ef003-0b2b-468f-a270-d4e5486d0a6a","transform":{"cx":160.50003,"cy":338.1302,"flipX":false,"opacity":1.0,"rotationDeg":-1.7924212,"scale":0.5384767},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056086557_4687.png"},"category":"CUSTOM","id":"644ba330-88f2-4321-a032-fab5a80caf13","transform":{"cx":253.14587,"cy":431.99133,"flipX":false,"opacity":1.0,"rotationDeg":-1.7924212,"scale":0.5384767},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056086557_4687.png"},"category":"CUSTOM","id":"a5389ccc-c372-4322-8fee-82b93621968f","transform":{"cx":360.32297,"cy":383.89066,"flipX":false,"opacity":1.0,"rotationDeg":-1.7924212,"scale":0.5384767},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056103666_6053.png"},"category":"CUSTOM","id":"layer_dec_90402513-3582-441f-8b9f-a8597557db45","transform":{"cx":165.53127,"cy":382.22394,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056103666_6053.png"},"category":"CUSTOM","id":"691ffee9-a02a-44a7-91ce-69d912ef10ba","transform":{"cx":374.27087,"cy":375.87677,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056116867_4204.png"},"category":"CUSTOM","id":"layer_dec_bc50ef36-c9ac-4f59-beb8-83ac440d07cb","transform":{"cx":78.1875,"cy":126.564224,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056116867_4204.png"},"category":"CUSTOM","id":"cf23f0ce-5ce3-431a-9f4d-f23234a2423d","transform":{"cx":443.9584,"cy":111.22396,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true}],"playback":{"animationType":"NONE","endTrimMs":8000,"loop":true,"speed":"X1","startTrimMs":0}}
                """.trimIndent()

                // Template 7 (4+ items)
                val t7Content = """
                    {"aiAnimations":[],"border":{"colorArgb":-1,"spacing":0.0,"thickness":11.0},"canvas":{"height":512,"width":512},"layers":[{"type":"SubjectLayer","cartoonCorePath":"","cutoutPath":"","id":"layer_subject_main","isTemplatePlaceholder":true,"mediaKind":"IMAGE","source":{"type":"CustomAsset","relativePath":"projects/proj_1792056656126/subject_source.png"},"strokeColorArgb":0,"strokeWidthRatio":0.0,"style":"ORIGINAL","styledPath":"projects/proj_1792056656126/subject_styled_1792056784667.png","transform":{"cx":259.90625,"cy":258.24826,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056686530_6508.png"},"category":"CUSTOM","id":"layer_dec_6d1250a6-7fc3-4109-a382-1ea4ba6fd54d","transform":{"cx":98.65624,"cy":100.54864,"flipX":false,"opacity":1.0,"rotationDeg":-27.978464,"scale":1.2778145},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056691849_3135.png"},"category":"CUSTOM","id":"layer_dec_8ea308fd-0213-4777-822c-0ec4ef711285","transform":{"cx":90.531235,"cy":353.63882,"flipX":false,"opacity":1.0,"rotationDeg":-23.825956,"scale":1.0912219},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056700605_2450.png"},"category":"CUSTOM","id":"layer_dec_7ba03181-694c-4660-aca1-35673827a2b6","transform":{"cx":370.84372,"cy":119.49821,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056719765_9183.png"},"category":"DRAWN","id":"ee12215d-ea37-4c5e-a094-7cd1eeafe9d1","transform":{"cx":316.78125,"cy":402.75348,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.47296026},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056723016_5122.png"},"category":"DRAWN","id":"56f9839e-604f-45f4-88c1-9b093f9dac0a","transform":{"cx":105.21875,"cy":185.63542,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.43445387},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056725606_4872.png"},"category":"DRAWN","id":"e3e9eb45-33a0-4912-b350-da0dd0e40915","transform":{"cx":242.25,"cy":432.62326,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":0.38885647},"visible":true},{"type":"TextLayer","align":"CENTER","bold":true,"boxWidthRatio":1.0,"colorArgb":-27392,"content":"Hmmmm!!!","fontId":"bold|fill","fontSizeRatio":1.0,"id":"layer_text_1792056755189","italic":false,"strokeColorArgb":0,"strokeWidthRatio":0.0,"transform":{"cx":250.37498,"cy":464.1249,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true}],"playback":{"animationType":"BOUNCE","endTrimMs":8000,"loop":true,"speed":"X1","startTrimMs":0}}
                """.trimIndent()

                // Template 8 (4+ items)
                val t8Content = """
                    {"aiAnimations":[],"border":{"colorArgb":-1,"spacing":20.0,"thickness":30.0},"canvas":{"height":512,"width":512},"layers":[{"type":"SubjectLayer","cartoonCorePath":"","cutoutPath":"","id":"layer_subject_main","isTemplatePlaceholder":true,"mediaKind":"IMAGE","source":{"type":"CustomAsset","relativePath":"projects/proj_1792056072085/subject_source.png"},"strokeColorArgb":0,"strokeWidthRatio":0.0,"style":"ORIGINAL","styledPath":"projects/proj_1792056072085/subject_styled_1792056131116.png","transform":{"cx":256.3125,"cy":256.32117,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056086557_4687.png"},"category":"CUSTOM","id":"layer_dec_8b8efd15-fc49-4a92-bb12-eec926567e8b","transform":{"cx":373.65625,"cy":126.243095,"flipX":false,"opacity":1.0,"rotationDeg":-1.7030473,"scale":0.53548235},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056086557_4687.png"},"category":"CUSTOM","id":"6fe4684a-a035-4f34-ae44-c4f5fa40aa94","transform":{"cx":379.58334,"cy":257.36105,"flipX":false,"opacity":1.0,"rotationDeg":-1.7439452,"scale":0.53847885},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056086557_4687.png"},"category":"CUSTOM","id":"9c2ef003-0b2b-468f-a270-d4e5486d0a6a","transform":{"cx":160.50003,"cy":338.1302,"flipX":false,"opacity":1.0,"rotationDeg":-1.7924212,"scale":0.5384767},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056086557_4687.png"},"category":"CUSTOM","id":"644ba330-88f2-4321-a032-fab5a80caf13","transform":{"cx":253.14587,"cy":431.99133,"flipX":false,"opacity":1.0,"rotationDeg":-1.7924212,"scale":0.5384767},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056086557_4687.png"},"category":"CUSTOM","id":"a5389ccc-c372-4322-8fee-82b93621968f","transform":{"cx":360.32297,"cy":383.89066,"flipX":false,"opacity":1.0,"rotationDeg":-1.7924212,"scale":0.5384767},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056103666_6053.png"},"category":"CUSTOM","id":"layer_dec_90402513-3582-441f-8b9f-a8597557db45","transform":{"cx":165.53127,"cy":382.22394,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056103666_6053.png"},"category":"CUSTOM","id":"691ffee9-a02a-44a7-91ce-69d912ef10ba","transform":{"cx":374.27087,"cy":375.87677,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056116867_4204.png"},"category":"CUSTOM","id":"layer_dec_bc50ef36-c9ac-4f59-beb8-83ac440d07cb","transform":{"cx":78.1875,"cy":126.564224,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true},{"type":"DecorationLayer","asset":{"type":"CustomAsset","relativePath":"custom_decorations/custom_1792056116867_4204.png"},"category":"CUSTOM","id":"cf23f0ce-5ce3-431a-9f4d-f23234a2423d","transform":{"cx":443.9584,"cy":111.22396,"flipX":false,"opacity":1.0,"rotationDeg":0.0,"scale":1.0},"visible":true}],"playback":{"animationType":"NONE","endTrimMs":8000,"loop":true,"speed":"X1","startTrimMs":0}}

                """.trimIndent()

                val templates = listOf(
                    StickerTemplateEntity(
                        id = "t1",
                        title = "Surprised!",
                        type = ProjectType.GIF,
                        tags = listOf("cute", "meme"),
                        thumbnailUrl = "thumbnail/thumb_proj_1792056656126.png",
                        contentJson = t1Content,
                        isAnimated = true
                    ), StickerTemplateEntity(
                        id = "t2",
                        title = "Hi!",
                        type = ProjectType.STICKER,
                        tags = listOf("cute", "stickers"),
                        thumbnailUrl = "thumbnail/thumb_proj_1792055719501.png",
                        contentJson = t2Content
                    ), StickerTemplateEntity(
                        id = "t3",
                        title = "Oke!",
                        type = ProjectType.STICKER,
                        tags = listOf("cute", "stickers"),
                        thumbnailUrl = "thumbnail/thumb_proj_1792055837389.png",
                        contentJson = t3Content
                    ), StickerTemplateEntity(
                        id = "t4",
                        title = "Surprised!",
                        type = ProjectType.ANIMATED_STICKER,
                        tags = listOf("meme", "gifs"),
                        thumbnailUrl = "backgrounds/pack_default/bg_holographic.png",
                        contentJson = t4Content,
                        isAnimated = true
                    ), StickerTemplateEntity(
                        id = "t5",
                        title = "Hi!",
                        type = ProjectType.STICKER,
                        tags = listOf("cute", "stickers"),
                        thumbnailUrl = "thumbnail/thumb_proj_1792055954665.png",
                        contentJson = t5Content
                    ), StickerTemplateEntity(
                        id = "t6",
                        title = "Oke!",
                        type = ProjectType.STICKER,
                        tags = listOf("meme", "stickers"),
                        thumbnailUrl = "thumbnail/thumb_proj_1792056072085.png",
                        contentJson = t6Content
                    ), StickerTemplateEntity(
                        id = "t7",
                        title = "Surprised!",
                        type = ProjectType.ANIMATED_STICKER,
                        tags = listOf("meme", "gifs"),
                        thumbnailUrl = "stickers/pack_more/st_p_cake.png",
                        contentJson = t7Content,
                        isAnimated = true
                    ), StickerTemplateEntity(
                        id = "t8",
                        title = "Hi!",
                        type = ProjectType.STICKER,
                        tags = listOf("cute", "stickers"),
                        thumbnailUrl = "stickers/pack_more/st_cake.png",
                        contentJson = t8Content
                    )
                )
                templateDao.insertTemplates(templates)
            }

            if (projectDao.getProjectCount() > 0) return@withContext

            val now = System.currentTimeMillis()

            val thumb1Path = copyAssetToThumbnail(
                context, "backgrounds/pack_default/bg_gradient_pastel.png", "sample_thumb_1.png"
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


            projectDao.insertProject(project1)


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
