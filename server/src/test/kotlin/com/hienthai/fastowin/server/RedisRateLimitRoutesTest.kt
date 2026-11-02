package com.hienthai.fastowin.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class RedisRateLimitRoutesTest {
    @Test
    fun `unavailable login limit returns 503`() = testApplication {
        application { gameModule(rateLimiter = object : RateLimiter {
            override suspend fun consume(bucket: String, key: String, policy: RateLimitPolicy): RateLimitResult =
                throw RateLimitUnavailableException(RateLimitFailure.DISCONNECTED)
        }) }
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"qa@example.com","password":"valid-test-password"}""")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertContains(response.bodyAsText(), "RATE_LIMIT_UNAVAILABLE")
    }
}
