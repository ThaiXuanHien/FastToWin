package com.hienthai.fastowin.server.ownership

import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import kotlin.test.*

class OwnershipQueryProfileTest {
    @Test fun `labels are closed and unknown stack text is never exported`() {
        assertEquals("ownership_isCurrentSocket", OwnershipQueryProfile.classify(listOf(
            "com.hienthai.fastowin.server.ownership.PostgresRoomOwnershipRepository\$query\$1",
            "com.hienthai.fastowin.server.ownership.PostgresRoomOwnershipRepository\$transaction\$2",
            "com.hienthai.fastowin.server.ownership.PostgresRoomOwnershipRepository\$isCurrentSocket\$2")))
        assertEquals("other", OwnershipQueryProfile.classify(listOf("private-value.example")))
        assertEquals("ownership_isCurrentSocket", OwnershipQueryProfile.classify(listOf(
            "com.hienthai.fastowin.server.ownership.PostgresRoomOwnershipRepository#withStatement",
            "com.hienthai.fastowin.server.ownership.PostgresRoomOwnershipRepository#isCurrentSocket\$lambda\$0")))
    }

    @Test fun `counts actual executions and preserves SQL failure type`() {
        val expected = SQLException("synthetic")
        val statement = Proxy.newProxyInstance(PreparedStatement::class.java.classLoader, arrayOf(PreparedStatement::class.java)) { _, method, _ ->
            when (method.name) { "executeQuery" -> throw expected; "close" -> null; else -> error("Unexpected method") }
        } as PreparedStatement
        val raw = Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, _ ->
            when (method.name) { "prepareStatement" -> statement; "close" -> null; else -> error("Unexpected method") }
        } as Connection
        val profile = OwnershipQueryProfile()
        profile.connection(raw, "a").use { connection ->
            connection.prepareStatement("synthetic input never retained").use { wrapped ->
                assertTrue(profile.snapshot().isEmpty())
                assertSame(expected, assertFailsWith<SQLException> { wrapped.executeQuery() })
            }
        }
        assertEquals(mapOf("a.other" to 1L), profile.snapshot())
    }
}
