package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_AVATAR_ID
import com.hienthai.fastowin.protocol.MAX_PROFILE_DISPLAY_NAME_LENGTH
import com.hienthai.fastowin.protocol.PROFILE_AVATAR_IDS
import com.hienthai.fastowin.protocol.GAME_NUMBER_COUNT
import com.hienthai.fastowin.protocol.GameSnapshot
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.FriendsSnapshot
import com.hienthai.fastowin.protocol.PlayerSnapshot
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.RematchEvent
import com.hienthai.fastowin.protocol.NotificationDestination
import com.hienthai.fastowin.protocol.NotificationKind
import com.hienthai.fastowin.protocol.NotificationSnapshot
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.protocol.RoomSummary
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.protocol.TournamentHubSnapshot
import com.hienthai.fastowin.protocol.TournamentInvitationSnapshot
import com.hienthai.fastowin.protocol.TournamentMatchPhase
import com.hienthai.fastowin.protocol.TournamentMatchSnapshot
import com.hienthai.fastowin.protocol.TournamentPhase
import com.hienthai.fastowin.protocol.TournamentPlayerSnapshot
import com.hienthai.fastowin.protocol.TournamentSnapshot
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
    private val tournamentRepository: TournamentRepository = InMemoryTournamentRepository(),
    private val clanRepository: ClanRepository = NoOpClanRepository,
    private val pushNotificationService: PushNotificationService = NoOpPushNotificationService,
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
    private val tournaments = mutableMapOf<String, Tournament>()
    private val tournamentInvitations = mutableMapOf<String, TournamentInvitationRecord>()
    private var activeRoomsRestored = false

    suspend fun connectGuest(displayName: String, resumeToken: String?): ConnectedPlayer {
        restoreActiveRooms()
        val safeName = displayName.trim().take(MAX_PLAYER_NAME_LENGTH)
        require(safeName.isNotEmpty()) { "TÃªn ngÆ°á»i chÆ¡i khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng." }
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
            val storedTournaments = tournamentRepository.loadActive()
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
                storedTournaments.forEach { snapshot ->
                    val tournament = snapshot.toTournament()
                    tournaments[tournament.id] = tournament
                    tournament.matches.forEach { tournamentMatch ->
                        tournamentMatch.roomId?.let { roomId ->
                            rooms[roomId]?.apply {
                                tournamentId = tournament.id
                                tournamentMatchId = tournamentMatch.id
                                tournamentRound = tournamentMatch.round
                            }
                        }
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
        if (message is ClientMessage.ClaimDailyCheckIn) return claimDailyCheckIn(playerId)
        if (message is ClientMessage.ClaimMissionReward) return claimMissionReward(playerId, message.missionCode)
        if (message is ClientMessage.GetFriendProfile) return loadFriendProfile(playerId, message.friendUserId)
        if (message is ClientMessage.GetMatchDetail) return loadMatchDetail(playerId, message.matchId)
        if (message is ClientMessage.UpdateProfile) return updateProfile(playerId, message)
        if (message is ClientMessage.EquipCosmetics) return equipCosmetics(playerId, message)
        if (message is ClientMessage.GetLeaderboard) return loadLeaderboard(playerId)
        if (message is ClientMessage.GetFriends) return loadFriends(playerId)
        if (message is ClientMessage.GetRoomInvitations) return loadRoomInvitations(playerId)
        if (message is ClientMessage.GetNotifications) return loadNotifications(playerId)
        if (message is ClientMessage.GetTournamentHub) return loadTournamentHub(playerId)
        if (message is ClientMessage.CreateTournament) return createTournament(playerId, message)
        if (message is ClientMessage.InviteTournamentPlayer) return inviteTournamentPlayer(playerId, message)
        if (message is ClientMessage.RespondTournamentInvitation) {
            return respondTournamentInvitation(playerId, message)
        }
        if (message is ClientMessage.StartTournament) return startTournament(playerId, message.tournamentId)
        if (message is ClientMessage.LeaveTournament) return leaveTournament(playerId, message.tournamentId)
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
        if (message is ClientMessage.CreateClan) return createClan(playerId, message.name, message.description)
        if (message is ClientMessage.JoinClan) return joinClan(playerId, message.clanId)
        if (message is ClientMessage.LeaveClan) return leaveClan(playerId)
        if (message is ClientMessage.GetClanInfo) return getClanInfo(playerId, message.clanId)
        if (message is ClientMessage.GetClanList) return getClanList(playerId, message.query)
        if (message is ClientMessage.InviteToClan) return inviteToClan(playerId, message.playerCode)
        if (message is ClientMessage.KickClanMember) return kickClanMember(playerId, message.clanId, message.memberId)
        if (message is ClientMessage.UpdateAvatar) return updateAvatar(playerId, message.base64Data)
        if (message is ClientMessage.UpdateClanLogo) return updateClanLogo(playerId, message.clanId, message.logoId)
        if (message is ClientMessage.JoinMatchmaking) return joinMatchmaking(playerId, message)
        if (message is ClientMessage.CancelMatchmaking) return cancelMatchmaking(playerId)
        if (message is ClientMessage.CreateRoom) {
            validateModeAccess(playerId, message.gameMode)?.let { return listOf(it) }
        }
        if (message is ClientMessage.JoinRoom) {
            val roomMode = mutex.withLock { rooms[message.roomId]?.gameMode }
            if (roomMode != null) validateModeAccess(playerId, roomMode)?.let { return listOf(it) }
        }
        val result = mutex.withLock {
            val player = sessionsByPlayerId[playerId]
                ?: return@withLock HandleResult(
                    listOf(error(playerId, "SESSION_NOT_FOUND", "PhiÃªn chÆ¡i khÃ´ng cÃ²n há»£p lá»‡."))
                )

            when (message) {
                is ClientMessage.ConnectGuest -> HandleResult(listOf(
                    error(playerId, "ALREADY_CONNECTED", "PhiÃªn WebSocket Ä‘Ã£ Ä‘Æ°á»£c xÃ¡c thá»±c.")
                ))
                is ClientMessage.ConnectAccount -> HandleResult(listOf(
                    error(playerId, "ALREADY_CONNECTED", "PhiÃªn WebSocket Ä‘Ã£ Ä‘Æ°á»£c xÃ¡c thá»±c.")
                ))

                ClientMessage.ListRooms -> HandleResult(
                    listOf(Delivery(ServerMessage.RoomList(publicRooms()), setOf(playerId)))
                )
                ClientMessage.GetProfile -> HandleResult(emptyList())
                ClientMessage.ClaimDailyCheckIn -> HandleResult(emptyList())
                is ClientMessage.ClaimMissionReward -> HandleResult(emptyList())
                is ClientMessage.GetFriendProfile -> HandleResult(emptyList())
                is ClientMessage.GetMatchDetail -> HandleResult(emptyList())
                is ClientMessage.UpdateProfile -> HandleResult(emptyList())
                is ClientMessage.EquipCosmetics -> HandleResult(emptyList())
                ClientMessage.GetLeaderboard -> HandleResult(emptyList())
                ClientMessage.GetFriends -> HandleResult(emptyList())
                ClientMessage.GetRoomInvitations -> HandleResult(emptyList())
                ClientMessage.GetNotifications -> HandleResult(emptyList())
                ClientMessage.GetTournamentHub -> HandleResult(emptyList())
                is ClientMessage.CreateTournament -> HandleResult(emptyList())
                is ClientMessage.InviteTournamentPlayer -> HandleResult(emptyList())
                is ClientMessage.RespondTournamentInvitation -> HandleResult(emptyList())
                is ClientMessage.StartTournament -> HandleResult(emptyList())
                is ClientMessage.LeaveTournament -> HandleResult(emptyList())
                is ClientMessage.SyncNotifications -> HandleResult(emptyList())
                is ClientMessage.MarkNotificationsRead -> HandleResult(emptyList())
                is ClientMessage.DismissNotifications -> HandleResult(emptyList())
                is ClientMessage.UpdateAvatar -> HandleResult(emptyList())
                is ClientMessage.UpdateClanLogo -> HandleResult(emptyList())
                is ClientMessage.MeasureLatency -> HandleResult(listOf(
                    Delivery(ServerMessage.LatencyPong(message.clientSentAtEpochMillis), setOf(playerId))
                ))
                is ClientMessage.UpdateLatency -> {
                    player.latencyMillis = message.latencyMillis
                    HandleResult(emptyList())
                }
                is ClientMessage.UpdateFcmToken -> {
                    playerProfileRepository.updateFcmToken(playerId, message.token)
                    HandleResult(emptyList())
                }
                is ClientMessage.JoinMatchmaking -> HandleResult(emptyList())
                ClientMessage.CancelMatchmaking -> HandleResult(emptyList())
                is ClientMessage.GetClanList -> HandleResult(emptyList())
                is ClientMessage.CreateClan -> HandleResult(emptyList())
                is ClientMessage.GetClanInfo -> HandleResult(emptyList())
                is ClientMessage.JoinClan -> HandleResult(emptyList())
                is ClientMessage.LeaveClan -> HandleResult(emptyList())
                is ClientMessage.InviteToClan -> HandleResult(emptyList())
                is ClientMessage.KickClanMember -> HandleResult(emptyList())
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
                is ClientMessage.SendEmoji -> sendEmoji(player, message)
            }
        }
        result.completedMatch?.let { completed ->
            runCatching { matchResultRepository.save(completed) }
                .onFailure { System.err.println("Could not persist match ${completed.matchId}: ${it.message}") }
        }
        result.changedRoomId?.let { persistRoom(it) }
        val tournamentDeliveries = result.completedMatch
            ?.let { advanceTournamentAfterMatch(it.matchId) }
            .orEmpty()
        val presenceChanged = message is ClientMessage.CreateRoom ||
            message is ClientMessage.JoinRoom || message is ClientMessage.LeaveRoom
        val affectedPlayers = if (presenceChanged) {
            (result.deliveries.flatMap { it.recipients.orEmpty() } + playerId).toSet()
        } else emptySet()
        return result.deliveries + tournamentDeliveries + affectedPlayers.flatMap { presenceUpdates(it) }
    }

    private suspend fun loadTournamentHub(playerId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val now = nowMillis()
        val hubState = mutex.withLock {
            tournamentInvitations.entries.removeAll { (_, invitation) ->
                invitation.expiresAtMillis <= now ||
                    tournaments[invitation.tournamentId]?.phase != TournamentPhase.LOBBY
            }
            val active = tournaments.values.firstOrNull { tournament ->
                playerId in tournament.playerIds() &&
                    tournament.phase in setOf(TournamentPhase.LOBBY, TournamentPhase.RUNNING)
            }?.snapshot()
            val invitations = tournamentInvitations.values
                .filter { it.inviteeId == playerId && it.expiresAtMillis > now }
                .map(TournamentInvitationRecord::snapshot)
            active to invitations
        }
        val recent = runCatching { tournamentRepository.loadRecent(playerId, TOURNAMENT_HISTORY_LIMIT) }
            .getOrElse {
                System.err.println("Could not load tournament history for $playerId: ${it.message}")
                emptyList()
            }
        return listOf(Delivery(
            ServerMessage.TournamentHubData(
                TournamentHubSnapshot(
                    activeTournament = hubState.first,
                    invitations = hubState.second,
                    recentTournaments = recent
                )
            ),
            setOf(playerId)
        ))
    }

    private suspend fun createTournament(
        playerId: String,
        command: ClientMessage.CreateTournament
    ): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        validateModeAccess(playerId, command.gameMode)?.let { return listOf(it) }
        val safeName = command.name.trim().take(MAX_TOURNAMENT_NAME_LENGTH)
        if (safeName.length < 3) {
            return listOf(error(playerId, "INVALID_TOURNAMENT_NAME", "TÃªn giáº£i cáº§n cÃ³ Ã­t nháº¥t 3 kÃ½ tá»±."))
        }
        var snapshotToSave: TournamentSnapshot? = null
        val deliveries = mutex.withLock {
            val host = sessionsByPlayerId[playerId]
                ?.takeIf { it.isConnected && it.resumeToken == null }
                ?: return@withLock listOf(error(playerId, "SESSION_NOT_FOUND", "PhiÃªn chÆ¡i khÃ´ng cÃ²n há»£p lá»‡."))
            if (activeTournamentFor(playerId) != null) {
                return@withLock listOf(error(playerId, "TOURNAMENT_ALREADY_ACTIVE", "Báº¡n Ä‘ang tham gia má»™t giáº£i khÃ¡c."))
            }
            if (roomFor(playerId) != null || playerId in matchmakingEntries) {
                return@withLock listOf(error(playerId, "PLAYER_BUSY", "HÃ£y rá»i phÃ²ng hoáº·c há»§y ghÃ©p tráº­n trÆ°á»›c khi táº¡o giáº£i."))
            }
            val tournament = Tournament(
                id = UUID.randomUUID().toString(),
                name = safeName,
                hostId = playerId,
                gameMode = command.gameMode,
                participants = mutableListOf(TournamentParticipant(playerId, host.displayName)),
                matches = mutableListOf(
                    TournamentMatch(UUID.randomUUID().toString(), round = 1, position = 1),
                    TournamentMatch(UUID.randomUUID().toString(), round = 1, position = 2),
                    TournamentMatch(UUID.randomUUID().toString(), round = 2, position = 1)
                ),
                createdAtMillis = nowMillis()
            )
            tournaments[tournament.id] = tournament
            val snapshot = tournament.snapshot()
            snapshotToSave = snapshot
            listOf(
                Delivery(ServerMessage.TournamentUpdated(snapshot), setOf(playerId)),
                Delivery(ServerMessage.TournamentNotice("ÄÃ£ táº¡o giáº£i riÃªng 4 ngÆ°á»i."), setOf(playerId))
            )
        }
        snapshotToSave?.let { tournamentRepository.save(it) }
        return deliveries
    }

    private suspend fun inviteTournamentPlayer(
        playerId: String,
        command: ClientMessage.InviteTournamentPlayer
    ): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        if (friendRepository.isBlockedEitherWay(playerId, command.friendPlayerId)) {
            return listOf(error(playerId, "INTERACTION_BLOCKED", "KhÃ´ng thá»ƒ má»i ngÆ°á»i chÆ¡i nÃ y vÃ o giáº£i."))
        }
        if (!friendRepository.areFriends(playerId, command.friendPlayerId)) {
            return listOf(error(playerId, "NOT_FRIENDS", "NgÆ°á»i chÆ¡i nÃ y chÆ°a pháº£i báº¡n bÃ¨."))
        }
        return mutex.withLock {
            val tournament = tournaments[command.tournamentId]
                ?.takeIf { it.hostId == playerId && it.phase == TournamentPhase.LOBBY }
                ?: return@withLock listOf(error(playerId, "TOURNAMENT_NOT_INVITABLE", "Giáº£i khÃ´ng cÃ²n nháº­n lá»i má»i."))
            if (tournament.participants.size >= TOURNAMENT_PLAYER_COUNT) {
                return@withLock listOf(error(playerId, "TOURNAMENT_FULL", "Giáº£i Ä‘Ã£ Ä‘á»§ 4 ngÆ°á»i."))
            }
            if (command.friendPlayerId in tournament.playerIds()) {
                return@withLock listOf(error(playerId, "PLAYER_ALREADY_JOINED", "NgÆ°á»i chÆ¡i Ä‘Ã£ á»Ÿ trong giáº£i."))
            }
            val friendId = command.friendPlayerId
            if (activeTournamentFor(friendId) != null || roomFor(friendId) != null) {
                return@withLock listOf(error(playerId, "FRIEND_BUSY", "Báº¡n bÃ¨ Ä‘ang báº­n á»Ÿ phÃ²ng hoáº·c giáº£i khÃ¡c."))
            }
            tournamentInvitations.entries.removeAll { (_, invitation) ->
                invitation.tournamentId == tournament.id && invitation.inviteeId == friendId
            }
            val invitation = TournamentInvitationRecord(
                id = UUID.randomUUID().toString(),
                tournamentId = tournament.id,
                inviteeId = friendId,
                hostId = playerId,
                hostDisplayName = sessionsByPlayerId[playerId]?.displayName.orEmpty(),
                tournamentName = tournament.name,
                gameMode = tournament.gameMode,
                expiresAtMillis = nowMillis() + TOURNAMENT_INVITATION_TTL_MILLIS
            )
            tournamentInvitations[invitation.id] = invitation
            
            // Send Push Notification
            val fcmToken = playerProfileRepository.findFcmToken(friendId)
            if (fcmToken != null) {
                pushNotificationService.sendNotification(
                    fcmToken,
                    "Lời mời giải đấu",
                    "${invitation.hostDisplayName} đã mời bạn vào giải đấu ${invitation.tournamentName}"
                )
            }
            
            listOf(
                Delivery(ServerMessage.TournamentInvitation(invitation.snapshot()), setOf(friendId)),
                Delivery(ServerMessage.TournamentNotice("ÄÃ£ gá»­i lá»i má»i tham gia giáº£i."), setOf(playerId))
            )
        }
    }

    private suspend fun respondTournamentInvitation(
        playerId: String,
        command: ClientMessage.RespondTournamentInvitation
    ): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        var snapshotToSave: TournamentSnapshot? = null
        val deliveries = mutex.withLock {
            val invitation = tournamentInvitations.remove(command.invitationId)
                ?.takeIf { it.inviteeId == playerId && it.expiresAtMillis > nowMillis() }
                ?: return@withLock listOf(error(playerId, "TOURNAMENT_INVITATION_EXPIRED", "Lá»i má»i giáº£i Ä‘áº¥u Ä‘Ã£ háº¿t háº¡n."))
            if (!command.accept) {
                return@withLock listOf(
                    Delivery(ServerMessage.TournamentNotice("ÄÃ£ tá»« chá»‘i lá»i má»i giáº£i Ä‘áº¥u."), setOf(playerId)),
                    Delivery(ServerMessage.TournamentNotice("Má»™t ngÆ°á»i báº¡n Ä‘Ã£ tá»« chá»‘i lá»i má»i giáº£i Ä‘áº¥u."), setOf(invitation.hostId))
                )
            }
            val tournament = tournaments[invitation.tournamentId]
                ?.takeIf { it.phase == TournamentPhase.LOBBY }
                ?: return@withLock listOf(error(playerId, "TOURNAMENT_NOT_FOUND", "Giáº£i Ä‘áº¥u khÃ´ng cÃ²n tá»“n táº¡i."))
            if (tournament.participants.size >= TOURNAMENT_PLAYER_COUNT) {
                return@withLock listOf(error(playerId, "TOURNAMENT_FULL", "Giáº£i Ä‘Ã£ Ä‘á»§ 4 ngÆ°á»i."))
            }
            if (activeTournamentFor(playerId) != null || roomFor(playerId) != null || playerId in matchmakingEntries) {
                return@withLock listOf(error(playerId, "PLAYER_BUSY", "HÃ£y rá»i phÃ²ng hoáº·c giáº£i hiá»‡n táº¡i trÆ°á»›c."))
            }
            val session = sessionsByPlayerId[playerId]
                ?.takeIf { it.isConnected && it.resumeToken == null }
                ?: return@withLock listOf(error(playerId, "SESSION_NOT_FOUND", "PhiÃªn chÆ¡i khÃ´ng cÃ²n há»£p lá»‡."))
            tournament.participants += TournamentParticipant(playerId, session.displayName)
            val snapshot = tournament.snapshot()
            snapshotToSave = snapshot
            listOf(
                Delivery(ServerMessage.TournamentUpdated(snapshot), tournament.playerIds()),
                Delivery(ServerMessage.TournamentNotice("ÄÃ£ tham gia giáº£i ${tournament.name}."), setOf(playerId))
            )
        }
        snapshotToSave?.let { tournamentRepository.save(it) }
        return deliveries
    }

    private suspend fun startTournament(playerId: String, tournamentId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        var snapshotToSave: TournamentSnapshot? = null
        var roomIdsToSave = emptyList<String>()
        val deliveries = mutex.withLock {
            val tournament = tournaments[tournamentId]
                ?.takeIf { it.hostId == playerId && it.phase == TournamentPhase.LOBBY }
                ?: return@withLock listOf(error(playerId, "TOURNAMENT_NOT_STARTABLE", "Báº¡n khÃ´ng thá»ƒ báº¯t Ä‘áº§u giáº£i nÃ y."))
            if (tournament.participants.size != TOURNAMENT_PLAYER_COUNT) {
                return@withLock listOf(error(playerId, "TOURNAMENT_NOT_FULL", "Cáº§n Ä‘á»§ 4 ngÆ°á»i Ä‘á»ƒ báº¯t Ä‘áº§u."))
            }
            val unavailable = tournament.playerIds().firstOrNull { participantId ->
                sessionsByPlayerId[participantId]?.let { !it.isConnected || it.resumeToken != null } != false ||
                    roomFor(participantId) != null || participantId in matchmakingEntries
            }
            if (unavailable != null) {
                return@withLock listOf(error(playerId, "TOURNAMENT_PLAYER_UNAVAILABLE", "Táº¥t cáº£ ngÆ°á»i chÆ¡i pháº£i online vÃ  khÃ´ng á»Ÿ phÃ²ng khÃ¡c."))
            }

            tournament.phase = TournamentPhase.RUNNING
            tournament.startedAtMillis = nowMillis()
            val players = tournament.participants.map(TournamentParticipant::playerId)
            val semifinalPairs = listOf(players[0] to players[3], players[1] to players[2])
            val gameDeliveries = mutableListOf<Delivery>()
            tournament.matches.filter { it.round == 1 }.sortedBy { it.position }
                .zip(semifinalPairs)
                .forEach { (match, pair) ->
                    match.playerOneId = pair.first
                    match.playerTwoId = pair.second
                    val room = createTournamentRoom(tournament, match)
                    gameDeliveries += Delivery(ServerMessage.GameStarted(room.snapshot()), room.playerIds())
                }
            tournamentInvitations.entries.removeAll { it.value.tournamentId == tournament.id }
            val snapshot = tournament.snapshot()
            snapshotToSave = snapshot
            roomIdsToSave = tournament.matches.mapNotNull(TournamentMatch::roomId)
            listOf(Delivery(ServerMessage.TournamentUpdated(snapshot), tournament.playerIds())) + gameDeliveries
        }
        snapshotToSave?.let { tournamentRepository.save(it) }
        roomIdsToSave.forEach { persistRoom(it) }
        return deliveries + snapshotToSave?.players.orEmpty().flatMap { presenceUpdates(it.playerId) }
    }

    private suspend fun leaveTournament(playerId: String, tournamentId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        var snapshotToSave: TournamentSnapshot? = null
        val deliveries = mutex.withLock {
            val tournament = tournaments[tournamentId]
                ?.takeIf { playerId in it.playerIds() }
                ?: return@withLock listOf(error(playerId, "TOURNAMENT_NOT_FOUND", "Báº¡n khÃ´ng cÃ²n á»Ÿ trong giáº£i nÃ y."))
            if (tournament.phase != TournamentPhase.LOBBY) {
                return@withLock listOf(error(playerId, "TOURNAMENT_ALREADY_STARTED", "KhÃ´ng thá»ƒ rá»i giáº£i sau khi Ä‘Ã£ báº¯t Ä‘áº§u."))
            }
            if (tournament.hostId == playerId) {
                tournament.phase = TournamentPhase.CANCELLED
                tournament.finishedAtMillis = nowMillis()
                tournamentInvitations.entries.removeAll { it.value.tournamentId == tournament.id }
                val snapshot = tournament.snapshot()
                snapshotToSave = snapshot
                listOf(
                    Delivery(ServerMessage.TournamentUpdated(snapshot), tournament.playerIds()),
                    Delivery(ServerMessage.TournamentNotice("Chá»§ giáº£i Ä‘Ã£ há»§y giáº£i Ä‘áº¥u."), tournament.playerIds())
                )
            } else {
                tournament.participants.removeAll { it.playerId == playerId }
                tournamentInvitations.entries.removeAll {
                    it.value.tournamentId == tournament.id && it.value.inviteeId == playerId
                }
                val snapshot = tournament.snapshot()
                snapshotToSave = snapshot
                listOf(
                    Delivery(ServerMessage.TournamentUpdated(snapshot), tournament.playerIds()),
                    Delivery(ServerMessage.TournamentNotice("ÄÃ£ rá»i giáº£i Ä‘áº¥u."), setOf(playerId))
                )
            }
        }
        snapshotToSave?.let { tournamentRepository.save(it) }
        return deliveries + loadTournamentHub(playerId)
    }

    private suspend fun loadFriends(playerId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val stored = runCatching { friendRepository.load(playerId) }.getOrElse {
            return listOf(error(playerId, "FRIENDS_UNAVAILABLE", "ChÆ°a táº£i Ä‘Æ°á»£c danh sÃ¡ch báº¡n bÃ¨."))
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
            .getOrElse { return listOf(error(playerId, "NOTIFICATIONS_UNAVAILABLE", "ChÆ°a táº£i Ä‘Æ°á»£c thÃ´ng bÃ¡o.")) }
        val pendingFriendNotifications = pendingRequests.map { request ->
            NotificationSnapshot(
                id = "friend:${request.requestId}",
                kind = NotificationKind.FRIEND_REQUEST,
                title = "Lá»i má»i káº¿t báº¡n",
                message = "${request.displayName} muá»‘n káº¿t báº¡n vá»›i báº¡n.",
                createdAtEpochMillis = nowMillis(),
                destination = NotificationDestination.FRIENDS
            )
        }
        notificationRepository.createNotifications(playerId, pendingFriendNotifications)
        val notifications = runCatching { notificationRepository.loadNotifications(playerId) }
            .getOrElse { return listOf(error(playerId, "NOTIFICATIONS_UNAVAILABLE", "ChÆ°a táº£i Ä‘Æ°á»£c thÃ´ng bÃ¡o.")) }
        return listOf(Delivery(ServerMessage.NotificationsData(notifications), setOf(playerId)))
    }

    private suspend fun syncNotifications(
        playerId: String,
        notifications: List<NotificationSnapshot>
    ): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        if (notifications.size > MAX_NOTIFICATION_SYNC_BATCH) {
            return listOf(error(playerId, "INVALID_NOTIFICATIONS", "CÃ³ quÃ¡ nhiá»u thÃ´ng bÃ¡o cáº§n Ä‘á»“ng bá»™."))
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
            return listOf(error(playerId, "INVALID_NOTIFICATIONS", "Dá»¯ liá»‡u thÃ´ng bÃ¡o khÃ´ng há»£p lá»‡."))
        }
        notificationRepository.createNotifications(playerId, normalized)
        return loadNotifications(playerId)
    }

    private suspend fun markNotificationsRead(playerId: String, notificationId: String?): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        if (notificationId != null && notificationId.length !in 1..160) {
            return listOf(error(playerId, "INVALID_NOTIFICATION_ID", "MÃ£ thÃ´ng bÃ¡o khÃ´ng há»£p lá»‡."))
        }
        notificationRepository.markNotificationsRead(playerId, notificationId, nowMillis())
        return loadNotifications(playerId)
    }

    private suspend fun dismissNotifications(playerId: String, notificationId: String?): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        if (notificationId != null && notificationId.length !in 1..160) {
            return listOf(error(playerId, "INVALID_NOTIFICATION_ID", "MÃ£ thÃ´ng bÃ¡o khÃ´ng há»£p lá»‡."))
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
            return listOf(error(playerId, "INVALID_PLAYER_CODE", "MÃ£ ngÆ°á»i chÆ¡i khÃ´ng há»£p lá»‡."))
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
                            title = "Lá»i má»i káº¿t báº¡n",
                            message = "${request.displayName} muá»‘n káº¿t báº¡n vá»›i báº¡n.",
                            createdAtEpochMillis = nowMillis(),
                            destination = NotificationDestination.FRIENDS
                        ))
                    )
                }
                listOf(Delivery(ServerMessage.SocialNotice("ÄÃ£ gá»­i lá»i má»i káº¿t báº¡n."), setOf(playerId))) +
                    refreshSocialFor(setOf(playerId, result.recipientId)) +
                    refreshNotificationsFor(setOf(result.recipientId))
            }
            FriendRequestResult.PlayerNotFound -> listOf(error(playerId, "PLAYER_NOT_FOUND", "KhÃ´ng tÃ¬m tháº¥y mÃ£ ngÆ°á»i chÆ¡i."))
            FriendRequestResult.SelfRequest -> listOf(error(playerId, "SELF_FRIEND_REQUEST", "Báº¡n khÃ´ng thá»ƒ tá»± káº¿t báº¡n vá»›i mÃ¬nh."))
            FriendRequestResult.AlreadyExists -> listOf(error(playerId, "FRIENDSHIP_EXISTS", "Hai ngÆ°á»i Ä‘Ã£ lÃ  báº¡n hoáº·c Ä‘ang cÃ³ lá»i má»i."))
            FriendRequestResult.Blocked -> listOf(error(
                playerId,
                "INTERACTION_BLOCKED",
                "KhÃ´ng thá»ƒ gá»­i lá»i má»i káº¿t báº¡n cho ngÆ°á»i chÆ¡i nÃ y."
            ))
        }
    }

    private suspend fun cancelFriendRequest(playerId: String, requestId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        return when (val result = friendRepository.cancelRequest(playerId, requestId)) {
            is FriendCancellationResult.Success -> {
                notificationRepository.dismissNotifications(result.recipientId, "friend:$requestId", nowMillis())
                listOf(Delivery(ServerMessage.SocialNotice("ÄÃ£ há»§y lá»i má»i káº¿t báº¡n."), setOf(playerId))) +
                    refreshSocialFor(setOf(playerId, result.recipientId)) +
                    refreshNotificationsFor(setOf(result.recipientId))
            }
            FriendCancellationResult.NotFound -> listOf(error(
                playerId,
                "FRIEND_REQUEST_NOT_FOUND",
                "Lá»i má»i káº¿t báº¡n khÃ´ng cÃ²n tá»“n táº¡i hoáº·c khÃ´ng thuá»™c vá» báº¡n."
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
                val notice = if (command.accept) "ÄÃ£ cháº¥p nháº­n lá»i má»i káº¿t báº¡n." else "ÄÃ£ tá»« chá»‘i lá»i má»i káº¿t báº¡n."
                listOf(Delivery(ServerMessage.SocialNotice(notice), setOf(playerId))) +
                    refreshSocialFor(setOf(playerId, result.requesterId)) +
                    refreshNotificationsFor(setOf(playerId))
            }
            FriendResponseResult.NotFound -> listOf(error(playerId, "FRIEND_REQUEST_NOT_FOUND", "Lá»i má»i khÃ´ng cÃ²n tá»“n táº¡i."))
        }
    }

    private suspend fun removeFriend(playerId: String, friendUserId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        return when (val result = friendRepository.removeFriend(playerId, friendUserId)) {
            is SocialMutationResult.Success -> {
                clearRoomInvitationsBetween(playerId, result.otherUserId)
                listOf(Delivery(ServerMessage.SocialNotice("ÄÃ£ há»§y káº¿t báº¡n."), setOf(playerId))) +
                    refreshSocialFor(setOf(playerId, result.otherUserId)) +
                    refreshRoomInvitationsFor(setOf(playerId, result.otherUserId))
            }
            SocialMutationResult.NotFound -> listOf(error(
                playerId,
                "FRIEND_NOT_FOUND",
                "Quan há»‡ báº¡n bÃ¨ khÃ´ng cÃ²n tá»“n táº¡i."
            ))
            SocialMutationResult.SelfAction -> listOf(error(
                playerId,
                "INVALID_SOCIAL_ACTION",
                "KhÃ´ng thá»ƒ thá»±c hiá»‡n thao tÃ¡c nÃ y vá»›i chÃ­nh báº¡n."
            ))
        }
    }

    private suspend fun blockPlayer(playerId: String, playerUserId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        return when (val result = friendRepository.blockPlayer(playerId, playerUserId, nowMillis())) {
            is SocialMutationResult.Success -> {
                clearRoomInvitationsBetween(playerId, result.otherUserId)
                listOf(Delivery(ServerMessage.SocialNotice("ÄÃ£ cháº·n ngÆ°á»i chÆ¡i."), setOf(playerId))) +
                    refreshSocialFor(setOf(playerId, result.otherUserId)) +
                    refreshRoomInvitationsFor(setOf(playerId, result.otherUserId))
            }
            SocialMutationResult.NotFound -> listOf(error(
                playerId,
                "PLAYER_NOT_FOUND",
                "KhÃ´ng tÃ¬m tháº¥y ngÆ°á»i chÆ¡i Ä‘á»ƒ cháº·n."
            ))
            SocialMutationResult.SelfAction -> listOf(error(
                playerId,
                "INVALID_SOCIAL_ACTION",
                "Báº¡n khÃ´ng thá»ƒ tá»± cháº·n chÃ­nh mÃ¬nh."
            ))
        }
    }

    private suspend fun unblockPlayer(playerId: String, playerUserId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        return when (val result = friendRepository.unblockPlayer(playerId, playerUserId)) {
            is SocialMutationResult.Success ->
                listOf(Delivery(ServerMessage.SocialNotice("ÄÃ£ bá» cháº·n ngÆ°á»i chÆ¡i."), setOf(playerId))) +
                    refreshSocialFor(setOf(playerId, result.otherUserId))
            SocialMutationResult.NotFound -> listOf(error(
                playerId,
                "BLOCK_NOT_FOUND",
                "NgÆ°á»i chÆ¡i nÃ y khÃ´ng cÃ²n trong danh sÃ¡ch cháº·n."
            ))
            SocialMutationResult.SelfAction -> listOf(error(
                playerId,
                "INVALID_SOCIAL_ACTION",
                "KhÃ´ng thá»ƒ thá»±c hiá»‡n thao tÃ¡c nÃ y vá»›i chÃ­nh báº¡n."
            ))
        }
    }

    private suspend fun inviteFriend(playerId: String, command: ClientMessage.InviteFriend): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        if (friendRepository.isBlockedEitherWay(playerId, command.friendUserId)) {
            return listOf(error(
                playerId,
                "INTERACTION_BLOCKED",
                "KhÃ´ng thá»ƒ má»i ngÆ°á»i chÆ¡i nÃ y vÃ o phÃ²ng."
            ))
        }
        if (!friendRepository.areFriends(playerId, command.friendUserId)) {
            return listOf(error(playerId, "NOT_FRIENDS", "NgÆ°á»i chÆ¡i nÃ y chÆ°a pháº£i báº¡n bÃ¨."))
        }
        var createdInvitation: RoomInvitationRecord? = null
        val replacedInvitationIds = mutableListOf<String>()
        val deliveries = mutex.withLock {
            val inviter = sessionsByPlayerId[playerId]
                ?: return@withLock listOf(error(playerId, "SESSION_NOT_FOUND", "PhiÃªn chÆ¡i khÃ´ng cÃ²n há»£p lá»‡."))
            val room = rooms[command.roomId]
                ?.takeIf { it.hostId == playerId && it.phase == RoomPhase.WAITING && it.guestId == null }
                ?: return@withLock listOf(error(playerId, "ROOM_NOT_INVITABLE", "PhÃ²ng khÃ´ng cÃ²n sáºµn sÃ ng Ä‘á»ƒ má» i báº¡n."))
            val friendId = command.friendUserId
            if (roomFor(friendId) != null) {
                return@withLock listOf(error(playerId, "FRIEND_BUSY", "Báº¡n bÃ¨ Ä‘ang á»Ÿ trong phÃ²ng khÃ¡c."))
            }
            val invitation = RoomInvitationRecord(
                id = UUID.randomUUID().toString(),
                inviterId = playerId,
                inviteeId = friendId,
                roomId = room.id,
                inviterDisplayName = inviter.displayName,
                roomName = room.name,
                expiresAtMillis = nowMillis() + ROOM_INVITATION_TTL_MILLIS
            )
            roomInvitations.entries.removeAll { entry ->
                (entry.value.inviterId == playerId && entry.value.inviteeId == friendId).also { removed ->
                    if (removed) replacedInvitationIds += entry.key
                }
            }
            roomInvitations[invitation.id] = invitation
            createdInvitation = invitation
            listOf(
                Delivery(invitation.toMessage(), setOf(friendId)),
                Delivery(ServerMessage.SocialNotice("Ä Ã£ gá»­i lá» i má» i vÃ o phÃ²ng."), setOf(playerId))
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
                    title = "Lá»i má»i vÃ o phÃ²ng",
                    message = "${invitation.inviterDisplayName} má»i báº¡n vÃ o phÃ²ng ${invitation.roomName}.",
                    createdAtEpochMillis = nowMillis(),
                    destination = NotificationDestination.FRIENDS
                ))
            )
            
            val fcmToken = playerProfileRepository.findFcmToken(invitation.inviteeId)
            if (fcmToken != null) {
                pushNotificationService.sendNotification(
                    fcmToken,
                    "Lời mời chơi game",
                    "${invitation.inviterDisplayName} đã mời bạn vào phòng ${invitation.roomName}"
                )
            }
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
                ?: return@withLock listOf(error(playerId, "INVITATION_EXPIRED", "Lá»i má»i vÃ o phÃ²ng Ä‘Ã£ háº¿t háº¡n."))
            invitationConsumed = true
            if (!command.accept) {
                return@withLock listOf(
                    Delivery(ServerMessage.SocialNotice("ÄÃ£ tá»« chá»‘i lá»i má»i vÃ o phÃ²ng."), setOf(playerId)),
                    Delivery(ServerMessage.SocialNotice("Báº¡n bÃ¨ Ä‘Ã£ tá»« chá»‘i lá»i má»i vÃ o phÃ²ng."), setOf(invitation.inviterId))
                )
            }
            val player = sessionsByPlayerId[playerId]
                ?: return@withLock listOf(error(playerId, "SESSION_NOT_FOUND", "PhiÃªn chÆ¡i khÃ´ng cÃ²n há»£p lá»‡."))
            if (roomFor(playerId) != null) {
                return@withLock listOf(error(playerId, "ALREADY_IN_ROOM", "Báº¡n Ä‘ang á»Ÿ trong má»™t phÃ²ng khÃ¡c."))
            }
            val room = rooms[invitation.roomId]
                ?.takeIf { it.hostId == invitation.inviterId && it.phase == RoomPhase.WAITING && it.guestId == null }
                ?: return@withLock listOf(error(playerId, "ROOM_NOT_FOUND", "PhÃ²ng Ä‘Æ°á»£c má»i khÃ´ng cÃ²n sáºµn sÃ ng."))
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

    private suspend fun validateModeAccess(playerId: String, mode: ProtocolGameMode): Delivery? {
        val level = if (isAccountSession(playerId)) {
            playerProfileRepository.findByPlayerId(playerId)?.progression?.level ?: 1
        } else {
            1
        }
        return if (level < mode.unlockLevel) {
            error(playerId, "MODE_LOCKED", "Chế độ này mở khóa ở cấp ${mode.unlockLevel}.")
        } else {
            null
        }
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
            return listOf(error(playerId, "ACCOUNT_REQUIRED", "HÃ£y Ä‘Äƒng nháº­p Ä‘á»ƒ ghÃ©p tráº­n trá»±c tuyáº¿n."))
        }
        if (mutex.withLock { activeTournamentFor(playerId) != null }) {
            return listOf(error(playerId, "TOURNAMENT_ACTIVE", "HÃ£y rá»i hoáº·c hoÃ n táº¥t giáº£i Ä‘áº¥u hiá»‡n táº¡i trÆ°á»›c."))
        }
        val profile = playerProfileRepository.findByPlayerId(playerId)
        val level = profile?.progression?.level ?: 1
        if (level < command.gameMode.unlockLevel) {
            return listOf(error(
                playerId,
                "MODE_LOCKED",
                "Cháº¿ Ä‘á»™ nÃ y má»Ÿ khÃ³a á»Ÿ cáº¥p ${command.gameMode.unlockLevel}."
            ))
        }
        val rating = profile?.statistics?.eloRating ?: DEFAULT_ELO_RATING
        val joinedAt = nowMillis()
        val candidates = mutex.withLock {
            if (roomFor(playerId) != null) {
                return@withLock emptyList<MatchmakingEntry>()
            }
            matchmakingEntries.values
                .filter {
                    it.playerId != playerId && it.gameMode == command.gameMode && it.matchType == command.matchType
                }
                .filter { candidate ->
                    sessionsByPlayerId[candidate.playerId]?.isConnected == true && roomFor(candidate.playerId) == null
                }
                .sortedWith(
                    if (command.matchType == MatchType.RANKED) {
                        compareBy<MatchmakingEntry> { kotlin.math.abs(it.eloRating - rating) }
                            .thenBy { if (command.gameMode == com.hienthai.fastowin.protocol.ProtocolGameMode.TEAM_2V2) (sessionsByPlayerId[it.playerId]?.latencyMillis ?: 0L) >= 150L else false }
                            .thenBy { it.joinedAtMillis }
                    } else {
                        compareBy<MatchmakingEntry> { if (command.gameMode == com.hienthai.fastowin.protocol.ProtocolGameMode.TEAM_2V2) (sessionsByPlayerId[it.playerId]?.latencyMillis ?: 0L) >= 150L else false }
                            .thenBy { it.joinedAtMillis }
                    }
                )
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
                ?: return@withLock listOf(error(playerId, "SESSION_NOT_FOUND", "PhiÃªn chÆ¡i khÃ´ng cÃ²n há»£p lá»‡."))
            if (roomFor(playerId) != null) {
                matchmakingEntries.remove(playerId)
                return@withLock listOf(error(playerId, "ALREADY_IN_ROOM", "Báº¡n Ä‘ang á»Ÿ trong má»™t phÃ²ng khÃ¡c."))
            }

            val queuedCandidate = candidate?.let { matchmakingEntries[it.playerId] }?.takeIf { queued ->
                queued.gameMode == command.gameMode &&
                    queued.matchType == command.matchType &&
                    sessionsByPlayerId[queued.playerId]?.isConnected == true &&
                    roomFor(queued.playerId) == null &&
                    (command.matchType == MatchType.CASUAL ||
                        kotlin.math.abs(queued.eloRating - rating) <= maxOf(
                            matchmakingRange(joinedAt - queued.joinedAtMillis),
                            matchmakingRange(0L)
                        ))
            }
            if (queuedCandidate == null) {
                val existing = matchmakingEntries[playerId]
                matchmakingEntries[playerId] = MatchmakingEntry(
                    playerId = player.playerId,
                    gameMode = command.gameMode,
                    matchType = command.matchType,
                    eloRating = rating,
                    joinedAtMillis = existing?.joinedAtMillis ?: joinedAt
                )
                return@withLock listOf(Delivery(
                    ServerMessage.MatchmakingStatus(
                        isSearching = true,
                        gameMode = command.gameMode,
                        matchType = command.matchType,
                        ratingRange = if (command.matchType == MatchType.RANKED) {
                            matchmakingRange(joinedAt - (existing?.joinedAtMillis ?: joinedAt))
                        } else {
                            0
                        }
                    ),
                    setOf(playerId)
                ))
            }

            val hostId = queuedCandidate.playerId
            matchmakingEntries.remove(hostId)
            matchmakingEntries.remove(playerId)
            val room = Room(
                id = UUID.randomUUID().toString(),
                name = "Äáº¥u nhanh",
                hostId = hostId,
                password = null,
                gameMode = command.gameMode,
                matchType = command.matchType,
                guestId = playerId,
                scores = mutableMapOf(hostId to 0, playerId to 0)
            )
            room.startMatch(joinedAt, timeAttackMillis)
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
        val entry = mutex.withLock { matchmakingEntries.remove(playerId) }
        return listOf(
            Delivery(
                ServerMessage.MatchmakingStatus(
                    isSearching = false,
                    gameMode = entry?.gameMode,
                    matchType = entry?.matchType ?: MatchType.RANKED,
                    ratingRange = if (entry?.matchType == MatchType.CASUAL) 0 else MATCHMAKING_INITIAL_RANGE
                ),
                setOf(playerId)
            )
        ) +
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
        "HÃ£y Ä‘Äƒng nháº­p tÃ i khoáº£n Ä‘á»ƒ sá»­ dá»¥ng tÃ­nh nÄƒng nÃ y."
    )

    private suspend fun loadProfile(playerId: String): List<Delivery> {
        val session = mutex.withLock { sessionsByPlayerId[playerId]?.copy() }
            ?: return listOf(error(playerId, "SESSION_NOT_FOUND", "PhiÃªn chÆ¡i khÃ´ng cÃ²n há»£p lá»‡."))
        val persisted = runCatching { playerProfileRepository.findByPlayerId(playerId) }
            .onFailure { System.err.println("Could not load profile $playerId: ${it.message}") }
            .getOrNull()
        val profile = persisted ?: PlayerProfileSnapshot(
            userId = playerId,
            displayName = session.displayName,
            playerCode = playerId.replace("-", "").take(10).uppercase()
        )
        return listOf(Delivery(ServerMessage.ProfileData(profile), setOf(playerId)))
    }

    private suspend fun claimDailyCheckIn(playerId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val result = runCatching { playerProfileRepository.claimDailyCheckIn(playerId) }
            .onFailure { System.err.println("Could not claim daily check-in for $playerId: ${it.message}") }
            .getOrNull()
            ?: return listOf(error(
                playerId,
                "DAILY_CHECK_IN_UNAVAILABLE",
                "ChÆ°a thá»ƒ Ä‘iá»ƒm danh. Vui lÃ²ng thá»­ láº¡i."
            ))
        return loadProfile(playerId) + Delivery(
            ServerMessage.DailyCheckInResult(result.claimed, result.rewardXp),
            setOf(playerId)
        )
    }

    private suspend fun claimMissionReward(playerId: String, missionCode: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val result = runCatching {
            playerProfileRepository.claimMissionReward(playerId, missionCode)
        }.onFailure {
            System.err.println("Could not claim mission reward $missionCode for $playerId: ${it.message}")
        }.getOrNull() ?: return listOf(error(
            playerId,
            "MISSION_REWARD_UNAVAILABLE",
            "ChÆ°a thá»ƒ nháº­n thÆ°á»Ÿng nhiá»‡m vá»¥. Vui lÃ²ng thá»­ láº¡i."
        ))
        return when (result.status) {
            MissionRewardClaimStatus.CLAIMED -> loadProfile(playerId) + Delivery(
                ServerMessage.MissionRewardResult(missionCode, claimed = true, rewardXp = result.rewardXp),
                setOf(playerId)
            )
            MissionRewardClaimStatus.ALREADY_CLAIMED -> listOf(error(
                playerId,
                "MISSION_ALREADY_CLAIMED",
                "Pháº§n thÆ°á»Ÿng nhiá»‡m vá»¥ nÃ y Ä‘Ã£ Ä‘Æ°á»£c nháº­n."
            ))
            MissionRewardClaimStatus.NOT_COMPLETED -> listOf(error(
                playerId,
                "MISSION_NOT_COMPLETED",
                "Nhiá»‡m vá»¥ chÆ°a hoÃ n thÃ nh."
            ))
            MissionRewardClaimStatus.INVALID_MISSION -> listOf(error(
                playerId,
                "INVALID_MISSION",
                "Nhiá»‡m vá»¥ khÃ´ng há»£p lá»‡."
            ))
        }
    }

    private suspend fun loadFriendProfile(playerId: String, friendUserId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        if (friendUserId.isBlank() || friendUserId.length > 64 || friendUserId == playerId) {
            return listOf(error(playerId, "INVALID_FRIEND", "NgÆ°á»i chÆ¡i khÃ´ng há»£p lá»‡."))
        }
        val areFriends = runCatching { friendRepository.areFriends(playerId, friendUserId) }
            .getOrElse {
                return listOf(error(playerId, "FRIEND_PROFILE_UNAVAILABLE", "ChÆ°a táº£i Ä‘Æ°á»£c há»“ sÆ¡ báº¡n bÃ¨."))
            }
        if (!areFriends) {
            return listOf(error(playerId, "FRIEND_PROFILE_FORBIDDEN", "Báº¡n chá»‰ cÃ³ thá»ƒ xem há»“ sÆ¡ cá»§a báº¡n bÃ¨."))
        }
        val profile = runCatching { playerProfileRepository.findByPlayerId(friendUserId) }
            .getOrNull()
            ?: return listOf(error(playerId, "FRIEND_PROFILE_NOT_FOUND", "KhÃ´ng tÃ¬m tháº¥y há»“ sÆ¡ ngÆ°á»i chÆ¡i."))
        return listOf(Delivery(ServerMessage.FriendProfileData(friendUserId, profile), setOf(playerId)))
    }

    private suspend fun loadMatchDetail(playerId: String, matchId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val detail = runCatching { playerProfileRepository.findMatchDetail(playerId, matchId) }.getOrNull()
            ?: return listOf(error(playerId, "MATCH_NOT_FOUND", "KhÃ´ng tÃ¬m tháº¥y chi tiáº¿t tráº­n Ä‘áº¥u."))
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
            return listOf(error(playerId, "COSMETIC_LOCKED", "Váº­t pháº©m chÆ°a Ä‘Æ°á»£c má»Ÿ khÃ³a hoáº·c khÃ´ng há»£p lá»‡."))
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
                "Biá»‡t danh pháº£i cÃ³ tá»« 1 Ä‘áº¿n $MAX_PROFILE_DISPLAY_NAME_LENGTH kÃ½ tá»±."
            ))
        }
        if (command.avatarId != null && command.avatarId !in PROFILE_AVATAR_IDS) {
            return listOf(error(playerId, "INVALID_AVATAR", "áº¢nh Ä‘áº¡i diá»‡n khÃ´ng há»£p lá»‡."))
        }
        val session = mutex.withLock { sessionsByPlayerId[playerId]?.copy() }
        if (session == null) {
            return listOf(error(playerId, "SESSION_NOT_FOUND", "PhiÃªn chÆ¡i khÃ´ng cÃ²n há»£p lá»‡."))
        }
        if (session.resumeToken != null) {
            return listOf(error(
                playerId,
                "ACCOUNT_REQUIRED",
                "HÃ£y lÆ°u tÃ i khoáº£n khÃ¡ch trÆ°á»›c khi chá»‰nh sá»­a há»“ sÆ¡."
            ))
        }
        if (command.avatarId == DAILY_CHECK_IN_AVATAR_ID) {
            val profile = runCatching { playerProfileRepository.findByPlayerId(playerId) }.getOrNull()
            val isUnlocked = profile?.progression?.cosmetics?.any {
                it.type == CosmeticType.AVATAR && it.id == DAILY_CHECK_IN_AVATAR_ID && it.unlocked
            } == true
            if (!isUnlocked) {
                return listOf(error(
                    playerId,
                    "AVATAR_LOCKED",
                    "áº¢nh Ä‘áº¡i diá»‡n nÃ y Ä‘Æ°á»£c má»Ÿ khÃ³a sau 50 láº§n Ä‘iá»ƒm danh."
                ))
            }
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
                "ChÆ°a thá»ƒ lÆ°u há»“ sÆ¡. Vui lÃ²ng thá»­ láº¡i."
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
            return listOf(error(playerId, "SESSION_NOT_FOUND", "PhiÃªn chÆ¡i khÃ´ng cÃ²n há»£p lá»‡."))
        }
        val leaderboard = runCatching {
            leaderboardRepository.load(playerId, LEADERBOARD_SIZE)
        }.onFailure {
            System.err.println("Could not load leaderboard $playerId: ${it.message}")
        }.getOrElse {
            return listOf(error(playerId, "LEADERBOARD_UNAVAILABLE", "ChÆ°a táº£i Ä‘Æ°á»£c báº£ng xáº¿p háº¡ng."))
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
                val previousSequence = room.sequence
                val gameFinished = room.finishIfTimedOut()
                if (room.sequence == previousSequence) {
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
                    TimedAdvance(
                        delivery = Delivery(
                            if (gameFinished) {
                                ServerMessage.GameFinished(room.snapshot())
                            } else {
                                ServerMessage.GameStateUpdated(
                                    game = room.snapshot(),
                                    acceptedNumber = 0,
                                    selectedByPlayerId = room.finishedPlayerIds.firstOrNull().orEmpty(),
                                    selectionAccepted = false
                                )
                            },
                            room.playerIds()
                        ),
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
                is ServerMessage.GameStateUpdated -> message.game.roomId
                is ServerMessage.RematchStatus -> message.game.roomId
                else -> null
            }?.let { persistRoom(it) }
        }
        val tournamentDeliveries = advances.mapNotNull(TimedAdvance::completedMatch)
            .flatMap { advanceTournamentAfterMatch(it.matchId) }
        return advances.map(TimedAdvance::delivery) + tournamentDeliveries
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
                            reason = "NgÆ°á»i chÆ¡i Ä‘Ã£ máº¥t káº¿t ná»‘i quÃ¡ lÃ¢u."
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
        if (activeTournamentFor(player.playerId) != null) {
            return listOf(error(player.playerId, "TOURNAMENT_ACTIVE", "HÃ£y rá»i hoáº·c hoÃ n táº¥t giáº£i Ä‘áº¥u hiá»‡n táº¡i trÆ°á»›c."))
        }
        if (roomFor(player.playerId) != null) {
            return listOf(error(player.playerId, "ALREADY_IN_ROOM", "Báº¡n Ä‘ang á»Ÿ trong má»™t phÃ²ng khÃ¡c."))
        }
        val name = command.roomName.trim().take(MAX_ROOM_NAME_LENGTH)
        if (name.isEmpty()) {
            return listOf(error(player.playerId, "INVALID_ROOM_NAME", "TÃªn phÃ²ng khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng."))
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
        if (activeTournamentFor(player.playerId) != null) {
            return listOf(error(player.playerId, "TOURNAMENT_ACTIVE", "HÃ£y rá»i hoáº·c hoÃ n táº¥t giáº£i Ä‘áº¥u hiá»‡n táº¡i trÆ°á»›c."))
        }
        if (roomFor(player.playerId) != null) {
            return listOf(error(player.playerId, "ALREADY_IN_ROOM", "Báº¡n Ä‘ang á»Ÿ trong má»™t phÃ²ng khÃ¡c."))
        }
        val room = rooms[command.roomId]
            ?: return listOf(error(player.playerId, "ROOM_NOT_FOUND", "PhÃ²ng khÃ´ng cÃ²n tá»“n táº¡i."))
        if (room.password != null && !room.password.matches(command.password)) {
            return listOf(error(player.playerId, "WRONG_PASSWORD", "Máº­t kháº©u phÃ²ng khÃ´ng Ä‘Ãºng."))
        }

        matchmakingEntries.remove(player.playerId)
        
        if (command.asSpectator) {
            room.spectatorIds.add(player.playerId)
            room.sequence++
            return listOf(Delivery(ServerMessage.RoomUpdated(room.snapshot()), room.participantIds()))
        }

        val maxPlayers = if (room.gameMode == com.hienthai.fastowin.protocol.ProtocolGameMode.TEAM_2V2) 4 else 2
        if (room.phase != RoomPhase.WAITING || room.playerIds().size >= maxPlayers) {
            return listOf(error(player.playerId, "ROOM_FULL", "PhÃ²ng Ä‘Ã£ Ä‘áº§y hoáº·c Ä‘ang chÆ¡i."))
        }

        if (room.guestId == null) {
            room.guestId = player.playerId
        } else {
            room.extraGuestIds.add(player.playerId)
        }
        
        if (room.gameMode == com.hienthai.fastowin.protocol.ProtocolGameMode.TEAM_2V2) {
            val teams = listOf("TEAM_A", "TEAM_B")
            val counts = room.teamIds.values.groupingBy { it }.eachCount()
            room.teamIds[player.playerId] = teams.minByOrNull { counts[it] ?: 0 } ?: "TEAM_A"
            if (player.playerId == room.guestId && room.hostId !in room.teamIds) {
                 room.teamIds[room.hostId] = "TEAM_A"
            }
        }
        room.sequence++
        room.scores[player.playerId] = 0
        room.readyPlayerIds.clear()
        val participants = room.participantIds()
        return listOf(
            Delivery(ServerMessage.RoomUpdated(room.snapshot()), participants),
            Delivery(ServerMessage.RoomList(publicRooms()))
        )
    }

    private fun setReady(player: GuestSession, command: ClientMessage.SetReady): HandleResult {
        val room = rooms[command.roomId]
            ?: return HandleResult(listOf(error(player.playerId, "ROOM_NOT_FOUND", "PhÃ²ng khÃ´ng cÃ²n tá»“n táº¡i.")))
        if (player.playerId !in room.playerIds()) {
            return HandleResult(listOf(error(player.playerId, "NOT_IN_ROOM", "Báº¡n khÃ´ng á»Ÿ trong phÃ²ng nÃ y.")))
        }
        if (room.phase != RoomPhase.WAITING) {
            return HandleResult(listOf(error(player.playerId, "ROOM_NOT_WAITING", "PhÃ²ng khÃ´ng cÃ²n á»Ÿ tráº¡ng thÃ¡i chá».")))
        }
        if (command.ready) room.readyPlayerIds += player.playerId else room.readyPlayerIds -= player.playerId
        room.sequence++
        val participants = room.playerIds()
        if (participants.size == 2 && room.readyPlayerIds.containsAll(participants)) {
            room.startMatch(nowMillis(), timeAttackMillis)
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
            ?: return HandleResult(listOf(error(player.playerId, "ROOM_NOT_FOUND", "PhÃ²ng khÃ´ng cÃ²n tá»“n táº¡i.")))
        if (room.hostId != player.playerId) {
            return HandleResult(listOf(error(player.playerId, "HOST_REQUIRED", "Chá»‰ chá»§ phÃ²ng má»›i cÃ³ thá»ƒ má»i ngÆ°á»i chÆ¡i ra ngoÃ i.")))
        }
        if (room.phase != RoomPhase.WAITING || command.playerId !in room.playerIds()) {
            return HandleResult(listOf(error(player.playerId, "PLAYER_NOT_IN_ROOM", "NgÆ°á»i chÆ¡i khÃ´ng cÃ²n trong phÃ²ng.")))
        }
        val kickedPlayerId = command.playerId
        if (room.guestId == kickedPlayerId) {
            room.guestId = null
        } else {
            room.extraGuestIds.remove(kickedPlayerId)
        }
        room.teamIds.remove(kickedPlayerId)
        room.readyPlayerIds.clear()
        room.scores.remove(kickedPlayerId)
        room.sequence++
        return HandleResult(
            deliveries = listOf(
                Delivery(ServerMessage.RoomClosed(room.id, "Chá»§ phÃ²ng Ä‘Ã£ má»i báº¡n ra khá»i phÃ²ng."), setOf(kickedPlayerId)),
                Delivery(ServerMessage.RoomUpdated(room.snapshot()), setOf(room.hostId)),
                Delivery(ServerMessage.RoomList(publicRooms()))
            ),
            changedRoomId = room.id
        )
    }

    private fun leaveRoom(player: GuestSession, command: ClientMessage.LeaveRoom): List<Delivery> {
        val room = rooms[command.roomId]
            ?: return listOf(error(player.playerId, "ROOM_NOT_FOUND", "PhÃ²ng khÃ´ng cÃ²n tá»“n táº¡i."))
        if (player.playerId !in room.playerIds() && player.playerId !in room.spectatorIds) {
            return listOf(error(player.playerId, "NOT_IN_ROOM", "Báº¡n khÃ´ng á»Ÿ trong phÃ²ng nÃ y."))
        }
        if (room.tournamentId != null && room.phase == RoomPhase.PLAYING) {
            return listOf(error(player.playerId, "TOURNAMENT_MATCH_ACTIVE", "KhÃ´ng thá»ƒ rá»i khi tráº­n Ä‘áº¥u giáº£i Ä‘ang diá»…n ra."))
        }

        if (player.playerId in room.spectatorIds) {
            room.spectatorIds.remove(player.playerId)
            room.sequence++
            return listOf(
                Delivery(ServerMessage.RoomUpdated(room.snapshot()), room.participantIds())
            )
        }

        val participants = room.participantIds()
        if (player.playerId == room.hostId) {
            rooms.remove(room.id)
            return listOf(
                Delivery(ServerMessage.RoomClosed(room.id, "Chá»§ phÃ²ng Ä‘Ã£ rá»i phÃ²ng."), participants),
                Delivery(ServerMessage.RoomList(publicRooms()))
            )
        } else {
            if (room.guestId == player.playerId) {
                room.guestId = null
            } else {
                room.extraGuestIds.remove(player.playerId)
            }
            room.teamIds.remove(player.playerId)
            room.scores.remove(player.playerId)
            room.readyPlayerIds.clear()
            room.sequence++
            return listOf(
                Delivery(ServerMessage.RoomUpdated(room.snapshot()), room.participantIds()),
                Delivery(ServerMessage.RoomList(publicRooms()))
            )
        }
    }

    private fun respondRematch(
        player: GuestSession,
        roomId: String,
        accept: Boolean
    ): HandleResult {
        val room = rooms[roomId]
            ?: return HandleResult(listOf(error(player.playerId, "ROOM_NOT_FOUND", "PhÃ²ng khÃ´ng cÃ²n tá»“n táº¡i.")))
        if (player.playerId !in room.playerIds()) {
            return HandleResult(listOf(error(player.playerId, "NOT_IN_ROOM", "Báº¡n khÃ´ng á»Ÿ trong phÃ²ng nÃ y.")))
        }
        if (room.tournamentId != null) {
            return HandleResult(listOf(error(player.playerId, "TOURNAMENT_REMATCH_DISABLED", "Tráº­n Ä‘áº¥u giáº£i khÃ´ng há»— trá»£ Ä‘áº¥u láº¡i.")))
        }
        if (room.matchType == MatchType.RANKED) {
            return HandleResult(
                listOf(
                    error(
                        player.playerId,
                        "RANKED_REMATCH_DISABLED",
                        "Tráº­n xáº¿p háº¡ng khÃ´ng há»— trá»£ Ä‘áº¥u láº¡i. HÃ£y ghÃ©p má»™t Ä‘á»‘i thá»§ má»›i."
                    )
                )
            )
        }
        if (room.phase != RoomPhase.FINISHED) {
            return HandleResult(listOf(error(player.playerId, "REMATCH_NOT_AVAILABLE", "Chá»‰ cÃ³ thá»ƒ Ä‘áº¥u láº¡i sau khi tráº­n káº¿t thÃºc.")))
        }
        if (room.playerIds().size != 2) {
            return HandleResult(listOf(error(player.playerId, "OPPONENT_LEFT", "Äá»‘i thá»§ Ä‘Ã£ rá»i phÃ²ng.")))
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
                return HandleResult(listOf(error(player.playerId, "REMATCH_NOT_PENDING", "KhÃ´ng cÃ³ yÃªu cáº§u Ä‘áº¥u láº¡i nÃ o Ä‘ang chá».")))
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
        room.startMatch(nowMillis(), timeAttackMillis)
        return HandleResult(
            deliveries = listOf(Delivery(ServerMessage.GameStarted(room.snapshot()), room.playerIds())),
            changedRoomId = room.id
        )
    }

    private fun selectNumber(player: GuestSession, command: ClientMessage.SelectNumber): HandleResult {
        val room = rooms[command.roomId]
            ?: return HandleResult(listOf(error(player.playerId, "ROOM_NOT_FOUND", "PhÃ²ng khÃ´ng cÃ²n tá»“n táº¡i.", command.requestId)))
        if (player.playerId !in room.playerIds()) {
            return HandleResult(listOf(error(player.playerId, "NOT_IN_ROOM", "Báº¡n khÃ´ng á»Ÿ trong phÃ²ng nÃ y.", command.requestId)))
        }
        if (command.requestId.isBlank() || command.requestId.length > MAX_REQUEST_ID_LENGTH) {
            return HandleResult(listOf(error(player.playerId, "INVALID_REQUEST_ID", "MÃ£ yÃªu cáº§u khÃ´ng há»£p lá»‡.")))
        }
        val requestKey = "${player.playerId}:${command.requestId}"
        room.processedRequests[requestKey]?.let { previous ->
            return HandleResult(listOf(Delivery(previous, setOf(player.playerId))))
        }
        if (room.processedRequests.size >= MAX_REQUESTS_PER_MATCH) {
            return HandleResult(listOf(error(player.playerId, "TOO_MANY_REQUESTS", "Tráº­n Ä‘áº¥u cÃ³ quÃ¡ nhiá»u lÆ°á»£t gá»­i.")))
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
                deliveries = listOf(error(player.playerId, "GAME_NOT_PLAYING", "Tráº­n Ä‘áº¥u chÆ°a báº¯t Ä‘áº§u hoáº·c Ä‘Ã£ káº¿t thÃºc.", command.requestId)),
                completedMatch = completedMatch,
                changedRoomId = room.id.takeIf { completedMatch != null }
            )
        }
        if (player.playerId in room.finishedPlayerIds) {
            return HandleResult(listOf(error(
                player.playerId,
                "PLAYER_FINISHED",
                "LÆ°á»£t chÆ¡i cá»§a báº¡n Ä‘Ã£ káº¿t thÃºc.",
                command.requestId
            )))
        }
        val expectedNumber = room.targetFor(player.playerId)
        if (command.number != expectedNumber) {
            val rejected = error(player.playerId, "WRONG_NUMBER", "ChÆ°a Ä‘Ãºng sá»‘, thá»­ láº¡i nhÃ©!", command.requestId)
            room.processedRequests[requestKey] = rejected.message
            room.recordSelection(player.playerId, command, expectedNumber, SelectionResult.REJECTED)
            val previousCombo = room.combos[player.playerId] ?: 0
            room.combos[player.playerId] = 0
            val stateChanged = when (room.gameMode) {
                ProtocolGameMode.TIME_BONUS -> {
                    val deadline = room.deadlinesAtEpochMillis[player.playerId] ?: nowMillis()
                    room.deadlinesAtEpochMillis[player.playerId] = deadline - TIME_BONUS_WRONG_MILLIS
                    if (deadline - TIME_BONUS_WRONG_MILLIS <= nowMillis()) {
                        room.finishedPlayerIds += player.playerId
                    }
                    true
                }
                ProtocolGameMode.SURVIVAL -> {
                    val remainingLives = ((room.lives[player.playerId] ?: SURVIVAL_STARTING_LIVES) - 1).coerceAtLeast(0)
                    room.lives[player.playerId] = remainingLives
                    if (remainingLives == 0) {
                        room.finishedPlayerIds += player.playerId
                        room.forcedWinnerId = room.playerIds().firstOrNull { it != player.playerId }
                    }
                    true
                }
                ProtocolGameMode.COMBO -> previousCombo > 0
                else -> false
            }
            if (!stateChanged) return HandleResult(listOf(rejected), changedRoomId = room.id)
            room.sequence++
            room.finishIfPlayersDone(nowMillis())
            val update = ServerMessage.GameStateUpdated(
                game = room.snapshot(),
                acceptedNumber = command.number,
                selectedByPlayerId = player.playerId,
                selectionAccepted = false
            )
            return HandleResult(
                deliveries = listOf(Delivery(update, room.playerIds()), rejected),
                completedMatch = room.takeCompletedMatch(),
                changedRoomId = room.id
            )
        }

        room.recordSelection(player.playerId, command, expectedNumber, SelectionResult.ACCEPTED)
        val nextCombo = (room.combos[player.playerId] ?: 0) + 1
        room.combos[player.playerId] = nextCombo
        val multiplier = if (room.gameMode == ProtocolGameMode.COMBO) comboMultiplier(nextCombo) else 1
        room.scores[player.playerId] = room.scores.getValue(player.playerId) + SCORE_PER_NUMBER * multiplier
        if (room.gameMode == ProtocolGameMode.TIME_BONUS) {
            room.selectedNumbersByPlayer.getOrPut(player.playerId, ::mutableListOf) += command.number
            room.targetIndexes[player.playerId] = (room.targetIndexes[player.playerId] ?: 0) + 1
            val deadline = room.deadlinesAtEpochMillis[player.playerId] ?: nowMillis()
            room.deadlinesAtEpochMillis[player.playerId] = maxOf(deadline, nowMillis()) + TIME_BONUS_CORRECT_MILLIS
        } else {
            room.selectedNumbers += command.number
            room.playerIds().forEach { id ->
                room.selectedNumbersByPlayer.getOrPut(id, ::mutableListOf) += command.number
                room.targetIndexes[id] = (room.targetIndexes[id] ?: 0) + 1
            }
        }
        val nextIndex = room.targetIndexes[player.playerId] ?: 0
        room.currentTarget = room.targetFor(player.playerId)
        if (room.gameMode == ProtocolGameMode.SPEED_UP && nextIndex < GAME_NUMBER_COUNT) {
            val targetMillis = (SPEED_UP_START_MILLIS - nextIndex * SPEED_UP_DECREMENT_MILLIS)
                .coerceAtLeast(SPEED_UP_MIN_MILLIS)
            room.playerIds().forEach { room.deadlinesAtEpochMillis[it] = nowMillis() + targetMillis }
        }
        room.sequence++
        if (nextIndex >= GAME_NUMBER_COUNT) {
            room.finishedPlayerIds += player.playerId
            room.forcedWinnerId = player.playerId
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
            deliveries = listOf(Delivery(event, room.participantIds())),
            completedMatch = room.takeCompletedMatch(),
            changedRoomId = room.id
        )
    }

    private fun sendEmoji(player: GuestSession, command: ClientMessage.SendEmoji): HandleResult {
        val room = rooms[command.roomId]
            ?: return HandleResult(listOf(error(player.playerId, "ROOM_NOT_FOUND", "KhÃ´ng tÃ¬m tháº¥y phÃ²ng.")))

        if (player.playerId !in room.playerIds() && player.playerId !in room.spectatorIds) {
            return HandleResult(listOf(error(player.playerId, "NOT_IN_ROOM", "Báº¡n khÃ´ng á»Ÿ trong phÃ²ng nÃ y.")))
        }

        val allParticipants = room.participantIds()
        val delivery = Delivery(ServerMessage.EmojiBroadcast(player.playerId, command.emojiId), allParticipants)
        return HandleResult(listOf(delivery))
    }

    private fun createTournamentRoom(tournament: Tournament, match: TournamentMatch): Room {
        val playerOneId = checkNotNull(match.playerOneId)
        val playerTwoId = checkNotNull(match.playerTwoId)
        val roomId = UUID.randomUUID().toString()
        val roundName = if (match.round == 1) "BÃ¡n káº¿t ${match.position}" else "Chung káº¿t"
        val room = Room(
            id = roomId,
            matchId = roomId,
            name = "${tournament.name} â€¢ $roundName",
            hostId = playerOneId,
            password = null,
            gameMode = tournament.gameMode,
            matchType = MatchType.CASUAL,
            guestId = playerTwoId,
            tournamentId = tournament.id,
            tournamentMatchId = match.id,
            tournamentRound = match.round
        )
        room.scores[playerTwoId] = 0
        room.startMatch(nowMillis(), timeAttackMillis)
        rooms[room.id] = room
        match.roomId = room.id
        match.phase = TournamentMatchPhase.PLAYING
        return room
    }

    private suspend fun advanceTournamentAfterMatch(matchId: String): List<Delivery> {
        var snapshotToSave: TournamentSnapshot? = null
        var removedRoomId: String? = null
        var createdRoomId: String? = null
        val deliveries = mutex.withLock {
            val tournament = tournaments.values.firstOrNull { candidate ->
                candidate.phase == TournamentPhase.RUNNING &&
                    candidate.matches.any { it.roomId == matchId && it.phase == TournamentMatchPhase.PLAYING }
            } ?: return@withLock emptyList()
            val tournamentMatch = tournament.matches.first { it.roomId == matchId }
            val room = rooms[matchId] ?: return@withLock emptyList()
            room.snapshot()
            val winnerId = room.winnerId() ?: return@withLock emptyList()

            tournamentMatch.winnerPlayerId = winnerId
            tournamentMatch.phase = TournamentMatchPhase.FINISHED
            removedRoomId = room.id
            rooms.remove(room.id)

            val gameDeliveries = mutableListOf<Delivery>()
            if (tournamentMatch.round == 1) {
                val semifinals = tournament.matches.filter { it.round == 1 }.sortedBy { it.position }
                if (semifinals.all { it.phase == TournamentMatchPhase.FINISHED }) {
                    val final = tournament.matches.single { it.round == 2 }
                    final.playerOneId = semifinals[0].winnerPlayerId
                    final.playerTwoId = semifinals[1].winnerPlayerId
                    val finalRoom = createTournamentRoom(tournament, final)
                    createdRoomId = finalRoom.id
                    gameDeliveries += Delivery(ServerMessage.GameStarted(finalRoom.snapshot()), finalRoom.playerIds())
                }
            } else {
                tournament.phase = TournamentPhase.FINISHED
                tournament.championPlayerId = winnerId
                tournament.finishedAtMillis = nowMillis()
            }

            val snapshot = tournament.snapshot()
            snapshotToSave = snapshot
            listOf(Delivery(ServerMessage.TournamentUpdated(snapshot), tournament.playerIds())) + gameDeliveries
        }
        snapshotToSave?.let { tournamentRepository.save(it) }
        removedRoomId?.let { persistRoom(it) }
        createdRoomId?.let { persistRoom(it) }
        return deliveries + snapshotToSave?.players.orEmpty().flatMap { presenceUpdates(it.playerId) }
    }

    private fun activeTournamentFor(playerId: String): Tournament? = tournaments.values.firstOrNull { tournament ->
        playerId in tournament.playerIds() &&
            tournament.phase in setOf(TournamentPhase.LOBBY, TournamentPhase.RUNNING)
    }

    private fun Tournament.snapshot(): TournamentSnapshot = TournamentSnapshot(
        tournamentId = id,
        name = name,
        hostPlayerId = hostId,
        gameMode = gameMode,
        phase = phase,
        maxPlayers = TOURNAMENT_PLAYER_COUNT,
        players = participants.map { participant ->
            TournamentPlayerSnapshot(
                playerId = participant.playerId,
                displayName = sessionsByPlayerId[participant.playerId]?.displayName ?: participant.displayName,
                isHost = participant.playerId == hostId,
                isOnline = sessionsByPlayerId[participant.playerId]?.let {
                    it.isConnected && it.resumeToken == null
                } == true
            )
        },
        matches = matches.sortedWith(compareBy(TournamentMatch::round, TournamentMatch::position)).map { match ->
            TournamentMatchSnapshot(
                matchId = match.id,
                round = match.round,
                position = match.position,
                playerOneId = match.playerOneId,
                playerTwoId = match.playerTwoId,
                winnerPlayerId = match.winnerPlayerId,
                roomId = match.roomId,
                phase = match.phase
            )
        },
        championPlayerId = championPlayerId,
        createdAtEpochMillis = createdAtMillis,
        startedAtEpochMillis = startedAtMillis,
        finishedAtEpochMillis = finishedAtMillis
    )

    private fun TournamentSnapshot.toTournament(): Tournament = Tournament(
        id = tournamentId,
        name = name,
        hostId = hostPlayerId,
        gameMode = gameMode,
        participants = players.mapTo(mutableListOf()) { TournamentParticipant(it.playerId, it.displayName) },
        matches = matches.mapTo(mutableListOf()) { match ->
            TournamentMatch(
                id = match.matchId,
                round = match.round,
                position = match.position,
                playerOneId = match.playerOneId,
                playerTwoId = match.playerTwoId,
                winnerPlayerId = match.winnerPlayerId,
                roomId = match.roomId,
                phase = match.phase
            )
        },
        phase = phase,
        championPlayerId = championPlayerId,
        createdAtMillis = createdAtEpochMillis,
        startedAtMillis = startedAtEpochMillis,
        finishedAtMillis = finishedAtEpochMillis
    )

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
            guests = extraGuestIds.map(::storedPlayer),
            spectators = spectatorIds.map(::storedPlayer),
            teamIds = teamIds.toMap(),
            passwordSalt = password?.salt?.copyOf(),
            passwordHash = password?.value?.copyOf(),
            gameMode = gameMode,
            matchType = matchType,
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
            selectionEvents = selectionEvents.toList(),
            targetOrder = targetOrder.toList(),
            selectedNumbersByPlayer = selectedNumbersByPlayer.mapValues { it.value.toList() },
            targetIndexes = targetIndexes.toMap(),
            combos = combos.toMap(),
            lives = lives.toMap(),
            deadlinesAtEpochMillis = deadlinesAtEpochMillis.toMap(),
            finishedPlayerIds = finishedPlayerIds.toSet(),
            forcedWinnerId = forcedWinnerId
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
        matchType = matchType,
        guestId = guest?.playerId,
        extraGuestIds = guests.map { it.playerId }.toMutableSet(),
        spectatorIds = spectators.map { it.playerId }.toMutableSet(),
        teamIds = teamIds.toMutableMap(),
        phase = phase,
        numbers = numbers.toList(),
        selectedNumbers = selectedNumbers.toMutableList(),
        currentTarget = currentTarget,
        targetOrder = targetOrder.ifEmpty { (1..GAME_NUMBER_COUNT).toList() },
        selectedNumbersByPlayer = selectedNumbersByPlayer
            .mapValuesTo(mutableMapOf()) { it.value.toMutableList() }
            .also { restored ->
                if (restored.isEmpty()) {
                    restored[host.playerId] = selectedNumbers.toMutableList()
                    guest?.let { restored[it.playerId] = selectedNumbers.toMutableList() }
                }
            },
        targetIndexes = targetIndexes.toMutableMap().also { restored ->
            if (restored.isEmpty()) {
                val index = (currentTarget - 1).coerceIn(0, GAME_NUMBER_COUNT)
                restored[host.playerId] = index
                guest?.let { restored[it.playerId] = index }
            }
        },
        combos = combos.toMutableMap(),
        lives = lives.toMutableMap(),
        deadlinesAtEpochMillis = deadlinesAtEpochMillis.toMutableMap().also { restored ->
            if (restored.isEmpty() && phase == RoomPhase.PLAYING) {
                val fallbackDeadline = when (gameMode) {
                    ProtocolGameMode.TIME_ATTACK -> startedAtEpochMillis?.plus(timeAttackMillis)
                    ProtocolGameMode.TIME_BONUS -> startedAtEpochMillis?.plus(TIME_BONUS_START_MILLIS)
                    ProtocolGameMode.SPEED_UP -> startedAtEpochMillis?.plus(SPEED_UP_START_MILLIS)
                    else -> null
                }
                fallbackDeadline?.let { deadline ->
                    restored[host.playerId] = deadline
                    guest?.let { restored[it.playerId] = deadline }
                }
            }
        },
        finishedPlayerIds = finishedPlayerIds.toMutableSet(),
        forcedWinnerId = forcedWinnerId,
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
                requiresPassword = room.password != null,
                matchType = room.matchType
            )
        }
        .sortedBy { it.name.lowercase() }
        .toList()

    private fun Room.snapshot(): GameSnapshot {
        val metricsByPlayer = selectionMetrics()
        if (phase == RoomPhase.FINISHED && tournamentId != null && winnerId() == null) {
            forcedWinnerId = playerIds().sortedWith(
                compareByDescending<String> { scores[it] ?: 0 }
                    .thenByDescending { metricsByPlayer[it]?.correct ?: 0 }
                    .thenBy { metricsByPlayer[it]?.wrong ?: 0 }
                    .thenBy { metricsByPlayer[it]?.averageReactionMillis ?: Long.MAX_VALUE }
                    .thenBy { it }
            ).firstOrNull()
        }
        return GameSnapshot(
            roomId = id,
            matchId = matchId,
            roomName = name,
            hostId = hostId,
            gameMode = gameMode,
            matchType = matchType,
            phase = phase,
            players = playerIds().mapNotNull { id ->
                sessionsByPlayerId[id]?.let { session ->
                    val metrics = metricsByPlayer[id] ?: SelectionMetrics()
                    val fastestSegment = metrics.fastestSegment
                    val slowestSegment = metrics.slowestSegment
                    PlayerSnapshot(
                        id = id,
                        name = session.displayName,
                        score = scores[id] ?: 0,
                        isReady = id in readyPlayerIds,
                        correctSelections = metrics.correct,
                        wrongSelections = metrics.wrong,
                        averageReactionMillis = metrics.averageReactionMillis,
                        currentTarget = targetFor(id),
                        selectedNumbers = selectedNumbersByPlayer[id]?.toList().orEmpty(),
                        combo = combos[id] ?: 0,
                        lives = lives[id] ?: SURVIVAL_STARTING_LIVES,
                        timeLeftMillis = deadlinesAtEpochMillis[id]
                            ?.let { (it - nowMillis()).coerceAtLeast(0L) }
                            ?: 0L,
                        isFinished = id in finishedPlayerIds,
                        fastestSegmentStart = fastestSegment?.start ?: 0,
                        fastestSegmentEnd = fastestSegment?.end ?: 0,
                        fastestSegmentAverageMillis = fastestSegment?.averageMillis ?: 0,
                        slowestSegmentStart = slowestSegment?.start ?: 0,
                        slowestSegmentEnd = slowestSegment?.end ?: 0,
                        slowestSegmentAverageMillis = slowestSegment?.averageMillis ?: 0,
                        teamId = teamIds[id]
                    )
                }
            },
            spectators = spectatorIds.mapNotNull { id ->
                sessionsByPlayerId[id]?.let { session ->
                    PlayerSnapshot(
                        id = id,
                        name = session.displayName,
                        score = 0
                    )
                }
            },
            numbers = numbers,
            selectedNumbers = selectedNumbers.toList(),
            currentTarget = currentTarget,
            sequence = sequence,
            startedAtEpochMillis = startedAtEpochMillis,
            finishedAtEpochMillis = finishedAtEpochMillis,
            winnerPlayerId = if (phase == RoomPhase.FINISHED) winnerId() else null,
            winnerTeamId = if (phase == RoomPhase.FINISHED) winnerTeamId() else null,
            rematchRequestedPlayerIds = rematchRequestedPlayerIds.toList(),
            rematchExpiresAtEpochMillis = rematchExpiresAtEpochMillis,
            tournamentId = tournamentId,
            tournamentMatchId = tournamentMatchId,
            tournamentRound = tournamentRound
        )
    }

    private fun Room.selectionMetrics(): Map<String, SelectionMetrics> {
        val metrics = playerIds().associateWith { SelectionMetrics() }.toMutableMap()
        val startedAt = startedAtEpochMillis ?: return metrics
        val targetAvailableAtMillis = playerIds().associateWith { startedAt }.toMutableMap()
        selectionEvents.sortedBy(MatchSelectionEvent::sequence).forEach { event ->
            val current = metrics.getOrPut(event.playerId) { SelectionMetrics() }
            when (event.result) {
                SelectionResult.REJECTED -> current.wrong++
                SelectionResult.ACCEPTED -> {
                    current.correct++
                    val rawReactionMillis =
                        (event.occurredAtMillis - (targetAvailableAtMillis[event.playerId] ?: startedAt))
                            .coerceAtLeast(0L)
                    val reactionMillis = if (
                        current.reactionSamples == 0 && rawReactionMillis >= MATCH_COUNTDOWN_MILLIS
                    ) {
                        rawReactionMillis - MATCH_COUNTDOWN_MILLIS
                    } else {
                        rawReactionMillis
                    }
                    current.reactionTimeTotalMillis += reactionMillis
                    current.reactionTimes += reactionMillis
                    current.reactionSamples++
                    if (gameMode == ProtocolGameMode.TIME_BONUS) {
                        targetAvailableAtMillis[event.playerId] = event.occurredAtMillis
                    } else {
                        playerIds().forEach { targetAvailableAtMillis[it] = event.occurredAtMillis }
                    }
                }
            }
        }
        return metrics
    }

    private fun Room.finishIfTimedOut(): Boolean {
        if (phase != RoomPhase.PLAYING || gameMode !in TIMED_GAME_MODES) return false
        val now = nowMillis()
        val expired = playerIds().filter { id ->
            id !in finishedPlayerIds && now >= (deadlinesAtEpochMillis[id] ?: Long.MAX_VALUE)
        }
        if (expired.isEmpty()) return false
        finishedPlayerIds += expired
        if (gameMode == ProtocolGameMode.SPEED_UP && expired.size == 1) {
            forcedWinnerId = playerIds().firstOrNull { it !in expired }
        }
        finishIfPlayersDone(now)
        sequence++
        return phase == RoomPhase.FINISHED
    }

    private fun Room.finishIfPlayersDone(now: Long) {
        if (phase != RoomPhase.PLAYING) return
        val shouldFinish = when (gameMode) {
            ProtocolGameMode.SURVIVAL, ProtocolGameMode.SPEED_UP -> finishedPlayerIds.isNotEmpty()
            ProtocolGameMode.TIME_BONUS, ProtocolGameMode.TIME_ATTACK ->
                finishedPlayerIds.containsAll(playerIds())
            else -> false
        }
        if (shouldFinish) {
            phase = RoomPhase.FINISHED
            finishedAtEpochMillis = now
        }
    }

    private fun Room.takeCompletedMatch(): CompletedMatch? {
        if (phase != RoomPhase.FINISHED || resultQueued) return null
        val startedAt = startedAtEpochMillis ?: return null
        val winnerId = winnerId()
        resultQueued = true
        val winnerTeam = winnerTeamId()
        return CompletedMatch(
            matchId = matchId,
            roomName = name,
            gameMode = gameMode,
            matchType = matchType,
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
                            gameMode == com.hienthai.fastowin.protocol.ProtocolGameMode.TEAM_2V2 -> {
                                when {
                                    winnerTeam == null -> MatchOutcome.DRAW
                                    teamIds[playerId] == winnerTeam -> MatchOutcome.WIN
                                    else -> MatchOutcome.LOSS
                                }
                            }
                            else -> {
                                when {
                                    winnerId == null -> MatchOutcome.DRAW
                                    playerId == winnerId -> MatchOutcome.WIN
                                    else -> MatchOutcome.LOSS
                                }
                            }
                        }
                    )
                }
            },
            events = selectionEvents.toList()
        )
    }

    private fun Room.winnerTeamId(): String? {
        if (gameMode != com.hienthai.fastowin.protocol.ProtocolGameMode.TEAM_2V2) return null
        forcedWinnerId?.let { return teamIds[it] }
        val teamScores = playerIds().groupBy { teamIds[it] ?: "" }
            .mapValues { (_, members) -> members.sumOf { scores[it] ?: 0 } }
        val highestScore = teamScores.values.maxOrNull() ?: 0
        return teamScores.filterValues { it == highestScore }.keys.singleOrNull()
    }

    private fun Room.winnerId(): String? {
        forcedWinnerId?.let { return it }
        if (gameMode == com.hienthai.fastowin.protocol.ProtocolGameMode.TEAM_2V2) return null
        val highestScore = scores.values.maxOrNull() ?: 0
        return scores.filterValues { it == highestScore }.keys.singleOrNull()
    }

    private fun Room.recordSelection(
        playerId: String,
        command: ClientMessage.SelectNumber,
        expectedNumber: Int,
        result: SelectionResult
    ) {
        selectionEvents += MatchSelectionEvent(
            playerId = playerId,
            requestId = command.requestId,
            number = command.number,
            expectedNumber = expectedNumber,
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
        var disconnectedAtMillis: Long? = null,
        var latencyMillis: Long? = null
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
        val matchType: MatchType = MatchType.CASUAL,
        var tournamentId: String? = null,
        var tournamentMatchId: String? = null,
        var tournamentRound: Int? = null,
        var guestId: String? = null,
        val extraGuestIds: MutableSet<String> = mutableSetOf(),
        val spectatorIds: MutableSet<String> = mutableSetOf(),
        val teamIds: MutableMap<String, String> = mutableMapOf(),
        var phase: RoomPhase = RoomPhase.WAITING,
        var numbers: List<Int> = emptyList(),
        val selectedNumbers: MutableList<Int> = mutableListOf(),
        var currentTarget: Int = 1,
        var targetOrder: List<Int> = emptyList(),
        val selectedNumbersByPlayer: MutableMap<String, MutableList<Int>> = mutableMapOf(),
        val targetIndexes: MutableMap<String, Int> = mutableMapOf(),
        val combos: MutableMap<String, Int> = mutableMapOf(),
        val lives: MutableMap<String, Int> = mutableMapOf(),
        val deadlinesAtEpochMillis: MutableMap<String, Long> = mutableMapOf(),
        val finishedPlayerIds: MutableSet<String> = mutableSetOf(),
        var forcedWinnerId: String? = null,
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
        fun playerIds(): Set<String> = setOfNotNull(hostId, guestId) + extraGuestIds
        fun participantIds(): Set<String> = playerIds() + spectatorIds

        fun targetFor(playerId: String): Int {
            val index = targetIndexes[playerId] ?: 0
            return targetOrder.getOrNull(index) ?: (GAME_NUMBER_COUNT + 1)
        }

        fun startMatch(now: Long, legacyTimeAttackMillis: Long) {
            phase = RoomPhase.PLAYING
            numbers = (1..GAME_NUMBER_COUNT).shuffled()
            targetOrder = if (gameMode == ProtocolGameMode.RANDOM_TARGET) {
                (1..GAME_NUMBER_COUNT).shuffled()
            } else {
                (1..GAME_NUMBER_COUNT).toList()
            }
            selectedNumbers.clear()
            currentTarget = targetOrder.first()
            selectedNumbersByPlayer.clear()
            targetIndexes.clear()
            combos.clear()
            lives.clear()
            deadlinesAtEpochMillis.clear()
            finishedPlayerIds.clear()
            forcedWinnerId = null
            playerIds().forEach { id ->
                scores[id] = 0
                selectedNumbersByPlayer[id] = mutableListOf()
                targetIndexes[id] = 0
                combos[id] = 0
                lives[id] = SURVIVAL_STARTING_LIVES
                when (gameMode) {
                    ProtocolGameMode.TIME_BONUS -> deadlinesAtEpochMillis[id] = now + TIME_BONUS_START_MILLIS
                    ProtocolGameMode.SPEED_UP -> deadlinesAtEpochMillis[id] = now + SPEED_UP_START_MILLIS
                    ProtocolGameMode.TIME_ATTACK -> deadlinesAtEpochMillis[id] = now + legacyTimeAttackMillis
                    else -> Unit
                }
            }
            sequence++
            startedAtEpochMillis = now
            finishedAtEpochMillis = null
            resultQueued = false
            readyPlayerIds.clear()
            rematchRequestedPlayerIds.clear()
            rematchExpiresAtEpochMillis = null
            processedRequests.clear()
            selectionEvents.clear()
        }
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

    private data class TournamentParticipant(
        val playerId: String,
        val displayName: String
    )

    private data class TournamentMatch(
        val id: String,
        val round: Int,
        val position: Int,
        var playerOneId: String? = null,
        var playerTwoId: String? = null,
        var winnerPlayerId: String? = null,
        var roomId: String? = null,
        var phase: TournamentMatchPhase = TournamentMatchPhase.PENDING
    )

    private data class Tournament(
        val id: String,
        val name: String,
        val hostId: String,
        val gameMode: ProtocolGameMode,
        val participants: MutableList<TournamentParticipant>,
        val matches: MutableList<TournamentMatch>,
        var phase: TournamentPhase = TournamentPhase.LOBBY,
        var championPlayerId: String? = null,
        val createdAtMillis: Long,
        var startedAtMillis: Long? = null,
        var finishedAtMillis: Long? = null
    ) {
        fun playerIds(): Set<String> = participants.mapTo(linkedSetOf(), TournamentParticipant::playerId)
    }

    private data class TournamentInvitationRecord(
        val id: String,
        val tournamentId: String,
        val inviteeId: String,
        val hostId: String,
        val hostDisplayName: String,
        val tournamentName: String,
        val gameMode: ProtocolGameMode,
        val expiresAtMillis: Long
    ) {
        fun snapshot() = TournamentInvitationSnapshot(
            invitationId = id,
            tournamentId = tournamentId,
            tournamentName = tournamentName,
            hostPlayerId = hostId,
            hostDisplayName = hostDisplayName,
            gameMode = gameMode,
            expiresAtEpochMillis = expiresAtMillis
        )
    }

    private data class MatchmakingEntry(
        val playerId: String,
        val gameMode: com.hienthai.fastowin.protocol.ProtocolGameMode,
        val matchType: MatchType,
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

    private suspend fun createClan(playerId: String, name: String, description: String): List<Delivery> {
        if (name.isBlank() || name.length > 32) return listOf(error(playerId, "INVALID_CLAN_NAME", "Tên clan không hợp lệ."))
        val clanId = clanRepository.createClan(playerId, name, description)
        return if (clanId != null) {
            listOf(Delivery(ServerMessage.ClanActionResult(true, "Tạo clan thành công", "create_clan"), setOf(playerId)))
        } else {
            listOf(error(playerId, "CREATE_CLAN_FAILED", "Tạo clan thất bại. Có thể bạn đã vào một clan khác hoặc tên bị trùng."))
        }
    }

    private suspend fun joinClan(playerId: String, clanId: String): List<Delivery> {
        val success = clanRepository.joinClan(playerId, clanId)
        return if (success) {
            listOf(Delivery(ServerMessage.ClanActionResult(true, "Vào clan thành công", "join_clan"), setOf(playerId)))
        } else {
            listOf(error(playerId, "JOIN_CLAN_FAILED", "Không thể vào clan này."))
        }
    }

    private suspend fun leaveClan(playerId: String): List<Delivery> {
        val success = clanRepository.leaveClan(playerId)
        return if (success) {
            listOf(Delivery(ServerMessage.ClanActionResult(true, "Đã rời clan", "leave_clan"), setOf(playerId)))
        } else {
            listOf(error(playerId, "LEAVE_CLAN_FAILED", "Rời clan thất bại."))
        }
    }

    private suspend fun getClanInfo(playerId: String, clanId: String): List<Delivery> {
        val clan = clanRepository.getClanById(clanId)
        return if (clan != null) {
            listOf(Delivery(ServerMessage.ClanInfoData(clan), setOf(playerId)))
        } else {
            listOf(error(playerId, "CLAN_NOT_FOUND", "Không tìm thấy clan."))
        }
    }

    private suspend fun getClanList(playerId: String, query: String? = null): List<Delivery> {
        val list = clanRepository.getClanList(50, 0, query)
        return listOf(Delivery(ServerMessage.ClanListData(list), setOf(playerId)))
    }

    private suspend fun kickClanMember(playerId: String, clanId: String, memberId: String): List<Delivery> {
        val clan = clanRepository.getClanById(clanId)
            ?: return listOf(error(playerId, "CLAN_NOT_FOUND", "Không tìm thấy bang hội."))
        if (clan.ownerId != playerId) {
            return listOf(error(playerId, "NOT_CLAN_OWNER", "Chỉ đội trưởng mới có thể kick thành viên."))
        }
        val success = clanRepository.kickMember(clanId, playerId, memberId)
        return if (success) {
            getClanInfo(playerId, clanId)
        } else {
            listOf(error(playerId, "KICK_FAILED", "Không thể kick thành viên này."))
        }
    }

    private suspend fun inviteToClan(playerId: String, playerCode: String): List<Delivery> {
        val inviterProfile = playerProfileRepository.findByPlayerId(playerId)
        val clanId = inviterProfile?.clanId
            ?: return listOf(error(playerId, "NOT_IN_CLAN", "Bạn chưa tham gia bang hội nào."))
        
        val clan = clanRepository.getClanById(clanId)
            ?: return listOf(error(playerId, "CLAN_NOT_FOUND", "Không tìm thấy bang hội."))

        val targetPlayer = playerProfileRepository.findByPlayerCode(playerCode)
            ?: return listOf(error(playerId, "PLAYER_NOT_FOUND", "Không tìm thấy người chơi này."))
        
        if (targetPlayer.clanId != null) {
            return listOf(error(playerId, "ALREADY_IN_CLAN", "Người chơi này đã có bang hội."))
        }

        val notification = NotificationSnapshot(
            id = UUID.randomUUID().toString(),
            kind = NotificationKind.CLAN_INVITATION,
            title = "Lời mời vào bang",
            message = "${inviterProfile.displayName} mời bạn vào bang ${clan.name}",
            createdAtEpochMillis = System.currentTimeMillis(),
            destination = NotificationDestination.CLAN,
            actionData = clanId
        )

        notificationRepository.createNotifications(targetPlayer.userId, listOf(notification))
        
        return listOf(
            Delivery(ServerMessage.NotificationsData(notificationRepository.loadNotifications(targetPlayer.userId)), setOf(targetPlayer.userId)),
            error(playerId, "INVITE_SENT", "Đã gửi lời mời.")
        )
    }
    suspend fun getAvatarData(playerId: String): String? = playerProfileRepository.getAvatarData(playerId)


    private suspend fun updateAvatar(playerId: String, base64: String): List<Delivery> {
        val success = playerProfileRepository.updateAvatarData(playerId, base64)
        if (success) {
            return loadProfile(playerId)
        }
        return listOf(error(playerId, "UPLOAD_FAILED", "Không thể tải lên ảnh đại diện."))
    }

    private suspend fun updateClanLogo(playerId: String, clanId: String, logoId: String): List<Delivery> {
        val clan = clanRepository.getClanById(clanId)
            ?: return listOf(error(playerId, "CLAN_NOT_FOUND", "Không tìm thấy bang hội."))
        if (clan.ownerId != playerId) {
            return listOf(error(playerId, "NOT_CLAN_OWNER", "Chỉ đội trưởng mới có thể đổi logo."))
        }
        val success = clanRepository.updateLogoId(clanId, logoId)
        if (success) {
            return getClanInfo(playerId, clanId)
        }
        return listOf(error(playerId, "UPLOAD_FAILED", "Không thể cập nhật logo."))
    }

    private companion object {
        const val MAX_PLAYER_NAME_LENGTH = 32
        const val MAX_ROOM_NAME_LENGTH = 48
        const val SCORE_PER_NUMBER = 10
        const val MAX_REQUEST_ID_LENGTH = 64
        const val MAX_REQUESTS_PER_MATCH = 2_000
        const val MAX_NOTIFICATION_SYNC_BATCH = 20
        const val LEADERBOARD_SIZE = 100
        const val TOURNAMENT_PLAYER_COUNT = 4
        const val MAX_TOURNAMENT_NAME_LENGTH = 48
        const val TOURNAMENT_HISTORY_LIMIT = 10
        const val TOURNAMENT_INVITATION_TTL_MILLIS = 10 * 60 * 1000L
        const val DEFAULT_TIME_ATTACK_MILLIS = 60_000L
        const val TIME_BONUS_START_MILLIS = 30_000L
        const val TIME_BONUS_CORRECT_MILLIS = 2_000L
        const val TIME_BONUS_WRONG_MILLIS = 3_000L
        const val SPEED_UP_START_MILLIS = 5_000L
        const val SPEED_UP_MIN_MILLIS = 1_500L
        const val SPEED_UP_DECREMENT_MILLIS = 70L
        const val SURVIVAL_STARTING_LIVES = 3
        const val REACTION_SEGMENT_SIZE = 10
        const val MATCH_COUNTDOWN_MILLIS = 3_000L
        const val DEFAULT_REMATCH_TIMEOUT_MILLIS = 30_000L
        const val ROOM_RECONNECT_GRACE_MILLIS = 30_000L
        const val IDLE_SESSION_TTL_MILLIS = 5 * 60_000L
        const val ROOM_INVITATION_TTL_MILLIS = 60_000L
        const val DEFAULT_ELO_RATING = 1_000
        const val MATCHMAKING_INITIAL_RANGE = 100
        const val MATCHMAKING_EXPAND_STEP = 50
        const val MATCHMAKING_MAX_RANGE = 300
        const val MATCHMAKING_EXPAND_INTERVAL_MILLIS = 10_000L
        const val RESTORED_GUEST_RESUME_TOKEN = "restored-guest-session"
        val TIMED_GAME_MODES = setOf(
            ProtocolGameMode.TIME_ATTACK,
            ProtocolGameMode.TIME_BONUS,
            ProtocolGameMode.SPEED_UP
        )
        val secureRandom = SecureRandom()

        fun comboMultiplier(combo: Int): Int = when {
            combo >= 20 -> 4
            combo >= 10 -> 3
            combo >= 5 -> 2
            else -> 1
        }
    }

    private data class SelectionMetrics(
        var correct: Int = 0,
        var wrong: Int = 0,
        var reactionTimeTotalMillis: Long = 0,
        var reactionSamples: Int = 0,
        val reactionTimes: MutableList<Long> = mutableListOf()
    ) {
        val averageReactionMillis: Long
            get() = if (reactionSamples == 0) 0L else reactionTimeTotalMillis / reactionSamples

        private val segments: List<ReactionSegment>
            get() = reactionTimes.chunked(REACTION_SEGMENT_SIZE).mapIndexed { index, values ->
                ReactionSegment(
                    start = index * REACTION_SEGMENT_SIZE + 1,
                    end = index * REACTION_SEGMENT_SIZE + values.size,
                    averageMillis = values.average().toLong()
                )
            }

        val fastestSegment: ReactionSegment?
            get() = segments.minByOrNull(ReactionSegment::averageMillis)

        val slowestSegment: ReactionSegment?
            get() = segments.maxByOrNull(ReactionSegment::averageMillis)
    }

    private data class ReactionSegment(val start: Int, val end: Int, val averageMillis: Long)
}
