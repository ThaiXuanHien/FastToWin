package com.hienthai.fastowin.server.avatars

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.roundToInt

data class NormalizedAvatar(val bytes: ByteArray, val width: Int, val height: Int, val contentType: String = "image/jpeg")

/** Reject dimensions from the header before ImageIO allocates the decoded raster. */
class AvatarImageNormalizer {
    fun normalize(base64: String): NormalizedAvatar? = normalize(base64, MAX_BYTES)

    // Historical WS payloads could decode slightly above 45 KiB. The output and raster limits stay identical.
    internal fun normalizeLegacy(base64: String): NormalizedAvatar? = normalize(base64, 64 * 1024)

    private fun normalize(base64: String, maximumInputBytes: Int): NormalizedAvatar? {
        val maximumEncoded = ((maximumInputBytes + 2) / 3) * 4
        if (base64.length > maximumEncoded + 32) return null
        val prefix = if (base64.startsWith("data:")) base64.substringBefore(',') else null
        if (prefix != null && prefix !in setOf("data:image/jpeg;base64", "data:image/png;base64")) return null
        val encoded = if (prefix == null) base64 else base64.substringAfter(',', "")
        if (encoded.length > maximumEncoded) return null
        return try {
            val bytes = Base64.getDecoder().decode(encoded)
            if (bytes.isEmpty() || bytes.size > maximumInputBytes) return null
            val mime = when {
                bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"
                bytes.size >= 8 && bytes.take(8).toByteArray().contentEquals(byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10)) -> "image/png"
                else -> return null
            }
            if (prefix != null && prefix != "data:$mime;base64") return null
            MemoryCacheImageInputStream(bytes.inputStream()).use { input ->
                val readers = ImageIO.getImageReaders(input)
                if (!readers.hasNext()) return null
                val reader = readers.next()
                try {
                    reader.input = input
                    if (reader.formatName.lowercase() !in setOf("jpeg", "jpg", "png")) return null
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    if (width !in 1..4096 || height !in 1..4096 || width.toLong() * height > 8_000_000L) return null
                    val source = reader.read(0) ?: return null
                    val scale = minOf(1.0, 512.0 / maxOf(width, height))
                    var targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
                    var targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
                    repeat(5) {
                        val target = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
                        target.createGraphics().let { graphics ->
                            try {
                                graphics.color = Color.WHITE
                                graphics.fillRect(0, 0, targetWidth, targetHeight)
                                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                                graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null)
                            } finally { graphics.dispose() }
                        }
                        for (quality in listOf(0.85f, 0.7f, 0.55f, 0.4f)) {
                            val output = ByteArrayOutputStream()
                            val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
                            try {
                                MemoryCacheImageOutputStream(output).use { stream ->
                                    writer.output = stream
                                    val parameters = writer.defaultWriteParam
                                    parameters.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                                    parameters.compressionQuality = quality
                                    writer.write(null, javax.imageio.IIOImage(target, null, null), parameters)
                                }
                            } finally { writer.dispose() }
                            if (output.size() <= MAX_BYTES) return NormalizedAvatar(output.toByteArray(), targetWidth, targetHeight)
                        }
                        targetWidth = (targetWidth * 0.75).roundToInt().coerceAtLeast(1)
                        targetHeight = (targetHeight * 0.75).roundToInt().coerceAtLeast(1)
                    }
                    null
                } finally { reader.dispose() }
            }
        } catch (_: Exception) { null }
    }

    companion object {
        const val MAX_BYTES = 45 * 1024
    }
}
