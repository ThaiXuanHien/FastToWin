package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.platform.PublicEndpoints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerUrlMigrationTest {
    private val old = "wss://previous.example.test/game"
    private val current = PublicEndpoints.productionGameServerUrl
    private val session = StoredAuthSession("user", "a@example.com", "A", "access", "refresh", 1L, 2L)

    @Test fun `a domain move carries the session and resume token to the new URL`() {
        val auth = InMemoryAuthSessionStore().apply { save(old, session) }
        val resume = InMemoryResumeTokenStore().apply { save(old, "resume") }

        migrateServerScopedSessions(current, listOf(old), auth, resume)

        assertEquals(session, auth.load(current)); assertNull(auth.load(old))
        assertEquals("resume", resume.load(current)); assertNull(resume.load(old))
    }

    @Test fun `an existing session for the current URL is never overwritten`() {
        val newer = session.copy(accessToken = "newer")
        val auth = InMemoryAuthSessionStore().apply { save(old, session); save(current, newer) }
        val resume = InMemoryResumeTokenStore()

        migrateServerScopedSessions(current, listOf(old), auth, resume)

        assertEquals(newer, auth.load(current)); assertEquals(session, auth.load(old))
    }

    @Test fun `the same URL or no history is a no-op`() {
        val auth = InMemoryAuthSessionStore().apply { save(old, session) }
        migrateServerScopedSessions(old, listOf(old), auth, InMemoryResumeTokenStore())
        assertEquals(session, auth.load(old))
    }

    @Test fun `a non-production destination leaves both old records in place`() {
        val auth = InMemoryAuthSessionStore().apply { save(old, session) }
        val resume = InMemoryResumeTokenStore().apply { save(old, "resume") }

        for (serverUrl in listOf("ws://127.0.0.1:8080/game", "wss://staging.example.test/game")) {
            migrateServerScopedSessions(serverUrl, listOf(old), auth, resume)

            assertNull(auth.load(serverUrl))
            assertNull(resume.load(serverUrl))
            assertEquals(session, auth.load(old))
            assertEquals("resume", resume.load(old))
        }
    }

    @Test fun `a failed auth save preserves the source and lets guest migration continue`() {
        val storedAuth = InMemoryAuthSessionStore().apply { save(old, session) }
        val auth = object : AuthSessionStore by storedAuth {
            override fun save(serverUrl: String, session: StoredAuthSession) {
                throw AuthSessionStoreException("TEST_STORAGE_FAILED", "Synthetic save failure")
            }
        }
        val resume = InMemoryResumeTokenStore().apply { save(old, "resume") }

        migrateServerScopedSessions(current, listOf(old), auth, resume)

        assertNull(auth.load(current))
        assertEquals(session, auth.load(old))
        assertEquals("resume", resume.load(current))
        assertNull(resume.load(old))

        migrateServerScopedSessions(current, listOf(old), storedAuth, resume)

        assertEquals(session, auth.load(current))
        assertNull(auth.load(old))
    }

    @Test fun `a failed guest save preserves the source without undoing auth migration`() {
        val auth = InMemoryAuthSessionStore().apply { save(old, session) }
        val storedResume = InMemoryResumeTokenStore().apply { save(old, "resume") }
        val resume = object : ResumeTokenStore by storedResume {
            override fun save(serverUrl: String, token: String) {
                throw IllegalStateException("Synthetic save failure")
            }
        }

        migrateServerScopedSessions(current, listOf(old), auth, resume)

        assertEquals(session, auth.load(current))
        assertNull(auth.load(old))
        assertNull(resume.load(current))
        assertEquals("resume", resume.load(old))
    }
}
