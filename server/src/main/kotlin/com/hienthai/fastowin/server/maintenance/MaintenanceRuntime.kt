package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.GameEngine
import com.hienthai.fastowin.server.admin.unavailable
import kotlinx.coroutines.*
import io.ktor.server.application.Application
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Separate owned observation and reconciliation workers; a stalled writer cannot pin ACTIVE admission. */
class MaintenanceRuntime(
    private val engine: GameEngine,
    private val service: MaintenanceService? = null,
    private val environmentStatus: () -> ServiceStatusResponse = { ServiceStatusResponse(false) },
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    private val observationMutex = Mutex()
    @Volatile private var observedAt = monotonicMillis()
    @Volatile private var stale = false
    @Volatile private var lastKnownPolicy = MaintenancePolicy()
    fun publicStatus(): ServiceStatusResponse {
        if (stale) unavailable()
        return service?.publicStatus() ?: environmentStatus()
    }

    fun fenceStaleObservation() {
        if (monotonicMillis() - observedAt >= 4_000) {
            stale = true
            engine.publishMaintenancePolicy(lastKnownPolicy.copy(available = false))
        }
    }

    suspend fun observe() = observationMutex.withLock {
        try {
            if (service == null) {
                val status = environmentStatus()
                lastKnownPolicy = MaintenancePolicy(if (status.maintenance) MaintenanceState.MAINTENANCE else status.state ?: MaintenanceState.ACTIVE,
                    source = MaintenanceSource.ENVIRONMENT)
                engine.publishMaintenancePolicy(lastKnownPolicy)
            } else {
                val observed = service.refresh(engine.activeMaintenanceMatchCount(), allowEmptyDrain = false)
                lastKnownPolicy = observed
                engine.publishMaintenancePolicy(observed)
                val next = service.refresh(engine.activeMaintenanceMatchCount(), allowEmptyDrain = observed.blocksNewIntent)
                lastKnownPolicy = next
                engine.publishMaintenancePolicy(next)
            }
            observedAt = monotonicMillis(); stale = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            stale = true
            engine.publishMaintenancePolicy((service?.effectivePolicy() ?: lastKnownPolicy).copy(available = false))
        }
    }

    suspend fun reconcile(): MaintenanceApplication {
        val first = engine.reconcileMaintenancePolicy()
        val settled = engine.retryMaintenanceSettlements()
        val second = engine.reconcileMaintenancePolicy()
        return MaintenanceApplication(first.deliveries + settled + second.deliveries,
            first.abortedRoomIds + second.abortedRoomIds, second.closeSockets)
    }

    suspend fun refresh(): MaintenanceApplication {
        observe()
        return reconcile()
    }
}

fun Application.startMaintenanceRuntime(runtime: MaintenanceRuntime, apply: suspend (MaintenanceApplication) -> Unit): Job =
    launchMaintenanceWorkers(runtime, apply)

internal fun CoroutineScope.launchMaintenanceWorkers(runtime: MaintenanceRuntime, apply: suspend (MaintenanceApplication) -> Unit,
    workerDispatcher: CoroutineDispatcher = Dispatchers.IO): Job = launch {
    launch(workerDispatcher) {
        while (isActive) { delay(1_000); runtime.observe() }
    }
    launch(workerDispatcher) {
        while (isActive) {
            delay(1_000)
            try { apply(runtime.reconcile()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Retained durable work is retried by this single worker. */ }
        }
    }
    launch {
        while (isActive) { delay(100); runtime.fenceStaleObservation() }
    }
}
