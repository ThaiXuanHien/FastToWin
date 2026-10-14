package com.hienthai.fastowin.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AvatarUploadTest {
    @Test
    fun `encoder accepts exactly 45 KiB without another lossy pass`() {
        val bytes = ByteArray(45 * 1024)
        var calls = 0
        assertSame(bytes, prepareAvatarUpload { _, _ -> calls++; bytes })
        assertEquals(1, calls)
    }

    @Test
    fun `encoder keeps shrinking until bytes fit`() {
        val dimensions = mutableListOf<Int>()
        val qualities = mutableListOf<Double>()
        val result = prepareAvatarUpload { edge, quality ->
            dimensions += edge
            qualities += quality
            ByteArray(if (edge <= 256) 40 * 1024 else 45 * 1024 + 1)
        }
        assertEquals(40 * 1024, result?.size)
        assertTrue(dimensions.last() < dimensions.first())
        assertTrue(qualities.last() < qualities.first())
        assertTrue(dimensions.zipWithNext().all { (a, b) -> b <= a })
        assertTrue(qualities.all { it in 0.4..0.9 })
    }

    @Test
    fun `encoder never returns oversize bytes and gives up within ten attempts`() {
        var calls = 0
        assertNull(prepareAvatarUpload { _, _ -> calls++; ByteArray(45 * 1024 + 1) })
        assertTrue(calls in 2..10)
    }

    @Test
    fun `failed or empty encoding stops immediately`() {
        for (candidate in listOf(null, ByteArray(0))) {
            var calls = 0
            assertNull(prepareAvatarUpload { _, _ -> calls++; candidate })
            assertEquals(1, calls)
        }
    }

    @Test
    fun `header dimensions bound decode allocation before scaling`() {
        assertEquals(8, avatarDecodeSampleSize(4096, 2048))
        assertEquals(16, avatarDecodeSampleSize(8000, 6000))
        assertEquals(1, avatarDecodeSampleSize(128, 64))
        val extremeSample = avatarDecodeSampleSize(Int.MAX_VALUE, Int.MAX_VALUE)!!
        assertTrue(Int.MAX_VALUE / extremeSample <= 512)
        assertNull(avatarDecodeSampleSize(-1, 512))
        assertNull(avatarDecodeSampleSize(512, 0))
    }

    @Test
    fun `resizing preserves aspect ratio and keeps very narrow images nonzero`() {
        assertEquals(512 to 256, avatarScaledSize(4096, 2048, 512))
        assertEquals(256 to 512, avatarScaledSize(2048, 4096, 512))
        assertEquals(1 to 512, avatarScaledSize(1, 4096, 512))
        assertEquals(128 to 64, avatarScaledSize(128, 64, 512))
    }
}
