package com.hienthai.fastowin.server.ownership

import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OwnedClusterAwaitTest {
    @Test fun `deadline returns last authenticated status while work remains stopped`() = runBlocking {
        val node = UUID.randomUUID()
        val process = UUID.randomUUID()
        val identity = OwnedCandidateIdentity("a".repeat(64), "b".repeat(64), "c".repeat(64))
        val binding = OwnedAdmissionBinding("d".repeat(64), "e".repeat(64))
        val policy = OwnedAdmissionPolicy(7, OwnedAdmissionState.STOP_NEW, binding.topologySha256, binding.policySha256)
        val stepIds = mutableSetOf<String>()
        val result = awaitClusterDrain(UUID.randomUUID(), 7, 100, node, identity, binding, pollMillis = 10) { request ->
            if (request.operation == "status") OwnedControlResponse(request.operationId, request.operation, request.generation,
                "ok", node.toString(), process.toString(), identity.candidateSha256, binding.topologySha256,
                false, policy, 1, 0, 1, accepting = true)
            else { stepIds += request.operationId; OwnedControlResponse(request.operationId, request.operation, request.generation, "pending", policy = policy) }
        }
        assertEquals(1, stepIds.size, "Retries of the same fingerprint reuse one operation identity")
        assertEquals("deadline", result.outcome)
        assertEquals(policy, assertNotNull(result.lastStatus).policy)
        assertEquals(1, result.lastStatus.openRooms)
    }

    @Test fun `unavailable is distinct from deadline`() = runBlocking {
        val result = awaitClusterDrain(UUID.randomUUID(), 7, 1000, UUID.randomUUID(),
            OwnedCandidateIdentity("a".repeat(64), "b".repeat(64), "c".repeat(64)),
            OwnedAdmissionBinding("d".repeat(64), "e".repeat(64)), pollMillis = 10) { null }
        assertEquals("unavailable", result.outcome)
        assertEquals(null, result.lastStatus)
    }
    @Test fun `await refuses stale initial policy and intervening resume`() = runBlocking {
        val node = UUID.randomUUID(); val process = UUID.randomUUID()
        val identity = OwnedCandidateIdentity("a".repeat(64), "b".repeat(64), "c".repeat(64))
        val binding = OwnedAdmissionBinding("d".repeat(64), "e".repeat(64))
        val stopped = OwnedAdmissionPolicy(7, OwnedAdmissionState.STOP_NEW, binding.topologySha256, binding.policySha256)
        val resumed = stopped.copy(generation = 8, state = OwnedAdmissionState.ACTIVE)
        var sends = 0
        val initial = awaitClusterDrain(UUID.randomUUID(), 7, 500, node, identity, binding, pollMillis = 1) { request ->
            sends++
            OwnedControlResponse(request.operationId, request.operation, request.generation, "stale", policy = resumed)
        }
        assertEquals("conflict", initial.outcome); assertEquals(1, sends)
        sends = 0
        val interrupted = awaitClusterDrain(UUID.randomUUID(), 7, 500, node, identity, binding, pollMillis = 1) { request ->
            sends++
            when (sends) {
                1 -> OwnedControlResponse(request.operationId, request.operation, request.generation, "ok", node.toString(),
                    process.toString(), identity.candidateSha256, binding.topologySha256, false, stopped, 1, 0, 1)
                2 -> OwnedControlResponse(request.operationId, request.operation, request.generation, "pending", policy = stopped)
                else -> OwnedControlResponse(request.operationId, request.operation, request.generation, "stale", policy = resumed)
            }
        }
        assertEquals("conflict", interrupted.outcome); assertEquals(3, sends)
        assertEquals(stopped, assertNotNull(interrupted.lastStatus).policy)
    }

    @Test fun `await refuses coordinator process replacement`() = runBlocking {
        val node = UUID.randomUUID(); val process = UUID.randomUUID(); val replacement = UUID.randomUUID()
        val identity = OwnedCandidateIdentity("a".repeat(64), "b".repeat(64), "c".repeat(64))
        val binding = OwnedAdmissionBinding("d".repeat(64), "e".repeat(64))
        val stopped = OwnedAdmissionPolicy(7, OwnedAdmissionState.STOP_NEW, binding.topologySha256, binding.policySha256)
        var sends = 0
        val result = awaitClusterDrain(UUID.randomUUID(), 7, 500, node, identity, binding, pollMillis = 1) { request ->
            sends++
            if (request.operation == "status") OwnedControlResponse(request.operationId, request.operation, request.generation,
                "ok", node.toString(), (if (sends == 1) process else replacement).toString(), identity.candidateSha256,
                binding.topologySha256, false, stopped, 1, 0, 1)
            else OwnedControlResponse(request.operationId, request.operation, request.generation, "pending", policy = stopped)
        }
        assertEquals("conflict", result.outcome); assertEquals(3, sends)
        assertEquals(process.toString(), assertNotNull(result.lastStatus).processId)
    }
}
