package com.hienthai.fastowin.server.avatars

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import javax.imageio.IIOImage
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.test.*

internal fun imageBase64(width: Int = 32, height: Int = 32, format: String = "png"): String {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    return Base64.getEncoder().encodeToString(ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray())
}

class AvatarImageNormalizerTest {
    private val normalizer = AvatarImageNormalizer()
    @Test fun `rejects invalid base64 SVG fake mime and oversized bytes`() {
        for (value in listOf("%%%%", Base64.getEncoder().encodeToString("<svg/>".toByteArray()),
            "data:image/jpeg;base64," + Base64.getEncoder().encodeToString("fake".toByteArray()),
            Base64.getEncoder().encodeToString(ByteArray(45 * 1024 + 1)))) assertNull(normalizer.normalize(value))
        assertNull(normalizer.normalize("data:image/svg+xml;base64," + imageBase64()))
    }
    @Test fun `checks dimensions before decoding a small compressed pixel bomb`() {
        assertNull(normalizer.normalize(imageBase64(4097, 1)))
        assertNull(normalizer.normalize(imageBase64(3000, 3000)))
    }
    @Test fun `normalizes PNG and JPEG into bounded decodable JPEG`() {
        for (value in listOf(imageBase64(1024, 600), imageBase64(40, 30, "jpg"))) {
            val result = assertNotNull(normalizer.normalize(value))
            assertTrue(result.bytes.size <= 45 * 1024)
            assertTrue(result.width <= 512 && result.height <= 512)
            assertEquals("image/jpeg", result.contentType)
            val decoded = ImageIO.read(result.bytes.inputStream())
            assertEquals(result.width, decoded.width)
            assertEquals(result.height, decoded.height)
            assertEquals(0xff, result.bytes[0].toInt() and 255)
            assertEquals(0xd8, result.bytes[1].toInt() and 255)
        }
    }
    @Test fun `transparent ARGB PNG is composited onto white in opaque JPEG`() {
        val source = BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB)
        // Transparent red must become white, rather than retaining hidden RGB or becoming black.
        for (y in 0 until 16) for (x in 0 until 32) {
            source.setRGB(x, y, if (x < 16) 0x00ff0000 else 0xff000000.toInt())
        }
        val png = ByteArrayOutputStream().also { ImageIO.write(source, "png", it) }.toByteArray()
        val fixture = ImageIO.read(png.inputStream())
        assertTrue(fixture.colorModel.hasAlpha())
        assertEquals(0, fixture.getRGB(4, 8).ushr(24))
        val result = assertNotNull(normalizer.normalize(Base64.getEncoder().encodeToString(png)))
        val decoded = ImageIO.read(result.bytes.inputStream())
        assertFalse(decoded.colorModel.hasAlpha())
        val white = java.awt.Color(decoded.getRGB(4, 8))
        assertTrue(white.red >= 250 && white.green >= 250 && white.blue >= 250)
        val black = java.awt.Color(decoded.getRGB(28, 8))
        assertTrue(black.red <= 5 && black.green <= 5 && black.blue <= 5)
    }

    @Test fun `normalization removes real PNG text and JPEG comment metadata`() {
        val marker = "private-avatar-fixture-metadata"
        for (format in listOf("png", "jpeg")) {
            val source = BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB)
            val writer = ImageIO.getImageWritersByFormatName(format).next()
            val bytes = ByteArrayOutputStream()
            try {
                val metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(source), writer.defaultWriteParam)
                val nativeFormat = metadata.nativeMetadataFormatName
                val root = metadata.getAsTree(nativeFormat) as IIOMetadataNode
                if (format == "png") {
                    root.appendChild(IIOMetadataNode("tEXt").also { text ->
                        text.appendChild(IIOMetadataNode("tEXtEntry").also {
                            it.setAttribute("keyword", "Description")
                            it.setAttribute("value", marker)
                        })
                    })
                } else {
                    (root.getElementsByTagName("markerSequence").item(0) as IIOMetadataNode)
                        .appendChild(IIOMetadataNode("com").also { it.setAttribute("comment", marker) })
                }
                metadata.setFromTree(nativeFormat, root)
                MemoryCacheImageOutputStream(bytes).use { output ->
                    writer.output = output
                    writer.write(null, IIOImage(source, null, metadata), writer.defaultWriteParam)
                }
            } finally { writer.dispose() }
            val encoded = bytes.toByteArray()
            assertTrue(encoded.toString(Charsets.ISO_8859_1).contains(marker), "Fixture must contain $format metadata")
            val result = assertNotNull(normalizer.normalize(Base64.getEncoder().encodeToString(encoded)))
            assertFalse(result.bytes.toString(Charsets.ISO_8859_1).contains(marker), format)
            assertNotNull(ImageIO.read(result.bytes.inputStream()))
        }
    }
    @Test fun `allows matching historical data prefix and rejects mismatched MIME`() {
        assertNotNull(normalizer.normalize("data:image/png;base64," + imageBase64()))
        assertNull(normalizer.normalize("data:image/jpeg;base64," + imageBase64()))
    }
}
