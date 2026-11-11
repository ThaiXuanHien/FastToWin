package com.hienthai.fastowin.server.ownership

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OwnedWorkerModuleTest {
    @Test
    fun `worker database bootstrap does not perform legacy mission import`() = runBlocking {
        withOwnedWorkerFixture { f ->
            f.source.connection.use { connection -> connection.createStatement().use { statement ->
                statement.execute("ALTER TABLE user_missions RENAME TO forbidden_legacy_mission_source")
            } }
            OwnedWorkerDatabaseRuntime.open(f.databaseSettings).use { database ->
                val runtime = database.openRoomOwnershipPilot(f.config)
                assertTrue(runtime.isProductionMode)
                assertTrue(runtime.beginDrain())
            }
        }
    }

    @Test
    fun `worker module exposes no public application routes`() = runBlocking {
        withOwnedWorkerFixture { f ->
            val runtime = f.pilot()
            testApplication {
                application { ownedWorkerModule(runtime) }
                for (path in listOf("/game", "/auth/login", "/auth/register", "/api/content-terms",
                    "/api/announcements", "/admin", "/admin/api/maintenance", "/gifts", "/status")) {
                    assertEquals(HttpStatusCode.NotFound, client.get(path).status, path)
                    assertEquals(HttpStatusCode.NotFound, client.post(path).status, path)
                }
                assertEquals(HttpStatusCode.OK, client.get("/health").status)
                assertEquals(HttpStatusCode.Unauthorized, client.post("/internal/room-ownership/command").status)
            }
        }
    }

    @Test
    fun `worker readiness observes explicit durable activation and STOP_NEW while liveness stays available`() = runBlocking {
        withOwnedWorkerFixture { f ->
            val runtime = f.pilot()
            testApplication {
                application { ownedWorkerModule(runtime) }
                client.get("/health")
                assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/ready").status)
                f.activate()
                withTimeout(8_000) { while (!runtime.isReady()) delay(25) }
                assertTrue(runtime.isReady(), "Ownership worker was not started")
                assertEquals(HttpStatusCode.OK, client.get("/ready").status)
                f.stopNew()
                assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/ready").status)
                assertEquals(HttpStatusCode.OK, client.get("/health").status)
                runtime.beginDrain()
                assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/ready").status)
                assertEquals(HttpStatusCode.OK, client.get("/health").status)
            }
        }
    }
}
