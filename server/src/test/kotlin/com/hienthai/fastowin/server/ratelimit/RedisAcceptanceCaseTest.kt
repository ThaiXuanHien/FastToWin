package com.hienthai.fastowin.server.ratelimit

import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedisAcceptanceCaseTest {
    class Probe {
        fun passes() = Unit
        fun skipped() { assumeTrue(false) }
    }
    @Test fun `missing and skipped cases cannot count as passing`() {
        val method = RedisAcceptanceRunner::class.java.methods.singleOrNull { it.name == "invokeCase" }
        assertTrue(method != null, "runner must validate the selected case result")
        fun invoke(name: String) = method.invoke(null, Probe::class.java.name, name) as Boolean
        assertTrue(invoke("passes"))
        assertFalse(invoke("missing"))
        assertFalse(invoke("skipped"))
    }
}
