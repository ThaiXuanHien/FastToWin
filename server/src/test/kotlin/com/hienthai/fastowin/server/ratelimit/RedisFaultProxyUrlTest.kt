package com.hienthai.fastowin.server.ratelimit

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RedisFaultProxyUrlTest {
    @Test fun `proxy preserves authentication and database`() {
        RedisFaultProxy("redis://fixture-user:fixture-pass@localhost:6379/3").use { proxy ->
            val uri = URI(proxy.proxyUrl)
            assertEquals("fixture-user:fixture-pass", uri.rawUserInfo)
            assertEquals("/3", uri.rawPath)
        }
    }

    @Test fun `proxy rejects TLS targets rather than pretending to verify their host`() {
        assertFailsWith<IllegalArgumentException> { RedisFaultProxy("rediss://localhost:6379/0") }
    }
}
