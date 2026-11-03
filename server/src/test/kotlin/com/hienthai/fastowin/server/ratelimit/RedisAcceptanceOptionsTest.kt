package com.hienthai.fastowin.server.ratelimit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RedisAcceptanceOptionsTest {
    @Test fun `missing URL and out of bounds load options are rejected without including secret`() {
        val runner = runCatching { Class.forName("com.hienthai.fastowin.server.ratelimit.RedisAcceptanceRunner") }.getOrNull()
        assertNotNull(runner, "runner entry point must exist")
        val validate = runner.getMethod("validate", Map::class.java)
        fun check(input: Map<String, String>) = validate.invoke(null, input) as String
        assertEquals("missing_url", check(emptyMap()))
        assertEquals("invalid_duration", check(mapOf("FASTTOWIN_TEST_REDIS_URL" to "redis://fixture:secret@host/0", "REDIS_ACCEPTANCE_DURATION_SECONDS" to "601")))
        assertEquals("invalid_rate", check(mapOf("FASTTOWIN_TEST_REDIS_URL" to "redis://fixture:secret@host/0", "REDIS_ACCEPTANCE_CONSUMES_PER_SECOND" to "21")))
        assertEquals("ok", check(mapOf("FASTTOWIN_TEST_REDIS_URL" to "redis://fixture:secret@host/0")))
    }
}
