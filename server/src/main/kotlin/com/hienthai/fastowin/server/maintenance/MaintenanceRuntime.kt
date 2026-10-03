package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.GameEngine
import com.hienthai.fastowin.server.admin.AdminException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import io.ktor.server.application.Application
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owned by the Application coroutine scope. No background thread or independent grant path. */
class MaintenanceRuntime(
    private val engine: GameEngine,
    private val service: MaintenanceService? = null,
    private val environmentStatus: () -> ServiceStatusResponse = { ServiceStatusResponse(false) }
) {
    private val mutex = Mutex()
    fun publicStatus(): ServiceStatusResponse = service?.publicStatus() ?: environmentStatus()

    suspend fun refresh(): MaintenanceApplication = mutex.withLock {
        if (service == null) {
            val status = environmentStatus()
            val applied = engine.applyMaintenancePolicy(MaintenancePolicy(
                state = if (status.maintenance) MaintenanceState.MAINTENANCE else status.state ?: MaintenanceState.ACTIVE,
                source = MaintenanceSource.ENVIRONMENT))
            return@withLock applied.copy(deliveries = applied.deliveries + engine.retryMaintenanceSettlements())
        }
        try {
            // An ACTIVE count of zero is not trusted: first read without empty-drain advancement,
            // apply the blocking policy, then sample under the engine's admission mutex.
            val observed = service.refresh(engine.activeMaintenanceMatchCount(), allowEmptyDrain = false)
            val first = engine.applyMaintenancePolicy(observed)
            val settled = engine.retryMaintenanceSettlements()
            val second = engine.applyMaintenancePolicy(service.refresh(engine.activeMaintenanceMatchCount(),
                allowEmptyDrain = observed.blocksNewIntent))
            MaintenanceApplication(first.deliveries + settled + second.deliveries,
                first.abortedRoomIds + second.abortedRoomIds, first.closeSockets || second.closeSockets)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: AdminException) {
            // The service retains its last known closure on read failure; initial failure closes.
            engine.applyMaintenancePolicy(service.effectivePolicy())
        }
    }
}

fun Application.startMaintenanceRuntime(runtime: MaintenanceRuntime, apply: suspend (MaintenanceApplication) -> Unit): Job = launch {
    while (isActive) {
        delay(1_000)
        try { apply(runtime.refresh()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { System.err.println("Could not apply maintenance policy; admission remains fenced.") }
    }
}
