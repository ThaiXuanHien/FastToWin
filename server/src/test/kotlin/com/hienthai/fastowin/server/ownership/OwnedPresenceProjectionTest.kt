package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.server.StoredActivePlayer
import com.hienthai.fastowin.server.StoredActiveRoom
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class OwnedPresenceProjectionTest {
    private val socket = SocketFence(UUID.randomUUID(), 7)
    private val room = StoredActiveRoom(
        roomId = UUID.randomUUID().toString(), roomName = "Presence",
        host = StoredActivePlayer(socket.playerId.toString(), "Host", true),
        gameMode = ProtocolGameMode.ORDER, phase = RoomPhase.WAITING,
        pilotConnectionEpoch = 3,
        pilotSocketGenerations = mapOf(socket.playerId.toString() to socket.generation)
    )

    @Test fun `owned waiting playing and finished snapshots have authoritative presence`() {
        assertEquals(OwnedPresenceRead.Present(FriendPresence.IN_ROOM), room.ownedPresence(socket, 3))
        for (phase in listOf(RoomPhase.PLAYING, RoomPhase.FINISHED)) {
            assertEquals(OwnedPresenceRead.Present(FriendPresence.PLAYING), room.copy(phase = phase).ownedPresence(socket, 3))
        }
    }

    @Test fun `stale socket takeover unattached departed or guest snapshots cannot publish presence`() {
        assertEquals(OwnedPresenceRead.Unavailable, room.ownedPresence(socket.copy(generation = 8), 3))
        assertEquals(OwnedPresenceRead.Unavailable, room.ownedPresence(socket, 4))
        assertEquals(OwnedPresenceRead.Unavailable, room.copy(pilotSocketGenerations = emptyMap()).ownedPresence(socket, 3))
        assertEquals(OwnedPresenceRead.Unavailable, room.copy(departedPlayerIds = setOf(socket.playerId.toString())).ownedPresence(socket, 3))
        assertEquals(OwnedPresenceRead.Unavailable, room.copy(host = room.host.copy(isAccount = false)).ownedPresence(socket, 3))
    }
}
