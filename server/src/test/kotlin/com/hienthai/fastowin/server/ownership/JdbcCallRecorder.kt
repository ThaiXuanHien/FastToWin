package com.hienthai.fastowin.server.ownership

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Statement
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/** Counts JDBC execute/commit/rollback calls; implicit driver protocol messages are not counted. */
internal class JdbcCallRecorder(private val source: DataSource, private val fixed: Connection? = null) : DataSource by source {
    val serverCalls = AtomicInteger()

    override fun getConnection(): Connection {
        val connection = fixed ?: source.connection
        return Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
            when (method.name) {
                "close" -> if (fixed != null) null else invoke(method, connection, args)
                "createStatement", "prepareStatement" -> {
                    val statement = invoke(method, connection, args) as Statement
                    val type = if (statement is PreparedStatement) PreparedStatement::class.java else Statement::class.java
                    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, call, parameters ->
                        if (call.name in setOf("execute", "executeQuery", "executeUpdate", "executeBatch")) serverCalls.incrementAndGet()
                        invoke(call, statement, parameters)
                    }
                }
                else -> {
                    if (method.name in setOf("commit", "rollback")) serverCalls.incrementAndGet()
                    invoke(method, connection, args)
                }
            }
        } as Connection
    }

    private fun invoke(method: Method, target: Any, args: Array<out Any?>?): Any? = try {
        method.invoke(target, *(args ?: emptyArray()))
    } catch (error: InvocationTargetException) {
        throw error.targetException
    }
}
