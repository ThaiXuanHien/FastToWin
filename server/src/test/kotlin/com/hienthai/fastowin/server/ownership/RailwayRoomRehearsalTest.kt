package com.hienthai.fastowin.server.ownership

import java.net.InetAddress
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class RailwayRoomRehearsalTest {
    private val now = Instant.parse("2026-11-08T12:00:00Z")
    private val run = "room-pilot-0123456789ab"
    private val hosts = mapOf("DATABASE" to "pg", "BACKEND_A" to "a", "BACKEND_B" to "b", "REDIS" to "redis", "PROXY" to "proxy")
    private val base = mapOf(
        "FTW_PILOT_SUITE" to "fault", "RAILWAY_PROJECT_ID" to "b5c5db71-2df8-46da-84fb-d9b9df6d7951",
        "RAILWAY_ENVIRONMENT_ID" to "8bcf4119-3a6b-49b3-b4ff-8ea20080f3b9", "RAILWAY_ENVIRONMENT_NAME" to "staging",
        "RAILWAY_SERVICE_NAME" to "$run-runner", "FTW_PILOT_DEADLINE_UTC" to now.plusSeconds(1100).toString(),
        "FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID" to "01234567-89ab-4abc-9abc-0123456789ab",
        "FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS" to (1L..40L).joinToString(",") { UUID(0,it).toString() },
        "FASTTOWIN_ROOM_OWNERSHIP_DATABASE_PASSWORD" to "f".repeat(64),
        "FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_PASSWORD" to "Pilot1!" + "a".repeat(64),
        "ROOM_FAULT_TOKEN" to "b".repeat(64), "FTW_PILOT_BACKEND_POOL_SIZE" to "20",
        "FTW_REHEARSAL_MANIFEST_SHA256" to "c".repeat(64)
    ) + hosts.map { (key,role) -> "FTW_PILOT_${key}_HOST" to "$run-$role.railway.internal" }

    @Test fun fixedPrivateTargetsAndProfiles() {
        for (suite in listOf("two-backend","fault")) {
            val profile = RailwayRehearsalSettings.parse(base + ("FTW_PILOT_SUITE" to suite), now)
            val endpoints = profile.endpoints { host ->
                listOf(InetAddress.getByName("10.0.0." + (base.values.indexOf(host) + 1)))
            }
            assertEquals(setOf("a","b","redis","proxy","pg"), endpoints.keys)
            for (ip in listOf("127.0.0.1","203.0.113.1","10.0.0.1"))
                assertFails { profile.endpoints { listOf(InetAddress.getByName(ip)) } }
        }
        val seed = base - hosts.filterValues { it != "pg" }.keys.map { "FTW_PILOT_${it}_HOST" }.toSet() - "ROOM_FAULT_TOKEN"
        assertEquals(setOf("pg"), RailwayRehearsalSettings.parse(seed,now,true).endpoints { listOf(InetAddress.getByName("10.0.0.2")) }.keys)
        assertFails { RailwayRehearsalSettings.parse(base,now,true) }
    }
    @Test fun rejectsCrossFixtureAndReducedSettings() {
        val wrong = hosts.keys.map { "FTW_PILOT_${it}_HOST" to "other.railway.internal" } + listOf(
            "FTW_PILOT_SUITE" to "baseline", "RAILWAY_PROJECT_ID" to "other", "RAILWAY_ENVIRONMENT_ID" to "other",
            "RAILWAY_ENVIRONMENT_NAME" to "production", "RAILWAY_SERVICE_NAME" to "other", "DATABASE_URL" to "external",
            "PGHOST" to "other", "RAILWAY_TOKEN" to "managed", "RAILWAY_API_TOKEN" to "managed", "ROOM_FAULT_TOKEN" to "bad",
            "FTW_PILOT_BACKEND_POOL_SIZE" to "10", "FTW_PILOT_DEADLINE_UTC" to now.plusSeconds(7201).toString(),
            "FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS" to UUID.randomUUID().toString(), "FTW_REHEARSAL_MANIFEST_SHA256" to "bad"
        )
        for (kv in wrong) assertFailsWith<IllegalArgumentException>(kv.first) { RailwayRehearsalSettings.parse(base + kv,now) }
    }
    @Test fun requireTwoObservedPoolSizes() {
        fun m(n:Int) = "fasttowin_room_ownership_pool_max_connections $n"
        assertEquals(listOf(20,20), observedRehearsalPools(listOf(m(20),m(20))))
        for (bad in listOf(listOf(m(20)),listOf(m(20),m(10)),listOf(m(20),""),listOf(m(20)+"\n"+m(20),m(20))))
            assertFails { observedRehearsalPools(bad) }
    }
}
