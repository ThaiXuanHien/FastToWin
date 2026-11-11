package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ProtocolGameMode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class OwnedRoomCreateTest {
    @Test fun `nonsecret descriptor digest excludes password but binds raw name and operation identity`() {
        val request = OwnedCreateRequest(UUID.randomUUID(), UUID.randomUUID(), SocketFence(ADMISSION_HOST, 1),
            ADMISSION_HOST.toString(), ADMISSION_NODE_B, OwnedAdmissionPolicy(7, OwnedAdmissionState.ACTIVE, "a".repeat(64), "b".repeat(64)),
            ClientMessage.CreateRoom("Name", "secret", ProtocolGameMode.ORDER))
        assertEquals(request.nonsecretSha256(), request.copy(command = request.command.copy(password = "other")).nonsecretSha256())
        assertNotEquals(request.nonsecretSha256(), request.copy(command = request.command.copy(roomName = "Name ")).nonsecretSha256())
        assertNotEquals(request.nonsecretSha256(), request.copy(operationId = UUID.randomUUID()).nonsecretSha256())
        assertNotEquals(request.nonsecretSha256(), request.copy(socket = request.socket.copy(generation = 2)).nonsecretSha256())
        assertEquals("OwnedCreateRequest(redacted)", request.toString())
    }

    @Test fun `create placement has portable vectors across roles and runtime restarts`() {
        val a = UUID.fromString("00000000-0000-0000-0000-00000000000a")
        val b = UUID.fromString("00000000-0000-0000-0000-00000000000b")
        val vectors = mapOf(
            "10000000-0000-0000-0000-000000000001" to b,
            "10000000-0000-0000-0000-000000000002" to a,
            "10000000-0000-0000-0000-000000000003" to b,
            "10000000-0000-0000-0000-000000000004" to a
        )
        for (role in RoomOwnershipRuntimeRole.entries) repeat(2) {
            val routing = RoomOwnershipRouting(admissionProductionConfig(role, nodeA = a, nodeB = b))
            vectors.forEach { (account, expected) -> assertEquals(expected, routing.createNode(account)) }
            assertFailsWith<IllegalArgumentException> { routing.createNode("not-an-account") }
            assertFailsWith<IllegalArgumentException> { routing.createNode("ABCDEF00-0000-0000-0000-000000000001") }
        }
    }
}
