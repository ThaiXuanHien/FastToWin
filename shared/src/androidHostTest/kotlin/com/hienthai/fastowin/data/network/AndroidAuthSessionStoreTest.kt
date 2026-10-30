package com.hienthai.fastowin.data.network

import android.content.SharedPreferences
import com.hienthai.fastowin.protocol.ProtocolJson
import kotlinx.serialization.encodeToString
import java.lang.reflect.Proxy
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class AndroidAuthSessionStoreTest {
    @Test fun pendingIsDurableBeforeReturnAndReusedByAnotherStoreInstance() {
        val disk = Preferences()
        val first = store(disk)
        first.save(URL, INITIAL)
        val pending = assertNotNull(first.prepareRefresh(URL, INITIAL))
        val secret = assertNotNull(pending.refreshAttemptSecret)
        assertTrue(secret.matches(Regex("[A-Za-z0-9_-]{43}")))
        assertEquals(32, Base64.getUrlDecoder().decode(secret).size)
        assertEquals(pending, store(disk).load(URL))
        assertEquals(pending, ProtocolJson.decodeFromString<StoredAuthSession>(disk.durableValues.getValue("auth_session.$URL")))
        assertEquals(pending, store(disk).prepareRefresh(URL, INITIAL))
        assertEquals(2, disk.commits)
        assertEquals(0, disk.applies)
    }

    @Test fun prepareCommitFailureIsReportedRatherThanSendingAnUnstoredAttempt() {
        val disk = Preferences()
        val store = store(disk)
        store.save(URL, INITIAL)
        disk.failCommit = true
        assertFailsWith<AuthSessionStoreException> { store.prepareRefresh(URL, INITIAL) }
        assertEquals(INITIAL, store(disk).load(URL))
        assertFailsWith<AuthSessionStoreException> { store(disk).prepareRefresh(URL, INITIAL) }
    }

    @Test fun failedCompletionRestoresPendingInSharedPreferencesMemory() {
        val disk = Preferences()
        val store = store(disk)
        store.save(URL, INITIAL)
        val pending = assertNotNull(store.prepareRefresh(URL, INITIAL))
        disk.failCommit = true
        assertFailsWith<AuthSessionStoreException> { store.completeRefresh(URL, pending, FRESH) }
        assertEquals(pending, store(disk).load(URL))
        disk.failCommit = false
        assertTrue(store(disk).completeRefresh(URL, pending, FRESH))
        assertNull(store(disk).load(URL)?.refreshAttemptSecret)
    }

    @Test fun completionKeepsMetadataAndClearsPendingWithCredentialsInOneCommit() {
        val disk = Preferences()
        val store = store(disk)
        store.save(URL, INITIAL)
        val pending = assertNotNull(store.prepareRefresh(URL, INITIAL))
        assertTrue(store.updateSession(URL, pending, pending.copy(displayName = "Edited", emailVerified = true)))
        val before = disk.commits
        assertTrue(store.completeRefresh(URL, pending, FRESH))
        assertEquals(before + 1, disk.commits)
        val saved = assertNotNull(store(disk).load(URL))
        assertEquals("fresh-refresh", saved.refreshToken)
        assertEquals("Edited", saved.displayName)
        assertTrue(saved.emailVerified)
        assertNull(saved.refreshAttemptSecret)
    }

    @Test fun staleGenerationOrAttemptCannotPrepareCompleteUpdateOrClear() {
        val disk = Preferences()
        val store = store(disk)
        store.save(URL, INITIAL)
        val pending = assertNotNull(store.prepareRefresh(URL, INITIAL))
        assertFalse(store.completeRefresh(URL, pending.copy(refreshAttemptSecret = "different"), FRESH))
        assertFalse(store.updateSession(URL, INITIAL, INITIAL.copy(displayName = "Stale")))
        store.save(URL, FRESH)
        assertNull(store.prepareRefresh(URL, pending))
        assertFalse(store.completeRefresh(URL, pending, FRESH))
        assertFalse(store.updateSession(URL, pending, pending.copy(displayName = "Stale")))
        assertFalse(store.clearSession(URL, pending))
        assertEquals(FRESH, store.load(URL))
    }

    @Test fun concurrentStoreInstancesPrepareOneDurableSecret() {
        val disk = Preferences()
        val first = store(disk)
        first.save(URL, INITIAL)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = listOf(first, store(disk)).map { instance -> pool.submit<StoredAuthSession?> {
                start.await()
                instance.prepareRefresh(URL, INITIAL)
            } }
            start.countDown()
            val a = assertNotNull(results[0].get(5, TimeUnit.SECONDS))
            val b = assertNotNull(results[1].get(5, TimeUnit.SECONDS))
            assertNotNull(a.refreshAttemptSecret)
            assertEquals(a, b)
            assertEquals(2, disk.commits)
        } finally { pool.shutdownNow() }
    }

    @Test fun completedGenerationGetsANewAttemptWhileOldJsonRemainsReadable() {
        val disk = Preferences()
        disk.values["auth_session.$URL"] = """{"userId":"fixture","email":"fixture@example.invalid","displayName":"Fixture","accessToken":"old-access","refreshToken":"old-refresh","accessExpiresAtEpochMillis":0,"refreshExpiresAtEpochMillis":9223372036854775807,"emailVerified":false}"""
        val store = store(disk)
        assertNull(store.load(URL)?.refreshAttemptSecret)
        val first = assertNotNull(store.prepareRefresh(URL, INITIAL))
        assertTrue(store.completeRefresh(URL, first, FRESH))
        val next = assertNotNull(store.prepareRefresh(URL, FRESH))
        assertNotNull(first.refreshAttemptSecret)
        assertNotNull(next.refreshAttemptSecret)
        assertNotEquals(first.refreshAttemptSecret, next.refreshAttemptSecret)
    }

    /** Host tests replace only Android disk/Keystore I/O, keeping the real store transactions. */
    private class Preferences {
        val values = mutableMapOf<String, String>()
        val durableValues = mutableMapOf<String, String>()
        var commits = 0
        var applies = 0
        var failCommit = false
        val preferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args -> when (method.name) {
                "getString" -> values[args!![0]] ?: args[1]
                "edit" -> editor()
                else -> error("Unexpected preferences operation: ${method.name}")
            } } as SharedPreferences
        private fun editor(): SharedPreferences.Editor {
            val writes = mutableMapOf<String, String?>()
            return Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args -> when (method.name) {
                    "putString" -> { writes[args!![0] as String] = args[1] as String; proxy }
                    "remove" -> { writes[args!![0] as String] = null; proxy }
                    "commit", "apply" -> {
                        if (method.name == "commit") commits++ else applies++
                        // Android commit() changes memory even if the subsequent disk write fails.
                        writes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                        if (!failCommit) {
                            durableValues.clear()
                            durableValues.putAll(values)
                        }
                        if (method.name == "commit") !failCommit else null
                    }
                    else -> error("Unexpected editor operation: ${method.name}")
                } } as SharedPreferences.Editor
        }
    }

    companion object {
        private const val URL = "http://android-store.invalid"
        private val INITIAL = StoredAuthSession("fixture", "fixture@example.invalid", "Fixture", "old-access", "old-refresh", 0L, Long.MAX_VALUE, false)
        private val FRESH = INITIAL.copy(accessToken = "fresh-access", refreshToken = "fresh-refresh")
        private fun store(disk: Preferences) = AndroidAuthSessionStore(disk.preferences,
            { ProtocolJson.encodeToString(it) }, { ProtocolJson.decodeFromString(it) })
    }
}
