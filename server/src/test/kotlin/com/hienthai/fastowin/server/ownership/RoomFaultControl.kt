package com.hienthai.fastowin.server.ownership

internal enum class BackendAction { KILL, START, PAUSE, RESUME }
internal enum class StoreRole(val wire: String, val compose: String) { PG("pg", "postgres"), REDIS("redis", "redis") }
internal interface RoomFaultControl : AutoCloseable {
    override fun close() {}
    val processPauseStores: Boolean
    suspend fun backend(node: Int, action: BackendAction)
    suspend fun store(role: StoreRole, unavailable: Boolean)
    suspend fun partition(node: Int, blocked: Boolean)
    suspend fun verifyHealthy()
}

internal class DockerRoomFaultControl(
    private val command: suspend (String, String) -> Unit,
    private val proxy: suspend (Int, Boolean) -> Unit
) : RoomFaultControl {
    override val processPauseStores = false
    override suspend fun backend(node: Int, action: BackendAction) {
        require(node in 0..1)
        val op = if (action == BackendAction.RESUME) "unpause" else action.name.lowercase()
        command(op, nodeName(node))
    }
    override suspend fun store(role: StoreRole, unavailable: Boolean) {
        command(if (unavailable) "stop" else "start", role.compose)
    }
    override suspend fun partition(node: Int, blocked: Boolean) {
        require(node in 0..1)
        proxy(node, blocked)
    }
    override suspend fun verifyHealthy() {}
}

internal class RecoveryTimer(private val start: Long, private val nanoTime: () -> Long) {
    fun elapsedMillis() = (nanoTime() - start) / 1_000_000L
    companion object {
        suspend fun inject(nanoTime: () -> Long = System::nanoTime, request: suspend () -> Unit): RecoveryTimer {
            val start = nanoTime()
            request()
            return RecoveryTimer(start, nanoTime)
        }
    }
}
