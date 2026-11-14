package com.hienthai.fastowin.server.ownership

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Test-only execution counts. Never retain SQL, parameters, identifiers or exception text. */
internal class OwnershipQueryProfile {
    private val counts = ConcurrentHashMap<String, AtomicLong>()

    fun snapshot(): Map<String, Long> = counts.mapValues { it.value.get() }

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
            if (classes.any { it.startsWith(root + "moderation.JdbcModerationTransaction") }) return "moderation"
            if (classes.any { it.startsWith(root + "ownership.PostgresOwnedAdmissionRepository") }) return "admission"
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
