package com.hienthai.fastowin.server

import com.hienthai.fastowin.server.ads.*
import kotlinx.coroutines.test.runTest
import java.util.Base64
import kotlin.test.*

class AdMobPublicKeysTest {
    @Test fun `unavailable or malformed key source never verifies and retries are bounded`() = runTest {
        for (body in listOf("invalid", "x".repeat(65_537), """{"keys":[]}""", """{"keys":[{"keyId":7,"base64":"invalid"}]}""")) {
            var calls = 0
            val keys = CachedAdMobPublicKeys({ calls++; body }, { 1_800_000_000_000L })
            assertFailsWith<AdMobUnavailable> { keys.key("7") }
            assertFailsWith<AdMobUnavailable> { keys.key("7") }
            assertEquals(1, calls)
        }
    }
    @Test fun `cached keys rotate with bounded unknown refresh and expire fail closed`() = runTest {
        val f = AdMobFixture(); var now = 100_000L; var calls = 0; var fail = false; var key = "7"
        val keys = CachedAdMobPublicKeys({ calls++; if (fail) error("offline")
            """{"keys":[{"keyId":$key,"base64":"${Base64.getEncoder().encodeToString(f.pair.public.encoded)}"}]}""" }, { now })
        assertNotNull(keys.key("7")); assertEquals(1, calls)
        repeat(5) { assertNull(keys.key("8")) }; assertEquals(1, calls)
        now += 60_001; key = "8"
        assertNotNull(keys.key("8")); assertEquals(2, calls)
        now += 86_400_001; fail = true
        assertFailsWith<AdMobUnavailable> { keys.key("8") }
    }
}
