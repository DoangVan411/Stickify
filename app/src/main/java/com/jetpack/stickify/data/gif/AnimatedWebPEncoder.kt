package com.jetpack.stickify.data.gif

import android.graphics.Bitmap
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AnimatedWebPEncoder {

    fun encode(frames: List<Bitmap>, delayMs: Int, outFile: File) {
        if (frames.isEmpty()) return

        val width = frames[0].width
        val height = frames[0].height

        val framePayloads = mutableListOf<ByteArray>()
        for (frame in frames) {
            val baos = ByteArrayOutputStream()
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSLESS
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            frame.compress(format, 100, baos)
            val webpBytes = baos.toByteArray()

            // FIX LỖI 2: Quét qua file WebP tĩnh để bóc tách chính xác Chunk ảnh (ALPH, VP8, VP8L)
            // Tuyệt đối không lấy nhầm VP8X, ICCP, hay EXIF vào trong ANMF
            val payloadStream = ByteArrayOutputStream()
            var offset = 12 // Bỏ qua 12 byte RIFF header ("RIFF" + size + "WEBP")

            while (offset + 8 <= webpBytes.size) {
                val chunkId = String(webpBytes, offset, 4)
                val chunkSize = ByteBuffer.wrap(webpBytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                val chunkTotal = 8 + chunkSize + (if (chunkSize % 2 != 0) 1 else 0)

                if (chunkId == "ALPH" || chunkId == "VP8 " || chunkId == "VP8L") {
                    payloadStream.write(webpBytes, offset, chunkTotal)
                }
                offset += chunkTotal
            }
            framePayloads.add(payloadStream.toByteArray())
        }

        // FIX LỖI 1: RIFF Size phải CHUẨN. Bao gồm: 4 (WEBP) + 18 (VP8X) + 14 (ANIM) = 36 byte
        var riffSize = 4 + 18 + 14
        for (payload in framePayloads) {
            riffSize += (24 + payload.size) // 24 byte header của ANMF + payload ảnh
        }

        val fileOutputStream = FileOutputStream(outFile)
        // buffer allocate = 8 byte (RIFF + size) + phần còn lại
        val buffer = ByteBuffer.allocate(8 + riffSize).order(ByteOrder.LITTLE_ENDIAN)

        // 1. RIFF Header
        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.putInt(riffSize)
        buffer.put('W'.code.toByte())
        buffer.put('E'.code.toByte())
        buffer.put('B'.code.toByte())
        buffer.put('P'.code.toByte())

        // 2. VP8X Chunk
        buffer.put('V'.code.toByte())
        buffer.put('P'.code.toByte())
        buffer.put('8'.code.toByte())
        buffer.put('X'.code.toByte())
        buffer.putInt(10) // Chunk size
        // Flags: Animation (0x02) | Alpha (0x10) = 0x12
        buffer.put(0x12.toByte())
        buffer.put(byteArrayOf(0, 0, 0)) // Reserved
        put24LE(buffer, width - 1)
        put24LE(buffer, height - 1)

        // 3. ANIM Chunk
        buffer.put('A'.code.toByte())
        buffer.put('N'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('M'.code.toByte())
        buffer.putInt(6) // Chunk size
        buffer.putInt(0) // Background color (BGRA)
        buffer.putShort(0.toShort()) // Loop count (0 = infinite)

        // 4. ANMF Chunks for frames
        for (payload in framePayloads) {
            buffer.put('A'.code.toByte())
            buffer.put('N'.code.toByte())
            buffer.put('M'.code.toByte())
            buffer.put('F'.code.toByte())

            val chunkSize = 16 + payload.size
            buffer.putInt(chunkSize)

            put24LE(buffer, 0) // X
            put24LE(buffer, 0) // Y
            put24LE(buffer, width - 1)
            put24LE(buffer, height - 1)
            put24LE(buffer, delayMs)

            // FIX LỖI 3: Bit 1 (Disposal) = 1. (0x02).
            // Bắt buộc phải xóa frame trước đó để tránh bóng ma khi vật thể di chuyển.
            buffer.put(0x02.toByte())

            // Nạp Payload
            buffer.put(payload)
        }

        fileOutputStream.write(buffer.array())
        fileOutputStream.close()
    }

    private fun put24LE(buffer: ByteBuffer, value: Int) {
        buffer.put((value and 0xFF).toByte())
        buffer.put(((value shr 8) and 0xFF).toByte())
        buffer.put(((value shr 16) and 0xFF).toByte())
    }
}