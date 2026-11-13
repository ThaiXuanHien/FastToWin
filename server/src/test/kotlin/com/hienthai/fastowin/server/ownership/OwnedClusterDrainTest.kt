package com.hienthai.fastowin.server.ownership

import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class OwnedClusterDrainTest {
    @Test
    fun `proof rejects restarted peer and preserves quiescing`() = runBlocking {
        val cluster = DrainFixture()
        cluster.peer.restartAfterQuiesce = true
        val result = cluster.drain.step(cluster.policy.generation)
        assertIs<OwnedDrainOutcome.Incomplete>(result)
        assertEquals(OwnedAdmissionState.QUIESCING, cluster.policy.state)
    }

    @Test
    fun `successful proof binds fresh processes candidate topology and both generations`() = runBlocking {
        val cluster = DrainFixture()
        val result = assertIs<OwnedDrainOutcome.Completed>(cluster.drain.step(cluster.policy.generation))
        assertEquals(OwnedAdmissionState.DISABLED, cluster.policy.state)
        assertEquals(cluster.local.instance, result.proof.nodes.single { it.nodeId == cluster.local.node }.processId)
        assertEquals(cluster.peer.instance, result.proof.nodes.single { it.nodeId == cluster.peer.node }.processId)
        assertEquals(cluster.binding.topologySha256, result.proof.topologySha256)
        assertEquals(cluster.candidate, result.proof.candidateSha256)
        assertEquals(OwnedAdmissionState.QUIESCING, result.proof.acknowledgedPolicy.state)
        assertEquals(cluster.policy, result.proof.disabledPolicy)
    }

    @Test
    fun `unavailable peer and nonzero database never disable owned routing`() = runBlocking {
        val cluster = DrainFixture()
        cluster.peer.unavailable = true
        assertIs<OwnedDrainOutcome.Unavailable>(cluster.drain.step(cluster.policy.generation))
        assertEquals(OwnedAdmissionState.STOP_NEW, cluster.policy.state)
        cluster.peer.unavailable = false
        cluster.snapshot = RoomOwnershipDurableSnapshot(0, 1, 0)
        assertIs<OwnedDrainOutcome.Incomplete>(cluster.drain.step(cluster.policy.generation))
        assertEquals(OwnedAdmissionState.STOP_NEW, cluster.policy.state)
    }

    @Test
    fun `disabled revalidation uses fresh processes and preserves authority`() = runBlocking {
        val cluster = DrainFixture()
        cluster.policy = cluster.policy.copy(generation = 8, state = OwnedAdmissionState.DISABLED)
        val result = assertIs<OwnedDrainOutcome.Completed>(cluster.drain.step(8))
        assertEquals(OwnedAdmissionState.DISABLED, result.proof.acknowledgedPolicy.state)
        assertEquals(cluster.policy, result.proof.disabledPolicy)
        assertEquals(setOf(cluster.local.instance, cluster.peer.instance), result.proof.nodes.map { it.processId }.toSet())
        cluster.peer.restartAfterQuiesce = true
        assertIs<OwnedDrainOutcome.Incomplete>(cluster.drain.step(8))
        assertEquals(OwnedAdmissionState.DISABLED, cluster.policy.state)
    }
    private class DrainFixture {
        val binding = OwnedAdmissionBinding("a".repeat(64), "b".repeat(64))
        val candidate = "c".repeat(64)
        var policy = OwnedAdmissionPolicy(1, OwnedAdmissionState.STOP_NEW, binding.topologySha256, binding.policySha256)
        var snapshot = RoomOwnershipDurableSnapshot(0, 0, 0)
        val local = Node(UUID.randomUUID())
        val peer = Node(UUID.randomUUID())
        val drain = OwnedClusterDrain(binding, candidate, local, peer,
            observe = { policy }, snapshot = { snapshot }, advance = { expected, next ->
                check(expected == policy)
                policy = policy.copy(generation = policy.generation + 1, state = next)
                policy
            }, verifyDisabled = { expected -> if (expected == policy) snapshot else null })
        inner class Node(val node: UUID) : OwnedDrainNode {
            var instance = UUID.randomUUID()
            var unavailable = false
            var restartAfterQuiesce = false
            var quiesced = false
            override val nodeId get() = node
            override suspend fun status(generation: Long): OwnedNodeDrainAck? = if (unavailable) null else
                OwnedNodeDrainAck(node, instance, generation, candidate, binding.topologySha256, quiesced, snapshot)
            override suspend fun closeWaiting(generation: Long) = !unavailable
            override suspend fun quiesce(generation: Long): OwnedNodeDrainAck? {
                if (unavailable) return null
                quiesced = true
                val ack = status(generation)
                if (restartAfterQuiesce) { instance = UUID.randomUUID(); quiesced = false }
                return ack
            }
        }
    }
}
