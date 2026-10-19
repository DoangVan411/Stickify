package com.jetpack.stickify.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.jetpack.stickify.data.gif.AiImageEncoder
import com.jetpack.stickify.data.processor.AiVideoFrameExtractor
import com.jetpack.stickify.data.gif.AiStickerExporter
import com.jetpack.stickify.data.source.remote.AiGenerationState
import com.jetpack.stickify.data.source.remote.StickerAiClient
import com.jetpack.stickify.domain.model.AiAnimation
import com.jetpack.stickify.domain.model.AiStickerStep
import com.jetpack.stickify.domain.repository.AiStickerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AiStickerRepositoryImpl @Inject constructor(
    private val client: StickerAiClient,
    private val encoder: AiImageEncoder,
    private val extractor: AiVideoFrameExtractor,
    private val exporter: AiStickerExporter
) : AiStickerRepository {

    override fun generate(source: Bitmap, prompt: String): Flow<AiStickerStep> = flow {
        emit(AiStickerStep.Progress(1, "Đang chuẩn bị ảnh..."))
        val jpeg = encoder.encode(source)

        client.generate(jpeg, buildPrompt(prompt)).collect { state ->
            when (state) {
                is AiGenerationState.Progress -> emit(AiStickerStep.Progress(state.percent.coerceAtMost(95), state.message))
                is AiGenerationState.Error -> emit(AiStickerStep.Failed(state.message))
                is AiGenerationState.Frames -> {
                    emit(AiStickerStep.Progress(97, "Đang xử lý..."))
                    val frames = coroutineScope {
                        state.webp.map { async(Dispatchers.Default) { BitmapFactory.decodeByteArray(it, 0, it.size) } }
                            .awaitAll().filterNotNull()
                    }
                    emit(if (frames.isEmpty()) AiStickerStep.Failed("Không đọc được frame nào")
                    else AiStickerStep.Done(AiAnimation(frames, 1000 / state.fps)))
                }

                else -> {}
            }
        }
    }.flowOn(Dispatchers.Default)

    override suspend fun exportAnimated(frames: List<Bitmap>, frameDelayMs: Int): String =
        exporter.export(frames, frameDelayMs)

    private fun buildPrompt(userPrompt: String) =
        "$userPrompt. The character animates in place with a smooth looping motion. " +
                "Keep the plain white background unchanged, no camera movement."
}