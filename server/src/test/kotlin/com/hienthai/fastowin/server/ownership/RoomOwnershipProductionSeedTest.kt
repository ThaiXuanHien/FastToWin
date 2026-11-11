package com.hienthai.fastowin.server.ownership

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

class RoomOwnershipProductionSeedTest {
    @Test
    fun `production rehearsal seed requires dedicated production scope`() {
        val accounts = List(2) { UUID.randomUUID() }
        val settings = RoomOwnershipProductionSeedSettings.fromEnvironment(values(accounts))

        assertEquals(accounts, settings.accounts)
        assertEquals("room-ownership-production", settings.scope)
        assertEquals("ProductionSeed123!", settings.password)
    }

    @Test
    fun `production rehearsal seed rejects fixture variables and non private database`() {
        val accounts = List(2) { UUID.randomUUID() }
        assertFailsWith<IllegalArgumentException> {
            RoomOwnershipProductionSeedSettings.fromEnvironment(
                values(accounts) + ("FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID" to UUID.randomUUID().toString())
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RoomOwnershipProductionSeedSettings.fromEnvironment(
                values(accounts).toMutableMap().apply {
                    this["DATABASE_URL"] = "jdbc:postgresql://public.example.test:5432/room"
                }
            )
        }
    }

    private fun values(accounts: List<UUID>): Map<String, String> = mapOf(
        "FASTTOWIN_ENV" to "prod",
        "FTW_REHEARSAL_SCOPE" to "room-ownership-production",
        "DATABASE_URL" to "jdbc:postgresql://room-pilot-pg.railway.internal:5432/room",
        "DATABASE_USER" to "room",
        "DATABASE_PASSWORD" to "A".repeat(32),
        "FASTTOWIN_ROOM_OWNERSHIP_MODE" to "production",
        "FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS" to accounts.joinToString(","),
        "FASTTOWIN_ROOM_OWNERSHIP_REHEARSAL_PASSWORD" to "ProductionSeed123!"
    )
}

