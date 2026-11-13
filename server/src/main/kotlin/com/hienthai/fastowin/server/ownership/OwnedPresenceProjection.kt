package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.server.StoredActiveRoom

internal sealed interface OwnedPresenceRead {
    data object NoMembership : OwnedPresenceRead
    data class Present(val presence: FriendPresence) : OwnedPresenceRead
    data object Unavailable : OwnedPresenceRead
}

internal fun StoredActiveRoom.ownedPresence(socket: SocketFence, epoch: Long): OwnedPresenceRead {
    val playerId = socket.playerId.toString()
    if (playerId !in activePlayers() || players().none { it.playerId == playerId && it.isAccount } ||
        pilotConnectionEpoch != epoch || pilotSocketGenerations[playerId] != socket.generation
    ) return OwnedPresenceRead.Unavailable
    return OwnedPresenceRead.Present(if (phase == RoomPhase.WAITING) FriendPresence.IN_ROOM else FriendPresence.PLAYING)
}
