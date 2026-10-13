package com.hienthai.fastowin.server.avatars

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
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
    @Test fun `normalizes PNG alpha and JPEG into bounded metadata free JPEG`() {
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
    @Test fun `allows matching historical data prefix and rejects mismatched MIME`() {
        assertNotNull(normalizer.normalize("data:image/png;base64," + imageBase64()))
        assertNull(normalizer.normalize("data:image/jpeg;base64," + imageBase64()))
    }
}
