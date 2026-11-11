package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.server.startServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.fail

class OwnedWorkerStartupTest {
    @Test
    fun `worker dispatch never enters public engine services or singleton job assembly`() {
        var workers = 0
        startServer(workerEnvironment(),
            ingress = { _, _ -> fail("Worker entered public GameEngine and singleton service assembly") },
            ownedWorker = { config, database ->
                workers++
                assertIs<RoomOwnershipProductionPolicy>(config)
                assertEquals("jdbc:postgresql://127.0.0.1:5432/worker", database.url)
            })
        assertEquals(1, workers)
    }

    @Test
    fun `default ingress keeps public startup and legacy restoration branch`() {
        var ingresses = 0
        startServer(emptyMap(),
            ingress = { environment, config ->
                ingresses++
                assertEquals("dev", environment)
                assertNull(config)
            },
            ownedWorker = { _, _ -> fail("Default ingress entered worker startup") })
        assertEquals(1, ingresses)
    }

    @Test
    fun `invalid worker configuration fails before either resource assembly branch`() {
        for (changes in listOf(
            mapOf("FASTTOWIN_ROOM_OWNERSHIP_MODE" to "legacy"),
            mapOf("DATABASE_URL" to "jdbc:h2:mem:worker"),
            mapOf("FASTTOWIN_ROOM_OWNERSHIP_PEER_URL" to "http://public.example.com:8080")
        )) {
            assertFailsWith<IllegalArgumentException> {
                startServer(workerEnvironment() + changes,
                    ingress = { _, _ -> fail("Invalid worker entered ingress") },
                    ownedWorker = { _, _ -> fail("Invalid worker opened resources") })
            }
        }
    }

    @Test
    fun `worker database credentials are validated without public URL SMTP avatar or admin settings`() {
        assertFailsWith<IllegalArgumentException> {
            startServer(workerEnvironment() + ("DATABASE_PASSWORD" to "fasttowin"),
                ingress = { _, _ -> fail("Worker entered ingress") },
                ownedWorker = { _, _ -> fail("Weak database secret opened resources") })
        }
    }

    private fun workerEnvironment() = mapOf(
        "FASTTOWIN_ENV" to "prod",
        "FASTTOWIN_ROOM_OWNERSHIP_ROLE" to "owned-worker",
        "FASTTOWIN_ROOM_OWNERSHIP_MODE" to "production",
        "FASTTOWIN_ROOM_OWNERSHIP_NODE_ID" to "00000000-0000-0000-0000-000000000001",
        "FASTTOWIN_ROOM_OWNERSHIP_PEER_ID" to "00000000-0000-0000-0000-000000000002",
        "FASTTOWIN_ROOM_OWNERSHIP_PEER_URL" to "http://backend-a.railway.internal:8080",
        "FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET" to "A".repeat(43),
        "FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS" to "00000000-0000-0000-0000-000000000003",
        "DATABASE_URL" to "jdbc:postgresql://127.0.0.1:5432/worker",
        "DATABASE_PASSWORD" to "isolated-test-password"
    )
}
