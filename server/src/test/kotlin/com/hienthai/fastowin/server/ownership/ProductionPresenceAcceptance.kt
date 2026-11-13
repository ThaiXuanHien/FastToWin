package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.*
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.time.Duration.Companion.nanoseconds

private data class TimedSocialFrame(val received: Long, val message: ServerMessage)

/** One consumer per viewer. Timestamp at receipt, before either assertion starts waiting. */
private class SocialViewer(
    scope: CoroutineScope,
    val socket: AcceptanceSocket,
    private val clanId: String?
) {
    private val frames = Channel<TimedSocialFrame>(64)
    private val reader = scope.launch {
        try {
            for (frame in socket.session.incoming) if (frame is Frame.Text) {
                val message = ProtocolJson.decodeFromString<ServerMessage>(frame.readText())
                frames.send(TimedSocialFrame(System.nanoTime(), message))
            }
        } finally { frames.close() }
    }
    private var lastPresenceRequest = 0L

    suspend fun await(after: Long, timeoutMillis: Long, predicate: (ServerMessage) -> Boolean): Long =
        withTimeout(timeoutMillis) {
            for (frame in frames) {
                if (frame.message is ServerMessage.Error) throw AcceptanceRejected(frame.message.code)
                if (frame.received >= after && predicate(frame.message))
                    return@withTimeout (frame.received - after).nanoseconds.inWholeMilliseconds
            }
            error("Social viewer disconnected")
        }

    private fun matches(message: ServerMessage, states: Map<String, FriendPresence>, refresh: Boolean): Boolean {
        val observed = when {
            clanId == null && message is ServerMessage.FriendsData ->
                message.social.friends.associate { it.userId to it.presence }
            clanId != null && message is ServerMessage.ClanInfoData &&
                message.clan.id == clanId && message.isPresenceRefresh == refresh ->
                message.clan.members.associate { it.userId to it.presence }
            else -> return false
        }
        return states.all { (id, state) -> observed[id] == state }
    }

    suspend fun push(start: Long, states: Map<String, FriendPresence>): Long =
        await(start, 12_000) { matches(it, states, true) }.also { check(it < 20_000) }

    suspend fun readback(states: Map<String, FriendPresence>) {
        if (lastPresenceRequest != 0L) {
            val elapsed = (System.nanoTime() - lastPresenceRequest).nanoseconds.inWholeMilliseconds
            delay((10_100L - elapsed).coerceAtLeast(0))
        }
        val request = UUID.randomUUID().toString()
        lastPresenceRequest = System.nanoTime()
        socket.send(ClientMessage.GetPresence(request, clanId))
        await(lastPresenceRequest, 8_000) { message ->
            message is ServerMessage.PresenceData && message.requestId == request &&
                states.all { (id, state) -> message.states[id] == state }
        }
        val start = System.nanoTime()
        socket.send(clanId?.let(ClientMessage::GetClanInfo) ?: ClientMessage.GetFriends)
        await(start, 8_000) { matches(it, states, false) }
    }

    suspend fun close() { reader.cancelAndJoin(); socket.closeGracefully() }
}

internal suspend fun productionPresenceAcceptance(fixture: RoomAcceptanceFixture): JsonObject = coroutineScope {
    val accounts = fixture.accounts()
    check(accounts.size == 40 && fixture.settings.soleIngress)
    val clanId = fixture.withFixtureDatabase { c ->
        c.prepareStatement("SELECT id FROM clans WHERE owner_id=?").use { statement ->
            statement.setObject(1, UUID.fromString(accounts[5].userId))
            statement.executeQuery().use { rows -> check(rows.next()); rows.getString(1).also { check(!rows.next()) } }
        }
    }
    val cases = mutableListOf<JsonObject>()
    for (owner in 0..1) {
        val viewerSockets = fixture.connectPair(accounts[4], 0, accounts[5], 0)
        val viewers = listOf(SocialViewer(this, viewerSockets[0], null), SocialViewer(this, viewerSockets[1], clanId))
        val pair = requireNotNull(fixture.settings.topologyStatus).targetPair(fixture.settings.accountIds, owner, owner)
            .map(accounts::get)
        val players = mutableListOf<AcceptanceSocket>()
        try {
            players += fixture.connectPair(pair[0], 0, pair[1], 0)
            val host = pair[0].userId
            val guest = pair[1].userId
            val online = FriendPresence.ONLINE
            val waiting = FriendPresence.IN_ROOM
            val playing = FriendPresence.PLAYING
            val stages = mutableListOf<JsonObject>()
            suspend fun stage(name: String, h: FriendPresence, g: FriendPresence, pushes: Boolean, action: suspend () -> Unit) {
                val expected = mapOf(host to h, guest to g)
                val start = System.nanoTime()
                action()
                val received = if (pushes) viewers.map { async { it.push(start, expected) } }.awaitAll() else emptyList()
                viewers.map { async { it.readback(expected) } }.awaitAll()
                stages += buildJsonObject {
                    put("name", name); put("readbacks", viewers.size)
                    put("friendPushMillis", received.getOrNull(0)?.let(::JsonPrimitive) ?: JsonNull)
                    put("clanPushMillis", received.getOrNull(1)?.let(::JsonPrimitive) ?: JsonNull)
                }
            }
            viewers.map { async { it.readback(mapOf(host to online, guest to online)) } }.awaitAll()
            var room = ""
            stage("created", waiting, online, true) {
                players[0].send(ClientMessage.CreateRoom("Social room", "SocialFixture123!", ProtocolGameMode.ORDER))
                room = (players[0].await { it is ServerMessage.RoomCreated } as ServerMessage.RoomCreated).game.roomId
                check(fixture.ownerFor(fixture.snapshot(room)) == owner)
            }
            stage("joined", waiting, waiting, true) {
                players[1].send(ClientMessage.JoinRoom(room, "SocialFixture123!"))
                for (player in players) player.await { it is ServerMessage.RoomUpdated && it.game.players.size == 2 }
            }
            stage("started", playing, playing, true) {
                players[0].send(ClientMessage.SetReady(room, true))
                for (player in players) player.await { it is ServerMessage.RoomUpdated }
                players[1].send(ClientMessage.SetReady(room, true))
                for (player in players) player.await { it is ServerMessage.GameStarted && it.game.roomId == room }
            }
            stage("replaced", playing, playing, false) {
                val old = players[0]
                players[0] = fixture.connect(pair[0], 0, old.ready.resumeToken)
                check(players[0].ready.currentGame?.roomId == room)
                old.closeGracefully()
            }
            stage("finished", playing, playing, false) {
                players[0].send(ClientMessage.LeaveRoom(room))
                for (player in players) player.await { it is ServerMessage.GameFinished && it.game.roomId == room }
            }
            stage("host-left", online, playing, true) { players[0].leaveRoom(room) }
            stage("guest-left", online, online, true) { players[1].leaveRoom(room) }
            cases += buildJsonObject { put("owner", owner); put("stages", JsonArray(stages)) }
        } finally {
            players.forEach { it.closeGracefully() }
            viewers.forEach { it.close() }
        }
    }
    val edges = fixture.number("SELECT count(*) FROM friendships WHERE status='ACCEPTED'")
    val members = fixture.number("SELECT count(*) FROM clan_members")
    check(edges == 4L && members == 5L)
    buildJsonObject { put("accounts", accounts.size); put("friendEdges", edges); put("clanMembers", members); put("cases", JsonArray(cases)) }
}
