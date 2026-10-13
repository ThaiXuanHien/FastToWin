package com.hienthai.fastowin.platform

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AvatarPreparationCoordinatorTest {
    @Test
    fun `slow first result cannot replace a newer completed selection`() = runBlocking {
        val firstGate = CompletableDeferred<Unit>()
        val delivered = mutableListOf<ByteArray?>()
        val coordinator = AvatarPreparationCoordinator(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            prepare = { selection: Int ->
                if (selection == 1) suspendCoroutine { continuation ->
                    firstGate.invokeOnCompletion { continuation.resume(byteArrayOf(1)) }
                } else byteArrayOf(2)
            },
            onResult = delivered::add
        )

        coordinator.select(1)
        coordinator.select(2)
        firstGate.complete(Unit)

        assertEquals(listOf(2.toByte()), delivered.map { it?.single() })
        coordinator.cancel()
    }

    @Test
    fun `opening another chooser invalidates old preparation before its URI returns`() = runBlocking {
        val firstGate = CompletableDeferred<Unit>()
        val delivered = mutableListOf<ByteArray?>()
        val coordinator = AvatarPreparationCoordinator(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            prepare = { selection: Int ->
                if (selection == 1) suspendCoroutine { continuation ->
                    firstGate.invokeOnCompletion { continuation.resume(byteArrayOf(1)) }
                } else byteArrayOf(2)
            },
            onResult = delivered::add
        )

        coordinator.select(1)
        coordinator.cancel() // The next chooser opened; its URI has not returned yet.
        firstGate.complete(Unit)
        assertTrue(delivered.isEmpty())

        coordinator.select(2)
        assertEquals(listOf(2.toByte()), delivered.map { it?.single() })
        coordinator.cancel()
    }
}
