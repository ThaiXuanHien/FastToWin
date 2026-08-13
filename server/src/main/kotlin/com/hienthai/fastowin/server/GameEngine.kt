package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.MAX_PROFILE_DISPLAY_NAME_LENGTH
import com.hienthai.fastowin.protocol.PROFILE_AVATAR_IDS
import com.hienthai.fastowin.protocol.GAME_NUMBER_COUNT
import com.hienthai.fastowin.protocol.GameSnapshot
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.FriendsSnapshot
import com.hienthai.fastowin.protocol.PlayerSnapshot
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.protocol.RoomSummary
import com.hienthai.fastowin.protocol.ServerMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class Delivery(
    val message: ServerMessage,
    val recipients: Set<String>? = null
)

data class ConnectedPlayer(
    val playerId: String,
    val resumeToken: String?,
    val currentGame: GameSnapshot?
)

class GameEngine(
    private val identityRepository: GuestIdentityRepository = InMemoryGuestIdentityRepository(),
    private val matchResultRepository: MatchResultRepository = NoOpMatchResultRepository,
    private val playerProfileRepository: PlayerProfileRepository = NoOpPlayerProfileRepository,
    private val leaderboardRepository: LeaderboardRepository = NoOpLeaderboardRepository,
    private val friendRepository: FriendRepository = NoOpFriendRepository,
    private val timeAttackMillis: Long = DEFAULT_TIME_ATTACK_MILLIS,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private val sessionsByPlayerId = mutableMapOf<String, GuestSession>()
    private val rooms = mutableMapOf<String, Room>()
    private val roomInvitations = mutableMapOf<String, RoomInvitationRecord>()

    suspend fun connectGuest(displayName: String, resumeToken: String?): ConnectedPlayer {
        val safeName = displayName.trim().take(MAX_PLAYER_NAME_LENGTH)
        require(safeName.isNotEmpty()) { "Tên người chơi không được để trống." }
        val identity = identityRepository.resolveGuest(safeName, resumeToken, nowMillis())

        return connectIdentity(identity.playerId, identity.displayName, identity.resumeToken)
    }

    suspend fun connectAccount(account: AuthenticatedAccount): ConnectedPlayer =
        connectIdentity(account.userId.toString(), account.displayName, null)

    private suspend fun connectIdentity(
        identityPlayerId: String,
        identityDisplayName: String,
        resumeToken: String?
    ): ConnectedPlayer = mutex.withLock {
            val session = sessionsByPlayerId[identityPlayerId]?.also { existing ->
                existing.displayName = identityDisplayName
                existing.resumeToken = resumeToken
            } ?: GuestSession(
                playerId = identityPlayerId,
                resumeToken = resumeToken,
                displayName = identityDisplayName
            ).also { created ->
                sessionsByPlayerId[created.playerId] = created
            }
            session.isConnected = true
            session.disconnectedAtMillis = null

            ConnectedPlayer(
                playerId = session.playerId,
                resumeToken = session.resumeToken,
                currentGame = roomFor(session.playerId)?.snapshot()
            )
        }

    suspend fun handle(playerId: String, message: ClientMessage): List<Delivery> {
        if (message is ClientMessage.GetProfile) return loadProfile(playerId)
        if (message is ClientMessage.UpdateProfile) return updateProfile(playerId, message)
        if (message is ClientMessage.GetLeaderboard) return loadLeaderboard(playerId)
        if (message is ClientMessage.GetFriends) return loadFriends(playerId)
        if (message is ClientMessage.SendFriendRequest) return sendFriendRequest(playerId, message.playerCode)
        if (message is ClientMessage.RespondFriendRequest) return respondFriendRequest(playerId, message)
        if (message is ClientMessage.InviteFriend) return inviteFriend(playerId, message)
        if (message is ClientMessage.RespondRoomInvitation) return respondRoomInvitation(playerId, message)
        val result = mutex.withLock {
            val player = sessionsByPlayerId[playerId]
                ?: return@withLock HandleResult(
                    listOf(error(playerId, "SESSION_NOT_FOUND", "Phiên chơi không còn hợp lệ."))
                )

            when (message) {
                is ClientMessage.ConnectGuest -> HandleResult(listOf(
                    error(playerId, "ALREADY_CONNECTED", "Phiên WebSocket đã được xác thực.")
                ))
                is ClientMessage.ConnectAccount -> HandleResult(listOf(
                    error(playerId, "ALREADY_CONNECTED", "Phiên WebSocket đã được xác thực.")
                ))

                ClientMessage.ListRooms -> HandleResult(
                    listOf(Delivery(ServerMessage.RoomList(publicRooms()), setOf(playerId)))
                )
                ClientMessage.GetProfile -> HandleResult(emptyList())
                is ClientMessage.UpdateProfile -> HandleResult(emptyList())
                ClientMessage.GetLeaderboard -> HandleResult(emptyList())
                ClientMessage.GetFriends -> HandleResult(emptyList())
                is ClientMessage.SendFriendRequest -> HandleResult(emptyList())
                is ClientMessage.RespondFriendRequest -> HandleResult(emptyList())
                is ClientMessage.InviteFriend -> HandleResult(emptyList())
                is ClientMessage.RespondRoomInvitation -> HandleResult(emptyList())
                is ClientMessage.CreateRoom -> HandleResult(createRoom(player, message))
                is ClientMessage.JoinRoom -> HandleResult(joinRoom(player, message))
                is ClientMessage.LeaveRoom -> HandleResult(leaveRoom(player, message))
                is ClientMessage.SelectNumber -> selectNumber(player, message)
            }
        }
        result.completedMatch?.let { completed ->
            runCatching { matchResultRepository.save(completed) }
                .onFailure { System.err.println("Could not persist match ${completed.matchId}: ${it.message}") }
        }
        val presenceChanged = message is ClientMessage.CreateRoom ||
            message is ClientMessage.JoinRoom || message is ClientMessage.LeaveRoom
        val affectedPlayers = if (presenceChanged) {
            (result.deliveries.flatMap { it.recipients.orEmpty() } + playerId).toSet()
        } else emptySet()
        return result.deliveries + affectedPlayers.flatMap { presenceUpdates(it) }
    }

    private suspend fun loadFriends(playerId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val stored = runCatching { friendRepository.load(playerId) }.getOrElse {
            return listOf(error(playerId, "FRIENDS_UNAVAILABLE", "Chưa tải được danh sách bạn bè."))
        }
        val presence = mutex.withLock {
            stored.friends.associate { friend -> friend.userId to presenceOf(friend.userId) }
        }
        return listOf(Delivery(
            ServerMessage.FriendsData(FriendsSnapshot(
                friends = stored.friends.map { it.copy(presence = presence[it.userId] ?: FriendPresence.OFFLINE) },
                incomingRequests = stored.incomingRequests,
                outgoingRequests = stored.outgoingRequests
            )),
            setOf(playerId)
        ))
    }

    private suspend fun sendFriendRequest(playerId: String, playerCode: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        if (playerCode.isBlank() || playerCode.length > 12) {
            return listOf(error(playerId, "INVALID_PLAYER_CODE", "Mã người chơi không hợp lệ."))
        }
        return when (val result = friendRepository.sendRequest(playerId, playerCode, nowMillis())) {
            is FriendRequestResult.Success ->
                listOf(Delivery(ServerMessage.SocialNotice("Đã gửi lời mời kết bạn."), setOf(playerId))) +
                    refreshSocialFor(setOf(playerId, result.recipientId))
            FriendRequestResult.PlayerNotFound -> listOf(error(playerId, "PLAYER_NOT_FOUND", "Không tìm thấy mã người chơi."))
            FriendRequestResult.SelfRequest -> listOf(error(playerId, "SELF_FRIEND_REQUEST", "Bạn không thể tự kết bạn với mình."))
            FriendRequestResult.AlreadyExists -> listOf(error(playerId, "FRIENDSHIP_EXISTS", "Hai người đã là bạn hoặc đang có lời mời."))
        }
    }

    private suspend fun respondFriendRequest(
        playerId: String,
        command: ClientMessage.RespondFriendRequest
    ): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        return when (val result = friendRepository.respond(playerId, command.requestId, command.accept, nowMillis())) {
            is FriendResponseResult.Success -> {
                val notice = if (command.accept) "Đã chấp nhận lời mời kết bạn." else "Đã từ chối lời mời kết bạn."
                listOf(Delivery(ServerMessage.SocialNotice(notice), setOf(playerId))) +
                    refreshSocialFor(setOf(playerId, result.requesterId))
            }
            FriendResponseResult.NotFound -> listOf(error(playerId, "FRIEND_REQUEST_NOT_FOUND", "Lời mời không còn tồn tại."))
        }
    }

    private suspend fun inviteFriend(playerId: String, command: ClientMessage.InviteFriend): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        if (!friendRepository.areFriends(playerId, command.friendUserId)) {
            return listOf(error(playerId, "NOT_FRIENDS", "Người chơi này chưa phải bạn bè."))
        }
        return mutex.withLock {
            val inviter = sessionsByPlayerId[playerId]
                ?: return@withLock listOf(error(playerId, "SESSION_NOT_FOUND", "Phiên chơi không còn hợp lệ."))
            val room = rooms[command.roomId]
                ?.takeIf { it.hostId == playerId && it.phase == RoomPhase.WAITING && it.guestId == null }
                ?: return@withLock listOf(error(playerId, "ROOM_NOT_INVITABLE", "Phòng không còn sẵn sàng để mời bạn."))
            val friend = sessionsByPlayerId[command.friendUserId]
                ?.takeIf { it.isConnected && it.resumeToken == null }
                ?: return@withLock listOf(error(playerId, "FRIEND_OFFLINE", "Bạn bè hiện không online."))
            if (roomFor(friend.playerId) != null) {
                return@withLock listOf(error(playerId, "FRIEND_BUSY", "Bạn bè đang ở trong phòng khác."))
            }
            val invitation = RoomInvitationRecord(
                id = UUID.randomUUID().toString(),
                inviterId = playerId,
                inviteeId = friend.playerId,
                roomId = room.id,
                expiresAtMillis = nowMillis() + ROOM_INVITATION_TTL_MILLIS
            )
            roomInvitations.entries.removeAll { it.value.inviterId == playerId && it.value.inviteeId == friend.playerId }
            roomInvitations[invitation.id] = invitation
            listOf(
                Delivery(ServerMessage.RoomInvitation(
                    invitationId = invitation.id,
                    fromUserId = playerId,
                    fromDisplayName = inviter.displayName,
                    roomId = room.id,
                    roomName = room.name,
                    expiresAtEpochMillis = invitation.expiresAtMillis
                ), setOf(friend.playerId)),
                Delivery(ServerMessage.SocialNotice("Đã gửi lời mời vào phòng."), setOf(playerId))
            )
        }
    }

    private suspend fun respondRoomInvitation(
        playerId: String,
        command: ClientMessage.RespondRoomInvitation
    ): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val result = mutex.withLock {
            val invitation = roomInvitations.remove(command.invitationId)
                ?.takeIf { it.inviteeId == playerId && it.expiresAtMillis > nowMillis() }
                ?: return@withLock listOf(error(playerId, "INVITATION_EXPIRED", "Lời mời vào phòng đã hết hạn."))
            if (!command.accept) {
                return@withLock listOf(
                    Delivery(ServerMessage.SocialNotice("Đã từ chối lời mời vào phòng."), setOf(playerId)),
                    Delivery(ServerMessage.SocialNotice("Bạn bè đã từ chối lời mời vào phòng."), setOf(invitation.inviterId))
                )
            }
            val player = sessionsByPlayerId[playerId]
                ?: return@withLock listOf(error(playerId, "SESSION_NOT_FOUND", "Phiên chơi không còn hợp lệ."))
            if (roomFor(playerId) != null) {
                return@withLock listOf(error(playerId, "ALREADY_IN_ROOM", "Bạn đang ở trong một phòng khác."))
            }
            val room = rooms[invitation.roomId]
                ?.takeIf { it.hostId == invitation.inviterId && it.phase == RoomPhase.WAITING && it.guestId == null }
                ?: return@withLock listOf(error(playerId, "ROOM_NOT_FOUND", "Phòng được mời không còn sẵn sàng."))
            room.guestId = player.playerId
            room.phase = RoomPhase.PLAYING
            room.numbers = (1..GAME_NUMBER_COUNT).shuffled()
            room.startedAtEpochMillis = nowMillis()
            room.sequence++
            room.scores[player.playerId] = 0
            listOf(
                Delivery(ServerMessage.GameStarted(room.snapshot()), room.playerIds()),
                Delivery(ServerMessage.RoomList(publicRooms()))
            )
        }
        val affectedPlayers = (result.flatMap { it.recipients.orEmpty() } + playerId).toSet()
        return result + affectedPlayers.flatMap { presenceUpdates(it) }
    }

    suspend fun presenceUpdates(playerId: String): List<Delivery> {
        val isAccount = mutex.withLock { sessionsByPlayerId[playerId]?.resumeToken == null }
        if (!isAccount) return emptyList()
        val stored = runCatching { friendRepository.load(playerId) }.getOrNull() ?: return emptyList()
        return refreshSocialFor((stored.friends.map { it.userId } + playerId).toSet())
    }

    private suspend fun refreshSocialFor(userIds: Set<String>): List<Delivery> {
        val connectedAccounts = mutex.withLock {
            userIds.filter { sessionsByPlayerId[it]?.let { session -> session.isConnected && session.resumeToken == null } == true }
        }
        return connectedAccounts.flatMap { loadFriends(it) }
    }

    private suspend fun isAccountSession(playerId: String): Boolean = mutex.withLock {
        sessionsByPlayerId[playerId]?.let { it.isConnected && it.resumeToken == null } == true
    }

    private fun presenceOf(playerId: String): FriendPresence {
        val session = sessionsByPlayerId[playerId]
        if (session?.isConnected != true || session.resumeToken != null) return FriendPresence.OFFLINE
        val room = roomFor(playerId) ?: return FriendPresence.ONLINE
        return if (room.phase == RoomPhase.WAITING) FriendPresence.IN_ROOM else FriendPresence.PLAYING
    }

    private fun accountRequired(playerId: String) = error(
        playerId,
        "ACCOUNT_REQUIRED",
        "Hãy đăng nhập tài khoản để sử dụng tính năng bạn bè."
    )

    private suspend fun loadProfile(playerId: String): List<Delivery> {
        val session = mutex.withLock { sessionsByPlayerId[playerId]?.copy() }
            ?: return listOf(error(playerId, "SESSION_NOT_FOUND", "Phiên chơi không còn hợp lệ."))
        val persisted = runCatching { playerProfileRepository.findByPlayerId(playerId) }
            .onFailure { System.err.println("Could not load profile $playerId: ${it.message}") }
            .getOrNull()
        val profile = persisted ?: PlayerProfileSnapshot(
            displayName = session.displayName,
            playerCode = playerId.replace("-", "").take(10).uppercase()
        )
        return listOf(Delivery(ServerMessage.ProfileData(profile), setOf(playerId)))
    }

    private suspend fun updateProfile(
        playerId: String,
        command: ClientMessage.UpdateProfile
    ): List<Delivery> {
        val safeName = command.displayName.trim()
        if (safeName.isEmpty() || safeName.length > MAX_PROFILE_DISPLAY_NAME_LENGTH) {
            return listOf(error(
                playerId,
                "INVALID_DISPLAY_NAME",
                "Biệt danh phải có từ 1 đến $MAX_PROFILE_DISPLAY_NAME_LENGTH ký tự."
            ))
        }
        if (command.avatarId != null && command.avatarId !in PROFILE_AVATAR_IDS) {
            return listOf(error(playerId, "INVALID_AVATAR", "Ảnh đại diện không hợp lệ."))
        }
        val session = mutex.withLock { sessionsByPlayerId[playerId]?.copy() }
        if (session == null) {
            return listOf(error(playerId, "SESSION_NOT_FOUND", "Phiên chơi không còn hợp lệ."))
        }
        if (session.resumeToken != null) {
            return listOf(error(
                playerId,
                "ACCOUNT_REQUIRED",
                "Hãy lưu tài khoản khách trước khi chỉnh sửa hồ sơ."
            ))
        }
        val updated = runCatching {
            playerProfileRepository.updateProfile(playerId, safeName, command.avatarId)
        }.onFailure {
            System.err.println("Could not update profile $playerId: ${it.message}")
        }.getOrDefault(false)
        if (!updated) {
            return listOf(error(
                playerId,
                "PROFILE_UPDATE_UNAVAILABLE",
                "Chưa thể lưu hồ sơ. Vui lòng thử lại."
            ))
        }
        mutex.withLock { sessionsByPlayerId[playerId]?.displayName = safeName }
        return loadProfile(playerId) + Delivery(roomList()) + presenceUpdates(playerId)
    }

    private suspend fun loadLeaderboard(playerId: String): List<Delivery> {
        val sessionExists = mutex.withLock { playerId in sessionsByPlayerId }
        if (!sessionExists) {
            return listOf(error(playerId, "SESSION_NOT_FOUND", "Phiên chơi không còn hợp lệ."))
        }
        val leaderboard = runCatching {
            leaderboardRepository.load(playerId, LEADERBOARD_SIZE)
        }.onFailure {
            System.err.println("Could not load leaderboard $playerId: ${it.message}")
        }.getOrElse {
            return listOf(error(playerId, "LEADERBOARD_UNAVAILABLE", "Chưa tải được bảng xếp hạng."))
        }
        return listOf(Delivery(ServerMessage.LeaderboardData(leaderboard), setOf(playerId)))
    }

    suspend fun roomList(): ServerMessage.RoomList = mutex.withLock {
        ServerMessage.RoomList(publicRooms())
    }

    suspend fun advanceTimedGames(): List<Delivery> {
        val advances = mutex.withLock {
            rooms.values.mapNotNull { room ->
                if (
                    room.gameMode != com.hienthai.fastowin.protocol.ProtocolGameMode.TIME_ATTACK ||
                    room.phase != RoomPhase.PLAYING ||
                    nowMillis() - (room.startedAtEpochMillis ?: return@mapNotNull null) < timeAttackMillis
                ) return@mapNotNull null

                room.phase = RoomPhase.FINISHED
                room.sequence++
                TimedAdvance(
                    delivery = Delivery(ServerMessage.GameFinished(room.snapshot()), room.playerIds()),
                    completedMatch = room.takeCompletedMatch()
                )
            }
        }
        advances.mapNotNull(TimedAdvance::completedMatch).forEach { completed ->
            runCatching { matchResultRepository.save(completed) }
                .onFailure { System.err.println("Could not persist timed match ${completed.matchId}: ${it.message}") }
        }
        return advances.map(TimedAdvance::delivery)
    }

    suspend fun markDisconnected(playerId: String): List<Delivery> {
        val disconnectedAt = nowMillis()
        val deliveries = mutex.withLock {
            val session = sessionsByPlayerId[playerId] ?: return@withLock emptyList()
            session.isConnected = false
            session.disconnectedAtMillis = disconnectedAt
            val room = roomFor(playerId)
            if (room?.phase == RoomPhase.WAITING && room.hostId == playerId) {
                listOf(Delivery(ServerMessage.RoomList(publicRooms())))
            } else {
                emptyList()
            }
        }
        runCatching { identityRepository.markDisconnected(playerId, disconnectedAt) }
            .onFailure { System.err.println("Could not persist disconnected session $playerId: ${it.message}") }
        return deliveries + presenceUpdates(playerId)
    }

    suspend fun cleanupExpiredSessions(): List<Delivery> = mutex.withLock {
        val now = nowMillis()
        roomInvitations.entries.removeAll { it.value.expiresAtMillis <= now }
        val expiredPlayerIds = sessionsByPlayerId.values
            .filter { session ->
                if (session.isConnected) return@filter false
                val disconnectedAt = session.disconnectedAtMillis ?: return@filter false
                val timeout = if (roomFor(session.playerId) == null) {
                    IDLE_SESSION_TTL_MILLIS
                } else {
                    ROOM_RECONNECT_GRACE_MILLIS
                }
                now - disconnectedAt >= timeout
            }
            .map { it.playerId }

        if (expiredPlayerIds.isEmpty()) return@withLock emptyList()

        val deliveries = mutableListOf<Delivery>()
        val removedRoomIds = mutableSetOf<String>()
        expiredPlayerIds.forEach { playerId ->
            roomFor(playerId)?.takeIf { removedRoomIds.add(it.id) }?.let { room ->
                rooms.remove(room.id)
                deliveries += Delivery(
                    ServerMessage.RoomClosed(
                        roomId = room.id,
                        reason = "Người chơi đã mất kết nối quá lâu."
                    ),
                    room.playerIds()
                )
            }
            sessionsByPlayerId.remove(playerId)
        }
        if (removedRoomIds.isNotEmpty()) {
            deliveries += Delivery(ServerMessage.RoomList(publicRooms()))
        }
        deliveries
    }

    private fun createRoom(player: GuestSession, command: ClientMessage.CreateRoom): List<Delivery> {
        if (roomFor(player.playerId) != null) {
            return listOf(error(player.playerId, "ALREADY_IN_ROOM", "Bạn đang ở trong một phòng khác."))
        }
        val name = command.roomName.trim().take(MAX_ROOM_NAME_LENGTH)
        if (name.isEmpty()) {
            return listOf(error(player.playerId, "INVALID_ROOM_NAME", "Tên phòng không được để trống."))
        }
        if (command.password.isEmpty()) {
            return listOf(error(player.playerId, "INVALID_PASSWORD", "Mật khẩu phòng không được để trống."))
        }

        val room = Room(
            id = UUID.randomUUID().toString(),
            name = name,
            hostId = player.playerId,
            password = PasswordHash.create(command.password),
            gameMode = command.gameMode
        )
        rooms[room.id] = room
        return listOf(
            Delivery(ServerMessage.RoomCreated(room.snapshot()), setOf(player.playerId)),
            Delivery(ServerMessage.RoomList(publicRooms()))
        )
    }

    private fun joinRoom(player: GuestSession, command: ClientMessage.JoinRoom): List<Delivery> {
        if (roomFor(player.playerId) != null) {
            return listOf(error(player.playerId, "ALREADY_IN_ROOM", "Bạn đang ở trong một phòng khác."))
        }
        val room = rooms[command.roomId]
            ?: return listOf(error(player.playerId, "ROOM_NOT_FOUND", "Phòng không còn tồn tại."))
        if (room.phase != RoomPhase.WAITING || room.guestId != null) {
            return listOf(error(player.playerId, "ROOM_FULL", "Phòng đã đủ người."))
        }
        if (!room.password.matches(command.password)) {
            return listOf(error(player.playerId, "WRONG_PASSWORD", "Mật khẩu phòng không đúng."))
        }

        room.guestId = player.playerId
        room.phase = RoomPhase.PLAYING
        room.numbers = (1..GAME_NUMBER_COUNT).shuffled()
        room.startedAtEpochMillis = nowMillis()
        room.sequence++
        room.scores[player.playerId] = 0
        val participants = room.playerIds()
        return listOf(
            Delivery(ServerMessage.GameStarted(room.snapshot()), participants),
            Delivery(ServerMessage.RoomList(publicRooms()))
        )
    }

    private fun leaveRoom(player: GuestSession, command: ClientMessage.LeaveRoom): List<Delivery> {
        val room = rooms[command.roomId]
            ?: return listOf(error(player.playerId, "ROOM_NOT_FOUND", "Phòng không còn tồn tại."))
        if (player.playerId !in room.playerIds()) {
            return listOf(error(player.playerId, "NOT_IN_ROOM", "Bạn không ở trong phòng này."))
        }

        val participants = room.playerIds()
        rooms.remove(room.id)
        return listOf(
            Delivery(ServerMessage.RoomClosed(room.id, "Một người chơi đã rời phòng."), participants),
            Delivery(ServerMessage.RoomList(publicRooms()))
        )
    }

    private fun selectNumber(player: GuestSession, command: ClientMessage.SelectNumber): HandleResult {
        val room = rooms[command.roomId]
            ?: return HandleResult(listOf(error(player.playerId, "ROOM_NOT_FOUND", "Phòng không còn tồn tại.", command.requestId)))
        if (player.playerId !in room.playerIds()) {
            return HandleResult(listOf(error(player.playerId, "NOT_IN_ROOM", "Bạn không ở trong phòng này.", command.requestId)))
        }
        if (command.requestId.isBlank() || command.requestId.length > MAX_REQUEST_ID_LENGTH) {
            return HandleResult(listOf(error(player.playerId, "INVALID_REQUEST_ID", "Mã yêu cầu không hợp lệ.")))
        }
        val requestKey = "${player.playerId}:${command.requestId}"
        room.processedRequests[requestKey]?.let { previous ->
            return HandleResult(listOf(Delivery(previous, setOf(player.playerId))))
        }
        if (room.processedRequests.size >= MAX_REQUESTS_PER_MATCH) {
            return HandleResult(listOf(error(player.playerId, "TOO_MANY_REQUESTS", "Trận đấu có quá nhiều lượt gửi.")))
        }
        room.refreshTimedState()
        if (room.phase != RoomPhase.PLAYING) {
            return HandleResult(
                deliveries = listOf(error(player.playerId, "GAME_NOT_PLAYING", "Trận đấu chưa bắt đầu hoặc đã kết thúc.", command.requestId)),
                completedMatch = room.takeCompletedMatch()
            )
        }
        if (command.number != room.currentTarget) {
            val rejected = error(player.playerId, "WRONG_NUMBER", "Chưa đúng số, thử lại nhé!", command.requestId)
            room.processedRequests[requestKey] = rejected.message
            room.recordSelection(player.playerId, command, SelectionResult.REJECTED)
            return HandleResult(listOf(rejected))
        }

        room.recordSelection(player.playerId, command, SelectionResult.ACCEPTED)
        room.selectedNumbers += command.number
        room.scores[player.playerId] = room.scores.getValue(player.playerId) + SCORE_PER_NUMBER
        room.currentTarget++
        room.sequence++
        if (room.currentTarget > GAME_NUMBER_COUNT) room.phase = RoomPhase.FINISHED

        val event = ServerMessage.GameStateUpdated(
            game = room.snapshot(),
            acceptedNumber = command.number,
            selectedByPlayerId = player.playerId
        )
        room.processedRequests[requestKey] = event
        return HandleResult(
            deliveries = listOf(Delivery(event, room.playerIds())),
            completedMatch = room.takeCompletedMatch()
        )
    }

    private fun roomFor(playerId: String): Room? = rooms.values.firstOrNull { playerId in it.playerIds() }

    private fun publicRooms(): List<RoomSummary> = rooms.values
        .asSequence()
        .filter {
            it.phase == RoomPhase.WAITING &&
                it.guestId == null &&
                sessionsByPlayerId[it.hostId]?.isConnected == true
        }
        .map { room ->
            RoomSummary(
                id = room.id,
                name = room.name,
                hostName = sessionsByPlayerId[room.hostId]?.displayName.orEmpty(),
                gameMode = room.gameMode,
                requiresPassword = true
            )
        }
        .sortedBy { it.name.lowercase() }
        .toList()

    private fun Room.snapshot(): GameSnapshot {
        refreshTimedState()
        return GameSnapshot(
            roomId = id,
            roomName = name,
            hostId = hostId,
            gameMode = gameMode,
            phase = phase,
            players = playerIds().mapNotNull { id ->
                sessionsByPlayerId[id]?.let { session ->
                    PlayerSnapshot(id = id, name = session.displayName, score = scores[id] ?: 0)
                }
            },
            numbers = numbers,
            selectedNumbers = selectedNumbers.toList(),
            currentTarget = currentTarget,
            sequence = sequence,
            startedAtEpochMillis = startedAtEpochMillis
        )
    }

    private fun Room.refreshTimedState() {
        val startedAt = startedAtEpochMillis ?: return
        if (
            gameMode == com.hienthai.fastowin.protocol.ProtocolGameMode.TIME_ATTACK &&
            phase == RoomPhase.PLAYING &&
            nowMillis() - startedAt >= timeAttackMillis
        ) {
            phase = RoomPhase.FINISHED
            sequence++
        }
    }

    private fun Room.takeCompletedMatch(): CompletedMatch? {
        if (phase != RoomPhase.FINISHED || resultQueued) return null
        val startedAt = startedAtEpochMillis ?: return null
        val highestScore = scores.values.maxOrNull() ?: 0
        val leaders = scores.filterValues { it == highestScore }.keys
        val winnerId = leaders.singleOrNull()
        resultQueued = true
        return CompletedMatch(
            matchId = id,
            roomName = name,
            gameMode = gameMode,
            startedAtMillis = startedAt,
            endedAtMillis = nowMillis(),
            winnerPlayerId = winnerId,
            players = playerIds().mapNotNull { playerId ->
                sessionsByPlayerId[playerId]?.let { session ->
                    CompletedMatchPlayer(
                        playerId = playerId,
                        displayName = session.displayName,
                        score = scores[playerId] ?: 0,
                        outcome = when {
                            winnerId == null -> MatchOutcome.DRAW
                            playerId == winnerId -> MatchOutcome.WIN
                            else -> MatchOutcome.LOSS
                        }
                    )
                }
            },
            events = selectionEvents.toList()
        )
    }

    private fun Room.recordSelection(
        playerId: String,
        command: ClientMessage.SelectNumber,
        result: SelectionResult
    ) {
        selectionEvents += MatchSelectionEvent(
            playerId = playerId,
            requestId = command.requestId,
            number = command.number,
            expectedNumber = currentTarget,
            result = result,
            occurredAtMillis = nowMillis(),
            sequence = selectionEvents.size + 1
        )
    }

    private fun error(
        playerId: String,
        code: String,
        message: String,
        requestId: String? = null
    ) = Delivery(ServerMessage.Error(code, message, requestId), setOf(playerId))

    private data class GuestSession(
        val playerId: String,
        var resumeToken: String?,
        var displayName: String,
        var isConnected: Boolean = true,
        var disconnectedAtMillis: Long? = null
    )

    private data class HandleResult(
        val deliveries: List<Delivery>,
        val completedMatch: CompletedMatch? = null
    )

    private data class TimedAdvance(
        val delivery: Delivery,
        val completedMatch: CompletedMatch?
    )

    private data class Room(
        val id: String,
        val name: String,
        val hostId: String,
        val password: PasswordHash,
        val gameMode: com.hienthai.fastowin.protocol.ProtocolGameMode,
        var guestId: String? = null,
        var phase: RoomPhase = RoomPhase.WAITING,
        var numbers: List<Int> = emptyList(),
        val selectedNumbers: MutableList<Int> = mutableListOf(),
        var currentTarget: Int = 1,
        val scores: MutableMap<String, Int> = mutableMapOf(hostId to 0),
        var sequence: Long = 0,
        var startedAtEpochMillis: Long? = null,
        var resultQueued: Boolean = false,
        val processedRequests: MutableMap<String, ServerMessage> = mutableMapOf(),
        val selectionEvents: MutableList<MatchSelectionEvent> = mutableListOf()
    ) {
        fun playerIds(): Set<String> = setOfNotNull(hostId, guestId)
    }

    private data class RoomInvitationRecord(
        val id: String,
        val inviterId: String,
        val inviteeId: String,
        val roomId: String,
        val expiresAtMillis: Long
    )

    private data class PasswordHash(val salt: ByteArray, val value: ByteArray) {
        fun matches(password: String): Boolean = MessageDigest.isEqual(value, derive(password, salt))

        companion object {
            fun create(password: String): PasswordHash {
                val salt = ByteArray(16).also(secureRandom::nextBytes)
                return PasswordHash(salt, derive(password, salt))
            }

            private fun derive(password: String, salt: ByteArray): ByteArray {
                val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            }
        }
    }

    private companion object {
        const val MAX_PLAYER_NAME_LENGTH = 32
        const val MAX_ROOM_NAME_LENGTH = 48
        const val SCORE_PER_NUMBER = 10
        const val MAX_REQUEST_ID_LENGTH = 64
        const val MAX_REQUESTS_PER_MATCH = 2_000
        const val LEADERBOARD_SIZE = 100
        const val DEFAULT_TIME_ATTACK_MILLIS = 60_000L
        const val ROOM_RECONNECT_GRACE_MILLIS = 30_000L
        const val IDLE_SESSION_TTL_MILLIS = 5 * 60_000L
        const val ROOM_INVITATION_TTL_MILLIS = 60_000L
        val secureRandom = SecureRandom()
    }
}
