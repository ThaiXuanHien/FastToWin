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
import com.hienthai.fastowin.protocol.RematchEvent
import com.hienthai.fastowin.protocol.NotificationDestination
import com.hienthai.fastowin.protocol.NotificationKind
import com.hienthai.fastowin.protocol.NotificationSnapshot
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
    private val activeRoomRepository: ActiveRoomRepository = NoOpActiveRoomRepository,
    private val notificationRepository: NotificationRepository = NoOpNotificationRepository,
    private val timeAttackMillis: Long = DEFAULT_TIME_ATTACK_MILLIS,
    private val rematchTimeoutMillis: Long = DEFAULT_REMATCH_TIMEOUT_MILLIS,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private val restorationMutex = Mutex()
    private val persistenceMutex = Mutex()
    private val sessionsByPlayerId = mutableMapOf<String, GuestSession>()
    private val rooms = mutableMapOf<String, Room>()
    private val roomInvitations = mutableMapOf<String, RoomInvitationRecord>()
    private val matchmakingEntries = mutableMapOf<String, MatchmakingEntry>()
    private var activeRoomsRestored = false

    suspend fun connectGuest(displayName: String, resumeToken: String?): ConnectedPlayer {
        restoreActiveRooms()
        val safeName = displayName.trim().take(MAX_PLAYER_NAME_LENGTH)
        require(safeName.isNotEmpty()) { "Tên người chơi không được để trống." }
        val identity = identityRepository.resolveGuest(safeName, resumeToken, nowMillis())

        return connectIdentity(identity.playerId, identity.displayName, identity.resumeToken)
    }

    suspend fun connectAccount(account: AuthenticatedAccount): ConnectedPlayer {
        restoreActiveRooms()
        return connectIdentity(account.userId.toString(), account.displayName, null)
    }

    private suspend fun connectIdentity(
        identityPlayerId: String,
        identityDisplayName: String,
        resumeToken: String?
    ): ConnectedPlayer {
        val connected = mutex.withLock {
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
        connected.currentGame?.roomId?.let { persistRoom(it) }
        return connected
    }

    suspend fun restoreActiveRooms() {
        if (activeRoomsRestored) return
        restorationMutex.withLock {
            if (activeRoomsRestored) return@withLock
            val storedRooms = activeRoomRepository.loadAll()
            val restoredAtMillis = nowMillis()
            val storedInvitations = notificationRepository.loadActiveRoomInvitations(restoredAtMillis)
            mutex.withLock {
                if (activeRoomsRestored) return@withLock
                storedRooms.forEach { stored ->
                    rooms[stored.roomId] = stored.toRoom()
                    listOfNotNull(stored.host, stored.guest).forEach { player ->
                        sessionsByPlayerId.putIfAbsent(
                            player.playerId,
                            GuestSession(
                                playerId = player.playerId,
                                resumeToken = if (player.isAccount) null else RESTORED_GUEST_RESUME_TOKEN,
                                displayName = player.displayName,
                                isConnected = false,
                                disconnectedAtMillis = restoredAtMillis
                            )
                        )
                    }
                }
                storedInvitations.forEach { stored ->
                    val room = rooms[stored.roomId]
                    if (
                        room != null && room.hostId == stored.inviterId &&
                        room.phase == RoomPhase.WAITING && room.guestId == null
                    ) {
                        roomInvitations[stored.id] = stored.toRecord()
                    }
                }
                activeRoomsRestored = true
            }
        }
    }

    private suspend fun persistRoom(roomId: String) {
        persistenceMutex.withLock {
            val removedInvitations = mutableListOf<RoomInvitationRecord>()
            val snapshot = mutex.withLock {
                rooms[roomId]?.toStoredRoom().also { stored ->
                    if (stored == null) {
                        roomInvitations.entries.removeAll { entry ->
                            (entry.value.roomId == roomId).also { removed ->
                                if (removed) removedInvitations += entry.value
                            }
                        }
                    }
                }
            }
            if (snapshot == null) {
                activeRoomRepository.delete(roomId)
                notificationRepository.deleteRoomInvitationsForRoom(roomId)
                removedInvitations.forEach { invitation ->
                    notificationRepository.dismissNotifications(
                        invitation.inviteeId,
                        "room:${invitation.id}",
                        nowMillis()
                    )
                }
            } else {
                activeRoomRepository.save(snapshot)
            }
        }
    }

    suspend fun handle(playerId: String, message: ClientMessage): List<Delivery> {
        restoreActiveRooms()
        if (message is ClientMessage.GetProfile) return loadProfile(playerId)
        if (message is ClientMessage.GetFriendProfile) return loadFriendProfile(playerId, message.friendUserId)
        if (message is ClientMessage.GetMatchDetail) return loadMatchDetail(playerId, message.matchId)
        if (message is ClientMessage.UpdateProfile) return updateProfile(playerId, message)
        if (message is ClientMessage.EquipCosmetics) return equipCosmetics(playerId, message)
        if (message is ClientMessage.GetLeaderboard) return loadLeaderboard(playerId)
        if (message is ClientMessage.GetFriends) return loadFriends(playerId)
        if (message is ClientMessage.GetRoomInvitations) return loadRoomInvitations(playerId)
        if (message is ClientMessage.GetNotifications) return loadNotifications(playerId)
        if (message is ClientMessage.SyncNotifications) return syncNotifications(playerId, message.notifications)
        if (message is ClientMessage.MarkNotificationsRead) {
            return markNotificationsRead(playerId, message.notificationId)
        }
        if (message is ClientMessage.DismissNotifications) {
            return dismissNotifications(playerId, message.notificationId)
        }
        if (message is ClientMessage.SendFriendRequest) return sendFriendRequest(playerId, message.playerCode)
        if (message is ClientMessage.RespondFriendRequest) return respondFriendRequest(playerId, message)
        if (message is ClientMessage.CancelFriendRequest) return cancelFriendRequest(playerId, message.requestId)
        if (message is ClientMessage.RemoveFriend) return removeFriend(playerId, message.friendUserId)
        if (message is ClientMessage.BlockPlayer) return blockPlayer(playerId, message.playerUserId)
        if (message is ClientMessage.UnblockPlayer) return unblockPlayer(playerId, message.playerUserId)
        if (message is ClientMessage.InviteFriend) return inviteFriend(playerId, message)
        if (message is ClientMessage.RespondRoomInvitation) return respondRoomInvitation(playerId, message)
        if (message is ClientMessage.JoinMatchmaking) return joinMatchmaking(playerId, message)
        if (message is ClientMessage.CancelMatchmaking) return cancelMatchmaking(playerId)
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
                is ClientMessage.GetFriendProfile -> HandleResult(emptyList())
                is ClientMessage.GetMatchDetail -> HandleResult(emptyList())
                is ClientMessage.UpdateProfile -> HandleResult(emptyList())
                is ClientMessage.EquipCosmetics -> HandleResult(emptyList())
                ClientMessage.GetLeaderboard -> HandleResult(emptyList())
                ClientMessage.GetFriends -> HandleResult(emptyList())
                ClientMessage.GetRoomInvitations -> HandleResult(emptyList())
                ClientMessage.GetNotifications -> HandleResult(emptyList())
                is ClientMessage.SyncNotifications -> HandleResult(emptyList())
                is ClientMessage.MarkNotificationsRead -> HandleResult(emptyList())
                is ClientMessage.DismissNotifications -> HandleResult(emptyList())
                is ClientMessage.MeasureLatency -> HandleResult(listOf(
                    Delivery(ServerMessage.LatencyPong(message.clientSentAtEpochMillis), setOf(playerId))
                ))
                is ClientMessage.JoinMatchmaking -> HandleResult(emptyList())
                ClientMessage.CancelMatchmaking -> HandleResult(emptyList())
                is ClientMessage.SendFriendRequest -> HandleResult(emptyList())
                is ClientMessage.RespondFriendRequest -> HandleResult(emptyList())
                is ClientMessage.CancelFriendRequest -> HandleResult(emptyList())
                is ClientMessage.RemoveFriend -> HandleResult(emptyList())
                is ClientMessage.BlockPlayer -> HandleResult(emptyList())
                is ClientMessage.UnblockPlayer -> HandleResult(emptyList())
                is ClientMessage.InviteFriend -> HandleResult(emptyList())
                is ClientMessage.RespondRoomInvitation -> HandleResult(emptyList())
                is ClientMessage.CreateRoom -> createRoom(player, message).let { deliveries ->
                    HandleResult(
                        deliveries = deliveries,
                        changedRoomId = deliveries.roomIdFrom<ServerMessage.RoomCreated>()
                    )
                }
                is ClientMessage.JoinRoom -> joinRoom(player, message).let { deliveries ->
                    HandleResult(
                        deliveries = deliveries,
                        changedRoomId = deliveries.roomIdFrom<ServerMessage.GameStarted>()
                    )
                }
                is ClientMessage.LeaveRoom -> leaveRoom(player, message).let { deliveries ->
                    HandleResult(
                        deliveries = deliveries,
                        changedRoomId = deliveries.asSequence()
                            .map(Delivery::message)
                            .filterIsInstance<ServerMessage.RoomClosed>()
                            .firstOrNull()
                            ?.roomId
                    )
                }
                is ClientMessage.SetReady -> setReady(player, message)
                is ClientMessage.KickPlayer -> kickPlayer(player, message)
                is ClientMessage.RequestRematch -> respondRematch(player, message.roomId, accept = true)
                is ClientMessage.RespondRematch -> respondRematch(player, message.roomId, message.accept)
                is ClientMessage.SelectNumber -> selectNumber(player, message)
            }
        }
        result.completedMatch?.let { completed ->
            runCatching { matchResultRepository.save(completed) }
                .onFailure { System.err.println("Could not persist match ${completed.matchId}: ${it.message}") }
        }
        result.changedRoomId?.let { persistRoom(it) }
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
                outgoingRequests = stored.outgoingRequests,
                blockedPlayers = stored.blockedPlayers,
                recentPlayers = stored.recentPlayers
            )),
            setOf(playerId)
        ))
    }

    private suspend fun loadNotifications(playerId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val pendingRequests = runCatching { friendRepository.load(playerId).incomingRequests }
            .getOrElse { return listOf(error(playerId, "NOTIFICATIONS_UNAVAILABLE", "Chưa tải được thông báo.")) }
        val pendingFriendNotifications = pendingRequests.map { request ->
            NotificationSnapshot(
                id = "friend:${request.requestId}",
                kind = NotificationKind.FRIEND_REQUEST,
                title = "Lời mời kết bạn",
                message = "${request.displayName} muốn kết bạn với bạn.",
                createdAtEpochMillis = nowMillis(),
                destination = NotificationDestination.FRIENDS
            )
        }
        notificationRepository.createNotifications(playerId, pendingFriendNotifications)
        val notifications = runCatching { notificationRepository.loadNotifications(playerId) }
            .getOrElse { return listOf(error(playerId, "NOTIFICATIONS_UNAVAILABLE", "Chưa tải được thông báo.")) }
        return listOf(Delivery(ServerMessage.NotificationsData(notifications), setOf(playerId)))
    }

    private suspend fun syncNotifications(
        playerId: String,
        notifications: List<NotificationSnapshot>
    ): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        if (notifications.size > MAX_NOTIFICATION_SYNC_BATCH) {
            return listOf(error(playerId, "INVALID_NOTIFICATIONS", "Có quá nhiều thông báo cần đồng bộ."))
        }
        val allowedKinds = setOf(NotificationKind.MISSION, NotificationKind.ACHIEVEMENT, NotificationKind.COSMETIC)
        val normalized = notifications.filter { notification ->
            notification.kind in allowedKinds &&
                notification.id.length in 1..160 &&
                notification.title.length in 1..120 &&
                notification.message.length in 1..300 &&
                notification.destination == NotificationDestination.PROFILE
        }.map { it.copy(createdAtEpochMillis = nowMillis(), isRead = false) }
        if (normalized.size != notifications.size) {
            return listOf(error(playerId, "INVALID_NOTIFICATIONS", "Dữ liệu thông báo không hợp lệ."))
        }
        notificationRepository.createNotifications(playerId, normalized)
        return loadNotifications(playerId)
    }

    private suspend fun markNotificationsRead(playerId: String, notificationId: String?): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        if (notificationId != null && notificationId.length !in 1..160) {
            return listOf(error(playerId, "INVALID_NOTIFICATION_ID", "Mã thông báo không hợp lệ."))
        }
        notificationRepository.markNotificationsRead(playerId, notificationId, nowMillis())
        return loadNotifications(playerId)
    }

    private suspend fun dismissNotifications(playerId: String, notificationId: String?): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        if (notificationId != null && notificationId.length !in 1..160) {
            return listOf(error(playerId, "INVALID_NOTIFICATION_ID", "Mã thông báo không hợp lệ."))
        }
        notificationRepository.dismissNotifications(playerId, notificationId, nowMillis())
        return loadNotifications(playerId)
    }

    private suspend fun refreshNotificationsFor(userIds: Set<String>): List<Delivery> {
        val connectedAccounts = mutex.withLock {
            userIds.filter { sessionsByPlayerId[it]?.let { session ->
                session.isConnected && session.resumeToken == null
            } == true }
        }
        return connectedAccounts.flatMap { loadNotifications(it) }
    }

    private suspend fun sendFriendRequest(playerId: String, playerCode: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        if (playerCode.isBlank() || playerCode.length > 12) {
            return listOf(error(playerId, "INVALID_PLAYER_CODE", "Mã người chơi không hợp lệ."))
        }
        return when (val result = friendRepository.sendRequest(playerId, playerCode, nowMillis())) {
            is FriendRequestResult.Success -> {
                val request = friendRepository.load(result.recipientId).incomingRequests
                    .firstOrNull { it.userId == playerId }
                if (request != null) {
                    notificationRepository.createNotifications(
                        result.recipientId,
                        listOf(NotificationSnapshot(
                            id = "friend:${request.requestId}",
                            kind = NotificationKind.FRIEND_REQUEST,
                            title = "Lời mời kết bạn",
                            message = "${request.displayName} muốn kết bạn với bạn.",
                            createdAtEpochMillis = nowMillis(),
                            destination = NotificationDestination.FRIENDS
                        ))
                    )
                }
                listOf(Delivery(ServerMessage.SocialNotice("Đã gửi lời mời kết bạn."), setOf(playerId))) +
                    refreshSocialFor(setOf(playerId, result.recipientId)) +
                    refreshNotificationsFor(setOf(result.recipientId))
            }
            FriendRequestResult.PlayerNotFound -> listOf(error(playerId, "PLAYER_NOT_FOUND", "Không tìm thấy mã người chơi."))
            FriendRequestResult.SelfRequest -> listOf(error(playerId, "SELF_FRIEND_REQUEST", "Bạn không thể tự kết bạn với mình."))
            FriendRequestResult.AlreadyExists -> listOf(error(playerId, "FRIENDSHIP_EXISTS", "Hai người đã là bạn hoặc đang có lời mời."))
            FriendRequestResult.Blocked -> listOf(error(
                playerId,
                "INTERACTION_BLOCKED",
                "Không thể gửi lời mời kết bạn cho người chơi này."
            ))
        }
    }

    private suspend fun cancelFriendRequest(playerId: String, requestId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        return when (val result = friendRepository.cancelRequest(playerId, requestId)) {
            is FriendCancellationResult.Success -> {
                notificationRepository.dismissNotifications(result.recipientId, "friend:$requestId", nowMillis())
                listOf(Delivery(ServerMessage.SocialNotice("Đã hủy lời mời kết bạn."), setOf(playerId))) +
                    refreshSocialFor(setOf(playerId, result.recipientId)) +
                    refreshNotificationsFor(setOf(result.recipientId))
            }
            FriendCancellationResult.NotFound -> listOf(error(
                playerId,
                "FRIEND_REQUEST_NOT_FOUND",
                "Lời mời kết bạn không còn tồn tại hoặc không thuộc về bạn."
            ))
        }
    }

    private suspend fun respondFriendRequest(
        playerId: String,
        command: ClientMessage.RespondFriendRequest
    ): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        return when (val result = friendRepository.respond(playerId, command.requestId, command.accept, nowMillis())) {
            is FriendResponseResult.Success -> {
                notificationRepository.dismissNotifications(playerId, "friend:${command.requestId}", nowMillis())
                val notice = if (command.accept) "Đã chấp nhận lời mời kết bạn." else "Đã từ chối lời mời kết bạn."
                listOf(Delivery(ServerMessage.SocialNotice(notice), setOf(playerId))) +
                    refreshSocialFor(setOf(playerId, result.requesterId)) +
                    refreshNotificationsFor(setOf(playerId))
            }
            FriendResponseResult.NotFound -> listOf(error(playerId, "FRIEND_REQUEST_NOT_FOUND", "Lời mời không còn tồn tại."))
        }
    }

    private suspend fun removeFriend(playerId: String, friendUserId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        return when (val result = friendRepository.removeFriend(playerId, friendUserId)) {
            is SocialMutationResult.Success -> {
                clearRoomInvitationsBetween(playerId, result.otherUserId)
                listOf(Delivery(ServerMessage.SocialNotice("Đã hủy kết bạn."), setOf(playerId))) +
                    refreshSocialFor(setOf(playerId, result.otherUserId)) +
                    refreshRoomInvitationsFor(setOf(playerId, result.otherUserId))
            }
            SocialMutationResult.NotFound -> listOf(error(
                playerId,
                "FRIEND_NOT_FOUND",
                "Quan hệ bạn bè không còn tồn tại."
            ))
            SocialMutationResult.SelfAction -> listOf(error(
                playerId,
                "INVALID_SOCIAL_ACTION",
                "Không thể thực hiện thao tác này với chính bạn."
            ))
        }
    }

    private suspend fun blockPlayer(playerId: String, playerUserId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        return when (val result = friendRepository.blockPlayer(playerId, playerUserId, nowMillis())) {
            is SocialMutationResult.Success -> {
                clearRoomInvitationsBetween(playerId, result.otherUserId)
                listOf(Delivery(ServerMessage.SocialNotice("Đã chặn người chơi."), setOf(playerId))) +
                    refreshSocialFor(setOf(playerId, result.otherUserId)) +
                    refreshRoomInvitationsFor(setOf(playerId, result.otherUserId))
            }
            SocialMutationResult.NotFound -> listOf(error(
                playerId,
                "PLAYER_NOT_FOUND",
                "Không tìm thấy người chơi để chặn."
            ))
            SocialMutationResult.SelfAction -> listOf(error(
                playerId,
                "INVALID_SOCIAL_ACTION",
                "Bạn không thể tự chặn chính mình."
            ))
        }
    }

    private suspend fun unblockPlayer(playerId: String, playerUserId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        return when (val result = friendRepository.unblockPlayer(playerId, playerUserId)) {
            is SocialMutationResult.Success ->
                listOf(Delivery(ServerMessage.SocialNotice("Đã bỏ chặn người chơi."), setOf(playerId))) +
                    refreshSocialFor(setOf(playerId, result.otherUserId))
            SocialMutationResult.NotFound -> listOf(error(
                playerId,
                "BLOCK_NOT_FOUND",
                "Người chơi này không còn trong danh sách chặn."
            ))
            SocialMutationResult.SelfAction -> listOf(error(
                playerId,
                "INVALID_SOCIAL_ACTION",
                "Không thể thực hiện thao tác này với chính bạn."
            ))
        }
    }

    private suspend fun inviteFriend(playerId: String, command: ClientMessage.InviteFriend): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        if (friendRepository.isBlockedEitherWay(playerId, command.friendUserId)) {
            return listOf(error(
                playerId,
                "INTERACTION_BLOCKED",
                "Không thể mời người chơi này vào phòng."
            ))
        }
        if (!friendRepository.areFriends(playerId, command.friendUserId)) {
            return listOf(error(playerId, "NOT_FRIENDS", "Người chơi này chưa phải bạn bè."))
        }
        var createdInvitation: RoomInvitationRecord? = null
        val replacedInvitationIds = mutableListOf<String>()
        val deliveries = mutex.withLock {
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
                inviterDisplayName = inviter.displayName,
                roomName = room.name,
                expiresAtMillis = nowMillis() + ROOM_INVITATION_TTL_MILLIS
            )
            roomInvitations.entries.removeAll { entry ->
                (entry.value.inviterId == playerId && entry.value.inviteeId == friend.playerId).also { removed ->
                    if (removed) replacedInvitationIds += entry.key
                }
            }
            roomInvitations[invitation.id] = invitation
            createdInvitation = invitation
            listOf(
                Delivery(invitation.toMessage(), setOf(friend.playerId)),
                Delivery(ServerMessage.SocialNotice("Đã gửi lời mời vào phòng."), setOf(playerId))
            )
        }
        createdInvitation?.let { invitation ->
            replacedInvitationIds.forEach { replacedId ->
                notificationRepository.deleteRoomInvitation(replacedId)
                notificationRepository.dismissNotifications(
                    invitation.inviteeId,
                    "room:$replacedId",
                    nowMillis()
                )
            }
            notificationRepository.saveRoomInvitation(invitation.toStored())
            notificationRepository.createNotifications(
                invitation.inviteeId,
                listOf(NotificationSnapshot(
                    id = "room:${invitation.id}",
                    kind = NotificationKind.ROOM_INVITATION,
                    title = "Lời mời vào phòng",
                    message = "${invitation.inviterDisplayName} mời bạn vào phòng ${invitation.roomName}.",
                    createdAtEpochMillis = nowMillis(),
                    destination = NotificationDestination.FRIENDS
                ))
            )
            return deliveries + refreshNotificationsFor(setOf(invitation.inviteeId))
        }
        return deliveries
    }

    private suspend fun loadRoomInvitations(playerId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val removedIds = mutableListOf<String>()
        val invitations = mutex.withLock {
            val now = nowMillis()
            roomInvitations.entries.removeAll { (id, invitation) ->
                val room = rooms[invitation.roomId]
                (invitation.expiresAtMillis <= now || room == null ||
                    room.hostId != invitation.inviterId || room.phase != RoomPhase.WAITING || room.guestId != null
                ).also { removed -> if (removed) removedIds += id }
            }
            roomInvitationMessagesFor(playerId)
        }
        removedIds.forEach { id ->
            notificationRepository.deleteRoomInvitation(id)
            notificationRepository.dismissNotifications(playerId, "room:$id", nowMillis())
        }
        return listOf(Delivery(ServerMessage.RoomInvitationsData(invitations), setOf(playerId)))
    }

    private suspend fun clearRoomInvitationsBetween(firstUserId: String, secondUserId: String) {
        val removedInvitations = mutableListOf<RoomInvitationRecord>()
        mutex.withLock {
            roomInvitations.entries.removeAll { (_, invitation) ->
                ((invitation.inviterId == firstUserId && invitation.inviteeId == secondUserId) ||
                    (invitation.inviterId == secondUserId && invitation.inviteeId == firstUserId)
                ).also { removed -> if (removed) removedInvitations += invitation }
            }
        }
        notificationRepository.deleteRoomInvitationsBetween(firstUserId, secondUserId)
        removedInvitations.forEach { invitation ->
            notificationRepository.dismissNotifications(
                invitation.inviteeId,
                "room:${invitation.id}",
                nowMillis()
            )
        }
    }

    private suspend fun respondRoomInvitation(
        playerId: String,
        command: ClientMessage.RespondRoomInvitation
    ): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        var invitationConsumed = false
        val otherRemovedInvitations = mutableListOf<RoomInvitationRecord>()
        val result = mutex.withLock {
            val invitation = roomInvitations.remove(command.invitationId)
                ?.takeIf { it.inviteeId == playerId && it.expiresAtMillis > nowMillis() }
                ?: return@withLock listOf(error(playerId, "INVITATION_EXPIRED", "Lời mời vào phòng đã hết hạn."))
            invitationConsumed = true
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
            room.sequence++
            room.scores[player.playerId] = 0
            room.readyPlayerIds.clear()
            roomInvitations.entries.removeAll { entry ->
                (entry.value.inviteeId == playerId).also { removed ->
                    if (removed) otherRemovedInvitations += entry.value
                }
            }
            listOf(
                Delivery(ServerMessage.RoomUpdated(room.snapshot()), room.playerIds()),
                Delivery(ServerMessage.RoomList(publicRooms()))
            )
        }
        if (invitationConsumed) {
            notificationRepository.deleteRoomInvitation(command.invitationId)
            notificationRepository.dismissNotifications(playerId, "room:${command.invitationId}", nowMillis())
        }
        if (result.any { it.message is ServerMessage.RoomUpdated }) {
            notificationRepository.deleteRoomInvitationsForInvitee(playerId)
            otherRemovedInvitations.forEach { invitation ->
                notificationRepository.dismissNotifications(
                    playerId,
                    "room:${invitation.id}",
                    nowMillis()
                )
            }
        }
        result.asSequence()
            .map(Delivery::message)
            .filterIsInstance<ServerMessage.RoomUpdated>()
            .firstOrNull()
            ?.game
            ?.roomId
            ?.let { persistRoom(it) }
        val affectedPlayers = (result.flatMap { it.recipients.orEmpty() } + playerId).toSet()
        return result + affectedPlayers.flatMap { presenceUpdates(it) } + loadRoomInvitations(playerId) +
            refreshNotificationsFor(setOf(playerId))
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

    private suspend fun refreshRoomInvitationsFor(userIds: Set<String>): List<Delivery> {
        val connectedAccounts = mutex.withLock {
            userIds.filter { sessionsByPlayerId[it]?.let { session ->
                session.isConnected && session.resumeToken == null
            } == true }
        }
        return connectedAccounts.flatMap { loadRoomInvitations(it) }
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

    private suspend fun joinMatchmaking(
        playerId: String,
        command: ClientMessage.JoinMatchmaking
    ): List<Delivery> {
        if (!isAccountSession(playerId)) {
            return listOf(error(playerId, "ACCOUNT_REQUIRED", "Hãy đăng nhập để chơi nhanh xếp hạng."))
        }
        val rating = playerProfileRepository.findByPlayerId(playerId)?.statistics?.eloRating ?: DEFAULT_ELO_RATING
        val joinedAt = nowMillis()
        val candidates = mutex.withLock {
            if (roomFor(playerId) != null) {
                return@withLock emptyList<MatchmakingEntry>()
            }
            matchmakingEntries.values
                .filter { it.playerId != playerId && it.gameMode == command.gameMode }
                .filter { candidate ->
                    sessionsByPlayerId[candidate.playerId]?.isConnected == true && roomFor(candidate.playerId) == null
                }
                .sortedWith(compareBy<MatchmakingEntry> { kotlin.math.abs(it.eloRating - rating) }.thenBy { it.joinedAtMillis })
        }
        val candidate = candidates.firstOrNull { queued ->
            !runCatching { friendRepository.isBlockedEitherWay(playerId, queued.playerId) }
                .onFailure { System.err.println("Could not check blocked players for matchmaking: ${it.message}") }
                .getOrDefault(true)
        }

        var matchedRoomId: String? = null
        val deliveries = mutex.withLock {
            val player = sessionsByPlayerId[playerId]
                ?.takeIf { it.isConnected && it.resumeToken == null }
                ?: return@withLock listOf(error(playerId, "SESSION_NOT_FOUND", "Phiên chơi không còn hợp lệ."))
            if (roomFor(playerId) != null) {
                matchmakingEntries.remove(playerId)
                return@withLock listOf(error(playerId, "ALREADY_IN_ROOM", "Bạn đang ở trong một phòng khác."))
            }

            val queuedCandidate = candidate?.let { matchmakingEntries[it.playerId] }?.takeIf { queued ->
                queued.gameMode == command.gameMode &&
                    sessionsByPlayerId[queued.playerId]?.isConnected == true &&
                    roomFor(queued.playerId) == null &&
                    kotlin.math.abs(queued.eloRating - rating) <= maxOf(
                        matchmakingRange(joinedAt - queued.joinedAtMillis),
                        matchmakingRange(0L)
                    )
            }
            if (queuedCandidate == null) {
                val existing = matchmakingEntries[playerId]
                matchmakingEntries[playerId] = MatchmakingEntry(
                    playerId = player.playerId,
                    gameMode = command.gameMode,
                    eloRating = rating,
                    joinedAtMillis = existing?.joinedAtMillis ?: joinedAt
                )
                return@withLock listOf(Delivery(
                    ServerMessage.MatchmakingStatus(
                        isSearching = true,
                        gameMode = command.gameMode,
                        ratingRange = matchmakingRange(joinedAt - (existing?.joinedAtMillis ?: joinedAt))
                    ),
                    setOf(playerId)
                ))
            }

            val hostId = queuedCandidate.playerId
            matchmakingEntries.remove(hostId)
            matchmakingEntries.remove(playerId)
            val room = Room(
                id = UUID.randomUUID().toString(),
                name = "Đấu nhanh",
                hostId = hostId,
                guestId = playerId,
                password = null,
                gameMode = command.gameMode,
                phase = RoomPhase.PLAYING,
                numbers = (1..GAME_NUMBER_COUNT).shuffled(),
                scores = mutableMapOf(hostId to 0, playerId to 0),
                startedAtEpochMillis = joinedAt
            )
            rooms[room.id] = room
            matchedRoomId = room.id
            listOf(
                Delivery(ServerMessage.GameStarted(room.snapshot()), room.playerIds()),
                Delivery(ServerMessage.RoomList(publicRooms()))
            )
        }
        matchedRoomId?.let { persistRoom(it) }
        val affectedPlayers = deliveries.flatMap { it.recipients.orEmpty() }.toSet()
        return deliveries + affectedPlayers.flatMap { presenceUpdates(it) }
    }

    private suspend fun cancelMatchmaking(playerId: String): List<Delivery> {
        mutex.withLock { matchmakingEntries.remove(playerId) }
        return listOf(Delivery(ServerMessage.MatchmakingStatus(isSearching = false), setOf(playerId))) +
            presenceUpdates(playerId)
    }

    private fun matchmakingRange(waitingMillis: Long): Int {
        val expansions = (waitingMillis.coerceAtLeast(0L) / MATCHMAKING_EXPAND_INTERVAL_MILLIS).toInt()
        return (MATCHMAKING_INITIAL_RANGE + expansions * MATCHMAKING_EXPAND_STEP)
            .coerceAtMost(MATCHMAKING_MAX_RANGE)
    }

    private fun accountRequired(playerId: String) = error(
        playerId,
        "ACCOUNT_REQUIRED",
        "Hãy đăng nhập tài khoản để sử dụng tính năng này."
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

    private suspend fun loadFriendProfile(playerId: String, friendUserId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        if (friendUserId.isBlank() || friendUserId.length > 64 || friendUserId == playerId) {
            return listOf(error(playerId, "INVALID_FRIEND", "Người chơi không hợp lệ."))
        }
        val areFriends = runCatching { friendRepository.areFriends(playerId, friendUserId) }
            .getOrElse {
                return listOf(error(playerId, "FRIEND_PROFILE_UNAVAILABLE", "Chưa tải được hồ sơ bạn bè."))
            }
        if (!areFriends) {
            return listOf(error(playerId, "FRIEND_PROFILE_FORBIDDEN", "Bạn chỉ có thể xem hồ sơ của bạn bè."))
        }
        val profile = runCatching { playerProfileRepository.findByPlayerId(friendUserId) }
            .getOrNull()
            ?: return listOf(error(playerId, "FRIEND_PROFILE_NOT_FOUND", "Không tìm thấy hồ sơ người chơi."))
        return listOf(Delivery(ServerMessage.FriendProfileData(friendUserId, profile), setOf(playerId)))
    }

    private suspend fun loadMatchDetail(playerId: String, matchId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val detail = runCatching { playerProfileRepository.findMatchDetail(playerId, matchId) }.getOrNull()
            ?: return listOf(error(playerId, "MATCH_NOT_FOUND", "Không tìm thấy chi tiết trận đấu."))
        return listOf(Delivery(ServerMessage.MatchDetailData(detail), setOf(playerId)))
    }

    private suspend fun equipCosmetics(
        playerId: String,
        command: ClientMessage.EquipCosmetics
    ): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val updated = runCatching {
            playerProfileRepository.equipCosmetics(playerId, command.frameId, command.titleId)
        }.getOrDefault(false)
        if (!updated) {
            return listOf(error(playerId, "COSMETIC_LOCKED", "Vật phẩm chưa được mở khóa hoặc không hợp lệ."))
        }
        return loadProfile(playerId)
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
        val activeRoomId = mutex.withLock {
            sessionsByPlayerId[playerId]?.displayName = safeName
            roomFor(playerId)?.id
        }
        activeRoomId?.let { persistRoom(it) }
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

    suspend fun roomList(): ServerMessage.RoomList {
        restoreActiveRooms()
        return mutex.withLock { ServerMessage.RoomList(publicRooms()) }
    }

    suspend fun advanceTimedGames(): List<Delivery> {
        restoreActiveRooms()
        val advances = mutex.withLock {
            val now = nowMillis()
            rooms.values.mapNotNull { room ->
                if (
                    room.gameMode != com.hienthai.fastowin.protocol.ProtocolGameMode.TIME_ATTACK ||
                    room.phase != RoomPhase.PLAYING ||
                    now - (room.startedAtEpochMillis ?: return@mapNotNull null) < timeAttackMillis
                ) {
                    if (
                        room.phase == RoomPhase.FINISHED &&
                        room.rematchRequestedPlayerIds.isNotEmpty() &&
                        now >= (room.rematchExpiresAtEpochMillis ?: Long.MAX_VALUE)
                    ) {
                        room.rematchRequestedPlayerIds.clear()
                        room.rematchExpiresAtEpochMillis = null
                        room.sequence++
                        TimedAdvance(
                            delivery = Delivery(
                                ServerMessage.RematchStatus(room.snapshot(), RematchEvent.EXPIRED),
                                room.playerIds()
                            ),
                            completedMatch = null
                        )
                    } else {
                        null
                    }
                } else {
                    room.phase = RoomPhase.FINISHED
                    room.finishedAtEpochMillis = now
                    room.sequence++
                    TimedAdvance(
                        delivery = Delivery(ServerMessage.GameFinished(room.snapshot()), room.playerIds()),
                        completedMatch = room.takeCompletedMatch()
                    )
                }
            }
        }
        advances.mapNotNull(TimedAdvance::completedMatch).forEach { completed ->
            runCatching { matchResultRepository.save(completed) }
                .onFailure { System.err.println("Could not persist timed match ${completed.matchId}: ${it.message}") }
        }
        advances.forEach { advance ->
            when (val message = advance.delivery.message) {
                is ServerMessage.GameFinished -> message.game.roomId
                is ServerMessage.RematchStatus -> message.game.roomId
                else -> null
            }?.let { persistRoom(it) }
        }
        return advances.map(TimedAdvance::delivery)
    }

    suspend fun markDisconnected(playerId: String): List<Delivery> {
        restoreActiveRooms()
        val disconnectedAt = nowMillis()
        val deliveries = mutex.withLock {
            val session = sessionsByPlayerId[playerId] ?: return@withLock emptyList()
            session.isConnected = false
            session.disconnectedAtMillis = disconnectedAt
            matchmakingEntries.remove(playerId)
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

    suspend fun cleanupExpiredSessions(): List<Delivery> {
        restoreActiveRooms()
        val removedInvitations = mutableListOf<RoomInvitationRecord>()
        val cleanup = mutex.withLock {
            val now = nowMillis()
            val affectedInvitationRecipients = roomInvitations.values
                .filter { it.expiresAtMillis <= now }
                .onEach(removedInvitations::add)
                .mapTo(mutableSetOf()) { it.inviteeId }
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
            if (removedRoomIds.isNotEmpty()) {
                roomInvitations.values
                    .filter { it.roomId in removedRoomIds }
                    .onEach(removedInvitations::add)
                    .mapTo(affectedInvitationRecipients) { it.inviteeId }
                roomInvitations.entries.removeAll { it.value.roomId in removedRoomIds }
            }
            affectedInvitationRecipients.forEach { inviteeId ->
                val session = sessionsByPlayerId[inviteeId]
                if (session?.isConnected == true && session.resumeToken == null) {
                    deliveries += Delivery(
                        ServerMessage.RoomInvitationsData(roomInvitationMessagesFor(inviteeId)),
                        setOf(inviteeId)
                    )
                }
            }
            CleanupResult(deliveries, removedRoomIds)
        }
        notificationRepository.deleteExpiredRoomInvitations(nowMillis())
        removedInvitations.forEach { invitation ->
            notificationRepository.dismissNotifications(
                invitation.inviteeId,
                "room:${invitation.id}",
                nowMillis()
            )
        }
        cleanup.removedRoomIds.forEach { persistRoom(it) }
        return cleanup.deliveries + refreshNotificationsFor(removedInvitations.mapTo(mutableSetOf()) { it.inviteeId })
    }

    private fun roomInvitationMessagesFor(playerId: String): List<ServerMessage.RoomInvitation> =
        roomInvitations.values
            .filter { it.inviteeId == playerId }
            .sortedByDescending { it.expiresAtMillis }
            .map(RoomInvitationRecord::toMessage)

    private fun createRoom(player: GuestSession, command: ClientMessage.CreateRoom): List<Delivery> {
        if (roomFor(player.playerId) != null) {
            return listOf(error(player.playerId, "ALREADY_IN_ROOM", "Bạn đang ở trong một phòng khác."))
        }
        val name = command.roomName.trim().take(MAX_ROOM_NAME_LENGTH)
        if (name.isEmpty()) {
            return listOf(error(player.playerId, "INVALID_ROOM_NAME", "Tên phòng không được để trống."))
        }
        matchmakingEntries.remove(player.playerId)
        val room = Room(
            id = UUID.randomUUID().toString(),
            name = name,
            hostId = player.playerId,
            password = command.password.takeIf(String::isNotBlank)?.let(PasswordHash::create),
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
        if (room.password != null && !room.password.matches(command.password)) {
            return listOf(error(player.playerId, "WRONG_PASSWORD", "Mật khẩu phòng không đúng."))
        }

        matchmakingEntries.remove(player.playerId)
        room.guestId = player.playerId
        room.sequence++
        room.scores[player.playerId] = 0
        room.readyPlayerIds.clear()
        val participants = room.playerIds()
        return listOf(
            Delivery(ServerMessage.RoomUpdated(room.snapshot()), participants),
            Delivery(ServerMessage.RoomList(publicRooms()))
        )
    }

    private fun setReady(player: GuestSession, command: ClientMessage.SetReady): HandleResult {
        val room = rooms[command.roomId]
            ?: return HandleResult(listOf(error(player.playerId, "ROOM_NOT_FOUND", "Phòng không còn tồn tại.")))
        if (player.playerId !in room.playerIds()) {
            return HandleResult(listOf(error(player.playerId, "NOT_IN_ROOM", "Bạn không ở trong phòng này.")))
        }
        if (room.phase != RoomPhase.WAITING) {
            return HandleResult(listOf(error(player.playerId, "ROOM_NOT_WAITING", "Phòng không còn ở trạng thái chờ.")))
        }
        if (command.ready) room.readyPlayerIds += player.playerId else room.readyPlayerIds -= player.playerId
        room.sequence++
        val participants = room.playerIds()
        if (participants.size == 2 && room.readyPlayerIds.containsAll(participants)) {
            room.phase = RoomPhase.PLAYING
            room.numbers = (1..GAME_NUMBER_COUNT).shuffled()
            room.startedAtEpochMillis = nowMillis()
            room.readyPlayerIds.clear()
            return HandleResult(
                deliveries = listOf(Delivery(ServerMessage.GameStarted(room.snapshot()), participants)),
                changedRoomId = room.id
            )
        }
        return HandleResult(
            deliveries = listOf(Delivery(ServerMessage.RoomUpdated(room.snapshot()), participants)),
            changedRoomId = room.id
        )
    }

    private fun kickPlayer(player: GuestSession, command: ClientMessage.KickPlayer): HandleResult {
        val room = rooms[command.roomId]
            ?: return HandleResult(listOf(error(player.playerId, "ROOM_NOT_FOUND", "Phòng không còn tồn tại.")))
        if (room.hostId != player.playerId) {
            return HandleResult(listOf(error(player.playerId, "HOST_REQUIRED", "Chỉ chủ phòng mới có thể mời người chơi ra ngoài.")))
        }
        if (room.phase != RoomPhase.WAITING || room.guestId != command.playerId) {
            return HandleResult(listOf(error(player.playerId, "PLAYER_NOT_IN_ROOM", "Người chơi không còn trong phòng.")))
        }
        val kickedPlayerId = command.playerId
        room.guestId = null
        room.readyPlayerIds.clear()
        room.scores.remove(kickedPlayerId)
        room.sequence++
        return HandleResult(
            deliveries = listOf(
                Delivery(ServerMessage.RoomClosed(room.id, "Chủ phòng đã mời bạn ra khỏi phòng."), setOf(kickedPlayerId)),
                Delivery(ServerMessage.RoomUpdated(room.snapshot()), setOf(room.hostId)),
                Delivery(ServerMessage.RoomList(publicRooms()))
            ),
            changedRoomId = room.id
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

    private fun respondRematch(
        player: GuestSession,
        roomId: String,
        accept: Boolean
    ): HandleResult {
        val room = rooms[roomId]
            ?: return HandleResult(listOf(error(player.playerId, "ROOM_NOT_FOUND", "Phòng không còn tồn tại.")))
        if (player.playerId !in room.playerIds()) {
            return HandleResult(listOf(error(player.playerId, "NOT_IN_ROOM", "Bạn không ở trong phòng này.")))
        }
        if (room.phase != RoomPhase.FINISHED) {
            return HandleResult(listOf(error(player.playerId, "REMATCH_NOT_AVAILABLE", "Chỉ có thể đấu lại sau khi trận kết thúc.")))
        }
        if (room.playerIds().size != 2) {
            return HandleResult(listOf(error(player.playerId, "OPPONENT_LEFT", "Đối thủ đã rời phòng.")))
        }

        if (
            room.rematchRequestedPlayerIds.isNotEmpty() &&
            nowMillis() >= (room.rematchExpiresAtEpochMillis ?: Long.MAX_VALUE)
        ) {
            room.rematchRequestedPlayerIds.clear()
            room.rematchExpiresAtEpochMillis = null
            room.sequence++
            return HandleResult(
                deliveries = listOf(
                    Delivery(ServerMessage.RematchStatus(room.snapshot(), RematchEvent.EXPIRED), room.playerIds())
                ),
                changedRoomId = room.id
            )
        }

        if (!accept) {
            if (room.rematchRequestedPlayerIds.isEmpty()) {
                return HandleResult(listOf(error(player.playerId, "REMATCH_NOT_PENDING", "Không có yêu cầu đấu lại nào đang chờ.")))
            }
            val event = if (player.playerId in room.rematchRequestedPlayerIds) {
                RematchEvent.CANCELLED
            } else {
                RematchEvent.DECLINED
            }
            room.rematchRequestedPlayerIds.clear()
            room.rematchExpiresAtEpochMillis = null
            room.sequence++
            return HandleResult(
                deliveries = listOf(
                    Delivery(
                        ServerMessage.RematchStatus(room.snapshot(), event, player.playerId),
                        room.playerIds()
                    )
                ),
                changedRoomId = room.id
            )
        }

        if (room.rematchRequestedPlayerIds.isEmpty()) {
            room.rematchExpiresAtEpochMillis = nowMillis() + rematchTimeoutMillis
        }
        room.rematchRequestedPlayerIds += player.playerId
        if (!room.rematchRequestedPlayerIds.containsAll(room.playerIds())) {
            room.sequence++
            return HandleResult(
                deliveries = listOf(
                    Delivery(
                        ServerMessage.RematchStatus(room.snapshot(), RematchEvent.REQUESTED, player.playerId),
                        room.playerIds()
                    )
                ),
                changedRoomId = room.id
            )
        }

        room.matchId = UUID.randomUUID().toString()
        room.phase = RoomPhase.PLAYING
        room.numbers = (1..GAME_NUMBER_COUNT).shuffled()
        room.selectedNumbers.clear()
        room.currentTarget = 1
        room.playerIds().forEach { room.scores[it] = 0 }
        room.sequence++
        room.startedAtEpochMillis = nowMillis()
        room.finishedAtEpochMillis = null
        room.resultQueued = false
        room.rematchRequestedPlayerIds.clear()
        room.rematchExpiresAtEpochMillis = null
        room.processedRequests.clear()
        room.selectionEvents.clear()
        return HandleResult(
            deliveries = listOf(Delivery(ServerMessage.GameStarted(room.snapshot()), room.playerIds())),
            changedRoomId = room.id
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
        val timedOut = room.finishIfTimedOut()
        if (room.phase != RoomPhase.PLAYING) {
            if (timedOut) {
                return HandleResult(
                    deliveries = listOf(Delivery(ServerMessage.GameFinished(room.snapshot()), room.playerIds())),
                    completedMatch = room.takeCompletedMatch(),
                    changedRoomId = room.id
                )
            }
            val completedMatch = room.takeCompletedMatch()
            return HandleResult(
                deliveries = listOf(error(player.playerId, "GAME_NOT_PLAYING", "Trận đấu chưa bắt đầu hoặc đã kết thúc.", command.requestId)),
                completedMatch = completedMatch,
                changedRoomId = room.id.takeIf { completedMatch != null }
            )
        }
        if (command.number != room.currentTarget) {
            val rejected = error(player.playerId, "WRONG_NUMBER", "Chưa đúng số, thử lại nhé!", command.requestId)
            room.processedRequests[requestKey] = rejected.message
            room.recordSelection(player.playerId, command, SelectionResult.REJECTED)
            return HandleResult(listOf(rejected), changedRoomId = room.id)
        }

        room.recordSelection(player.playerId, command, SelectionResult.ACCEPTED)
        room.selectedNumbers += command.number
        room.scores[player.playerId] = room.scores.getValue(player.playerId) + SCORE_PER_NUMBER
        room.currentTarget++
        room.sequence++
        if (room.currentTarget > GAME_NUMBER_COUNT) {
            room.phase = RoomPhase.FINISHED
            room.finishedAtEpochMillis = nowMillis()
        }

        val event = ServerMessage.GameStateUpdated(
            game = room.snapshot(),
            acceptedNumber = command.number,
            selectedByPlayerId = player.playerId
        )
        room.processedRequests[requestKey] = event
        return HandleResult(
            deliveries = listOf(Delivery(event, room.playerIds())),
            completedMatch = room.takeCompletedMatch(),
            changedRoomId = room.id
        )
    }

    private fun roomFor(playerId: String): Room? = rooms.values.firstOrNull { playerId in it.playerIds() }

    private inline fun <reified T : ServerMessage> List<Delivery>.roomIdFrom(): String? {
        return when (val message = asSequence().map(Delivery::message).filterIsInstance<T>().firstOrNull()) {
            is ServerMessage.RoomCreated -> message.game.roomId
            is ServerMessage.GameStarted -> message.game.roomId
            is ServerMessage.GameFinished -> message.game.roomId
            is ServerMessage.RoomClosed -> message.roomId
            else -> null
        }
    }

    private fun Room.toStoredRoom(): StoredActiveRoom {
        fun storedPlayer(playerId: String): StoredActivePlayer {
            val session = checkNotNull(sessionsByPlayerId[playerId]) {
                "Room $id references missing player session $playerId."
            }
            return StoredActivePlayer(
                playerId = playerId,
                displayName = session.displayName,
                isAccount = session.resumeToken == null
            )
        }

        return StoredActiveRoom(
            roomId = id,
            matchId = matchId,
            roomName = name,
            host = storedPlayer(hostId),
            guest = guestId?.let(::storedPlayer),
            passwordSalt = password?.salt?.copyOf(),
            passwordHash = password?.value?.copyOf(),
            gameMode = gameMode,
            phase = phase,
            numbers = numbers.toList(),
            selectedNumbers = selectedNumbers.toList(),
            currentTarget = currentTarget,
            scores = scores.toMap(),
            sequence = sequence,
            startedAtEpochMillis = startedAtEpochMillis,
            finishedAtEpochMillis = finishedAtEpochMillis,
            resultQueued = resultQueued,
            readyPlayerIds = readyPlayerIds.toSet(),
            rematchRequestedPlayerIds = rematchRequestedPlayerIds.toSet(),
            rematchExpiresAtEpochMillis = rematchExpiresAtEpochMillis,
            processedRequests = processedRequests.toMap(),
            selectionEvents = selectionEvents.toList()
        )
    }

    private fun StoredActiveRoom.toRoom(): Room = Room(
        id = roomId,
        matchId = matchId,
        name = roomName,
        hostId = host.playerId,
        password = if (passwordSalt != null && passwordHash != null) {
            PasswordHash(passwordSalt.copyOf(), passwordHash.copyOf())
        } else {
            null
        },
        gameMode = gameMode,
        guestId = guest?.playerId,
        phase = phase,
        numbers = numbers.toList(),
        selectedNumbers = selectedNumbers.toMutableList(),
        currentTarget = currentTarget,
        scores = scores.toMutableMap().also { restoredScores ->
            restoredScores.putIfAbsent(host.playerId, 0)
            guest?.let { restoredScores.putIfAbsent(it.playerId, 0) }
        },
        sequence = sequence,
        startedAtEpochMillis = startedAtEpochMillis,
        finishedAtEpochMillis = finishedAtEpochMillis,
        resultQueued = resultQueued,
        readyPlayerIds = readyPlayerIds.toMutableSet(),
        rematchRequestedPlayerIds = rematchRequestedPlayerIds.toMutableSet(),
        rematchExpiresAtEpochMillis = rematchExpiresAtEpochMillis,
        processedRequests = processedRequests.toMutableMap(),
        selectionEvents = selectionEvents.toMutableList()
    )

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
                requiresPassword = room.password != null
            )
        }
        .sortedBy { it.name.lowercase() }
        .toList()

    private fun Room.snapshot(): GameSnapshot {
        val metricsByPlayer = selectionMetrics()
        return GameSnapshot(
            roomId = id,
            matchId = matchId,
            roomName = name,
            hostId = hostId,
            gameMode = gameMode,
            phase = phase,
            players = playerIds().mapNotNull { id ->
                sessionsByPlayerId[id]?.let { session ->
                    val metrics = metricsByPlayer[id] ?: SelectionMetrics()
                    PlayerSnapshot(
                        id = id,
                        name = session.displayName,
                        score = scores[id] ?: 0,
                        isReady = id in readyPlayerIds,
                        correctSelections = metrics.correct,
                        wrongSelections = metrics.wrong,
                        averageReactionMillis = metrics.averageReactionMillis
                    )
                }
            },
            numbers = numbers,
            selectedNumbers = selectedNumbers.toList(),
            currentTarget = currentTarget,
            sequence = sequence,
            startedAtEpochMillis = startedAtEpochMillis,
            finishedAtEpochMillis = finishedAtEpochMillis,
            rematchRequestedPlayerIds = rematchRequestedPlayerIds.toList(),
            rematchExpiresAtEpochMillis = rematchExpiresAtEpochMillis
        )
    }

    private fun Room.selectionMetrics(): Map<String, SelectionMetrics> {
        val metrics = playerIds().associateWith { SelectionMetrics() }.toMutableMap()
        var targetAvailableAtMillis = startedAtEpochMillis ?: return metrics
        selectionEvents.sortedBy(MatchSelectionEvent::sequence).forEach { event ->
            val current = metrics.getOrPut(event.playerId) { SelectionMetrics() }
            when (event.result) {
                SelectionResult.REJECTED -> current.wrong++
                SelectionResult.ACCEPTED -> {
                    current.correct++
                    current.reactionTimeTotalMillis +=
                        (event.occurredAtMillis - targetAvailableAtMillis).coerceAtLeast(0L)
                    current.reactionSamples++
                    targetAvailableAtMillis = event.occurredAtMillis
                }
            }
        }
        return metrics
    }

    private fun Room.finishIfTimedOut(): Boolean {
        val startedAt = startedAtEpochMillis ?: return false
        if (
            gameMode == com.hienthai.fastowin.protocol.ProtocolGameMode.TIME_ATTACK &&
            phase == RoomPhase.PLAYING &&
            nowMillis() - startedAt >= timeAttackMillis
        ) {
            phase = RoomPhase.FINISHED
            finishedAtEpochMillis = nowMillis()
            sequence++
            return true
        }
        return false
    }

    private fun Room.takeCompletedMatch(): CompletedMatch? {
        if (phase != RoomPhase.FINISHED || resultQueued) return null
        val startedAt = startedAtEpochMillis ?: return null
        val highestScore = scores.values.maxOrNull() ?: 0
        val leaders = scores.filterValues { it == highestScore }.keys
        val winnerId = leaders.singleOrNull()
        resultQueued = true
        return CompletedMatch(
            matchId = matchId,
            roomName = name,
            gameMode = gameMode,
            startedAtMillis = startedAt,
            endedAtMillis = finishedAtEpochMillis ?: nowMillis(),
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
        val completedMatch: CompletedMatch? = null,
        val changedRoomId: String? = null
    )

    private data class TimedAdvance(
        val delivery: Delivery,
        val completedMatch: CompletedMatch?
    )

    private data class CleanupResult(
        val deliveries: List<Delivery>,
        val removedRoomIds: Set<String>
    )

    private data class Room(
        val id: String,
        var matchId: String = id,
        val name: String,
        val hostId: String,
        val password: PasswordHash?,
        val gameMode: com.hienthai.fastowin.protocol.ProtocolGameMode,
        var guestId: String? = null,
        var phase: RoomPhase = RoomPhase.WAITING,
        var numbers: List<Int> = emptyList(),
        val selectedNumbers: MutableList<Int> = mutableListOf(),
        var currentTarget: Int = 1,
        val scores: MutableMap<String, Int> = mutableMapOf(hostId to 0),
        var sequence: Long = 0,
        var startedAtEpochMillis: Long? = null,
        var finishedAtEpochMillis: Long? = null,
        var resultQueued: Boolean = false,
        val readyPlayerIds: MutableSet<String> = mutableSetOf(),
        val rematchRequestedPlayerIds: MutableSet<String> = mutableSetOf(),
        var rematchExpiresAtEpochMillis: Long? = null,
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
        val inviterDisplayName: String,
        val roomName: String,
        val expiresAtMillis: Long
    ) {
        fun toMessage() = ServerMessage.RoomInvitation(
            invitationId = id,
            fromUserId = inviterId,
            fromDisplayName = inviterDisplayName,
            roomId = roomId,
            roomName = roomName,
            expiresAtEpochMillis = expiresAtMillis
        )

        fun toStored() = StoredRoomInvitation(
            id, inviterId, inviteeId, roomId, inviterDisplayName, roomName, expiresAtMillis
        )
    }

    private fun StoredRoomInvitation.toRecord() = RoomInvitationRecord(
        id, inviterId, inviteeId, roomId, inviterDisplayName, roomName, expiresAtMillis
    )

    private data class MatchmakingEntry(
        val playerId: String,
        val gameMode: com.hienthai.fastowin.protocol.ProtocolGameMode,
        val eloRating: Int,
        val joinedAtMillis: Long
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
        const val MAX_NOTIFICATION_SYNC_BATCH = 20
        const val LEADERBOARD_SIZE = 100
        const val DEFAULT_TIME_ATTACK_MILLIS = 60_000L
        const val DEFAULT_REMATCH_TIMEOUT_MILLIS = 30_000L
        const val ROOM_RECONNECT_GRACE_MILLIS = 30_000L
        const val IDLE_SESSION_TTL_MILLIS = 5 * 60_000L
        const val ROOM_INVITATION_TTL_MILLIS = 60_000L
        const val DEFAULT_ELO_RATING = 1_000
        const val MATCHMAKING_INITIAL_RANGE = 100
        const val MATCHMAKING_EXPAND_STEP = 50
        const val MATCHMAKING_MAX_RANGE = 600
        const val MATCHMAKING_EXPAND_INTERVAL_MILLIS = 10_000L
        const val RESTORED_GUEST_RESUME_TOKEN = "restored-guest-session"
        val secureRandom = SecureRandom()
    }

    private data class SelectionMetrics(
        var correct: Int = 0,
        var wrong: Int = 0,
        var reactionTimeTotalMillis: Long = 0,
        var reactionSamples: Int = 0
    ) {
        val averageReactionMillis: Long
            get() = if (reactionSamples == 0) 0L else reactionTimeTotalMillis / reactionSamples
    }
}
