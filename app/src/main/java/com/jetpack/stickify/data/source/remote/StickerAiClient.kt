package com.jetpack.stickify.data.source.remote

import android.content.Context
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named

sealed interface AiGenerationState {
    data class Progress(val percent: Int, val message: String) : AiGenerationState
    /** Video đã được ghi ra file cache (KHÔNG giữ chuỗi base64 vài MB trong state). */
    data class Complete(val videoFile: File) : AiGenerationState
    data class Error(val code: Int, val message: String) : AiGenerationState
    data class Frames(val webp: List<ByteArray>, val fps: Int) : AiGenerationState

}



/**
 * Mỗi lần generate() = 1 socket riêng, tự đóng khi xong / lỗi / bị huỷ.
 * Sửa so với bản cũ:
 *  - Không còn StateFlow "dính": Complete/Error cũ bị phát lại mỗi lần Activity về STARTED.
 *  - Không connect() sớm rồi mất request nếu socket chưa kết nối xong: emit ngay trong EVENT_CONNECT.
 *  - Giải mã base64 + ghi file ở thread socket, không chạy trên Main.
 */
class StickerAiClient @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("aiStickerBaseUrl") private val baseUrl: String
) {

    fun generate(imageBytes: ByteArray, prompt: String): Flow<AiGenerationState> = callbackFlow {
        val finished = AtomicBoolean(false)
        fun finish(state: AiGenerationState) {
            if (finished.compareAndSet(false, true)) {
                trySend(state)
                close()
            }
        }

        val options = IO.Options().apply {
            transports = arrayOf("websocket")
            reconnection = false
            forceNew = true
            timeout = 15_000
        }
        val socket = IO.socket(URI(baseUrl), options)

        socket.on(Socket.EVENT_CONNECT) {
            val payload = JSONObject()
                .put("imageBase64", Base64.encodeToString(imageBytes, Base64.NO_WRAP))
                .put("prompt", prompt)
            socket.emit("generate_sticker", payload)
            trySend(AiGenerationState.Progress(2, "Submitting request..."))
        }

        socket.on("progress_update") { args ->
            (args.firstOrNull() as? JSONObject)?.let {
                trySend(AiGenerationState.Progress(it.optInt("percent", 0), it.optString("message", "")))
            }
        }

        socket.on("generation_complete") { args ->
            val o = args.firstOrNull() as? JSONObject
            val arr = o?.optJSONArray("frames")
            if (arr == null || arr.length() == 0) { finish(AiGenerationState.Error(500, "Dữ liệu trả về rỗng")); return@on }
            val list = List(arr.length()) { arr.get(it) as ByteArray }
            finish(AiGenerationState.Frames(list, o.optInt("fps", 12)))
        }

        socket.on("generation_error") { args ->
            val o = args.firstOrNull() as? JSONObject
            finish(AiGenerationState.Error(o?.optInt("code", 500) ?: 500, o?.optString("message") ?: "Lỗi không xác định"))
        }

        socket.on(Socket.EVENT_CONNECT_ERROR) { args ->
            Log.e(TAG, "connect_error: ${args.contentToString()}")
            finish(AiGenerationState.Error(0, "Không kết nối được máy chủ AI"))
        }
        socket.on(Socket.EVENT_DISCONNECT) {
            finish(AiGenerationState.Error(0, "Mất kết nối máy chủ AI"))
        }

        socket.connect()

        // Collector bị huỷ (bấm Back / VM clear) -> ngắt socket -> server huỷ job Veo
        awaitClose {
            socket.off()
            socket.disconnect()
            socket.close()
        }
    }

    private companion object { const val TAG = "StickerAiClient" }
}