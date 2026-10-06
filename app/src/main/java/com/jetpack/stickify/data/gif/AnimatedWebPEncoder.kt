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

            // Strip 12-byte RIFF header ("RIFF" + 4-byte size + "WEBP") if present
            val payload = if (webpBytes.size > 12 &&
                webpBytes[0] == 'R'.code.toByte() &&
                webpBytes[1] == 'I'.code.toByte() &&
                webpBytes[2] == 'F'.code.toByte() &&
                webpBytes[3] == 'F'.code.toByte()
            ) {
                webpBytes.copyOfRange(12, webpBytes.size)
            } else {
                webpBytes
            }
            framePayloads.add(payload)
        }

        var bodySize = 18 + 14
        for (payload in framePayloads) {
            bodySize += (24 + payload.size)
        }

        val fileOutputStream = FileOutputStream(outFile)
        val buffer = ByteBuffer.allocate(12 + bodySize).order(ByteOrder.LITTLE_ENDIAN)

        // 1. RIFF Header
        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.putInt(bodySize)
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
            put24LE(buffer, width - 1) // Width - 1
            put24LE(buffer, height - 1) // Height - 1
            put24LE(buffer, delayMs) // Duration in ms

            // Frame flags
            buffer.put(0.toByte())

            // Frame payload (VP8L chunk)
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
