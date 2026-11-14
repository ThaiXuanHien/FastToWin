package com.hienthai.fastowin.server.ownership

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.server.PlayerProfileRepository

/** Test-only execution counts. Never retain SQL, parameters, identifiers or exception text. */
internal class OwnershipQueryProfile {
    private val counts = ConcurrentHashMap<String, AtomicLong>()

    fun snapshot(): Map<String, Long> = counts.mapValues { it.value.get() }

    fun profileRepository(delegate: PlayerProfileRepository): PlayerProfileRepository =
        object : PlayerProfileRepository by delegate {
            override suspend fun findByPlayerId(playerId: String): PlayerProfileSnapshot? {
                val caller = Throwable().stackTrace.firstOrNull { frame ->
                    frame.className.startsWith("com.hienthai.fastowin.server.GameEngine")
                }?.let { frame -> "${frame.className.substringAfterLast('.')}#${frame.methodName}" }
                    ?: "unknown"
                counts.computeIfAbsent("profile_call.$caller") { AtomicLong() }.incrementAndGet()
                return delegate.findByPlayerId(playerId)
            }
        }

    fun connection(raw: Connection, node: String): Connection {
        require(node in listOf("a", "b", "control"))
        return proxy(Connection::class.java, raw) { method, args ->
            val result = invoke(raw, method, args)
            if (method.name == "prepareStatement" && result is PreparedStatement) {
                val label = classify(Throwable().stackTrace.map { it.className + "#" + it.methodName })
                proxy(PreparedStatement::class.java, result) { statementMethod, statementArgs ->
                    if (statementMethod.name in listOf("execute", "executeQuery", "executeUpdate", "executeLargeUpdate", "executeBatch")) {
                        counts.computeIfAbsent("$node.$label") { AtomicLong() }.incrementAndGet()
                    }
                    invoke(result, statementMethod, statementArgs)
                }
            } else result
        }
    }

    companion object {
        // Closed labels are selected from call-site classes, never from SQL or player data.
        fun classify(classes: List<String>): String {
            val root = "com.hienthai.fastowin.server."
            val operations = setOf("isCurrentSocket", "readState", "readEvents", "presence", "findOwner", "lookupRoom",
                "listJoinableRooms", "oldestQueuedEventAgeSeconds", "ownedFences", "expiredRooms")
            val operation = classes.asSequence()
                .filter { it.startsWith(root + "ownership.PostgresRoomOwnershipRepository") }
                .map { if (it.startsWith(root + "ownership.PostgresRoomOwnershipRepository#"))
                    it.substringAfter('#').substringBefore('$')
                    else it.substringAfter("PostgresRoomOwnershipRepository$").substringBefore('$') }
                .firstOrNull { it in operations }
            if (operation != null) return "ownership_$operation"
            if (classes.any { it.startsWith(root + "ownership.PostgresRoomOwnershipRepository") }) return "ownership_other"
            if (classes.any { it.startsWith(root + "PostgresAuthRepository") }) return "auth"
            classes.firstOrNull { it.startsWith(root + "moderation.JdbcModerationTransaction") }?.let {
                val suffix = it.substringAfter("JdbcModerationTransaction").trimStart('#', '$')
                    .substringBefore('$').ifBlank { "other" }
                return "moderation_$suffix"
            }
            if (classes.any { it.startsWith(root + "ownership.PostgresOwnedAdmissionRepository") }) return "admission"
            if (classes.any { it.startsWith(root + "PostgresPlayQuotaRepository") }) return "play_quota"
            if (classes.any { it.startsWith(root + "PostgresMatchResultRepository") }) return "match_result"
            if (classes.any { it.startsWith(root + "PostgresFriendRepository") }) return "friends"
            if (classes.any { it.startsWith(root + "PostgresClanRepository") }) return "clans"
            classes.firstOrNull { it.startsWith(root + "PostgresPlayerProfileRepository") }?.let {
                val suffix = it.substringAfter("PostgresPlayerProfileRepository").trimStart('#', '$')
                    .substringBefore('$').ifBlank { "other" }
                val caller = classes.firstOrNull { frame -> frame.startsWith(root + "GameEngine#") || frame.startsWith(root + "GameEngine$") }
                    ?.let { frame -> if ('#' in frame) frame.substringAfter('#') else frame.substringAfter("GameEngine$") }
                    ?.substringBefore('$')
                return "profile_${caller ?: suffix}"
            }
            if (classes.any { it.startsWith(root + "PostgresActiveRoomRepository") }) return "legacy_room"
            return "other"
        }

        private fun invoke(target: Any, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? =
            try { method.invoke(target, *(args ?: emptyArray())) }
            catch (failure: InvocationTargetException) { throw failure.targetException }

        private fun <T> proxy(type: Class<T>, raw: T,
            call: (java.lang.reflect.Method, Array<out Any?>?) -> Any?): T = type.cast(
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> call(method, args) })
    }
}
