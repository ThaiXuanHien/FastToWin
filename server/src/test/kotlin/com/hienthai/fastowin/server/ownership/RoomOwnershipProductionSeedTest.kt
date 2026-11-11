package com.hienthai.fastowin.server.ownership

import java.util.UUID
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RoomOwnershipProductionSeedTest {
    @Test
    fun seedEntrypointBindsReceiptAndSuppressesSensitiveFailureDetails() {
        val values = values()
        val output = ByteArrayOutputStream()
        var attempts = 0
        val success = runProductionSeed(arrayOf("--seed-production"), values, PrintStream(output)) {
            attempts++
        }
        assertEquals(0, success)
        assertEquals(1, attempts)
        val settings = RoomOwnershipProductionSeedSettings.fromEnvironment(values)
        val expected = """{"productionSeeded":true,"manifestSha256":"${settings.manifestSha256}","runId":"${settings.runId}"}"""
        assertEquals(expected, output.toString().trim())
        output.reset()
        assertEquals(1, runProductionSeed(arrayOf("--seed-production"), values, PrintStream(output)) {
            throw IllegalStateException(values.getValue("DATABASE_PASSWORD"))
        })
        assertEquals("Production rehearsal seed failed", output.toString().trim())
        output.reset()
        assertEquals(1, runProductionSeed(emptyArray(), values, PrintStream(output)) { attempts++ })
        assertEquals(1, attempts)
    }

    @Test
    fun bindsSeederToDisposableStagingIdentity() {
        val settings = RoomOwnershipProductionSeedSettings.fromEnvironment(values())
        assertEquals(2, settings.accounts.size)
        assertEquals("room-ownership-production", settings.scope)
        assertEquals("room_rehearsal_123456781234", settings.databaseName)
    }

    @Test
    fun rejectsSharedTargetsAndFixtureVariables() {
        val valid = values()
        val rejected = listOf(
            "RAILWAY_PROJECT_ID" to "8daf3d1f-df4c-4f39-a07b-61becae674fa",
            "RAILWAY_ENVIRONMENT_NAME" to "production",
            "DATABASE_URL" to "jdbc:postgresql://rate-limit-redis.railway.internal:5432/room",
            "DATABASE_URL" to "jdbc:postgresql://localhost:5432/room",
            "DATABASE_USER" to "postgres",
            "DATABASE_PASSWORD" to "weak",
            "FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID" to UUID.randomUUID().toString(),
            "FASTTOWIN_ROOM_OWNERSHIP_ACCEPTANCE" to "true",
            "RAILWAY_API_TOKEN" to "unexpected"
        )
        for ((key, value) in rejected) {
            assertFailsWith<IllegalArgumentException>(key) {
                RoomOwnershipProductionSeedSettings.fromEnvironment(valid + (key to value))
            }
        }
    }

    companion object {
        fun values(id: UUID = UUID.fromString("12345678-1234-4234-8234-123456789abc")): Map<String, String> {
            val suffix = id.toString().replace("-", "").take(12)
            val run = "room-prod-" + suffix
            val database = "room_rehearsal_" + suffix
            return mapOf(
                "RAILWAY_PROJECT_ID" to STAGING_PROJECT,
                "RAILWAY_ENVIRONMENT_ID" to STAGING_ENVIRONMENT,
                "RAILWAY_ENVIRONMENT_NAME" to "staging",
                "RAILWAY_SERVICE_NAME" to run + "-runner",
                "FTW_REHEARSAL_ID" to id.toString(),
                "FTW_REHEARSAL_MANIFEST_SHA256" to "b".repeat(64),
                "FASTTOWIN_ENV" to "prod",
                "FTW_REHEARSAL_SCOPE" to "room-ownership-production",
                "DATABASE_URL" to "jdbc:postgresql://" + run + "-pg.railway.internal:5432/" + database,
                "DATABASE_USER" to database,
                "DATABASE_PASSWORD" to "a".repeat(64),
                "FASTTOWIN_ROOM_OWNERSHIP_MODE" to "production",
                "FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS" to List(2) { UUID.randomUUID() }.joinToString(","),
                "FASTTOWIN_ROOM_OWNERSHIP_REHEARSAL_PASSWORD" to "Rehearsal1!" + "c".repeat(64)
            )
        }
    }
}

