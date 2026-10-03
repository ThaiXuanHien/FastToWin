package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.protocol.MaintenanceState
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.*

class MaintenanceObservationConcurrencyTest {
    @Test fun unavailableTransformCannotRestoreCapturedActiveAfterNewerClosure() = runTest {
        val observations=MaintenanceObservations()
        observations.publish(MaintenanceObservation(MaintenancePolicy(MaintenanceState.ACTIVE,version=0),
            MaintenanceRuntimeObservation(version=0,observedAtEpochMillis=100,activeMatches=1,available=true)))
        val captured=CountDownLatch(1)
        val resumeFailure=CountDownLatch(1)
        val firstAttempt=AtomicBoolean(true)
        coroutineScope {
            val failure=async(Dispatchers.IO) {
                observations.transform { current ->
                    if(firstAttempt.compareAndSet(true,false)) {
                        assertEquals(MaintenanceState.ACTIVE,current.policy.state)
                        captured.countDown()
                        check(resumeFailure.await(10,TimeUnit.SECONDS))
                    }
                    current.unavailable()
                }
            }
            try {
                withContext(Dispatchers.IO) { assertTrue(captured.await(10,TimeUnit.SECONDS)) }
                observations.publish(MaintenanceObservation(MaintenancePolicy(MaintenanceState.MAINTENANCE,version=1),
                    MaintenanceRuntimeObservation(version=1,observedAtEpochMillis=200,activeMatches=0,available=true)))
            } finally { resumeFailure.countDown() }
            failure.await()
        }
        val unavailable=observations.current()
        assertEquals(MaintenanceState.MAINTENANCE,unavailable.policy.state)
        assertEquals(1,unavailable.policy.version)
        assertEquals(1,unavailable.runtime.version)
        assertEquals(200,unavailable.runtime.observedAtEpochMillis)
        assertEquals(0,unavailable.runtime.activeMatches)
        assertFalse(unavailable.policy.available); assertFalse(unavailable.runtime.available)
        assertEquals(MaintenanceSource.UNAVAILABLE,unavailable.policy.source)
        assertTrue(unavailable.policy.blocksNewIntent)
        assertFalse(unavailable.policy.allowsExistingMatch)
        assertTrue(unavailable.policy.isFullyClosed)

        // An explicit reopening observed later still restores normal availability.
        observations.publish(MaintenanceObservation(MaintenancePolicy(MaintenanceState.ACTIVE,version=2),
            MaintenanceRuntimeObservation(version=2,observedAtEpochMillis=300,activeMatches=0,available=true)))
        val recovered=observations.current()
        assertEquals(2,recovered.policy.version); assertEquals(2,recovered.runtime.version)
        assertTrue(recovered.policy.available); assertTrue(recovered.runtime.available)
        assertFalse(recovered.policy.blocksNewIntent); assertTrue(recovered.policy.allowsExistingMatch)
        assertFalse(recovered.policy.isFullyClosed)
    }
}
