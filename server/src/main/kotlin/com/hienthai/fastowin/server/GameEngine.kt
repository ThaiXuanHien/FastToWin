package com.hienthai.fastowin.server

import com.hienthai.fastowin.server.maintenance.*
import com.hienthai.fastowin.protocol.MaintenanceState

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ClanDonationStatus
import com.hienthai.fastowin.localization.protocolTextKeyForCode
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.LocalizationService
import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_AVATAR_ID
import com.hienthai.fastowin.protocol.MAX_PROFILE_DISPLAY_NAME_LENGTH
import com.hienthai.fastowin.protocol.PROFILE_AVATAR_IDS
import com.hienthai.fastowin.protocol.GAME_NUMBER_COUNT
import com.hienthai.fastowin.protocol.GameSnapshot
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.FriendsSnapshot
import com.hienthai.fastowin.protocol.FRAME_CATALOG
import com.hienthai.fastowin.protocol.GoldExchangeStatus
import com.hienthai.fastowin.protocol.PlayerSnapshot
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.PushNotificationCategory
import com.hienthai.fastowin.protocol.PushPreferencesSnapshot
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.PublicTournamentQuery
import com.hienthai.fastowin.protocol.PublicTournamentSummary
import com.hienthai.fastowin.protocol.TournamentFeeFilter
import com.hienthai.fastowin.protocol.TournamentVisibility
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.MatchRewardEventSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import com.hienthai.fastowin.protocol.RematchEvent
import com.hienthai.fastowin.protocol.RewardedAdAvailability
import com.hienthai.fastowin.protocol.RewardedAdBonusStatus
import com.hienthai.fastowin.protocol.NotificationDestination
import com.hienthai.fastowin.protocol.NotificationKind
import com.hienthai.fastowin.protocol.NotificationSnapshot
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.protocol.RoomSummary
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.protocol.StorePurchaseRequest
import com.hienthai.fastowin.protocol.StorePurchaseStatus
import com.hienthai.fastowin.protocol.TournamentHubSnapshot
import com.hienthai.fastowin.protocol.TournamentInvitationSnapshot
import com.hienthai.fastowin.protocol.TournamentMatchPhase
import com.hienthai.fastowin.protocol.TournamentMatchSnapshot
import com.hienthai.fastowin.protocol.TournamentPhase
import com.hienthai.fastowin.protocol.TournamentPlayerSnapshot
import com.hienthai.fastowin.protocol.TournamentSnapshot
import com.hienthai.fastowin.protocol.TournamentScheduleSnapshot
import com.hienthai.fastowin.protocol.PROTOCOL_VERSION
import com.hienthai.fastowin.protocol.TITLE_CATALOG
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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

class InvalidResumeTokenException : RuntimeException()

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
    private val storePurchaseVerifier: StorePurchaseVerifier = RejectingStorePurchaseVerifier,
    private val playQuotaRepository: PlayQuotaRepository = InMemoryPlayQuotaRepository(),
    private val rewardedAdVerifier: RewardedAdVerifier = RejectingRewardedAdVerifier,
    private val rewardedAdAvailability: RewardedAdAvailability = RewardedAdAvailability.UNAVAILABLE,
    private val storeSandboxEnabled: Boolean = false,
    private val timeAttackMillis: Long = DEFAULT_TIME_ATTACK_MILLIS,
    private val rematchTimeoutMillis: Long = DEFAULT_REMATCH_TIMEOUT_MILLIS,
    val storePurchaseService: StorePurchaseService = StorePurchaseService(playerProfileRepository, storePurchaseVerifier),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val eventRewardSource: com.hienthai.fastowin.server.events.EventRewardSource = com.hienthai.fastowin.server.events.NoRewardEventSource,
    val avatarService: com.hienthai.fastowin.server.avatars.AvatarService =
        com.hienthai.fastowin.server.avatars.AvatarService(
            com.hienthai.fastowin.server.avatars.ProfileAvatarRepository(playerProfileRepository))
) {
    constructor(nowMillis: () -> Long) : this(nowMillis = nowMillis, eventRewardSource = com.hienthai.fastowin.server.events.NoRewardEventSource)

    private val mutex = Mutex()
    private val restorationMutex = Mutex()
    private val persistenceMutex = Mutex()
    private val matchmakingSweepMutex = Mutex()
    private val sessionsByPlayerId = mutableMapOf<String, GuestSession>()
    private val rooms = mutableMapOf<String, Room>()
    private val pendingReconnectResultsByPlayerId = mutableMapOf<String, PendingReconnectResult>()
    private val pendingAccountTokensByPlayerId = mutableMapOf<String, PendingAccountToken>()
    private val roomInvitations = mutableMapOf<String, RoomInvitationRecord>()
    private val matchmakingEntries = mutableMapOf<String, MatchmakingEntry>()
    private val tournaments = mutableMapOf<String, Tournament>()
    private val tournamentInvitations = mutableMapOf<String, TournamentInvitationRecord>()
    private var activeRoomsRestored = false
    @Volatile private var maintenancePolicy = MaintenancePolicy(MaintenanceState.ACTIVE)
    private val pendingMaintenanceRooms = mutableMapOf<String, StoredActiveRoom>()
    private val pendingMaintenanceTournaments = mutableMapOf<String, TournamentSnapshot>()
    private val legacyRestoredAccounts = mutableSetOf<String>()
    private val pendingCompletedMatches = mutableMapOf<String, CompletedMatch>()
    private val pendingRoomRemovals = mutableSetOf<String>()
    private val settlementsInFlight = mutableSetOf<String>()
    private val preparedStarts = mutableMapOf<String, PreparedMatchStart>()
    private val startsInFlight = mutableSetOf<String>()
    private val pendingMatchmakingDeliveries = mutableListOf<Delivery>()

    suspend fun activeMaintenanceMatchCount(): Int = mutex.withLock {
        (rooms.values.filter { it.phase == RoomPhase.PLAYING && !it.maintenanceAborted }.map { it.matchId } + pendingCompletedMatches.keys +
            pendingTournamentProgressions.values.flatMap { it.rooms.map { room -> room.matchId } } +
            preparedStarts.values.mapNotNull { it.candidate?.matchId }).toSet().size
    }

    suspend fun admitsNewIntent(): Boolean = mutex.withLock { !maintenancePolicy.blocksNewIntent }
    /** Atomic admission observation for non-suspending HTTP/gift transaction boundaries. */
    fun newIntentAllowed(): Boolean = !maintenancePolicy.blocksNewIntent
    fun gameConnectionsClosed(): Boolean = maintenancePolicy.isFullyClosed

    @Volatile private var desiredMaintenancePolicy = maintenancePolicy
    @Volatile private var maintenanceWritesPending = false
    private val maintenancePublicationLock = Any()

    /** Publication never waits for engine/storage work. The monitor protects only these short policy assignments. */
    fun publishMaintenancePolicy(policy: MaintenancePolicy) = synchronized(maintenancePublicationLock) {
        desiredMaintenancePolicy = policy
        if (policy.blocksNewIntent || !maintenanceWritesPending) maintenancePolicy = policy
    }

    suspend fun applyMaintenancePolicy(policy: MaintenancePolicy): MaintenanceApplication {
        publishMaintenancePolicy(policy)
        return reconcileMaintenancePolicy()
    }

    /** Durable reconciliation is independent of policy observation and never grants early close permission. */
    suspend fun reconcileMaintenancePolicy(): MaintenanceApplication {
        restoreActiveRooms()
        val result = persistenceMutex.withLock {
            val abandonedScheduledStarts = mutex.withLock {
                synchronized(maintenancePublicationLock) {
                    if (maintenancePolicy.isFullyClosed) maintenanceWritesPending = true
                }
                if (maintenancePolicy.isFullyClosed) pendingTournamentProgressions.filterValues {
                    it.paused.phase == TournamentPhase.LOBBY && it.paused.schedule != null
                }.toMap() else emptyMap()
            }
            for ((id, staged) in abandonedScheduledStarts) withContext(NonCancellable) {
                tournamentRepository.commitProgression(TournamentProgression(staged.paused, emptyList(),
                    removedRoomIds = staged.rooms.map { it.id }), activeRoomRepository, playerProfileRepository)
                mutex.withLock { pendingTournamentProgressions.remove(id) }
            }
            val abandonedMatchmaking = mutableListOf<PreparedMatchStart>()
            val change = mutex.withLock {
                synchronized(maintenancePublicationLock) {
                    // Fence reopening before staging any abort; no I/O or engine-lock acquisition occurs under this monitor.
                    if (maintenancePolicy.isFullyClosed) maintenanceWritesPending = true
                }
                if (maintenancePolicy.blocksNewIntent) matchmakingEntries.clear()
                val matchmakingStopped = if (maintenancePolicy.isFullyClosed) preparedStarts.values
                    .filter { it.fromMatchmaking && it.candidate == null }.toList().flatMap { start ->
                        abandonedMatchmaking += start
                        discardUnpublishedMatchmaking(start)
                    } else emptyList()
                if (maintenancePolicy.isFullyClosed) preparedStarts.values.forEach { pending ->
                    pending.candidate?.let { frozen ->
                        if (rooms[pending.roomId]?.maintenanceAborted != true) rooms[pending.roomId] = frozen.toRoom()
                    }
                }
                val aborted = if (maintenancePolicy.isFullyClosed) rooms.values.filter { it.phase != RoomPhase.FINISHED && !it.maintenanceAborted } else emptyList()
                aborted.forEach { room ->
                    room.maintenanceAborted = true; room.resultQueued = true; room.phase = RoomPhase.FINISHED
                    room.forcedWinnerId = null; room.finishedAtEpochMillis = nowMillis()
                    room.rematchRequestedPlayerIds.clear(); room.rematchExpiresAtEpochMillis = null; room.sequence++
                    pendingMaintenanceRooms[room.id] = room.toStoredRoom()
                }
                tournaments.values.filter { maintenancePolicy.isFullyClosed && it.phase in setOf(TournamentPhase.LOBBY, TournamentPhase.RUNNING) &&
                    it.id !in pendingTournamentProgressions && it.matches.none { match -> rooms[match.roomId]?.matchId in pendingCompletedMatches } }.forEach { tournament ->
                    pendingMaintenanceTournaments[tournament.id] = tournament.cancelledSnapshot().copy(prizePool = 0)
                }
                MaintenanceApplication(matchmakingStopped + aborted.map { room -> Delivery(ServerMessage.RoomClosed(room.id,
                    LocalizationService(com.hienthai.fastowin.localization.AppLanguage.VIETNAMESE).text(TextKey.MaintenanceDefaultMessage),
                    messageKey = TextKey.MaintenanceDefaultMessage.name, code = "MAINTENANCE"), room.participantIds()) }, pendingMaintenanceRooms.keys.toSet())
            }
            for (start in abandonedMatchmaking) withContext(NonCancellable) {
                // Preparation may still finish later; its owner repeats this idempotent refund on invalid admission.
                runCatching { playQuotaRepository.releaseMatch(start.playerIds, start.matchId, emptySet()) }
            }
            for ((id, room) in pendingMaintenanceRooms.toMap()) {
                activeRoomRepository.save(room); pendingMaintenanceRooms.remove(id)
                val abandoned = mutex.withLock { preparedStarts.remove(id) }
                if (abandoned != null) {
                    if (abandoned.candidate != null) playQuotaRepository.finalizeMatch(abandoned.playerIds, abandoned.matchId)
                    else playQuotaRepository.releaseMatch(abandoned.playerIds, abandoned.matchId, emptySet())
                }
            }
            for ((id, tournament) in pendingMaintenanceTournaments.toMap()) {
                tournamentRepository.cancelForMaintenance(tournament, playerProfileRepository)
                mutex.withLock { tournaments[id] = tournament.toTournament() }
                pendingMaintenanceTournaments.remove(id)
            }
            synchronized(maintenancePublicationLock) {
                maintenanceWritesPending = false
                maintenancePolicy = desiredMaintenancePolicy
            }
            change
        }
        val resumed = resumePendingTournamentRounds()
        return result.copy(deliveries = result.deliveries + resumed,
            closeSockets = maintenancePolicy.isFullyClosed && !maintenanceWritesPending && activeMaintenanceMatchCount() == 0)
    }
    private fun maintenanceError(playerId: String) = Delivery(ServerMessage.Error(code = "MAINTENANCE",
        message = LocalizationService(com.hienthai.fastowin.localization.AppLanguage.VIETNAMESE).text(TextKey.MaintenanceDefaultMessage),
        messageKey = TextKey.MaintenanceDefaultMessage.name), setOf(playerId))

    suspend fun connectGuest(displayName: String, resumeToken: String?): ConnectedPlayer {
        restoreActiveRooms()
        val safeName = displayName.trim().take(MAX_PLAYER_NAME_LENGTH)
        require(safeName.isNotEmpty()) { "Player name must not be blank." }
        val identity = identityRepository.resolveGuest(safeName, resumeToken, nowMillis())

        return connectIdentity(identity.playerId, identity.displayName, identity.resumeToken)
    }

    suspend fun connectAccount(account: AuthenticatedAccount, resumeToken: String? = null, protocolVersion: Int = PROTOCOL_VERSION): ConnectedPlayer {
        restoreActiveRooms()
        val legacyRestored = mutex.withLock {
            val existing = sessionsByPlayerId[account.userId.toString()]
            val expectedToken = existing?.accountResumeToken
                ?: pendingAccountTokensByPlayerId[account.userId.toString()]?.token
            val legacy = expectedToken == null && account.userId.toString() in legacyRestoredAccounts
            if (resumeToken != null && expectedToken != resumeToken && !legacy) {
                throw InvalidResumeTokenException()
            }
            legacy
        }
        val appearance = runCatching {
            playerProfileRepository.findAppearance(account.userId.toString())
        }.getOrNull()
        return connectIdentity(
            identityPlayerId = account.userId.toString(),
            identityDisplayName = account.displayName,
            resumeToken = null,
            accountResumeToken = if (legacyRestored) newAccountResumeToken() else resumeToken ?: newAccountResumeToken(),
            avatarId = appearance?.avatarId,
            frameId = appearance?.frameId ?: "frame_default",
            protocolVersion = protocolVersion
        )
    }

    private suspend fun connectIdentity(
        identityPlayerId: String,
        identityDisplayName: String,
        resumeToken: String?,
        accountResumeToken: String? = null,
        avatarId: String? = null,
        frameId: String = "frame_default",
        protocolVersion: Int = PROTOCOL_VERSION
    ): ConnectedPlayer {
        val connected = mutex.withLock {
            if (maintenancePolicy.blocksNewIntent) {
                val existing = sessionsByPlayerId[identityPlayerId]
                val matchingResume = if (accountResumeToken != null) existing?.accountResumeToken == accountResumeToken ||
                    (identityPlayerId in legacyRestoredAccounts && existing?.accountResumeToken == null)
                    else resumeToken != null && existing?.resumeToken == resumeToken
                if (!maintenancePolicy.allowsExistingMatch || !matchingResume || roomFor(identityPlayerId)?.phase != RoomPhase.PLAYING) {
                    throw MaintenanceAdmissionException()
                }
            }
            val session = sessionsByPlayerId[identityPlayerId]?.also { existing ->
                existing.displayName = identityDisplayName
                existing.resumeToken = resumeToken
                existing.accountResumeToken = accountResumeToken ?: existing.accountResumeToken
                existing.avatarId = avatarId
                existing.frameId = frameId
            } ?: GuestSession(
                playerId = identityPlayerId,
                resumeToken = resumeToken,
                displayName = identityDisplayName,
                accountResumeToken = accountResumeToken,
                avatarId = avatarId,
                frameId = frameId
            ).also { created ->
                sessionsByPlayerId[created.playerId] = created
            }
            session.isConnected = true
            session.protocolVersion = protocolVersion
            legacyRestoredAccounts.remove(identityPlayerId)
            session.disconnectedAtMillis = null

            ConnectedPlayer(
                playerId = session.playerId,
                resumeToken = session.accountResumeToken ?: session.resumeToken,
                currentGame = roomFor(session.playerId)?.snapshot()
                    ?: pendingReconnectResultsByPlayerId.remove(session.playerId)?.snapshot.also {
                        pendingAccountTokensByPlayerId.remove(session.playerId)
                    }
            )
        }
        connected.currentGame?.roomId?.let { persistRoom(it) }
        return connected
    }

    private fun newAccountResumeToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun restoreActiveRooms() {
        if (activeRoomsRestored) return
        restorationMutex.withLock {
            if (activeRoomsRestored) return@withLock
            val storedRooms = activeRoomRepository.loadAll()
            playQuotaRepository.recoverPreparedMatches(storedRooms.flatMap { stored ->
                listOfNotNull(stored.matchId.takeIf { stored.startedAtEpochMillis != null }, stored.pendingCompletedMatch?.matchId)
            }.toSet())
            val restoredAtMillis = nowMillis()
            val storedInvitations = notificationRepository.loadActiveRoomInvitations(restoredAtMillis)
            val storedTournaments = tournamentRepository.loadActive()
            mutex.withLock {
                if (activeRoomsRestored) return@withLock
                storedRooms.forEach { stored ->
                    rooms[stored.roomId] = stored.toRoom()
                    if (!stored.maintenanceAborted) stored.pendingCompletedMatch?.let { pendingCompletedMatches[it.matchId] = it }
                    (listOfNotNull(stored.host, stored.guest) + stored.guests + stored.spectators).forEach { player ->
                        if (player.isAccount && player.accountResumeToken == null) legacyRestoredAccounts += player.playerId
                        sessionsByPlayerId.putIfAbsent(
                            player.playerId,
                            GuestSession(
                                playerId = player.playerId,
                                resumeToken = if (player.isAccount) null else RESTORED_GUEST_RESUME_TOKEN,
                                displayName = player.displayName,
                                avatarId = player.avatarId,
                                frameId = player.frameId,
                                accountResumeToken = player.accountResumeToken,
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
                    // Paused rounds may have no room snapshots left; their verified account roster remains authoritative.
                    snapshot.players.forEach { player ->
                        if (player.playerId !in sessionsByPlayerId) {
                            sessionsByPlayerId[player.playerId] = GuestSession(playerId = player.playerId, resumeToken = null,
                                displayName = player.displayName, isConnected = false, disconnectedAtMillis = restoredAtMillis)
                            legacyRestoredAccounts += player.playerId
                        }
                    }
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
                // Repository reads and lock acquisition may outlive a scheduled activation.
                val activationClassificationMillis = nowMillis()
                tournaments.values.forEach { tournament ->
                    val unactivated = rooms.values.filter { it.tournamentId == tournament.id && it.activationPending &&
                        it.phase == RoomPhase.PLAYING && it.selectionEvents.isEmpty() && (it.startedAtEpochMillis ?: Long.MAX_VALUE) <= activationClassificationMillis }
                    if (unactivated.isNotEmpty()) {
                        val ids = unactivated.map { it.id }.toSet()
                        val snapshot = tournament.snapshot()
                        val paused = snapshot.copy(matches = snapshot.matches.map {
                            if (it.roomId in ids) it.copy(roomId = null, phase = TournamentMatchPhase.PENDING) else it
                        })
                        pendingTournamentProgressions[tournament.id] = StagedProgression(
                            TournamentProgression(snapshot, unactivated.map { it.toStoredRoom() }), paused, unactivated)
                        ids.forEach { rooms.remove(it) }
                    }
                }
                activeRoomsRestored = true
            }
        }
    }

    private suspend fun persistRoom(roomId: String) {
        persistenceMutex.withLock {
            // The lobby is deliberately still visible in memory while this exact candidate awaits durable retry.
            if (mutex.withLock { preparedStarts[roomId]?.let { it.candidate != null || it.fromMatchmaking } == true }) return@withLock
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

    private fun prepareStart(room: Room, matchId: String, requesterId: String, fromMatchmaking: Boolean = false): PreparedMatchStart =
        PreparedMatchStart(room.id, matchId, room.matchId, room.sequence, room.activePlayerIds().toSet(), requesterId, fromMatchmaking)
            .also { preparedStarts[room.id] = it }

    /** Engine mutex required. Never discard a frozen payload whose durable write may have succeeded. */
    private fun discardUnpublishedMatchmaking(start: PreparedMatchStart): List<Delivery> {
        if (!start.fromMatchmaking || start.candidate != null || preparedStarts[start.roomId] !== start) return emptyList()
        preparedStarts.remove(start.roomId)
        val room = rooms[start.roomId]?.takeIf { it.matchId == start.previousMatchId && it.startedAtEpochMillis == null }
        if (room != null) rooms.remove(start.roomId)
        return listOf(Delivery(ServerMessage.MatchmakingStatus(isSearching = false,
            gameMode = room?.gameMode, matchType = room?.matchType ?: MatchType.RANKED), start.playerIds))
    }

    private fun startError(playerId: String) = Delivery(ServerMessage.Error(
        code = "MATCH_START_UNAVAILABLE", message = legacyFallback("Máy chủ đang bận. Vui lòng thử lại."),
        messageKey = TextKey.ServerUnavailable.name), setOf(playerId))

    /** The event callback is pool-free and never suspends for the engine mutex. */
    private suspend fun performPreparedStart(start: PreparedMatchStart): List<Delivery> {
        if (!mutex.withLock { preparedStarts[start.roomId] === start && startsInFlight.add(start.roomId) })
            return listOf(startError(start.requesterId))
        try {
            val consumption = start.consumption ?: playQuotaRepository.prepareMatch(start.playerIds, start.matchId, nowMillis())
                .also { start.consumption = it }
            if (consumption.status == PlayQuotaConsumptionStatus.EXHAUSTED) {
                if (start.fromMatchmaking) {
                    val stopped = mutex.withLock { discardUnpublishedMatchmaking(start) }
                    return stopped + quotaRejectedDeliveries(start.playerIds, consumption)
                }
                val rejected = mutex.withLock {
                    val room = rooms[start.roomId]?.apply { readyPlayerIds.clear(); rematchRequestedPlayerIds.clear(); rematchExpiresAtEpochMillis = null; sequence++ }
                    preparedStarts.remove(start.roomId)
                    room?.let { Delivery(if (it.phase == RoomPhase.FINISHED) ServerMessage.RematchStatus(it.snapshot(), RematchEvent.CANCELLED)
                        else ServerMessage.RoomUpdated(it.snapshot()), start.playerIds) }
                }
                persistRoom(start.roomId)
                return quotaRejectedDeliveries(start.playerIds, consumption) + listOfNotNull(rejected)
            }
            return persistenceMutex.withLock {
                if (start.candidate == null) {
                    var candidate: StoredActiveRoom? = null
                    repeat(20) {
                        if (candidate == null) {
                            candidate = eventRewardSource.withStartCatalog { catalog ->
                                if (!mutex.tryLock()) return@withStartCatalog null
                                try {
                                    val room = rooms[start.roomId] ?: error("Start room disappeared")
                                    check(preparedStarts[room.id] === start && room.matchId == start.previousMatchId &&
                                        room.sequence == start.sequence && room.activePlayerIds() == start.playerIds &&
                                        start.playerIds.all { sessionsByPlayerId[it]?.isConnected == true }) { "Start admission changed" }
                                    check(!maintenancePolicy.blocksNewIntent) { "Maintenance admission closed" }
                                    val detached = room.toStoredRoom().toRoom()
                                    detached.matchId = start.matchId
                                    val actualStart = nowMillis()
                                    detached.startMatch(actualStart, timeAttackMillis, catalog.snapshot(actualStart, room.matchType))
                                    detached.toStoredRoom()
                                } finally { mutex.unlock() }
                            }
                            if (candidate == null) delay(10) // event transaction and pooled connection have already closed
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    mutex.withLock {
                        // Source commit/close runs outside the engine mutex. Recheck the same admission
                        // before retaining its payload so cancellation in that gap cannot revive a room.
                        val room = rooms[start.roomId] ?: error("Start room disappeared")
                        check(preparedStarts[start.roomId] === start && room.matchId == start.previousMatchId &&
                            room.sequence == start.sequence && room.activePlayerIds() == start.playerIds &&
                            start.playerIds.all { sessionsByPlayerId[it]?.isConnected == true } &&
                            !maintenancePolicy.blocksNewIntent) { "Start admission changed" }
                        start.candidate = candidate ?: error("Engine remained busy")
                    }
                }
                val frozen = checkNotNull(start.candidate)
                check(mutex.withLock { preparedStarts[start.roomId] === start }) { "Start was invalidated" }
                // A write failure is ambiguous. Keep payload, reservation and fence for retry/recovery.
                activeRoomRepository.save(frozen)
                // Receipt cleanup failure cannot undo a durable start; recovery also checks snapshots/results.
                runCatching { playQuotaRepository.finalizeMatch(start.playerIds, start.matchId) }
                mutex.withLock {
                    check(preparedStarts[start.roomId] === start)
                    val published = frozen.toRoom()
                    rooms[start.roomId] = published
                    preparedStarts.remove(start.roomId)
                    if (maintenancePolicy.isFullyClosed) {
                        // Reconciliation owns the durable abort; no gameplay is advertised while closed.
                        listOf(maintenanceError(start.requesterId))
                    } else listOf(Delivery(ServerMessage.GameStarted(published.snapshot()), start.playerIds)) +
                        quotaDataDeliveries(consumption.quotas)
                }
            }
        } catch (failure: Throwable) {
            var stopped = emptyList<Delivery>()
            if (start.candidate == null) withContext(NonCancellable) {
                // No durable start write was attempted. A failed refund remains reserved for startup recovery.
                runCatching { playQuotaRepository.releaseMatch(start.playerIds, start.matchId, emptySet()) }
                mutex.withLock {
                    stopped = discardUnpublishedMatchmaking(start)
                    if (!start.fromMatchmaking && preparedStarts[start.roomId] === start) preparedStarts.remove(start.roomId)
                    if (failure is CancellationException) {
                        pendingMatchmakingDeliveries += stopped + if (stopped.isNotEmpty()) start.playerIds.map(::startError) else emptyList()
                    }
                }
            }
            if (failure is CancellationException) throw failure
            if (start.fromMatchmaking && start.candidate == null) return stopped +
                if (stopped.isNotEmpty()) start.playerIds.map { if (maintenancePolicy.blocksNewIntent) maintenanceError(it) else startError(it) } else emptyList()
            return listOf(if (maintenancePolicy.blocksNewIntent) maintenanceError(start.requesterId) else startError(start.requesterId))
        } finally {
            withContext(NonCancellable) { mutex.withLock { startsInFlight.remove(start.roomId) } }
        }
    }

    suspend fun handle(playerId: String, message: ClientMessage): List<Delivery> {
        restoreActiveRooms()
        if (message is ClientMessage.JoinMatchmaking || message is ClientMessage.CreateRoom) mutex.withLock {
            // A new intent supersedes deferred notifications from a cancelled request coroutine.
            val remaining = pendingMatchmakingDeliveries.mapNotNull { delivery ->
                delivery.recipients?.minus(playerId)?.takeIf { it.isNotEmpty() }?.let { delivery.copy(recipients = it) }
            }
            pendingMatchmakingDeliveries.clear(); pendingMatchmakingDeliveries.addAll(remaining)
        }
        // Admission for economic/profile/social work is short; accepted atomic work may finish.
        if (mutex.withLock { !message.allowedDuringMaintenance(maintenancePolicy) }) return listOf(maintenanceError(playerId))
        if (message is ClientMessage.GetProfile) return loadProfile(playerId)
        if (message is ClientMessage.GetPlayQuota) return loadPlayQuota(playerId)
        if (message is ClientMessage.ClaimRewardedAdBonus) return claimRewardedAdBonus(playerId, message)
        if (message is ClientMessage.AcknowledgeSeasonReward) {
            return acknowledgeSeasonReward(playerId, message.seasonNumber)
        }
        if (message is ClientMessage.GetWalletHistory) return loadWalletHistory(playerId)
        if (message is ClientMessage.GetGemStoreCatalog) return loadGemStoreCatalog(playerId)
        if (message is ClientMessage.ExchangeGemsForGold) return exchangeGemsForGold(playerId, message)
        if (message is ClientMessage.VerifyStorePurchase) return verifyStorePurchase(playerId, message)
        if (message is ClientMessage.ClaimDailyCheckIn) return claimDailyCheckIn(playerId)
        if (message is ClientMessage.ClaimMissionReward) return claimMissionReward(playerId, message.missionCode)
        if (message is ClientMessage.GetFriendProfile) return loadFriendProfile(playerId, message.friendUserId)
        if (message is ClientMessage.GetMatchDetail) return loadMatchDetail(playerId, message.matchId)
        if (message is ClientMessage.UpdateProfile) return updateProfile(playerId, message)
        if (message is ClientMessage.UpdatePushPreferences) {
            return updatePushPreferences(playerId, message)
        }
        if (message is ClientMessage.EquipCosmetics) return equipCosmetics(playerId, message)
        if (message is ClientMessage.GetLeaderboard) return loadLeaderboard(playerId)
        if (message is ClientMessage.GetFriends) return loadFriends(playerId)
        if (message is ClientMessage.GetRoomInvitations) return loadRoomInvitations(playerId)
        if (message is ClientMessage.GetNotifications) return loadNotifications(playerId)
        if (message is ClientMessage.GetTournamentHub) return loadTournamentHub(playerId)
        if (message is ClientMessage.GetPublicTournaments) return publicTournaments(playerId, message.query)
        if (message is ClientMessage.CreateTournament) return createTournament(playerId, message)
        if (message is ClientMessage.JoinPublicTournament) return joinPublicTournament(playerId, message.tournamentId)
        if (message is ClientMessage.InviteTournamentPlayer) return inviteTournamentPlayer(playerId, message)
        if (message is ClientMessage.RespondTournamentInvitation) {
            return respondTournamentInvitation(playerId, message)
        }
        if (message is ClientMessage.StartTournament) return startTournament(playerId, message.tournamentId)
        if (message is ClientMessage.CheckInTournament) return checkInTournament(playerId, message.tournamentId)
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
        if (message is ClientMessage.RespondClanJoinRequest) return respondClanJoinRequest(playerId, message)
        if (message is ClientMessage.LeaveClan) return leaveClan(playerId)
        if (message is ClientMessage.GetClanInfo) return getClanInfo(playerId, message.clanId)
        if (message is ClientMessage.GetClanList) return getClanList(playerId, message.query)
        if (message is ClientMessage.InviteToClan) return inviteToClan(playerId, message.playerCode)
        if (message is ClientMessage.KickClanMember) return kickClanMember(playerId, message.clanId, message.memberId)
        if (message is ClientMessage.ClaimClanQuestReward) return claimClanQuestReward(playerId, message.clanId)
        if (message is ClientMessage.DonateToClan) return donateToClan(playerId, message)
        if (message is ClientMessage.UpdateAvatar) return updateAvatar(playerId, message.base64Data)
        if (message is ClientMessage.UpdateClanLogo) return updateClanLogo(playerId, message.clanId, message.logoId)
        if (message is ClientMessage.BuyCosmetic) return rejectLegacyCosmeticPurchase(playerId)
        if (message is ClientMessage.EquipCosmetic) return equipCosmetic(playerId, message.cosmeticId)
        if (message is ClientMessage.JoinMatchmaking) return joinMatchmaking(playerId, message)
        if (message is ClientMessage.CancelMatchmaking) return cancelMatchmaking(playerId)
        if (message is ClientMessage.CreateRoom) {
            requireOnlineAccount(playerId)?.let { return listOf(it) }
            rejectIfQuotaExhausted(playerId)?.let { return it }
            validateModeAccess(playerId, message.gameMode)?.let { return listOf(it) }
        }
        if (message is ClientMessage.JoinRoom) {
            requireOnlineAccount(playerId)?.let { return listOf(it) }
            if (!message.asSpectator) rejectIfQuotaExhausted(playerId)?.let { return it }
            val roomMode = mutex.withLock { rooms[message.roomId]?.gameMode }
            if (roomMode != null) validateModeAccess(playerId, roomMode)?.let { return listOf(it) }
        }
        if (
            message is ClientMessage.SetReady ||
            message is ClientMessage.RequestRematch ||
            message is ClientMessage.RespondRematch
        ) {
            requireOnlineAccount(playerId)?.let { return listOf(it) }
        }
        val preparedRetry = mutex.withLock { preparedStarts.values.any { playerId in it.playerIds } }
        if (message is ClientMessage.SetReady && message.ready && !preparedRetry) {
            rejectIfQuotaExhausted(playerId)?.let { return it }
        }
        if (
            !preparedRetry && (message is ClientMessage.RequestRematch ||
            (message is ClientMessage.RespondRematch && message.accept))
        ) {
            rejectIfQuotaExhausted(playerId)?.let { return it }
        }
        val result = mutex.withLock {
            // Recheck after asynchronous profile/quota work, at the actual transition boundary.
            if (!message.allowedDuringMaintenance(maintenancePolicy)) return@withLock HandleResult(listOf(maintenanceError(playerId)))
            val player = sessionsByPlayerId[playerId]
                ?: return@withLock HandleResult(
                    listOf(error(playerId, "SESSION_NOT_FOUND", legacyFallback("Phiên chơi không còn hợp lệ.")))
                )

            val fencedRoomId = when (message) {
                is ClientMessage.SetReady -> message.roomId
                is ClientMessage.RequestRematch -> message.roomId
                is ClientMessage.RespondRematch -> message.roomId
                is ClientMessage.LeaveRoom -> message.roomId
                is ClientMessage.JoinRoom -> message.roomId
                is ClientMessage.KickPlayer -> message.roomId
                is ClientMessage.SelectNumber -> message.roomId
                else -> null
            }
            preparedStarts[fencedRoomId]?.let { pending ->
                return@withLock if (playerId in pending.playerIds && pending.roomId !in startsInFlight &&
                    ((message is ClientMessage.SetReady && message.ready) || message is ClientMessage.RequestRematch ||
                        (message is ClientMessage.RespondRematch && message.accept))) {
                    HandleResult(emptyList(), preparedStart = pending)
                } else HandleResult(listOf(startError(playerId)))
            }

            when (message) {
                is ClientMessage.ConnectGuest -> HandleResult(listOf(
                    error(playerId, "ALREADY_CONNECTED", legacyFallback("Phiên WebSocket đã được xác thực."))
                ))
                is ClientMessage.ConnectAccount -> HandleResult(listOf(
                    error(playerId, "ALREADY_CONNECTED", legacyFallback("Phiên WebSocket đã được xác thực."))
                ))

                ClientMessage.ListRooms -> HandleResult(
                    listOf(Delivery(ServerMessage.RoomList(publicRooms()), setOf(playerId)))
                )
                ClientMessage.GetProfile -> HandleResult(emptyList())
                ClientMessage.GetPlayQuota -> HandleResult(emptyList())
                is ClientMessage.AcknowledgeSeasonReward -> HandleResult(emptyList())
                ClientMessage.GetWalletHistory -> HandleResult(emptyList())
                ClientMessage.GetGemStoreCatalog -> HandleResult(emptyList())
                is ClientMessage.ExchangeGemsForGold -> HandleResult(emptyList())
                is ClientMessage.VerifyStorePurchase -> HandleResult(emptyList())
                is ClientMessage.ClaimRewardedAdBonus -> HandleResult(emptyList())
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
                is ClientMessage.GetPublicTournaments -> HandleResult(emptyList())
                is ClientMessage.CreateTournament -> HandleResult(emptyList())
                is ClientMessage.JoinPublicTournament -> HandleResult(emptyList())
                is ClientMessage.InviteTournamentPlayer -> HandleResult(emptyList())
                is ClientMessage.RespondTournamentInvitation -> HandleResult(emptyList())
                is ClientMessage.StartTournament -> HandleResult(emptyList())
                is ClientMessage.CheckInTournament -> HandleResult(emptyList())
                is ClientMessage.LeaveTournament -> HandleResult(emptyList())
                is ClientMessage.SyncNotifications -> HandleResult(emptyList())
                is ClientMessage.MarkNotificationsRead -> HandleResult(emptyList())
                is ClientMessage.DismissNotifications -> HandleResult(emptyList())
                is ClientMessage.UpdateAvatar -> HandleResult(emptyList())
                is ClientMessage.UpdateClanLogo -> HandleResult(emptyList())
                is ClientMessage.BuyCosmetic -> HandleResult(emptyList())
                is ClientMessage.EquipCosmetic -> HandleResult(emptyList())
                is ClientMessage.MeasureLatency -> HandleResult(listOf(
                    Delivery(ServerMessage.LatencyPong(message.clientSentAtEpochMillis), setOf(playerId))
                ))
                is ClientMessage.UpdateLatency -> {
                    player.latencyMillis = message.latencyMillis
                    HandleResult(emptyList())
                }
                is ClientMessage.UpdateFcmToken -> {
                    playerProfileRepository.updateFcmToken(
                        playerId = playerId,
                        token = message.token.trim().take(4096),
                        languageTag = resolveNotificationLanguage(message.languageTag).languageTag
                    )
                    HandleResult(emptyList())
                }
                is ClientMessage.UpdatePushPreferences -> HandleResult(emptyList())
                is ClientMessage.JoinMatchmaking -> HandleResult(emptyList())
                ClientMessage.CancelMatchmaking -> HandleResult(emptyList())
                is ClientMessage.GetClanList -> HandleResult(emptyList())
                is ClientMessage.CreateClan -> HandleResult(emptyList())
                is ClientMessage.GetClanInfo -> HandleResult(emptyList())
                is ClientMessage.JoinClan -> HandleResult(emptyList())
                is ClientMessage.RespondClanJoinRequest -> HandleResult(emptyList())
                is ClientMessage.LeaveClan -> HandleResult(emptyList())
                is ClientMessage.InviteToClan -> HandleResult(emptyList())
                is ClientMessage.KickClanMember -> HandleResult(emptyList())
                is ClientMessage.ClaimClanQuestReward -> HandleResult(emptyList())
                is ClientMessage.DonateToClan -> HandleResult(emptyList())
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
                is ClientMessage.LeaveRoom -> leaveRoom(player, message)
                is ClientMessage.SetReady -> setReady(player, message)
                is ClientMessage.KickPlayer -> kickPlayer(player, message)
                is ClientMessage.RequestRematch -> respondRematch(player, message.roomId, accept = true)
                is ClientMessage.RespondRematch -> respondRematch(player, message.roomId, message.accept)
                is ClientMessage.SelectNumber -> selectNumber(player, message)
                is ClientMessage.SendEmoji -> sendEmoji(player, message)
            }
        }
        result.preparedStart?.let { return performPreparedStart(it) }
        val tournamentDeliveries = result.completedMatch?.let { settleCompletedMatch(it) }.orEmpty()
        result.changedRoomId?.let { persistRoom(it) }
        val presenceChanged = message is ClientMessage.CreateRoom ||
            message is ClientMessage.JoinRoom || message is ClientMessage.LeaveRoom
        val affectedPlayers = if (presenceChanged) {
            (result.deliveries.flatMap { it.recipients.orEmpty() } + playerId).toSet()
        } else emptySet()
        return result.deliveries + tournamentDeliveries + affectedPlayers.flatMap { presenceUpdates(it) }
    }

    private suspend fun loadTournamentHub(playerId: String, isHistoryRefresh: Boolean = false): List<Delivery> {
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
                .map { it.snapshot(now) }
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
                ),
                isHistoryRefresh = isHistoryRefresh
            ),
            setOf(playerId)
        ))
    }

    private suspend fun persistTournament(tournamentId: String) {
        persistenceMutex.withLock {
            val latest = mutex.withLock { tournaments[tournamentId]?.snapshot() }
            latest?.let { tournamentRepository.save(it) }
        }
    }

    private suspend fun publicTournaments(playerId: String, query: PublicTournamentQuery): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val nameQuery = query.nameQuery.trim()
        val summaries = mutex.withLock {
            tournaments.values.asSequence()
                .filter { it.visibility == TournamentVisibility.PUBLIC && it.phase == TournamentPhase.LOBBY }
                .filter { it.participants.size < it.maxPlayers }
                .filter { nameQuery.isEmpty() || it.name.contains(nameQuery, ignoreCase = true) }
                .filter { query.gameMode == null || it.gameMode == query.gameMode }
                .filter { query.maxPlayers == null || it.maxPlayers == query.maxPlayers }
                .filter {
                    when (query.fee) {
                        TournamentFeeFilter.ALL -> true
                        TournamentFeeFilter.FREE -> it.entryFee == 0
                        TournamentFeeFilter.PAID -> it.entryFee > 0
                    }
                }
                .map { tournament ->
                    PublicTournamentSummary(
                        tournamentId = tournament.id,
                        name = tournament.name,
                        hostPlayerId = tournament.hostId,
                        hostDisplayName = sessionsByPlayerId[tournament.hostId]?.displayName
                            ?: tournament.participants.first().displayName,
                        gameMode = tournament.gameMode,
                        playerCount = tournament.participants.size,
                        maxPlayers = tournament.maxPlayers,
                        entryFee = tournament.entryFee,
                        prizePool = tournament.prizePool,
                        createdAtEpochMillis = tournament.createdAtMillis,
                        schedule = tournament.schedule,
                        serverTimeEpochMillis = nowMillis()
                    )
                }
                .sortedWith(compareBy<PublicTournamentSummary> { it.maxPlayers - it.playerCount }
                    .thenByDescending { it.createdAtEpochMillis }
                    .thenBy { it.tournamentId })
                .take(50)
                .toList()
        }
        return listOf(Delivery(ServerMessage.PublicTournamentsData(summaries), setOf(playerId)))
    }

    private fun publicTournamentsInvalidated(): Delivery = Delivery(
        ServerMessage.PublicTournamentsInvalidated,
        sessionsByPlayerId.values.filter { it.isConnected && it.resumeToken == null }
            .mapTo(mutableSetOf()) { it.playerId }
    )

    private suspend fun createTournament(
        playerId: String,
        command: ClientMessage.CreateTournament
    ): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        validateModeAccess(playerId, command.gameMode)?.let { return listOf(it) }
        val safeName = command.name.trim().take(MAX_TOURNAMENT_NAME_LENGTH)
        if (safeName.length < 3) {
            return listOf(error(playerId, "INVALID_TOURNAMENT_NAME", legacyFallback("Tên giải cần có ít nhất 3 ký tự.")))
        }
        if (command.maxPlayers !in SUPPORTED_TOURNAMENT_PLAYER_COUNTS) {
            return listOf(error(playerId, "INVALID_TOURNAMENT_SIZE", legacyFallback("Giải đấu chỉ hỗ trợ 4, 8 hoặc 16 người.")))
        }
        var created = false
        val tournamentId = UUID.randomUUID().toString()
        val deliveries = persistenceMutex.withLock { mutex.withLock {
            if (maintenancePolicy.blocksNewIntent) return@withLock listOf(maintenanceError(playerId))
            val host = sessionsByPlayerId[playerId]
                ?.takeIf { it.isConnected && it.resumeToken == null }
                ?: return@withLock listOf(error(playerId, "SESSION_NOT_FOUND", legacyFallback("Phiên chơi không còn hợp lệ.")))
            val schedule = command.schedule?.let { request ->
                if (host.protocolVersion < 44) return@withLock listOf(scheduleError(playerId, "TOURNAMENT_CLIENT_UPDATE_REQUIRED"))
                val now = nowMillis()
                if (request.startsAtEpochMillis < now + 900_000L || request.startsAtEpochMillis > now + 31_536_000_000L ||
                    runCatching { java.time.ZoneId.of(request.timeZoneId) }.isFailure) {
                    return@withLock listOf(scheduleError(playerId, "INVALID_TOURNAMENT_SCHEDULE"))
                }
                TournamentScheduleSnapshot(request.startsAtEpochMillis, request.timeZoneId,
                    request.startsAtEpochMillis - 900_000L, request.startsAtEpochMillis - 900_000L)
            }
            if (activeTournamentFor(playerId) != null) {
                return@withLock listOf(error(playerId, "TOURNAMENT_ALREADY_ACTIVE", legacyFallback("Bạn đang tham gia một giải khác.")))
            }
            if (roomFor(playerId) != null || playerId in matchmakingEntries) {
                return@withLock listOf(error(playerId, "PLAYER_BUSY", legacyFallback("Hãy rời phòng hoặc hủy ghép trận trước khi tạo giải.")))
            }
            val tournament = Tournament(
                id = tournamentId,
                name = safeName,
                hostId = playerId,
                gameMode = command.gameMode,
                visibility = command.visibility,
                maxPlayers = command.maxPlayers,
                entryFee = command.entryFee,
                prizePool = 0,
                participants = mutableListOf(TournamentParticipant(playerId, host.displayName)),
                matches = createTournamentMatches(command.maxPlayers),
                createdAtMillis = nowMillis(),
                schedule = schedule
            )
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                val result = try {
                    tournamentRepository.admitPlayer(tournament.snapshot(), playerId, playerProfileRepository)
                } catch (saveError: TournamentAdmissionSaveException) {
                    return@withContext listOf(error(playerId, "TOURNAMENT_SAVE_FAILED", legacyFallback("Chưa thể lưu lượt tham gia giải.")))
                }
                when (result.walletStatus) {
                    WalletMutationStatus.APPLIED, WalletMutationStatus.DUPLICATE -> Unit
                    WalletMutationStatus.INSUFFICIENT_FUNDS -> return@withContext listOf(
                        error(playerId, "NOT_ENOUGH_GOLD", legacyFallback("Bạn không đủ Vàng để tạo giải đấu."))
                    )
                    WalletMutationStatus.PLAYER_NOT_FOUND -> return@withContext listOf(
                        error(playerId, "PROFILE_NOT_FOUND", legacyFallback("Không tìm thấy hồ sơ tài sản."))
                    )
                }
                val snapshot = checkNotNull(result.tournament)
                tournament.prizePool = snapshot.prizePool
                tournaments[tournament.id] = tournament
                created = true
                listOf(
                    Delivery(ServerMessage.TournamentUpdated(snapshot), setOf(playerId)),
                    Delivery(
                        ServerMessage.TournamentNotice(
                            message = legacyFallback("Đã tạo giải ${command.maxPlayers} người."),
                            messageKey = TextKey.TournamentCreatedNotice.name,
                            messageArgs = mapOf("players" to command.maxPlayers.toString()),
                            code = "TOURNAMENT_CREATED"
                        ),
                        setOf(playerId)
                    )
                ) + if (tournament.visibility == TournamentVisibility.PUBLIC) listOf(publicTournamentsInvalidated()) else emptyList()
            }
        } }
        return deliveries + if (created) loadProfile(playerId) else emptyList()
    }

    private suspend fun inviteTournamentPlayer(
        playerId: String,
        command: ClientMessage.InviteTournamentPlayer
    ): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        if (friendRepository.isBlockedEitherWay(playerId, command.friendPlayerId)) {
            return listOf(error(playerId, "INTERACTION_BLOCKED", legacyFallback("Không thể mời người chơi này vào giải.")))
        }
        if (!friendRepository.areFriends(playerId, command.friendPlayerId)) {
            return listOf(error(playerId, "NOT_FRIENDS", legacyFallback("Người chơi này chưa phải bạn bè.")))
        }
        return mutex.withLock {
            val tournament = tournaments[command.tournamentId]
                ?.takeIf { it.hostId == playerId && it.phase == TournamentPhase.LOBBY }
                ?: return@withLock listOf(error(playerId, "TOURNAMENT_NOT_INVITABLE", legacyFallback("Giải không còn nhận lời mời.")))
            if (tournament.participants.size >= tournament.maxPlayers) {
                return@withLock listOf(
                    error(playerId, "TOURNAMENT_FULL", legacyFallback("Giải đã đủ ${tournament.maxPlayers} người."))
                )
            }
            if (command.friendPlayerId in tournament.playerIds()) {
                return@withLock listOf(error(playerId, "FRIEND_ALREADY_JOINED_TOURNAMENT", legacyFallback("Người chơi đã ở trong giải.")))
            }
            val friendId = command.friendPlayerId
            if (activeTournamentFor(friendId) != null || roomFor(friendId) != null) {
                return@withLock listOf(error(playerId, "FRIEND_BUSY", legacyFallback("Bạn bè đang bận ở phòng hoặc giải khác.")))
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
                maxPlayers = tournament.maxPlayers,
                entryFee = tournament.entryFee,
                schedule = tournament.schedule,
                expiresAtMillis = nowMillis() + TOURNAMENT_INVITATION_TTL_MILLIS
            )
            tournamentInvitations[invitation.id] = invitation
            
            // Send Push Notification
            val pushTarget = playerProfileRepository.findPushTarget(
                friendId,
                PushNotificationCategory.TOURNAMENT_INVITATIONS
            )
            if (pushTarget != null) {
                sendPushNotification(
                    playerId = friendId,
                    target = pushTarget,
                    content = LocalizedPushContent(
                        titleKey = TextKey.PushTournamentInvitationTitle,
                        bodyKey = TextKey.PushTournamentInvitationMessage,
                        bodyArguments = mapOf(
                            "player" to invitation.hostDisplayName,
                            "tournament" to invitation.tournamentName
                        )
                    ),
                    destinationPath = "/tournament"
                )
            }
            
            listOf(
                Delivery(ServerMessage.TournamentInvitation(invitation.snapshot(nowMillis())), setOf(friendId)),
                Delivery(ServerMessage.TournamentNotice(
                    message = legacyFallback("Đã gửi lời mời tham gia giải."),
                    messageKey = TextKey.TournamentInvitationSentNotice.name,
                    code = "TOURNAMENT_INVITATION_SENT"
                ), setOf(playerId))
            )
        }
    }

    private suspend fun respondTournamentInvitation(
        playerId: String,
        command: ClientMessage.RespondTournamentInvitation
    ): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val deliveries = persistenceMutex.withLock {
            mutex.withLock {
                val invitation = tournamentInvitations[command.invitationId]
                    ?.takeIf { it.inviteeId == playerId && it.expiresAtMillis > nowMillis() }
                    ?: return@withLock listOf(error(playerId, "TOURNAMENT_INVITATION_EXPIRED", legacyFallback("Lời mời giải đấu đã hết hạn.")))
                if (!command.accept) {
                    tournamentInvitations.remove(command.invitationId)
                    return@withLock listOf(
                        Delivery(ServerMessage.TournamentNotice(
                            legacyFallback("Đã từ chối lời mời giải đấu."),
                            TextKey.TournamentInvitationDeclinedNotice.name,
                            code = "TOURNAMENT_INVITATION_DECLINED"
                        ), setOf(playerId)),
                        Delivery(ServerMessage.TournamentNotice(
                            legacyFallback("Một người bạn đã từ chối lời mời giải đấu."),
                            TextKey.TournamentInvitationDeclinedByFriendNotice.name,
                            code = "TOURNAMENT_INVITATION_DECLINED_BY_FRIEND"
                        ), setOf(invitation.hostId))
                    )
                }
                val tournament = tournaments[invitation.tournamentId]
                    ?.takeIf { it.phase == TournamentPhase.LOBBY }
                    ?: return@withLock listOf(error(playerId, "TOURNAMENT_NOT_FOUND", legacyFallback("Giải đấu không còn tồn tại.")))
                val admission = admitTournamentPlayerAndPersistLocked(
                    playerId, tournament, TournamentAdmissionSource.INVITATION
                )
                if (admission.any { it.message is ServerMessage.TournamentUpdated }) {
                    tournamentInvitations.remove(command.invitationId)
                }
                admission
            }
        }
        return admissionProfileDeliveries(playerId, deliveries)
    }

    private suspend fun joinPublicTournament(playerId: String, tournamentId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val deliveries = persistenceMutex.withLock {
            mutex.withLock {
                val tournament = tournaments[tournamentId]
                    ?: return@withLock listOf(error(playerId, "TOURNAMENT_NOT_FOUND", legacyFallback("Giải đấu không còn tồn tại.")))
                admitTournamentPlayerAndPersistLocked(playerId, tournament, TournamentAdmissionSource.PUBLIC_DISCOVERY)
            }
        }
        return admissionProfileDeliveries(playerId, deliveries)
    }

    private suspend fun admissionProfileDeliveries(playerId: String, deliveries: List<Delivery>): List<Delivery> {
        val snapshot = deliveries.map(Delivery::message)
            .filterIsInstance<ServerMessage.TournamentUpdated>().singleOrNull()?.tournament
        return deliveries + if (snapshot != null) loadProfile(playerId) else emptyList()
    }

    private enum class TournamentAdmissionSource { INVITATION, PUBLIC_DISCOVERY }

    /** Caller holds [persistenceMutex] and [mutex] through repository commit and memory publication. */
    private suspend fun admitTournamentPlayerAndPersistLocked(
        playerId: String,
        tournament: Tournament,
        source: TournamentAdmissionSource
    ): List<Delivery> {
        if (maintenancePolicy.blocksNewIntent) return listOf(maintenanceError(playerId))
        if (tournament.schedule != null) {
            if ((sessionsByPlayerId[playerId]?.protocolVersion ?: 0) < 44) return listOf(scheduleError(playerId, "TOURNAMENT_CLIENT_UPDATE_REQUIRED"))
            if (nowMillis() >= tournament.schedule.registrationClosesAtEpochMillis) return listOf(scheduleError(playerId, "TOURNAMENT_REGISTRATION_CLOSED"))
        }
        if (source == TournamentAdmissionSource.PUBLIC_DISCOVERY && tournament.visibility != TournamentVisibility.PUBLIC) {
            return listOf(error(playerId, "TOURNAMENT_NOT_PUBLIC", legacyFallback("Giải này không còn được công khai.")))
        }
        when (tournament.phase) {
            TournamentPhase.CANCELLED -> return listOf(
                error(playerId, "TOURNAMENT_CANCELLED", legacyFallback("Giải đấu đã bị hủy."))
            )
            TournamentPhase.RUNNING, TournamentPhase.FINISHED -> return listOf(
                error(playerId, "TOURNAMENT_ALREADY_STARTED", legacyFallback("Giải đấu đã bắt đầu hoặc kết thúc."))
            )
            TournamentPhase.LOBBY -> Unit
        }
        if (playerId in tournament.playerIds()) {
            return listOf(error(playerId, "PLAYER_ALREADY_JOINED", legacyFallback("Bạn đã tham gia giải này.")))
        }
        if (tournament.participants.size >= tournament.maxPlayers) {
            return listOf(error(playerId, "TOURNAMENT_FULL", legacyFallback("Giải đã đủ ${tournament.maxPlayers} người.")))
        }
        val session = sessionsByPlayerId[playerId]
            ?.takeIf { it.isConnected && it.resumeToken == null }
            ?: return listOf(error(playerId, "SESSION_NOT_FOUND", legacyFallback("Phiên chơi không còn hợp lệ.")))
        if (activeTournamentFor(playerId) != null || roomFor(playerId) != null || playerId in matchmakingEntries) {
            return listOf(error(playerId, "PLAYER_BUSY", legacyFallback("Hãy rời phòng hoặc giải hiện tại trước.")))
        }
        currentCoroutineContext().ensureActive()
        return withContext(NonCancellable) {
            val candidate = tournament.snapshot().let { snapshot ->
                snapshot.copy(players = snapshot.players + TournamentPlayerSnapshot(
                    playerId, session.displayName, isOnline = true
                ))
            }
            val result = try {
                tournamentRepository.admitPlayer(candidate, playerId, playerProfileRepository)
            } catch (saveError: TournamentAdmissionSaveException) {
                return@withContext listOf(error(playerId, "TOURNAMENT_SAVE_FAILED", legacyFallback("Chưa thể lưu lượt tham gia giải.")))
            }
            when (result.walletStatus) {
                WalletMutationStatus.APPLIED, WalletMutationStatus.DUPLICATE -> Unit
                WalletMutationStatus.INSUFFICIENT_FUNDS -> return@withContext listOf(
                    error(playerId, "NOT_ENOUGH_GOLD", legacyFallback("Bạn không đủ Vàng để tham gia giải này."))
                )
                WalletMutationStatus.PLAYER_NOT_FOUND -> return@withContext listOf(
                    error(playerId, "PROFILE_NOT_FOUND", legacyFallback("Không tìm thấy hồ sơ tài sản."))
                )
            }
            val snapshot = checkNotNull(result.tournament)
            tournament.participants += TournamentParticipant(playerId, session.displayName)
            if (tournament.schedule != null && tournament.recordedPlayers.none { it.playerId == playerId }) {
                tournament.recordedPlayers += TournamentPlayerSnapshot(playerId, session.displayName)
            }
            tournament.prizePool = snapshot.prizePool
            listOf(
                Delivery(ServerMessage.TournamentUpdated(snapshot), tournament.playerIds()),
                Delivery(ServerMessage.TournamentNotice(
                    message = legacyFallback("Đã tham gia giải ${tournament.name}."),
                    messageKey = TextKey.TournamentJoinedNotice.name,
                    messageArgs = mapOf("tournament" to tournament.name),
                    code = "TOURNAMENT_JOINED"
                ), setOf(playerId))
            ) + if (tournament.visibility == TournamentVisibility.PUBLIC) listOf(publicTournamentsInvalidated()) else emptyList()
        }
    }

    private suspend fun startTournament(playerId: String, tournamentId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        var snapshotToSave: TournamentSnapshot? = null
        var roomIdsToSave = emptyList<String>()
        val deliveries = mutex.withLock {
            if (maintenancePolicy.blocksNewIntent) return@withLock listOf(maintenanceError(playerId))
            val tournament = tournaments[tournamentId]
                ?.takeIf { it.hostId == playerId && it.phase == TournamentPhase.LOBBY }
                ?: return@withLock listOf(error(playerId, "TOURNAMENT_NOT_STARTABLE", legacyFallback("Bạn không thể bắt đầu giải này.")))
            if (tournament.schedule != null) return@withLock listOf(scheduleError(playerId, "TOURNAMENT_AUTOMATIC_START"))
            if (tournament.participants.size != tournament.maxPlayers) {
                return@withLock listOf(
                    error(playerId, "TOURNAMENT_NOT_FULL", legacyFallback("Cần đủ ${tournament.maxPlayers} người để bắt đầu."))
                )
            }
            val unavailable = tournament.playerIds().firstOrNull { participantId ->
                sessionsByPlayerId[participantId]?.let { !it.isConnected || it.resumeToken != null } != false ||
                    roomFor(participantId) != null || participantId in matchmakingEntries
            }
            if (unavailable != null) {
                return@withLock listOf(error(playerId, "TOURNAMENT_PLAYER_UNAVAILABLE", legacyFallback("Tất cả người chơi phải online và không ở phòng khác.")))
            }

            tournament.phase = TournamentPhase.RUNNING
            tournament.startedAtMillis = nowMillis()
            val players = tournament.participants.map(TournamentParticipant::playerId)
            val openingPairs = (0 until players.size / 2).map { index ->
                players[index] to players[players.lastIndex - index]
            }
            val gameDeliveries = mutableListOf<Delivery>()
            tournament.matches.filter { it.round == 1 }.sortedBy { it.position }
                .zip(openingPairs)
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
            listOf(Delivery(ServerMessage.TournamentUpdated(snapshot), tournament.playerIds())) +
                gameDeliveries +
                if (tournament.visibility == TournamentVisibility.PUBLIC) listOf(publicTournamentsInvalidated()) else emptyList()
        }
        snapshotToSave?.let { persistTournament(it.tournamentId) }
        roomIdsToSave.forEach { persistRoom(it) }
        return deliveries + snapshotToSave?.players.orEmpty().flatMap { presenceUpdates(it.playerId) }
    }

    private fun scheduleError(playerId: String, code: String): Delivery {
        val key = when (code) {
            "INVALID_TOURNAMENT_SCHEDULE" -> TextKey.InvalidTournamentSchedule
            "TOURNAMENT_CLIENT_UPDATE_REQUIRED" -> TextKey.TournamentClientUpdateRequired
            "TOURNAMENT_REGISTRATION_CLOSED" -> TextKey.TournamentRegistrationClosed
            "TOURNAMENT_AUTOMATIC_START" -> TextKey.TournamentAutomaticStart
            "TOURNAMENT_CHECK_IN_CLOSED" -> TextKey.TournamentCheckInClosed
            else -> TextKey.TournamentScheduleNotFound
        }
        return Delivery(ServerMessage.Error(code, LocalizationService(com.hienthai.fastowin.localization.AppLanguage.VIETNAMESE).text(key),
            messageKey = key.name), setOf(playerId))
    }

    private suspend fun checkInTournament(playerId: String, tournamentId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        return persistenceMutex.withLock { mutex.withLock {
            if (maintenancePolicy.blocksNewIntent) return@withLock listOf(maintenanceError(playerId))
            val tournament = tournaments[tournamentId]?.takeIf { playerId in it.playerIds() }
                ?: return@withLock listOf(scheduleError(playerId, "TOURNAMENT_NOT_FOUND"))
            if ((sessionsByPlayerId[playerId]?.protocolVersion ?: 0) < 44) {
                return@withLock listOf(scheduleError(playerId, "TOURNAMENT_CLIENT_UPDATE_REQUIRED"))
            }
            val schedule = tournament.schedule
            val now = nowMillis()
            if (tournament.phase != TournamentPhase.LOBBY || schedule == null ||
                now < schedule.checkInOpensAtEpochMillis || now >= schedule.startsAtEpochMillis) {
                return@withLock listOf(scheduleError(playerId, "TOURNAMENT_CHECK_IN_CLOSED"))
            }
            val candidate = tournament.snapshot().let { snapshot -> snapshot.copy(players = snapshot.players.map {
                if (it.playerId == playerId) it.copy(isCheckedIn = true) else it
            }) }
            withContext(NonCancellable) {
                tournamentRepository.save(candidate)
                tournaments[tournamentId] = candidate.toTournament()
                listOf(Delivery(ServerMessage.TournamentUpdated(candidate), tournament.playerIds()))
            }
        } }
    }

    private fun scheduledRosterAvailable(tournament: Tournament): Boolean =
        tournament.participants.size == tournament.maxPlayers && tournament.participants.all { participant ->
            participant.isCheckedIn && sessionsByPlayerId[participant.playerId]?.let {
                it.isConnected && it.resumeToken == null && it.protocolVersion >= 44
            } == true && roomFor(participant.playerId) == null && participant.playerId !in matchmakingEntries
        }

    private fun Tournament.cancelledSnapshot(): TournamentSnapshot = snapshot().let { snapshot ->
        snapshot.copy(phase = TournamentPhase.CANCELLED, championPlayerId = null, finishedAtEpochMillis = nowMillis(),
            players = if (schedule == null) snapshot.players else (snapshot.players + snapshot.recordedPlayers).distinctBy { it.playerId })
    }

    /** Scheduler and request mutations share serialization; no catch-up starts before complete restoration. */
    suspend fun advanceScheduledTournaments(): List<Delivery> {
        restoreActiveRooms()
        return persistenceMutex.withLock {
            val ids = mutex.withLock {
                if (maintenancePolicy.blocksNewIntent) emptyList() else tournaments.values.filter {
                    it.phase == TournamentPhase.LOBBY && it.schedule?.startsAtEpochMillis?.let { start -> nowMillis() >= start } == true
                }.map { it.id }
            }
            val deliveries = mutableListOf<Delivery>()
            var tickFailure: Exception? = null
            for (id in ids) {
                try {
                    val staged = mutex.withLock {
                        val tournament = tournaments[id] ?: return@withLock null
                        if (maintenancePolicy.blocksNewIntent || tournament.phase != TournamentPhase.LOBBY) return@withLock null
                        if (!scheduledRosterAvailable(tournament)) return@withLock null
                        pendingTournamentProgressions.getOrPut(id) {
                            val candidate = tournament.snapshot().toTournament()
                            candidate.phase = TournamentPhase.RUNNING
                            candidate.startedAtMillis = nowMillis()
                            val players = candidate.participants.map { it.playerId }
                            val generated = candidate.matches.filter { it.round == 1 }.sortedBy { it.position }.mapIndexed { index, match ->
                                match.playerOneId = players[index]
                                match.playerTwoId = players[players.lastIndex - index]
                                createTournamentRoom(candidate, match, publish = false)
                            }
                            StagedProgression(TournamentProgression(candidate.snapshot(), generated.map { it.toStoredRoom() }), tournament.snapshot(), generated)
                        }
                    }
                    if (staged != null) {
                        deliveries += withContext(NonCancellable) { commitStagedProgression(id, staged) }
                    } else {
                        val cancelled = mutex.withLock {
                            val tournament = tournaments[id]
                            if (maintenancePolicy.blocksNewIntent || tournament?.phase != TournamentPhase.LOBBY) null else tournament.cancelledSnapshot()
                        } ?: continue
                        withContext(NonCancellable) {
                            // A timed activation may have committed but failed its publication deadline on a prior tick.
                            pendingTournamentProgressions[id]?.let { previous ->
                                tournamentRepository.commitProgression(TournamentProgression(previous.paused, emptyList(),
                                    removedRoomIds = previous.rooms.map { it.id }), activeRoomRepository, playerProfileRepository)
                            }
                            tournamentRepository.cancelWithRefund(cancelled, playerProfileRepository)
                            val (updates, departedPlayers) = mutex.withLock {
                                val currentPlayers = checkNotNull(tournaments[id]).playerIds()
                                pendingTournamentProgressions.remove(id)
                                tournamentInvitations.entries.removeAll { it.value.tournamentId == id }
                                publishProgression(id, cancelled, emptyList(), null, currentPlayers) to
                                    (cancelled.players.map { it.playerId }.toSet() - currentPlayers)
                            }
                            deliveries += updates
                            // Historical updates must not clear another tournament on supported older clients.
                            departedPlayers.forEach { deliveries += loadTournamentHub(it, isHistoryRefresh = true) }
                            cancelled.players.forEach { deliveries += loadProfile(it.playerId, isBackgroundRefresh = true) }
                        }
                    }
                    deliveries += mutex.withLock {
                        if (tournaments[id]?.visibility == TournamentVisibility.PUBLIC) listOf(publicTournamentsInvalidated()) else emptyList()
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    org.slf4j.LoggerFactory.getLogger("ScheduledTournaments").error("Scheduled tournament $id failed; will retry", failure)
                    tickFailure = failure
                }
            }
            // Preserve already acknowledged deliveries if another schedule failed later in this tick.
            if (deliveries.isEmpty()) tickFailure?.let { throw it }
            deliveries
        }
    }

    private suspend fun leaveScheduledTournament(playerId: String, tournamentId: String): List<Delivery>? = persistenceMutex.withLock {
        val deliveries = mutex.withLock {
            val tournament = tournaments[tournamentId]?.takeIf { it.schedule != null } ?: return@withLock null
            if (playerId !in tournament.playerIds()) return@withLock listOf(scheduleError(playerId, "TOURNAMENT_NOT_FOUND"))
            if (tournament.phase != TournamentPhase.LOBBY) return@withLock listOf(scheduleError(playerId, "TOURNAMENT_CHECK_IN_CLOSED"))
            val hostCancellation = tournament.hostId == playerId && nowMillis() < checkNotNull(tournament.schedule).startsAtEpochMillis
            if (!hostCancellation && nowMillis() >= checkNotNull(tournament.schedule).registrationClosesAtEpochMillis) {
                return@withLock listOf(scheduleError(playerId, "TOURNAMENT_REGISTRATION_CLOSED"))
            }
            val candidate = if (hostCancellation) tournament.cancelledSnapshot() else tournament.snapshot().let {
                it.copy(players = it.players.filter { player -> player.playerId != playerId })
            }
            withContext(NonCancellable) {
                if (hostCancellation) tournamentRepository.cancelWithRefund(candidate, playerProfileRepository) else tournamentRepository.save(candidate)
                tournaments[tournamentId] = candidate.toTournament()
                tournamentInvitations.entries.removeAll { it.value.tournamentId == tournamentId && (hostCancellation || it.value.inviteeId == playerId) }
                listOf(Delivery(ServerMessage.TournamentUpdated(candidate), tournament.playerIds())) +
                    if (tournament.visibility == TournamentVisibility.PUBLIC) listOf(publicTournamentsInvalidated()) else emptyList()
            }
        } ?: return@withLock null
        val cancelled = deliveries.map { it.message }.filterIsInstance<ServerMessage.TournamentUpdated>().singleOrNull()?.tournament
            ?.takeIf { it.phase == TournamentPhase.CANCELLED }
        deliveries + cancelled?.players.orEmpty().flatMap { loadProfile(it.playerId) }
    }

    private suspend fun leaveTournament(playerId: String, tournamentId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        leaveScheduledTournament(playerId, tournamentId)?.let { return it + loadTournamentHub(playerId) }
        var snapshotToSave: TournamentSnapshot? = null
        val deliveries = mutex.withLock {
            val tournament = tournaments[tournamentId]
                ?.takeIf { playerId in it.playerIds() }
                ?: return@withLock listOf(error(playerId, "TOURNAMENT_NOT_FOUND", legacyFallback("Bạn không còn ở trong giải này.")))
            if (tournament.phase != TournamentPhase.LOBBY) {
                return@withLock listOf(error(playerId, "TOURNAMENT_ALREADY_STARTED", legacyFallback("Không thể rời giải sau khi đã bắt đầu.")))
            }
            if (tournament.hostId == playerId) {
                tournament.phase = TournamentPhase.CANCELLED
                tournament.finishedAtMillis = nowMillis()
                tournamentInvitations.entries.removeAll { it.value.tournamentId == tournament.id }
                val snapshot = tournament.snapshot()
                snapshotToSave = snapshot
                listOf(
                    Delivery(ServerMessage.TournamentUpdated(snapshot), tournament.playerIds()),
                    Delivery(ServerMessage.TournamentNotice(
                        legacyFallback("Chủ giải đã hủy giải đấu."),
                        TextKey.TournamentCancelledNotice.name,
                        code = "TOURNAMENT_CANCELLED"
                    ), tournament.playerIds())
                ) + if (tournament.visibility == TournamentVisibility.PUBLIC) listOf(publicTournamentsInvalidated()) else emptyList()
            } else {
                tournament.participants.removeAll { it.playerId == playerId }
                tournamentInvitations.entries.removeAll {
                    it.value.tournamentId == tournament.id && it.value.inviteeId == playerId
                }
                val snapshot = tournament.snapshot()
                snapshotToSave = snapshot
                listOf(
                    Delivery(ServerMessage.TournamentUpdated(snapshot), tournament.playerIds()),
                    Delivery(ServerMessage.TournamentNotice(
                        legacyFallback("Đã rời giải đấu."),
                        TextKey.TournamentLeftNotice.name,
                        code = "TOURNAMENT_LEFT"
                    ), setOf(playerId))
                ) + if (tournament.visibility == TournamentVisibility.PUBLIC) listOf(publicTournamentsInvalidated()) else emptyList()
            }
        }
        snapshotToSave?.let { persistTournament(it.tournamentId) }
        return deliveries + loadTournamentHub(playerId)
    }

    private suspend fun loadFriends(playerId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val stored = runCatching { friendRepository.load(playerId) }.getOrElse {
            return listOf(error(playerId, "FRIENDS_UNAVAILABLE", legacyFallback("Chưa tải được danh sách bạn bè.")))
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
            .getOrElse { return listOf(error(playerId, "NOT_NOTIFICATIONS_UNAVAILABLE", legacyFallback("Chưa tải được thông báo."))) }
        val pendingFriendNotifications = pendingRequests.map { request ->
            NotificationSnapshot(
                id = "friend:${request.requestId}",
                kind = NotificationKind.FRIEND_REQUEST,
                title = legacyFallback("Lời mời kết bạn"),
                message = legacyFallback("${request.displayName} muốn kết bạn với bạn."),
                createdAtEpochMillis = nowMillis(),
                destination = NotificationDestination.FRIENDS,
                titleKey = TextKey.FriendRequests.name,
                messageKey = TextKey.NotificationFriendRequestMessage.name,
                messageArgs = mapOf("player" to request.displayName)
            )
        }
        notificationRepository.createNotifications(playerId, pendingFriendNotifications)
        val notifications = runCatching { notificationRepository.loadNotifications(playerId) }
            .getOrElse { return listOf(error(playerId, "NOTIFICATIONS_UNAVAILABLE", legacyFallback("Chưa tải được thông báo."))) }
        return listOf(Delivery(ServerMessage.NotificationsData(notifications), setOf(playerId)))
    }

    private suspend fun syncNotifications(
        playerId: String,
        notifications: List<NotificationSnapshot>
    ): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        if (notifications.size > MAX_NOTIFICATION_SYNC_BATCH) {
            return listOf(error(playerId, "INVALID_NOTIFICATIONS", legacyFallback("Có quá nhiều thông báo cần đồng bộ.")))
        }
        val allowedKinds = setOf(NotificationKind.MISSION, NotificationKind.ACHIEVEMENT, NotificationKind.COSMETIC)
        val normalized = notifications.filter { notification ->
            notification.kind in allowedKinds &&
                notification.announcement == null &&
                !notification.id.startsWith("admin-announcement:") &&
                notification.id.length in 1..160 &&
                notification.title.length in 1..120 &&
                notification.message.length in 1..300 &&
                notification.destination == NotificationDestination.PROFILE &&
                notification.hasAllowedSyncTemplate()
        }.map { it.copy(createdAtEpochMillis = nowMillis(), isRead = false) }
        if (normalized.size != notifications.size) {
            return listOf(error(playerId, "INVALID_NOTIFICATIONS", legacyFallback("Dữ liệu thông báo không hợp lệ.")))
        }
        notificationRepository.createNotifications(playerId, normalized)
        return loadNotifications(playerId)
    }

    private suspend fun markNotificationsRead(playerId: String, notificationId: String?): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        if (notificationId != null && notificationId.length !in 1..160) {
            return listOf(error(playerId, "INVALID_NOTIFICATION_ID", legacyFallback("Mã thông báo không hợp lệ.")))
        }
        notificationRepository.markNotificationsRead(playerId, notificationId, nowMillis())
        return loadNotifications(playerId)
    }

    private suspend fun dismissNotifications(playerId: String, notificationId: String?): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        if (notificationId != null && notificationId.length !in 1..160) {
            return listOf(error(playerId, "INVALID_NOTIFICATION_ID", legacyFallback("Mã thông báo không hợp lệ.")))
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
            return listOf(error(playerId, "INVALID_PLAYER_CODE", legacyFallback("Mã người chơi không hợp lệ.")))
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
                            title = legacyFallback("Lời mời kết bạn"),
                            message = legacyFallback("${request.displayName} muốn kết bạn với bạn."),
                            createdAtEpochMillis = nowMillis(),
                            destination = NotificationDestination.FRIENDS,
                            titleKey = TextKey.FriendRequests.name,
                            messageKey = TextKey.NotificationFriendRequestMessage.name,
                            messageArgs = mapOf("player" to request.displayName)
                        ))
                    )
                }
                listOf(Delivery(ServerMessage.SocialNotice(
                    legacyFallback("Đã gửi lời mời kết bạn."),
                    TextKey.FriendRequestSentNotice.name,
                    code = "FRIEND_REQUEST_SENT"
                ), setOf(playerId))) +
                    refreshSocialFor(setOf(playerId, result.recipientId)) +
                    refreshNotificationsFor(setOf(result.recipientId))
            }
            FriendRequestResult.PlayerNotFound -> listOf(error(playerId, "PLAYER_NOT_FOUND", legacyFallback("Không tìm thấy mã người chơi.")))
            FriendRequestResult.SelfRequest -> listOf(error(playerId, "SELF_FRIEND_REQUEST", legacyFallback("Bạn không thể tự kết bạn với mình.")))
            FriendRequestResult.AlreadyExists -> listOf(error(playerId, "FRIENDSHIP_EXISTS", legacyFallback("Hai người đã là bạn hoặc đang có lời mời.")))
            FriendRequestResult.Blocked -> listOf(error(
                playerId,
                "INTERACTION_BLOCKED",
                legacyFallback("Không thể gửi lời mời kết bạn cho người chơi này.")
            ))
        }
    }

    private suspend fun cancelFriendRequest(playerId: String, requestId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        return when (val result = friendRepository.cancelRequest(playerId, requestId)) {
            is FriendCancellationResult.Success -> {
                notificationRepository.dismissNotifications(result.recipientId, "friend:$requestId", nowMillis())
                listOf(Delivery(ServerMessage.SocialNotice(
                    legacyFallback("Đã hủy lời mời kết bạn."),
                    TextKey.FriendRequestCancelledNotice.name,
                    code = "FRIEND_REQUEST_CANCELLED"
                ), setOf(playerId))) +
                    refreshSocialFor(setOf(playerId, result.recipientId)) +
                    refreshNotificationsFor(setOf(result.recipientId))
            }
            FriendCancellationResult.NotFound -> listOf(error(
                playerId,
                "FRIEND_REQUEST_NOT_FOUND",
                legacyFallback("Lời mời kết bạn không còn tồn tại hoặc không thuộc về bạn.")
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
                val notice = if (command.accept) legacyFallback("Đã chấp nhận lời mời kết bạn.") else legacyFallback("Đã từ chối lời mời kết bạn.")
                val noticeKey = if (command.accept) TextKey.FriendRequestAcceptedNotice else TextKey.FriendRequestDeclinedNotice
                val noticeCode = if (command.accept) "FRIEND_REQUEST_ACCEPTED" else "FRIEND_REQUEST_DECLINED"
                listOf(Delivery(ServerMessage.SocialNotice(
                    notice,
                    noticeKey.name,
                    code = noticeCode
                ), setOf(playerId))) +
                    refreshSocialFor(setOf(playerId, result.requesterId)) +
                    refreshNotificationsFor(setOf(playerId))
            }
            FriendResponseResult.NotFound -> listOf(error(playerId, "FRIEND_REQUEST_NOT_FOUND", legacyFallback("Lời mời không còn tồn tại.")))
        }
    }

    private suspend fun removeFriend(playerId: String, friendUserId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        return when (val result = friendRepository.removeFriend(playerId, friendUserId)) {
            is SocialMutationResult.Success -> {
                clearRoomInvitationsBetween(playerId, result.otherUserId)
                listOf(Delivery(ServerMessage.SocialNotice(
                    legacyFallback("Đã hủy kết bạn."), TextKey.FriendRemovedNotice.name, code = "FRIEND_REMOVED"
                ), setOf(playerId))) +
                    refreshSocialFor(setOf(playerId, result.otherUserId)) +
                    refreshRoomInvitationsFor(setOf(playerId, result.otherUserId))
            }
            SocialMutationResult.NotFound -> listOf(error(
                playerId,
                "FRIEND_NOT_FOUND",
                legacyFallback("Quan hệ bạn bè không còn tồn tại.")
            ))
            SocialMutationResult.SelfAction -> listOf(error(
                playerId,
                "INVALID_SOCIAL_ACTION",
                legacyFallback("Không thể thực hiện thao tác này với chính bạn.")
            ))
        }
    }

    private suspend fun blockPlayer(playerId: String, playerUserId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        return when (val result = friendRepository.blockPlayer(playerId, playerUserId, nowMillis())) {
            is SocialMutationResult.Success -> {
                clearRoomInvitationsBetween(playerId, result.otherUserId)
                listOf(Delivery(ServerMessage.SocialNotice(
                    legacyFallback("Đã chặn người chơi."), TextKey.PlayerBlockedNotice.name, code = "PLAYER_BLOCKED"
                ), setOf(playerId))) +
                    refreshSocialFor(setOf(playerId, result.otherUserId)) +
                    refreshRoomInvitationsFor(setOf(playerId, result.otherUserId))
            }
            SocialMutationResult.NotFound -> listOf(error(
                playerId,
                "PLAYER_NOT_FOUND",
                legacyFallback("Không tìm thấy người chơi để chặn.")
            ))
            SocialMutationResult.SelfAction -> listOf(error(
                playerId,
                "INVALID_SOCIAL_ACTION",
                legacyFallback("Bạn không thể tự chặn chính mình.")
            ))
        }
    }

    private suspend fun unblockPlayer(playerId: String, playerUserId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        return when (val result = friendRepository.unblockPlayer(playerId, playerUserId)) {
            is SocialMutationResult.Success ->
                listOf(Delivery(ServerMessage.SocialNotice(
                    legacyFallback("Đã bỏ chặn người chơi."), TextKey.PlayerUnblockedNotice.name, code = "PLAYER_UNBLOCKED"
                ), setOf(playerId))) +
                    refreshSocialFor(setOf(playerId, result.otherUserId))
            SocialMutationResult.NotFound -> listOf(error(
                playerId,
                "BLOCK_NOT_FOUND",
                legacyFallback("Người chơi này không còn trong danh sách chặn.")
            ))
            SocialMutationResult.SelfAction -> listOf(error(
                playerId,
                "INVALID_SOCIAL_ACTION",
                legacyFallback("Không thể thực hiện thao tác này với chính bạn.")
            ))
        }
    }

    private suspend fun inviteFriend(playerId: String, command: ClientMessage.InviteFriend): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        if (friendRepository.isBlockedEitherWay(playerId, command.friendUserId)) {
            return listOf(error(
                playerId,
                "INTERACTION_BLOCKED",
                legacyFallback("Không thể mời người chơi này vào phòng.")
            ))
        }
        if (!friendRepository.areFriends(playerId, command.friendUserId)) {
            return listOf(error(playerId, "NOT_FRIENDS", legacyFallback("Người chơi này chưa phải bạn bè.")))
        }
        var createdInvitation: RoomInvitationRecord? = null
        val replacedInvitationIds = mutableListOf<String>()
        val deliveries = mutex.withLock {
            val inviter = sessionsByPlayerId[playerId]
                ?: return@withLock listOf(error(playerId, "SESSION_NOT_FOUND", legacyFallback("Phiên chơi không còn hợp lệ.")))
            val room = rooms[command.roomId]
                ?.takeIf { it.hostId == playerId && it.phase == RoomPhase.WAITING && it.guestId == null }
                ?: return@withLock listOf(error(playerId, "ROOM_NOT_INVITABLE", legacyFallback("Phòng không còn sẵn sàng để mời bạn.")))
            val friendId = command.friendUserId
            if (roomFor(friendId) != null) {
                return@withLock listOf(error(playerId, "FRIEND_BUSY", legacyFallback("Bạn bè đang ở trong phòng khác.")))
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
                Delivery(invitation.toMessage(), setOf(friendId))
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
                    title = legacyFallback("Lời mời vào phòng"),
                    message = legacyFallback("${invitation.inviterDisplayName} mời bạn vào phòng ${invitation.roomName}."),
                    createdAtEpochMillis = nowMillis(),
                    destination = NotificationDestination.FRIENDS,
                    titleKey = TextKey.RoomInvitationTitle.name,
                    messageKey = TextKey.NotificationRoomInvitationMessage.name,
                    messageArgs = mapOf(
                        "player" to invitation.inviterDisplayName,
                        "room" to invitation.roomName
                    )
                ))
            )
            
            val pushTarget = playerProfileRepository.findPushTarget(
                invitation.inviteeId,
                PushNotificationCategory.ROOM_INVITATIONS
            )
            if (pushTarget != null) {
                sendPushNotification(
                    playerId = invitation.inviteeId,
                    target = pushTarget,
                    content = LocalizedPushContent(
                        titleKey = TextKey.PushRoomInvitationTitle,
                        bodyKey = TextKey.PushRoomInvitationMessage,
                        bodyArguments = mapOf(
                            "player" to invitation.inviterDisplayName,
                            "room" to invitation.roomName
                        )
                    ),
                    destinationPath = "/friends"
                )
            }
            return deliveries + loadRoomInvitations(playerId) +
                refreshNotificationsFor(setOf(invitation.inviteeId))
        }
        return deliveries
    }

    private suspend fun loadRoomInvitations(playerId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val removedInvitations = mutableListOf<RoomInvitationRecord>()
        val invitationsData = mutex.withLock {
            val now = nowMillis()
            roomInvitations.entries.removeAll { (_, invitation) ->
                val room = rooms[invitation.roomId]
                (invitation.expiresAtMillis <= now || room == null ||
                    room.hostId != invitation.inviterId || room.phase != RoomPhase.WAITING || room.guestId != null
                ).also { removed -> if (removed) removedInvitations += invitation }
            }
            roomInvitationsDataFor(playerId)
        }
        removedInvitations.forEach { invitation ->
            notificationRepository.deleteRoomInvitation(invitation.id)
            notificationRepository.dismissNotifications(
                invitation.inviteeId,
                "room:${invitation.id}",
                nowMillis()
            )
        }
        val otherAffectedUsers = removedInvitations
            .flatMap { listOf(it.inviterId, it.inviteeId) }
            .toSet() - playerId
        val refreshDeliveries = mutex.withLock {
            otherAffectedUsers.mapNotNull { affectedUserId ->
                sessionsByPlayerId[affectedUserId]
                    ?.takeIf { it.isConnected && it.resumeToken == null }
                    ?.let {
                        Delivery(roomInvitationsDataFor(affectedUserId), setOf(affectedUserId))
                    }
            }
        }
        return listOf(Delivery(invitationsData, setOf(playerId))) + refreshDeliveries
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
        if (command.accept) rejectIfQuotaExhausted(playerId)?.let { return it }
        var invitationConsumed = false
        val otherRemovedInvitations = mutableListOf<RoomInvitationRecord>()
        val result = mutex.withLock {
            if (maintenancePolicy.blocksNewIntent) return@withLock listOf(maintenanceError(playerId))
            val invitation = roomInvitations.remove(command.invitationId)
                ?.takeIf { it.inviteeId == playerId && it.expiresAtMillis > nowMillis() }
                ?: return@withLock listOf(error(playerId, "INVITATION_EXPIRED", legacyFallback("Lời mời vào phòng đã hết hạn.")))
            invitationConsumed = true
            if (!command.accept) {
                return@withLock listOf(
                    Delivery(ServerMessage.SocialNotice(
                        legacyFallback("Đã từ chối lời mời vào phòng."),
                        TextKey.RoomInvitationDeclinedNotice.name,
                        code = "ROOM_INVITATION_DECLINED"
                    ), setOf(playerId)),
                    Delivery(ServerMessage.SocialNotice(
                        legacyFallback("Bạn bè đã từ chối lời mời vào phòng."),
                        TextKey.RoomInvitationDeclinedByFriendNotice.name,
                        code = "ROOM_INVITATION_DECLINED_BY_FRIEND"
                    ), setOf(invitation.inviterId))
                )
            }
            val player = sessionsByPlayerId[playerId]
                ?: return@withLock listOf(error(playerId, "SESSION_NOT_FOUND", legacyFallback("Phiên chơi không còn hợp lệ.")))
            if (roomFor(playerId) != null) {
                return@withLock listOf(error(playerId, "ALREADY_IN_ROOM", legacyFallback("Bạn đang ở trong một phòng khác.")))
            }
            val room = rooms[invitation.roomId]
                ?.takeIf { it.hostId == invitation.inviterId && it.phase == RoomPhase.WAITING && it.guestId == null }
                ?: return@withLock listOf(error(playerId, "ROOM_NOT_FOUND", legacyFallback("Phòng được mời không còn sẵn sàng.")))
            room.guestId = player.playerId
            room.sequence++
            room.scores[player.playerId] = 0
            room.readyPlayerIds.clear()
            roomInvitations.entries.removeAll { entry ->
                (entry.value.roomId == room.id).also { removed ->
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
            notificationRepository.deleteRoomInvitationsForRoom(
                result.asSequence()
                    .map(Delivery::message)
                    .filterIsInstance<ServerMessage.RoomUpdated>()
                    .first()
                    .game.roomId
            )
            otherRemovedInvitations.forEach { invitation ->
                notificationRepository.dismissNotifications(
                    invitation.inviteeId,
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
        return result + affectedPlayers.flatMap { presenceUpdates(it) } +
            refreshRoomInvitationsFor(affectedPlayers) +
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
            error(playerId, "MODE_LOCKED", legacyFallback("Chế độ này mở khóa ở cấp ${mode.unlockLevel}."))
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
        requireOnlineAccount(playerId)?.let { return listOf(it) }
        rejectIfQuotaExhausted(playerId)?.let { return it }
        if (mutex.withLock { activeTournamentFor(playerId) != null }) {
            return listOf(error(playerId, "TOURNAMENT_ACTIVE", legacyFallback("Hãy rời hoặc hoàn tất giải đấu hiện tại trước.")))
        }
        val profile = playerProfileRepository.findByPlayerId(playerId)
        val level = profile?.progression?.level ?: 1
        if (level < command.gameMode.unlockLevel) {
            return listOf(error(
                playerId,
                "MODE_LOCKED",
                legacyFallback("Chế độ này mở khóa ở cấp ${command.gameMode.unlockLevel}.")
            ))
        }
        val rating = profile?.statistics?.eloRating ?: DEFAULT_ELO_RATING
        val joinedAt = nowMillis()
        val entry = mutex.withLock {
            if (maintenancePolicy.blocksNewIntent) return listOf(maintenanceError(playerId))
            val player = sessionsByPlayerId[playerId]
                ?.takeIf { it.isConnected && it.resumeToken == null }
                ?: return listOf(error(playerId, "SESSION_NOT_FOUND", legacyFallback("Phiên chơi không còn hợp lệ.")))
            if (activeTournamentFor(playerId) != null) {
                return listOf(error(playerId, "TOURNAMENT_ACTIVE", legacyFallback("Hãy rời hoặc hoàn tất giải đấu hiện tại trước.")))
            }
            if (roomFor(playerId) != null) {
                matchmakingEntries.remove(playerId)
                return listOf(error(playerId, "ALREADY_IN_ROOM", legacyFallback("Bạn đang ở trong một phòng khác.")))
            }
            val existing = matchmakingEntries[playerId]?.takeIf {
                it.gameMode == command.gameMode && it.matchType == command.matchType
            }
            MatchmakingEntry(player.playerId, command.gameMode, command.matchType, rating,
                existing?.joinedAtMillis ?: joinedAt).also { matchmakingEntries[playerId] = it }
        }
        val matched = matchQueuedPlayer(entry)
        val waiting = mutex.withLock {
            if (!isQueuedForMatchmaking(entry)) emptyList() else listOf(matchmakingStatus(entry, nowMillis()))
        }
        return matched + waiting + if (waiting.isNotEmpty()) presenceUpdates(playerId) else emptyList()
    }

    /** Revisit existing entries without refetching profiles or charging quota until a pair is reserved. */
    suspend fun advanceMatchmaking(deliver: suspend (List<Delivery>) -> Unit) {
        if (!matchmakingSweepMutex.tryLock()) return
        try {
            val queued = mutex.withLock {
                matchmakingEntries.values.filter(::isQueuedForMatchmaking).sortedBy { it.joinedAtMillis }
            }
            val checkedPairs = mutableSetOf<Set<String>>()
            for (entry in queued) {
                val deliveries = try {
                    matchQueuedPlayer(entry, checkedPairs)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    // Keep other entries and later ticks working after a transient repository failure.
                    System.err.println("Could not advance matchmaking: ${failure.javaClass.simpleName}")
                    emptyList()
                }
                // A published room's game timer is already running: send its start before checking another pair.
                if (deliveries.isNotEmpty()) deliver(deliveries)
            }
            // Clients already display the expanding range using their queue timer. Do not send
            // unsolicited searching acknowledgements that could arrive after a cancel response.
        } finally {
            matchmakingSweepMutex.unlock()
        }
    }

    // These helpers only inspect engine state while holding mutex.
    private fun isQueuedForMatchmaking(entry: MatchmakingEntry): Boolean =
        !maintenancePolicy.blocksNewIntent && matchmakingEntries[entry.playerId] === entry &&
            sessionsByPlayerId[entry.playerId]?.let { it.isConnected && it.resumeToken == null } == true &&
            roomFor(entry.playerId) == null && activeTournamentFor(entry.playerId) == null

    private fun MatchmakingEntry.ratingRange(now: Long) =
        if (matchType == MatchType.RANKED) matchmakingRange(now - joinedAtMillis) else 0

    private fun compatibleForMatchmaking(first: MatchmakingEntry, second: MatchmakingEntry, now: Long): Boolean =
        first.playerId != second.playerId && first.gameMode == second.gameMode && first.matchType == second.matchType &&
            isQueuedForMatchmaking(first) && isQueuedForMatchmaking(second) &&
            (first.matchType == MatchType.CASUAL ||
                kotlin.math.abs(first.eloRating.toLong() - second.eloRating) <= maxOf(first.ratingRange(now), second.ratingRange(now)))

    private fun matchmakingStatus(entry: MatchmakingEntry, now: Long): Delivery {
        return Delivery(ServerMessage.MatchmakingStatus(true, entry.gameMode, entry.matchType, entry.ratingRange(now)), setOf(entry.playerId))
    }

    private suspend fun matchQueuedPlayer(entry: MatchmakingEntry, checkedPairs: MutableSet<Set<String>> = mutableSetOf()): List<Delivery> {
        val candidates = mutex.withLock {
            val now = nowMillis()
            if (!isQueuedForMatchmaking(entry)) return emptyList()
            matchmakingEntries.values.filter { compatibleForMatchmaking(entry, it, now) }.sortedWith(
                compareBy<MatchmakingEntry> { if (entry.matchType == MatchType.RANKED) kotlin.math.abs(it.eloRating.toLong() - entry.eloRating) else 0L }
                    .thenBy { entry.gameMode == ProtocolGameMode.TEAM_2V2 && (sessionsByPlayerId[it.playerId]?.latencyMillis ?: 0L) >= 150L }
                    .thenBy { it.joinedAtMillis }
            )
        }
        for (candidate in candidates) {
            if (!mutex.withLock { isQueuedForMatchmaking(entry) }) break
            if (!checkedPairs.add(setOf(entry.playerId, candidate.playerId))) continue
            val blocked = try {
                friendRepository.isBlockedEitherWay(entry.playerId, candidate.playerId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                System.err.println("Could not check blocked players for matchmaking: ${failure.javaClass.simpleName}")
                true
            }
            if (blocked) continue
            val start = mutex.withLock {
                // Entry identity fences cancel/rejoin, mode switches and concurrent joins/sweeps after I/O.
                if (!compatibleForMatchmaking(entry, candidate, nowMillis())) return@withLock null
                val room = Room(id = UUID.randomUUID().toString(), name = legacyFallback("Đấu nhanh"), hostId = candidate.playerId,
                    password = null, gameMode = entry.gameMode, matchType = entry.matchType, guestId = entry.playerId,
                    scores = mutableMapOf(candidate.playerId to 0, entry.playerId to 0))
                matchmakingEntries.remove(candidate.playerId)
                matchmakingEntries.remove(entry.playerId)
                room.readyPlayerIds.addAll(room.playerIds())
                rooms[room.id] = room
                prepareStart(room, room.matchId, entry.playerId, fromMatchmaking = true)
            }
            if (start != null) return performPreparedStart(start)
        }
        return emptyList()
    }

    private suspend fun cancelMatchmaking(playerId: String): List<Delivery> {
        var abandoned: PreparedMatchStart? = null
        val stopped = mutex.withLock {
            val pending = preparedStarts.values.firstOrNull { it.fromMatchmaking && playerId in it.playerIds }
            if (pending?.candidate != null) return@withLock listOf(startError(playerId))
            pending?.let { abandoned = it; discardUnpublishedMatchmaking(it) }.orEmpty()
        }
        if (stopped.isNotEmpty()) {
            abandoned?.let { start -> withContext(NonCancellable) {
                runCatching { playQuotaRepository.releaseMatch(start.playerIds, start.matchId, emptySet()) }
            } }
            return stopped
        }
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
        val expansions = (waitingMillis.coerceAtLeast(0L) / MATCHMAKING_EXPAND_INTERVAL_MILLIS)
            .coerceAtMost(((MATCHMAKING_MAX_RANGE - MATCHMAKING_INITIAL_RANGE) / MATCHMAKING_EXPAND_STEP).toLong()).toInt()
        return (MATCHMAKING_INITIAL_RANGE + expansions * MATCHMAKING_EXPAND_STEP)
            .coerceAtMost(MATCHMAKING_MAX_RANGE)
    }

    private fun accountRequired(playerId: String) = error(
        playerId,
        "ACCOUNT_REQUIRED",
        legacyFallback("Hãy đăng nhập tài khoản để sử dụng tính năng này.")
    )

    private suspend fun requireOnlineAccount(playerId: String): Delivery? =
        if (isAccountSession(playerId)) {
            null
        } else {
            error(
                playerId,
                "ACCOUNT_REQUIRED",
                legacyFallback("Hãy đăng nhập để chơi trực tuyến.")
            )
        }

    private suspend fun rejectIfQuotaExhausted(playerId: String): List<Delivery>? {
        val check = playQuotaRepository.canPlay(setOf(playerId), nowMillis())
        if (check.allowed) return null
        return quotaRejectedDeliveries(setOf(playerId), check.quotas)
    }

    private fun quotaRejectedDeliveries(
        playerIds: Set<String>,
        consumption: PlayQuotaConsumption
    ): List<Delivery> = quotaRejectedDeliveries(playerIds, consumption.quotas)

    private fun quotaRejectedDeliveries(
        playerIds: Set<String>,
        quotas: Map<String, com.hienthai.fastowin.protocol.PlayQuotaSnapshot>
    ): List<Delivery> = quotaDataDeliveries(quotas) + playerIds.map { affectedPlayerId ->
        error(
            affectedPlayerId,
            "PLAY_QUOTA_EXHAUSTED",
            legacyFallback("Đã hết lượt đấu hôm nay. Xem quảng cáo để nhận thêm 2 lượt.")
        )
    }

    private fun quotaDataDeliveries(
        quotas: Map<String, com.hienthai.fastowin.protocol.PlayQuotaSnapshot>
    ): List<Delivery> = quotas.map { (affectedPlayerId, quota) ->
        Delivery(
            ServerMessage.PlayQuotaData(quota.withRewardedAdAvailability()),
            setOf(affectedPlayerId)
        )
    }

    private suspend fun loadPlayQuota(playerId: String): List<Delivery> {
        requireOnlineAccount(playerId)?.let { return listOf(it) }
        val quota = playQuotaRepository.getSnapshot(playerId, nowMillis())
        return listOf(
            Delivery(
                ServerMessage.PlayQuotaData(quota.withRewardedAdAvailability()),
                setOf(playerId)
            )
        )
    }

    private suspend fun claimRewardedAdBonus(
        playerId: String,
        command: ClientMessage.ClaimRewardedAdBonus
    ): List<Delivery> {
        requireOnlineAccount(playerId)?.let { return listOf(it) }
        val currentQuota: suspend () -> com.hienthai.fastowin.protocol.PlayQuotaSnapshot = {
            playQuotaRepository.getSnapshot(playerId, nowMillis()).withRewardedAdAvailability()
        }
        if (
            command.requestId.isBlank() || command.requestId.length > MAX_REQUEST_ID_LENGTH ||
            command.providerTransactionId.isBlank() ||
            command.providerTransactionId.length > MAX_REWARDED_AD_TRANSACTION_ID_LENGTH ||
            command.proof.isBlank() || command.proof.length > MAX_REWARDED_AD_PROOF_LENGTH
        ) {
            return listOf(rewardedAdResult(
                playerId,
                command.requestId,
                RewardedAdBonusStatus.INVALID,
                currentQuota()
            ))
        }
        if (rewardedAdAvailability == RewardedAdAvailability.UNAVAILABLE) {
            return listOf(rewardedAdResult(
                playerId,
                command.requestId,
                RewardedAdBonusStatus.UNAVAILABLE,
                currentQuota()
            ))
        }
        val verified = try {
            rewardedAdVerifier.verify(
                userId = playerId,
                provider = command.provider,
                providerTransactionId = command.providerTransactionId,
                proof = command.proof
            )
        } catch (error: Throwable) {
            System.err.println("Could not verify rewarded ad for $playerId: ${error.message}")
            return listOf(rewardedAdResult(
                playerId,
                command.requestId,
                RewardedAdBonusStatus.FAILED,
                currentQuota()
            ))
        }
        if (verified == null) {
            return listOf(rewardedAdResult(
                playerId,
                command.requestId,
                RewardedAdBonusStatus.INVALID,
                currentQuota()
            ))
        }
        val grant = try {
            playQuotaRepository.grantRewardedAd(
                userId = playerId,
                provider = verified.provider,
                providerTransactionId = verified.providerTransactionId,
                nowMillis = nowMillis()
            )
        } catch (error: Throwable) {
            System.err.println("Could not grant rewarded ad quota for $playerId: ${error.message}")
            return listOf(rewardedAdResult(
                playerId,
                command.requestId,
                RewardedAdBonusStatus.FAILED,
                currentQuota()
            ))
        }
        return listOf(rewardedAdResult(
            playerId,
            command.requestId,
            grant.status,
            grant.quota.withRewardedAdAvailability()
        ))
    }

    private fun rewardedAdResult(
        playerId: String,
        requestId: String,
        status: RewardedAdBonusStatus,
        quota: com.hienthai.fastowin.protocol.PlayQuotaSnapshot
    ) = Delivery(
        ServerMessage.RewardedAdBonusResult(requestId, status, quota),
        setOf(playerId)
    )

    private fun com.hienthai.fastowin.protocol.PlayQuotaSnapshot.withRewardedAdAvailability() =
        copy(rewardedAdAvailability = this@GameEngine.rewardedAdAvailability)

    private suspend fun loadProfile(playerId: String, isBackgroundRefresh: Boolean = false): List<Delivery> {
        val session = mutex.withLock { sessionsByPlayerId[playerId]?.copy() }
            ?: return listOf(error(playerId, "SESSION_NOT_FOUND", legacyFallback("Phiên chơi không còn hợp lệ.")))
        if (session.resumeToken == null) {
            runCatching { playerProfileRepository.settleCompletedSeasonRewards(playerId) }
                .onFailure { System.err.println("Could not settle season rewards for $playerId: ${it.message}") }
        }
        val persisted = runCatching { playerProfileRepository.findByPlayerId(playerId) }
            .onFailure { System.err.println("Could not load profile $playerId: ${it.message}") }
            .getOrNull()
        val profile = persisted ?: PlayerProfileSnapshot(
            userId = playerId,
            displayName = session.displayName,
            playerCode = playerId.replace("-", "").take(10).uppercase()
        )
        return listOf(Delivery(ServerMessage.ProfileData(profile, isBackgroundRefresh), setOf(playerId)))
    }

    /** Retryable admitted work; no provider or match-result IO holds the engine mutex. */
    suspend fun retryMaintenanceSettlements(): List<Delivery> {
        val starts = mutex.withLock { preparedStarts.values.filter { it.candidate != null }.toList() }
        val startDeliveries = starts.flatMap { performPreparedStart(it) }
        val pending = mutex.withLock { pendingCompletedMatches.values.toList() }
        val settlementDeliveries = pending.flatMap { settleCompletedMatch(it) }
        // Keep deferred notifications invalidatable through every storage wait, then check
        // the participant's current intent immediately before returning them for delivery.
        val matchmakingDeliveries = mutex.withLock {
            val result = pendingMatchmakingDeliveries.mapNotNull { delivery ->
                delivery.recipients?.filter { sessionsByPlayerId[it]?.isConnected == true &&
                    roomFor(it) == null && it !in matchmakingEntries }?.toSet()?.takeIf { it.isNotEmpty() }
                    ?.let { delivery.copy(recipients = it) }
            }
            pendingMatchmakingDeliveries.clear()
            result
        }
        return matchmakingDeliveries + startDeliveries + settlementDeliveries
    }

    private suspend fun settleCompletedMatch(completed: CompletedMatch): List<Delivery> = withContext(NonCancellable) {
        val admitted = mutex.withLock { completed.matchId in pendingCompletedMatches && settlementsInFlight.add(completed.matchId) }
        if (!admitted) return@withContext emptyList()
        try { withTimeout(4_000) {
            val roomId = mutex.withLock { rooms.values.firstOrNull { it.matchId == completed.matchId }?.id }
            // Save the exact accepted result before wallet/result work so restored rooms can retry.
            roomId?.let { persistRoom(it) }
            if (!persistCompletedMatch(completed)) return@withTimeout emptyList()
            val deliveries = advanceTournamentAfterMatch(completed.matchId)
            persistenceMutex.withLock {
                val remaining = mutex.withLock {
                    rooms.values.firstOrNull { it.matchId == completed.matchId }?.let {
                        it.id to if (it.id in pendingRoomRemovals || it.activePlayerIds().isEmpty()) null else it.toStoredRoom().copy(pendingCompletedMatch = null)
                    }
                }
                remaining?.let { (id, snapshot) -> if (snapshot == null) activeRoomRepository.delete(id) else activeRoomRepository.save(snapshot) }
                mutex.withLock {
                    if (remaining != null && remaining.second == null) {
                        rooms.remove(remaining.first); pendingRoomRemovals.remove(remaining.first)
                    }
                    pendingCompletedMatches.remove(completed.matchId)
                }
            }
            deliveries
        }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            // This attempt owns its timeout; keep the durable payload and the runtime retry worker alive.
            emptyList()
        } finally {
            mutex.withLock { settlementsInFlight.remove(completed.matchId) }
        }
    }

    private suspend fun persistCompletedMatch(completed: CompletedMatch): Boolean {
        val profilesBefore = completed.players.associate { player ->
            player.playerId to runCatching {
                playerProfileRepository.findByPlayerId(player.playerId)
            }.getOrNull()
        }
        val saved = runCatching { matchResultRepository.save(completed) }
            .onFailure { error ->
                System.err.println("Could not persist match ${completed.matchId}: ${error.message}")
            }
            .isSuccess
        if (!saved) return false

        completed.players.forEach { player ->
            val previous = profilesBefore[player.playerId] ?: return@forEach
            val current = runCatching {
                playerProfileRepository.findByPlayerId(player.playerId)
            }.getOrNull() ?: return@forEach
            val previouslyCompleted = (
                previous.progression.dailyMissions + previous.progression.weeklyMissions
            ).filter { it.completed }.mapTo(mutableSetOf()) { it.code }
            val newlyCompleted = (
                current.progression.dailyMissions + current.progression.weeklyMissions
            ).filter { it.completed && it.code !in previouslyCompleted }
            if (newlyCompleted.isEmpty()) return@forEach

            val target = runCatching {
                playerProfileRepository.findPushTarget(
                    player.playerId,
                    PushNotificationCategory.MISSION_REWARDS
                )
            }.getOrNull() ?: return@forEach
            newlyCompleted.forEach { mission ->
                val missionTitle = mission.titleKey
                    ?.let { keyName -> TextKey.entries.firstOrNull { it.name == keyName } }
                    ?.let { key -> LocalizationService(target.language).text(key) }
                    ?: mission.title
                sendPushNotification(
                    playerId = player.playerId,
                    target = target,
                    content = LocalizedPushContent(
                        titleKey = TextKey.PushMissionCompletedTitle,
                        bodyKey = TextKey.PushMissionCompletedMessage,
                        bodyArguments = mapOf("mission" to missionTitle)
                    ),
                    destinationPath = "/account/missions"
                )
            }
        }
        return true
    }

    private suspend fun sendPushNotification(
        playerId: String,
        target: PushTarget,
        content: LocalizedPushContent,
        destinationPath: String
    ) {
        if (
            pushNotificationService.sendNotification(
                fcmToken = target.fcmToken,
                language = target.language,
                content = content,
                destinationPath = destinationPath
            ) == PushDeliveryStatus.INVALID_TOKEN
        ) {
            playerProfileRepository.clearFcmToken(playerId, target.fcmToken)
        }
    }

    private suspend fun acknowledgeSeasonReward(playerId: String, seasonNumber: Int): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val acknowledged = runCatching {
            playerProfileRepository.acknowledgeSeasonReward(playerId, seasonNumber)
        }.onFailure {
            System.err.println("Could not acknowledge season reward for $playerId: ${it.message}")
        }.getOrDefault(false)
        return if (acknowledged) {
            loadProfile(playerId)
        } else {
            listOf(error(
                playerId,
                "SEASON_REWARD_NOT_FOUND",
                legacyFallback("Không tìm thấy phần thưởng mùa cần xác nhận.")
            ))
        }
    }

    private suspend fun loadWalletHistory(playerId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val transactions = runCatching { playerProfileRepository.loadWalletHistory(playerId) }
            .onFailure { System.err.println("Could not load wallet history for $playerId: ${it.message}") }
            .getOrElse {
                return listOf(error(
                    playerId,
                    "WALLET_HISTORY_UNAVAILABLE",
                    legacyFallback("Chưa thể tải lịch sử tài sản. Vui lòng thử lại.")
                ))
            }
        return listOf(Delivery(ServerMessage.WalletHistory(transactions), setOf(playerId)))
    }

    private suspend fun loadGemStoreCatalog(playerId: String): List<Delivery> {
        val hasSession = mutex.withLock { sessionsByPlayerId.containsKey(playerId) }
        if (!hasSession) return listOf(error(playerId, "SESSION_NOT_FOUND", legacyFallback("Phiên chơi không còn hợp lệ.")))
        return listOf(Delivery(
            ServerMessage.GemStoreCatalog(GEM_STORE_PACKAGES, sandboxEnabled = storeSandboxEnabled),
            setOf(playerId)
        ))
    }

    private suspend fun exchangeGemsForGold(
        playerId: String,
        message: ClientMessage.ExchangeGemsForGold
    ): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))

        val offer = RewardEconomy.goldExchange(message.offerId)
            ?: return listOf(Delivery(
                ServerMessage.GoldExchangeResult(
                    requestId = message.requestId,
                    offerId = message.offerId,
                    status = GoldExchangeStatus.INVALID_OFFER
                ),
                setOf(playerId)
            ))
        if (message.requestId.length !in 1..64) {
            return listOf(Delivery(
                ServerMessage.GoldExchangeResult(
                    requestId = message.requestId,
                    offerId = message.offerId,
                    status = GoldExchangeStatus.FAILED
                ),
                setOf(playerId)
            ))
        }

        val status = runCatching {
            playerProfileRepository.exchangeGemsForGold(
                playerId = playerId,
                requestId = message.requestId,
                offer = offer
            )
        }.onFailure {
            System.err.println("Could not exchange Gems for Gold for $playerId: ${it.message}")
        }.getOrDefault(GoldExchangeStatus.FAILED)

        val result = Delivery(
            ServerMessage.GoldExchangeResult(
                requestId = message.requestId,
                offerId = message.offerId,
                spentGems = if (status == GoldExchangeStatus.GRANTED) offer.gemsCost else 0,
                receivedGold = if (status == GoldExchangeStatus.GRANTED) offer.goldAmount else 0,
                status = status
            ),
            setOf(playerId)
        )
        return if (
            status == GoldExchangeStatus.GRANTED ||
            status == GoldExchangeStatus.ALREADY_GRANTED
        ) {
            listOf(result) + loadProfile(playerId)
        } else {
            listOf(result)
        }
    }

    private suspend fun verifyStorePurchase(
        playerId: String,
        message: ClientMessage.VerifyStorePurchase
    ): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val result = storePurchaseService.verify(playerId, StorePurchaseRequest(
            message.store, message.productId, message.purchaseToken, message.requestId
        ))
        val delivery = listOf(Delivery(result, setOf(playerId)))
        return if (result.status == StorePurchaseStatus.GRANTED) delivery + loadProfile(playerId) else delivery
    }

    private suspend fun claimDailyCheckIn(playerId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val result = runCatching { playerProfileRepository.claimDailyCheckIn(playerId) }
            .onFailure { System.err.println("Could not claim daily check-in for $playerId: ${it.message}") }
            .getOrNull()
            ?: return listOf(error(
                playerId,
                "DAILY_CHECK_IN_UNAVAILABLE",
                legacyFallback("Chưa thể điểm danh. Vui lòng thử lại.")
            ))
        return loadProfile(playerId) + Delivery(
            ServerMessage.DailyCheckInResult(
                result.claimed,
                result.rewardXp,
                result.rewardGold,
                result.rewardGems
            ),
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
            legacyFallback("Chưa thể nhận thưởng nhiệm vụ. Vui lòng thử lại.")
        ))
        return when (result.status) {
            MissionRewardClaimStatus.CLAIMED -> loadProfile(playerId) + Delivery(
                ServerMessage.MissionRewardResult(
                    missionCode,
                    claimed = true,
                    rewardXp = result.rewardXp,
                    rewardGold = result.rewardGold,
                    rewardGems = result.rewardGems
                ),
                setOf(playerId)
            )
            MissionRewardClaimStatus.ALREADY_CLAIMED -> listOf(error(
                playerId,
                "MISSION_ALREADY_CLAIMED",
                legacyFallback("Phần thưởng nhiệm vụ này đã được nhận.")
            ))
            MissionRewardClaimStatus.NOT_COMPLETED -> listOf(error(
                playerId,
                "MISSION_NOT_COMPLETED",
                legacyFallback("Nhiệm vụ chưa hoàn thành.")
            ))
            MissionRewardClaimStatus.INVALID_MISSION -> listOf(error(
                playerId,
                "INVALID_MISSION",
                legacyFallback("Nhiệm vụ không hợp lệ.")
            ))
        }
    }

    private suspend fun loadFriendProfile(playerId: String, friendUserId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        if (friendUserId.isBlank() || friendUserId.length > 64 || friendUserId == playerId) {
            return listOf(error(playerId, "INVALID_FRIEND", legacyFallback("Người chơi không hợp lệ.")))
        }
        val areFriends = runCatching { friendRepository.areFriends(playerId, friendUserId) }
            .getOrElse {
                return listOf(error(playerId, "FRIEND_PROFILE_UNAVAILABLE", legacyFallback("Chưa tải được hồ sơ bạn bè.")))
            }
        if (!areFriends) {
            return listOf(error(playerId, "FRIEND_PROFILE_FORBIDDEN", legacyFallback("Bạn chỉ có thể xem hồ sơ của bạn bè.")))
        }
        val profile = runCatching { playerProfileRepository.findByPlayerId(friendUserId) }
            .getOrNull()
            ?: return listOf(error(playerId, "FRIEND_PROFILE_NOT_FOUND", legacyFallback("Không tìm thấy hồ sơ người chơi.")))
        return listOf(Delivery(
            ServerMessage.FriendProfileData(
                friendUserId,
                profile.copy(pushPreferences = PushPreferencesSnapshot())
            ),
            setOf(playerId)
        ))
    }

    private suspend fun loadMatchDetail(playerId: String, matchId: String): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val detail = runCatching { playerProfileRepository.findMatchDetail(playerId, matchId) }.getOrNull()
            ?: return listOf(error(playerId, "MATCH_NOT_FOUND", legacyFallback("Không tìm thấy chi tiết trận đấu.")))
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
            return listOf(error(playerId, "COSMETIC_LOCKED", legacyFallback("Vật phẩm chưa được mở khóa hoặc không hợp lệ.")))
        }
        val activeRoomId = mutex.withLock {
            sessionsByPlayerId[playerId]?.frameId = command.frameId
            roomFor(playerId)?.id
        }
        activeRoomId?.let { persistRoom(it) }
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
                legacyFallback("Biệt danh phải có từ 1 đến $MAX_PROFILE_DISPLAY_NAME_LENGTH ký tự.")
            ))
        }
        if (command.avatarId != null && command.avatarId !in PROFILE_AVATAR_IDS) {
            return listOf(error(playerId, "INVALID_AVATAR", legacyFallback("Ảnh đại diện không hợp lệ.")))
        }
        val session = mutex.withLock { sessionsByPlayerId[playerId]?.copy() }
        if (session == null) {
            return listOf(error(playerId, "SESSION_NOT_FOUND", legacyFallback("Phiên chơi không còn hợp lệ.")))
        }
        if (session.resumeToken != null) {
            return listOf(error(
                playerId,
                "ACCOUNT_REQUIRED",
                legacyFallback("Hãy lưu tài khoản khách trước khi chỉnh sửa hồ sơ.")
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
                    legacyFallback("Ảnh đại diện này được mở khóa sau 50 lần điểm danh.")
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
                legacyFallback("Chưa thể lưu hồ sơ. Vui lòng thử lại.")
            ))
        }
        val activeRoomId = mutex.withLock {
            sessionsByPlayerId[playerId]?.displayName = safeName
            sessionsByPlayerId[playerId]?.avatarId = command.avatarId
            roomFor(playerId)?.id
        }
        activeRoomId?.let { persistRoom(it) }
        return loadProfile(playerId) + Delivery(roomList()) + presenceUpdates(playerId)
    }

    private suspend fun updatePushPreferences(
        playerId: String,
        command: ClientMessage.UpdatePushPreferences
    ): List<Delivery> {
        if (!isAccountSession(playerId)) return listOf(accountRequired(playerId))
        val updated = runCatching {
            playerProfileRepository.updatePushPreferences(playerId, command.preferences)
        }.onFailure { error ->
            System.err.println("Could not update push preferences for $playerId: ${error.message}")
        }.getOrDefault(false)
        return if (updated) {
            loadProfile(playerId)
        } else {
            listOf(error(
                playerId,
                "PUSH_PREFERENCES_UNAVAILABLE",
                legacyFallback("Chưa thể lưu tùy chọn thông báo. Vui lòng thử lại.")
            ))
        }
    }

    private suspend fun loadLeaderboard(playerId: String): List<Delivery> {
        val sessionExists = mutex.withLock { playerId in sessionsByPlayerId }
        if (!sessionExists) {
            return listOf(error(playerId, "SESSION_NOT_FOUND", legacyFallback("Phiên chơi không còn hợp lệ.")))
        }
        val leaderboard = runCatching {
            leaderboardRepository.load(playerId, LEADERBOARD_SIZE)
        }.onFailure {
            System.err.println("Could not load leaderboard $playerId: ${it.message}")
        }.getOrElse {
            return listOf(error(playerId, "LEADERBOARD_UNAVAILABLE", legacyFallback("Chưa tải được bảng xếp hạng.")))
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
            if (maintenancePolicy.isFullyClosed) return@withLock emptyList<TimedAdvance>()
            val now = nowMillis()
            rooms.values.mapNotNull { room ->
                if (room.id in preparedStarts) return@mapNotNull null
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
                                room.activePlayerIds()
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
                            room.activePlayerIds()
                        ),
                        completedMatch = room.takeCompletedMatch()
                    )
                }
            }
        }
        val tournamentDeliveries = advances.mapNotNull(TimedAdvance::completedMatch).flatMap { settleCompletedMatch(it) }
        advances.forEach { advance ->
            when (val message = advance.delivery.message) {
                is ServerMessage.GameFinished -> message.game.roomId
                is ServerMessage.GameStateUpdated -> message.game.roomId
                is ServerMessage.RematchStatus -> message.game.roomId
                else -> null
            }?.let { persistRoom(it) }
        }
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
        if (maintenancePolicy.isFullyClosed) return emptyList()
        val removedInvitations = mutableListOf<RoomInvitationRecord>()
        val cleanup = mutex.withLock {
            // Publication can close admission while cleanup waits for a suspended engine operation.
            // Accepted results remain in the independent durable settlement retry queue.
            if (maintenancePolicy.isFullyClosed) return emptyList()
            val now = nowMillis()
            pendingReconnectResultsByPlayerId.entries.removeIf {
                now - it.value.createdAtMillis >= ROOM_RECONNECT_GRACE_MILLIS
            }
            pendingAccountTokensByPlayerId.entries.removeIf {
                now - it.value.createdAtMillis >= ROOM_RECONNECT_GRACE_MILLIS
            }
            val affectedInvitationUsers = roomInvitations.values
                .filter { it.expiresAtMillis <= now }
                .onEach(removedInvitations::add)
                .flatMapTo(mutableSetOf()) { listOf(it.inviterId, it.inviteeId) }
            roomInvitations.entries.removeAll { it.value.expiresAtMillis <= now }
            val expiredPlayerIds = sessionsByPlayerId.values
                .filter { session ->
                    if (preparedStarts.values.any { session.playerId in it.playerIds }) return@filter false
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
            val handledRoomIds = mutableSetOf<String>()
            val completedMatches = mutableListOf<CompletedMatch>()
            val advancingDisconnectedPlayers = mutableSetOf<String>()
            expiredPlayerIds.forEach { playerId ->
                roomFor(playerId)?.takeIf { handledRoomIds.add(it.id) }?.let { room ->
                    val tournamentForfeitWinner = room.takeIf {
                        it.tournamentId != null && it.phase == RoomPhase.PLAYING
                    }?.playerIds()?.firstOrNull { it != playerId }
                    if (tournamentForfeitWinner != null) {
                        advancingDisconnectedPlayers += tournamentForfeitWinner
                        room.forcedWinnerId = tournamentForfeitWinner
                        room.finishedPlayerIds += playerId
                        room.phase = RoomPhase.FINISHED
                        room.finishedAtEpochMillis = now
                        room.sequence++
                        room.takeCompletedMatch()?.let(completedMatches::add)
                        deliveries += Delivery(
                            ServerMessage.GameFinished(room.snapshot()),
                            room.activePlayerIds()
                        )
                    } else if (room.phase == RoomPhase.PLAYING) {
                        val forcedWinnerId = room.playerIds().firstOrNull {
                            it != playerId && sessionsByPlayerId[it]?.isConnected == true
                        }
                        if (forcedWinnerId != null) {
                            room.forcedWinnerId = forcedWinnerId
                            room.finishedPlayerIds += playerId
                            room.phase = RoomPhase.FINISHED
                            room.finishedAtEpochMillis = now
                            room.sequence++
                            val snapshot = room.snapshot()
                            pendingReconnectResultsByPlayerId[playerId] = PendingReconnectResult(snapshot, now)
                            sessionsByPlayerId[playerId]?.accountResumeToken?.let {
                                pendingAccountTokensByPlayerId[playerId] = PendingAccountToken(it, now)
                            }
                            room.takeCompletedMatch()?.let(completedMatches::add)
                            deliveries += Delivery(ServerMessage.GameFinished(snapshot), room.activePlayerIds())
                            removeRoomIfSettled(room)
                            removedRoomIds += room.id
                        } else {
                            removedRoomIds += room.id
                            removeRoomIfSettled(room)
                            deliveries += Delivery(
                                ServerMessage.RoomClosed(
                                    roomId = room.id,
                                    reason = legacyFallback("Cả hai người chơi đã mất kết nối quá lâu."),
                                    messageKey = TextKey.RoomDisconnectedTooLongNotice.name,
                                    code = "ROOM_DISCONNECTED_TOO_LONG"
                                ),
                                room.playerIds()
                            )
                        }
                    } else {
                        removedRoomIds += room.id
                        removeRoomIfSettled(room)
                        deliveries += Delivery(
                            ServerMessage.RoomClosed(
                                roomId = room.id,
                                reason = legacyFallback("Người chơi đã mất kết nối quá lâu."),
                                messageKey = TextKey.RoomDisconnectedTooLongNotice.name,
                                code = "ROOM_DISCONNECTED_TOO_LONG"
                            ),
                            room.playerIds()
                        )
                    }
                }
                // Pending durable results still need participant identity when snapshotting the room.
                if (playerId !in advancingDisconnectedPlayers && pendingCompletedMatches.values.none { match ->
                    match.players.any { it.playerId == playerId }
                }) {
                    sessionsByPlayerId.remove(playerId)
                }
            }
            if (removedRoomIds.isNotEmpty()) {
                deliveries += Delivery(ServerMessage.RoomList(publicRooms()))
            }
            if (removedRoomIds.isNotEmpty()) {
                roomInvitations.values
                    .filter { it.roomId in removedRoomIds }
                    .onEach(removedInvitations::add)
                    .flatMapTo(affectedInvitationUsers) { listOf(it.inviterId, it.inviteeId) }
                roomInvitations.entries.removeAll { it.value.roomId in removedRoomIds }
            }
            affectedInvitationUsers.forEach { affectedUserId ->
                val session = sessionsByPlayerId[affectedUserId]
                if (session?.isConnected == true && session.resumeToken == null) {
                    deliveries += Delivery(
                        roomInvitationsDataFor(affectedUserId),
                        setOf(affectedUserId)
                    )
                }
            }
            CleanupResult(deliveries, removedRoomIds, completedMatches)
        }
        notificationRepository.deleteExpiredRoomInvitations(nowMillis())
        removedInvitations.forEach { invitation ->
            notificationRepository.dismissNotifications(
                invitation.inviteeId,
                "room:${invitation.id}",
                nowMillis()
            )
        }
        val tournamentDeliveries = cleanup.completedMatches.flatMap { settleCompletedMatch(it) }
        cleanup.removedRoomIds.forEach { persistRoom(it) }
        return cleanup.deliveries + tournamentDeliveries +
            refreshNotificationsFor(removedInvitations.mapTo(mutableSetOf()) { it.inviteeId })
    }

    private fun roomInvitationMessagesFor(playerId: String): List<ServerMessage.RoomInvitation> =
        roomInvitations.values
            .filter { it.inviteeId == playerId }
            .sortedByDescending { it.expiresAtMillis }
            .map(RoomInvitationRecord::toMessage)

    private fun roomInvitationsDataFor(playerId: String) = ServerMessage.RoomInvitationsData(
        invitations = roomInvitationMessagesFor(playerId),
        outgoingFriendUserIds = roomInvitations.values
            .filter { it.inviterId == playerId }
            .sortedByDescending { it.expiresAtMillis }
            .map { it.inviteeId }
            .distinct()
    )

    private fun createRoom(player: GuestSession, command: ClientMessage.CreateRoom): List<Delivery> {
        if (activeTournamentFor(player.playerId) != null) {
            return listOf(error(player.playerId, "TOURNAMENT_ACTIVE", legacyFallback("Hãy rời hoặc hoàn tất giải đấu hiện tại trước.")))
        }
        if (roomFor(player.playerId) != null) {
            return listOf(error(player.playerId, "ALREADY_IN_ROOM", legacyFallback("Bạn đang ở trong một phòng khác.")))
        }
        val name = command.roomName.trim().take(MAX_ROOM_NAME_LENGTH)
        if (name.isEmpty()) {
            return listOf(error(player.playerId, "INVALID_ROOM_NAME", legacyFallback("Tên phòng không được để trống.")))
        }
        matchmakingEntries.remove(player.playerId)
        val room = Room(
            id = UUID.randomUUID().toString(),
            name = name,
            hostId = player.playerId,
            password = command.password.takeIf(String::isNotBlank)?.let(PasswordHash::create),
            gameMode = command.gameMode,
            matchType = command.matchType
        )
        rooms[room.id] = room
        return listOf(
            Delivery(ServerMessage.RoomCreated(room.snapshot()), setOf(player.playerId)),
            Delivery(ServerMessage.RoomList(publicRooms()))
        )
    }

    private fun joinRoom(player: GuestSession, command: ClientMessage.JoinRoom): List<Delivery> {
        if (activeTournamentFor(player.playerId) != null) {
            return listOf(error(player.playerId, "TOURNAMENT_ACTIVE", legacyFallback("Hãy rời hoặc hoàn tất giải đấu hiện tại trước.")))
        }
        if (roomFor(player.playerId) != null) {
            return listOf(error(player.playerId, "ALREADY_IN_ROOM", legacyFallback("Bạn đang ở trong một phòng khác.")))
        }
        val room = rooms[command.roomId]
            ?: return listOf(error(player.playerId, "ROOM_NOT_FOUND", legacyFallback("Phòng không còn tồn tại.")))
        if (room.password != null && !room.password.matches(command.password)) {
            return listOf(error(player.playerId, "WRONG_PASSWORD", legacyFallback("Mật khẩu phòng không đúng.")))
        }

        matchmakingEntries.remove(player.playerId)
        
        if (command.asSpectator) {
            room.spectatorIds.add(player.playerId)
            room.sequence++
            return listOf(Delivery(ServerMessage.RoomUpdated(room.snapshot()), room.participantIds()))
        }

        val maxPlayers = if (room.gameMode == com.hienthai.fastowin.protocol.ProtocolGameMode.TEAM_2V2) 4 else 2
        if (room.phase != RoomPhase.WAITING || room.playerIds().size >= maxPlayers) {
            return listOf(error(player.playerId, "ROOM_FULL", legacyFallback("Phòng đã đầy hoặc đang chơi.")))
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

    private suspend fun setReady(player: GuestSession, command: ClientMessage.SetReady): HandleResult {
        val room = rooms[command.roomId]
            ?: return HandleResult(listOf(error(player.playerId, "ROOM_NOT_FOUND", legacyFallback("Phòng không còn tồn tại."))))
        if (player.playerId !in room.activePlayerIds()) {
            return HandleResult(listOf(error(player.playerId, "NOT_IN_ROOM", legacyFallback("Bạn không ở trong phòng này."))))
        }
        if (room.phase != RoomPhase.WAITING) {
            return HandleResult(listOf(error(player.playerId, "ROOM_NOT_WAITING", legacyFallback("Phòng không còn ở trạng thái chờ."))))
        }
        if (command.ready) room.readyPlayerIds += player.playerId else room.readyPlayerIds -= player.playerId
        room.sequence++
        val participants = room.playerIds()
        if (participants.size == 2 && room.readyPlayerIds.containsAll(participants)) {
            if (room.tournamentId == null) {
                return HandleResult(emptyList(), preparedStart = prepareStart(room, room.matchId, player.playerId))
            }
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
            ?: return HandleResult(listOf(error(player.playerId, "ROOM_NOT_FOUND", legacyFallback("Phòng không còn tồn tại."))))
        if (room.hostId != player.playerId) {
            return HandleResult(listOf(error(player.playerId, "HOST_REQUIRED", legacyFallback("Chỉ chủ phòng mới có thể mời người chơi ra ngoài."))))
        }
        if (room.phase != RoomPhase.WAITING || command.playerId !in room.playerIds()) {
            return HandleResult(listOf(error(player.playerId, "PLAYER_NOT_IN_ROOM", legacyFallback("Người chơi không còn trong phòng."))))
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
                Delivery(ServerMessage.RoomClosed(
                    room.id,
                    legacyFallback("Chủ phòng đã mời bạn ra khỏi phòng."),
                    TextKey.RoomKickedNotice.name,
                    code = "ROOM_KICKED"
                ), setOf(kickedPlayerId)),
                Delivery(ServerMessage.RoomUpdated(room.snapshot()), setOf(room.hostId)),
                Delivery(ServerMessage.RoomList(publicRooms()))
            ),
            changedRoomId = room.id
        )
    }

    private fun leaveRoom(player: GuestSession, command: ClientMessage.LeaveRoom): HandleResult {
        val room = rooms[command.roomId]
            ?: return HandleResult(listOf(error(player.playerId, "ROOM_NOT_FOUND", legacyFallback("Phòng không còn tồn tại."))))
        if (player.playerId !in room.activePlayerIds() && player.playerId !in room.spectatorIds) {
            return HandleResult(listOf(error(player.playerId, "NOT_IN_ROOM", legacyFallback("Bạn không ở trong phòng này."))))
        }
        if (player.playerId in room.spectatorIds) {
            room.spectatorIds.remove(player.playerId)
            room.sequence++
            return HandleResult(
                deliveries = listOf(Delivery(ServerMessage.RoomUpdated(room.snapshot()), room.participantIds())),
                changedRoomId = room.id
            )
        }

        if (room.phase == RoomPhase.PLAYING) {
            val leavingTeamId = room.teamIds[player.playerId]
            val winnerId = room.playerIds().firstOrNull { candidateId ->
                candidateId != player.playerId &&
                    (room.gameMode != ProtocolGameMode.TEAM_2V2 || room.teamIds[candidateId] != leavingTeamId)
            } ?: return HandleResult(listOf(error(
                player.playerId,
                "FORFEIT_WINNER_NOT_FOUND",
                legacyFallback("Chưa thể xử lý rời trận lúc này.")
            )))
            room.forcedWinnerId = winnerId
            room.intentionalLeavePlayerIds += player.playerId
            room.finishedPlayerIds += player.playerId
            room.phase = RoomPhase.FINISHED
            room.finishedAtEpochMillis = nowMillis()
            room.sequence++
            val finished = ServerMessage.GameFinished(room.snapshot())
            val completedMatch = room.takeCompletedMatch()
            return HandleResult(
                deliveries = listOf(Delivery(finished, room.activePlayerIds())),
                completedMatch = completedMatch,
                changedRoomId = room.id
            )
        }

        if (room.phase == RoomPhase.FINISHED) {
            room.departedPlayerIds += player.playerId
            room.rematchRequestedPlayerIds.remove(player.playerId)
            room.sequence++
            val noPlayersRemain = room.activePlayerIds().isEmpty()
            if (noPlayersRemain) removeRoomIfSettled(room)
            return HandleResult(
                deliveries = listOf(
                    Delivery(
                        ServerMessage.RoomClosed(
                            room.id,
                            legacyFallback("Bạn đã rời màn kết quả."),
                            TextKey.ResultsLeftNotice.name,
                            code = "RESULTS_LEFT"
                        ),
                        setOf(player.playerId)
                    )
                ),
                changedRoomId = room.id
            )
        }

        val participants = room.participantIds()
        if (room.phase == RoomPhase.WAITING) {
            removeRoomIfSettled(room)
            return HandleResult(
                deliveries = listOf(
                    Delivery(
                        ServerMessage.RoomClosed(
                            roomId = room.id,
                            reason = if (player.playerId == room.hostId) {
                                legacyFallback("Chủ phòng đã rời phòng.")
                            } else {
                                legacyFallback("${player.displayName} đã rời phòng.")
                            },
                            messageKey = if (player.playerId == room.hostId) {
                                TextKey.RoomHostLeftNotice.name
                            } else {
                                TextKey.RoomPlayerLeftNotice.name
                            },
                            messageArgs = if (player.playerId == room.hostId) {
                                emptyMap()
                            } else {
                                mapOf("player" to player.displayName)
                            },
                            code = if (player.playerId == room.hostId) "ROOM_HOST_LEFT" else "ROOM_PLAYER_LEFT"
                        ),
                        participants
                    ),
                    Delivery(ServerMessage.RoomList(publicRooms()))
                ),
                changedRoomId = room.id
            )
        }

        if (player.playerId == room.hostId) {
            removeRoomIfSettled(room)
            return HandleResult(
                deliveries = listOf(
                    Delivery(ServerMessage.RoomClosed(
                        room.id,
                        legacyFallback("Chủ phòng đã rời phòng."),
                        TextKey.RoomHostLeftNotice.name,
                        code = "ROOM_HOST_LEFT"
                    ), participants),
                    Delivery(ServerMessage.RoomList(publicRooms()))
                ),
                changedRoomId = room.id
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
            return HandleResult(
                deliveries = listOf(
                    Delivery(ServerMessage.RoomUpdated(room.snapshot()), room.participantIds()),
                    Delivery(ServerMessage.RoomList(publicRooms()))
                ),
                changedRoomId = room.id
            )
        }
    }

    private suspend fun respondRematch(
        player: GuestSession,
        roomId: String,
        accept: Boolean
    ): HandleResult {
        val room = rooms[roomId]
            ?: return HandleResult(listOf(error(player.playerId, "ROOM_NOT_FOUND", legacyFallback("Phòng không còn tồn tại."))))
        if (player.playerId !in room.activePlayerIds()) {
            return HandleResult(listOf(error(player.playerId, "NOT_IN_ROOM", legacyFallback("Bạn không ở trong phòng này."))))
        }
        if (room.tournamentId != null) {
            return HandleResult(listOf(error(player.playerId, "TOURNAMENT_REMATCH_DISABLED", legacyFallback("Trận đấu giải không hỗ trợ đấu lại."))))
        }
        if (room.matchType == MatchType.RANKED) {
            return HandleResult(
                listOf(
                    error(
                        player.playerId,
                        "RANKED_REMATCH_DISABLED",
                        legacyFallback("Trận xếp hạng không hỗ trợ đấu lại. Hãy ghép một đối thủ mới.")
                    )
                )
            )
        }
        if (room.phase != RoomPhase.FINISHED) {
            return HandleResult(listOf(error(player.playerId, "REMATCH_NOT_AVAILABLE", legacyFallback("Chỉ có thể đấu lại sau khi trận kết thúc."))))
        }
        if (room.maintenanceAborted) {
            return HandleResult(listOf(error(player.playerId, "MAINTENANCE_REMATCH_UNAVAILABLE",
                legacyFallback("Trận này đã dừng do bảo trì. Hãy rời phòng rồi tạo hoặc tham gia phòng mới."))))
        }
        if (room.matchId in pendingCompletedMatches || room.matchId in settlementsInFlight) {
            return HandleResult(listOf(error(player.playerId, "REMATCH_NOT_AVAILABLE", legacyFallback("Kết quả trận đấu đang được lưu."))))
        }
        if (room.activePlayerIds().size != 2) {
            return HandleResult(listOf(error(player.playerId, "OPPONENT_LEFT", legacyFallback("Đối thủ đã rời phòng."))))
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
                    Delivery(ServerMessage.RematchStatus(room.snapshot(), RematchEvent.EXPIRED), room.activePlayerIds())
                ),
                changedRoomId = room.id
            )
        }

        if (!accept) {
            if (room.rematchRequestedPlayerIds.isEmpty()) {
                return HandleResult(listOf(error(player.playerId, "REMATCH_NOT_PENDING", legacyFallback("Không có yêu cầu đấu lại nào đang chờ."))))
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
                        room.activePlayerIds()
                    )
                ),
                changedRoomId = room.id
            )
        }

        if (room.rematchRequestedPlayerIds.isEmpty()) {
            room.rematchExpiresAtEpochMillis = nowMillis() + rematchTimeoutMillis
        }
        room.rematchRequestedPlayerIds += player.playerId
        if (!room.rematchRequestedPlayerIds.containsAll(room.activePlayerIds())) {
            room.sequence++
            return HandleResult(
                deliveries = listOf(
                    Delivery(
                        ServerMessage.RematchStatus(room.snapshot(), RematchEvent.REQUESTED, player.playerId),
                        room.activePlayerIds()
                    )
                ),
                changedRoomId = room.id
            )
        }

        return HandleResult(emptyList(), preparedStart = prepareStart(room, rematchIdFor(room.matchId), player.playerId))
    }
    private fun rematchIdFor(previousMatchId: String): String = UUID.nameUUIDFromBytes(
        "fasttowin-rematch:$previousMatchId".toByteArray(Charsets.UTF_8)
    ).toString()

    private fun selectNumber(player: GuestSession, command: ClientMessage.SelectNumber): HandleResult {
        val room = rooms[command.roomId]
            ?: return HandleResult(listOf(error(player.playerId, "ROOM_NOT_FOUND", legacyFallback("Phòng không còn tồn tại."), command.requestId)))
        if (player.playerId !in room.playerIds()) {
            return HandleResult(listOf(error(player.playerId, "NOT_IN_ROOM", legacyFallback("Bạn không ở trong phòng này."), command.requestId)))
        }
        if (command.requestId.isBlank() || command.requestId.length > MAX_REQUEST_ID_LENGTH) {
            return HandleResult(listOf(error(player.playerId, "INVALID_REQUEST_ID", legacyFallback("Mã yêu cầu không hợp lệ."))))
        }
        if (room.activationPending && nowMillis() < (room.startedAtEpochMillis ?: Long.MAX_VALUE)) {
            return HandleResult(listOf(error(player.playerId, "GAME_NOT_PLAYING", legacyFallback("Trận đấu chưa bắt đầu hoặc đã kết thúc."), command.requestId)))
        }
        val requestKey = "${player.playerId}:${command.requestId}"
        room.processedRequests[requestKey]?.let { previous ->
            return HandleResult(listOf(Delivery(previous, setOf(player.playerId))))
        }
        if (room.processedRequests.size >= MAX_REQUESTS_PER_MATCH) {
            return HandleResult(listOf(error(player.playerId, "TOO_MANY_REQUESTS", legacyFallback("Trận đấu có quá nhiều lượt gửi."))))
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
                deliveries = listOf(error(player.playerId, "GAME_NOT_PLAYING", legacyFallback("Trận đấu chưa bắt đầu hoặc đã kết thúc."), command.requestId)),
                completedMatch = completedMatch,
                changedRoomId = room.id.takeIf { completedMatch != null }
            )
        }
        if (player.playerId in room.finishedPlayerIds) {
            return HandleResult(listOf(error(
                player.playerId,
                "PLAYER_FINISHED",
                legacyFallback("Lượt chơi của bạn đã kết thúc."),
                command.requestId
            )))
        }
        val expectedNumber = room.targetFor(player.playerId)
        // Persisted with this first event, never through a separate activation-marker write.
        room.activationPending = false
        if (command.number != expectedNumber) {
            val rejected = error(player.playerId, "WRONG_NUMBER", legacyFallback("Chưa đúng số, thử lại nhé!"), command.requestId)
            room.processedRequests[requestKey] = rejected.message
            room.recordSelection(player.playerId, command, expectedNumber, SelectionResult.REJECTED)
            room.combos[player.playerId] = 0
            when (room.gameMode) {
                ProtocolGameMode.TIME_BONUS -> {
                    val deadline = room.deadlinesAtEpochMillis[player.playerId] ?: nowMillis()
                    room.deadlinesAtEpochMillis[player.playerId] = deadline - TIME_BONUS_WRONG_MILLIS
                    if (deadline - TIME_BONUS_WRONG_MILLIS <= nowMillis()) {
                        room.finishedPlayerIds += player.playerId
                    }
                }
                ProtocolGameMode.SURVIVAL -> {
                    val remainingLives = ((room.lives[player.playerId] ?: SURVIVAL_STARTING_LIVES) - 1).coerceAtLeast(0)
                    room.lives[player.playerId] = remainingLives
                    if (remainingLives == 0) {
                        room.finishedPlayerIds += player.playerId
                        room.forcedWinnerId = room.playerIds().firstOrNull { it != player.playerId }
                    }
                }
                ProtocolGameMode.COMBO -> Unit
                else -> Unit
            }
            room.sequence++
            room.finishIfPlayersDone(nowMillis())
            val update = ServerMessage.GameStateUpdated(
                game = room.snapshot(),
                acceptedNumber = command.number,
                selectedByPlayerId = player.playerId,
                selectionAccepted = false
            )
            return HandleResult(
                deliveries = listOf(Delivery(update, room.participantIds()), rejected),
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
            // Shared boards are decided by score, not by who taps the final number.
            // Only independent time-bonus boards award the first finisher the win.
            if (room.gameMode == ProtocolGameMode.TIME_BONUS) {
                room.forcedWinnerId = player.playerId
            }
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
            ?: return HandleResult(listOf(error(player.playerId, "ROOM_NOT_FOUND", legacyFallback("Không tìm thấy phòng."))))

        if (player.playerId !in room.activePlayerIds() && player.playerId !in room.spectatorIds) {
            return HandleResult(listOf(error(player.playerId, "NOT_IN_ROOM", legacyFallback("Bạn không ở trong phòng này."))))
        }

        val allParticipants = room.participantIds()
        val delivery = Delivery(ServerMessage.EmojiBroadcast(player.playerId, command.emojiId), allParticipants)
        return HandleResult(listOf(delivery))
    }

    private fun createTournamentMatches(maxPlayers: Int): MutableList<TournamentMatch> {
        val matches = mutableListOf<TournamentMatch>()
        var round = 1
        var matchesInRound = maxPlayers / 2
        while (matchesInRound >= 1) {
            repeat(matchesInRound) { position ->
                matches += TournamentMatch(
                    id = UUID.randomUUID().toString(),
                    round = round,
                    position = position + 1
                )
            }
            round += 1
            matchesInRound /= 2
        }
        return matches
    }

    private fun createTournamentRoom(tournament: Tournament, match: TournamentMatch, publish: Boolean = true): Room {
        val playerOneId = checkNotNull(match.playerOneId)
        val playerTwoId = checkNotNull(match.playerTwoId)
        val roomId = UUID.randomUUID().toString()
        val finalRound = tournament.matches.maxOf(TournamentMatch::round)
        val roundName = when (match.round) {
            finalRound -> legacyFallback("Chung kết")
            finalRound - 1 -> legacyFallback("Bán kết ${match.position}")
            finalRound - 2 -> legacyFallback("Tứ kết ${match.position}")
            finalRound - 3 -> legacyFallback("Vòng 1/8 ${match.position}")
            else -> legacyFallback("Vòng ${match.round} • Trận ${match.position}")
        }
        val room = Room(
            id = roomId,
            matchId = roomId,
            name = "${tournament.name} • $roundName",
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
        room.activationPending = !publish && room.gameMode in TIMED_GAME_MODES
        if (publish) rooms[room.id] = room
        match.roomId = room.id
        match.phase = TournamentMatchPhase.PLAYING
        return room
    }

    private data class StagedProgression(var batch: TournamentProgression, val paused: TournamentSnapshot, val rooms: List<Room>)
    private val pendingTournamentProgressions = mutableMapOf<String, StagedProgression>()

    /** One pairing implementation for ordinary completion and reopening. Called under engine mutex. */
    private fun stageProgression(tournament: Tournament, completedRoom: Room?): StagedProgression {
        val candidate = tournament.snapshot().toTournament()
        if (completedRoom != null) {
            val match = candidate.matches.single { it.roomId == completedRoom.id }
            completedRoom.snapshot()
            match.winnerPlayerId = checkNotNull(completedRoom.winnerId())
            match.phase = TournamentMatchPhase.FINISHED
            if (match.round == candidate.matches.maxOf { it.round }) {
                candidate.phase = TournamentPhase.FINISHED
                candidate.championPlayerId = match.winnerPlayerId
                candidate.finishedAtMillis = nowMillis()
            }
        }
        val paused = candidate.snapshot()
        val generated = mutableListOf<Room>()
        if (!maintenancePolicy.blocksNewIntent && candidate.phase == TournamentPhase.RUNNING) {
            val next = candidate.matches.filter { it.phase == TournamentMatchPhase.PENDING }.minByOrNull { it.round }
            if (next != null && next.round > 1) {
                val previous = candidate.matches.filter { it.round == next.round - 1 }.sortedBy { it.position }
                if (previous.isNotEmpty() && previous.all { it.phase == TournamentMatchPhase.FINISHED && it.winnerPlayerId != null }) {
                    val winners = previous.map { checkNotNull(it.winnerPlayerId) }
                    candidate.matches.filter { it.round == next.round }.sortedBy { it.position }.forEachIndexed { index, match ->
                        match.playerOneId = winners[index * 2]; match.playerTwoId = winners[index * 2 + 1]
                        generated += createTournamentRoom(candidate, match, publish = false)
                    }
                }
            }
        }
        return StagedProgression(TournamentProgression(candidate.snapshot(), generated.map { it.toStoredRoom() }, completedRoom?.id), paused, generated)
    }

    /** Requires persistenceMutex. No staged room/bracket is observable before repository acknowledgment. */
    private suspend fun commitStagedProgression(id: String, staged: StagedProgression): List<Delivery> {
        var acknowledged = false
        repeat(2) {
            if (!acknowledged) {
                mutex.withLock {
                    val activationAt = nowMillis() + 1_000
                    staged.rooms.filter { it.activationPending }.forEach { room ->
                        val shift = activationAt - checkNotNull(room.startedAtEpochMillis)
                        room.startedAtEpochMillis = activationAt
                        room.deadlinesAtEpochMillis.replaceAll { _, deadline -> deadline + shift }
                    }
                    staged.batch = staged.batch.copy(newRooms = staged.rooms.map { it.toStoredRoom() })
                }
                tournamentRepository.commitProgression(staged.batch, activeRoomRepository, playerProfileRepository)
                acknowledged = staged.rooms.none { it.activationPending && nowMillis() >= checkNotNull(it.startedAtEpochMillis) }
            }
        }
        check(acknowledged) { "Timed round activation awaits timely storage acknowledgment" }
        var published: List<Delivery>? = mutex.withLock {
            check(staged.rooms.none { it.activationPending && nowMillis() >= checkNotNull(it.startedAtEpochMillis) }) {
                "Timed round activation elapsed before publication"
            }
            if (staged.rooms.isNotEmpty() && (maintenancePolicy.blocksNewIntent ||
                (staged.paused.phase == TournamentPhase.LOBBY && staged.paused.schedule != null &&
                    !scheduledRosterAvailable(checkNotNull(tournaments[id]))))) null
            else {
                tournamentInvitations.entries.removeAll { it.value.tournamentId == id }
                publishProgression(id, staged.batch.tournament, staged.rooms, staged.batch.completedRoomId)
            }
        }
        if (published == null) {
            // Policy changed during storage: keep the completed round, durably return the unexposed next round to PENDING.
            val paused = TournamentProgression(staged.paused, emptyList(), staged.batch.completedRoomId, staged.rooms.map { it.id })
            tournamentRepository.commitProgression(paused, activeRoomRepository, playerProfileRepository)
            published = mutex.withLock { publishProgression(id, staged.paused, emptyList(), staged.batch.completedRoomId) }
        }
        mutex.withLock { pendingTournamentProgressions.remove(id) }
        return checkNotNull(published)
    }

    private fun publishProgression(id: String, snapshot: TournamentSnapshot, generated: List<Room>, completedRoomId: String?,
        recipients: Set<String> = snapshot.players.mapTo(linkedSetOf()) { it.playerId }): List<Delivery> {
        tournaments[id] = snapshot.toTournament()
        completedRoomId?.let { roomId ->
            rooms.remove(roomId)?.let { pendingCompletedMatches.remove(it.matchId) }
            pendingRoomRemovals.remove(roomId)
        }
        generated.forEach { rooms[it.id] = it }
        return listOf(Delivery(ServerMessage.TournamentUpdated(snapshot.copy(serverTimeEpochMillis = nowMillis())), recipients)) +
            generated.map { Delivery(ServerMessage.GameStarted(it.snapshot()), it.playerIds()) }
    }

    private suspend fun advanceTournamentAfterMatch(matchId: String): List<Delivery> = persistenceMutex.withLock {
        val stage = mutex.withLock {
            val room = rooms.values.firstOrNull { it.matchId == matchId }
                ?: error("Accepted result room missing before progression acknowledgment")
            val id = room.tournamentId ?: return@withLock null
            check(!room.maintenanceAborted)
            check(pendingTournamentProgressions[id].let { it == null || it.batch.completedRoomId == room.id }) {
                "Previous tournament progression awaits acknowledgment"
            }
            id to pendingTournamentProgressions.getOrPut(id) {
                stageProgression(checkNotNull(tournaments[id]), room)
            }
        } ?: return@withLock emptyList()
        commitStagedProgression(stage.first, stage.second)
    }

    private suspend fun resumePendingTournamentRounds(): List<Delivery> = persistenceMutex.withLock {
        val stages = mutex.withLock {
            tournaments.values.filter { it.phase == TournamentPhase.RUNNING }.mapNotNull { tournament ->
                val staged = pendingTournamentProgressions[tournament.id] ?: stageProgression(tournament, null)
                if (staged.rooms.isEmpty()) null else (tournament.id to staged).also { pendingTournamentProgressions[tournament.id] = staged }
            }
        }
        stages.flatMap { (id, staged) -> commitStagedProgression(id, staged) }
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
        visibility = visibility,
        phase = phase,
        maxPlayers = maxPlayers,
        entryFee = entryFee,
        prizePool = prizePool,
        players = participants.map { participant ->
            TournamentPlayerSnapshot(
                playerId = participant.playerId,
                displayName = sessionsByPlayerId[participant.playerId]?.displayName ?: participant.displayName,
                isHost = participant.playerId == hostId,
                isCheckedIn = participant.isCheckedIn,
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
        finishedAtEpochMillis = finishedAtMillis,
        schedule = schedule,
        serverTimeEpochMillis = nowMillis(),
        recordedPlayers = if (schedule == null) emptyList() else (recordedPlayers + participants.map {
            TournamentPlayerSnapshot(it.playerId, it.displayName, isHost = it.playerId == hostId)
        }).distinctBy { it.playerId }
    )

    private fun TournamentSnapshot.toTournament(): Tournament = Tournament(
        id = tournamentId,
        name = name,
        hostId = hostPlayerId,
        gameMode = gameMode,
        visibility = visibility,
        maxPlayers = maxPlayers,
        entryFee = entryFee,
        prizePool = prizePool,
        participants = players.mapTo(mutableListOf()) { TournamentParticipant(it.playerId, it.displayName, it.isCheckedIn) },
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
        finishedAtMillis = finishedAtEpochMillis,
        schedule = schedule,
        recordedPlayers = recordedPlayers.toMutableList()
    )

    private fun roomFor(playerId: String): Room? = rooms.values.firstOrNull { playerId in it.activePlayerIds() }

    private fun removeRoomIfSettled(room: Room) {
        if (room.matchId !in pendingCompletedMatches && room.matchId !in settlementsInFlight) rooms.remove(room.id)
        else pendingRoomRemovals += room.id
    }

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
                isAccount = session.resumeToken == null,
                avatarId = session.avatarId,
                frameId = session.frameId,
                accountResumeToken = session.accountResumeToken
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
            maintenanceAborted = maintenanceAborted,
            activationPending = activationPending,
            eventRewards = eventRewards,
            pendingCompletedMatch = pendingCompletedMatches[matchId],
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
            departedPlayerIds = departedPlayerIds.toSet(),
            intentionalLeavePlayerIds = intentionalLeavePlayerIds.toSet(),
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
        eventRewards = eventRewards,
        intentionalLeavePlayerIds = intentionalLeavePlayerIds.toMutableSet(),
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
        departedPlayerIds = departedPlayerIds.toMutableSet(),
        forcedWinnerId = forcedWinnerId,
        scores = scores.toMutableMap().also { restoredScores ->
            restoredScores.putIfAbsent(host.playerId, 0)
            guest?.let { restoredScores.putIfAbsent(it.playerId, 0) }
        },
        sequence = sequence,
        startedAtEpochMillis = startedAtEpochMillis,
        finishedAtEpochMillis = finishedAtEpochMillis,
        resultQueued = resultQueued,
        maintenanceAborted = maintenanceAborted,
        activationPending = activationPending,
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
        if (!maintenanceAborted && phase == RoomPhase.FINISHED && tournamentId != null && winnerId() == null) {
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
            eventRewards = eventRewards.takeIf { startedAtEpochMillis != null },
            rewardBreakdowns = if (phase == RoomPhase.FINISHED && !maintenanceAborted) playerIds().associateWith { id ->
                val outcome = when {
                    gameMode == ProtocolGameMode.TEAM_2V2 -> when {
                        winnerTeamId() == null -> MatchOutcome.DRAW
                        teamIds[id] == winnerTeamId() -> MatchOutcome.WIN
                        else -> MatchOutcome.LOSS
                    }
                    winnerId() == null -> MatchOutcome.DRAW
                    winnerId() == id -> MatchOutcome.WIN
                    else -> MatchOutcome.LOSS
                }
                RewardEconomy.matchRewardBreakdown(matchType, outcome, id in intentionalLeavePlayerIds, eventRewards)
            } else emptyMap(),
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
                        teamId = teamIds[id],
                        avatarId = session.avatarId,
                        frameId = session.frameId
                    )
                }
            },
            spectators = spectatorIds.mapNotNull { id ->
                sessionsByPlayerId[id]?.let { session ->
                    PlayerSnapshot(
                        id = id,
                        name = session.displayName,
                        score = 0,
                        avatarId = session.avatarId,
                        frameId = session.frameId
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
        activationPending = false
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
        if (maintenanceAborted || phase != RoomPhase.FINISHED || resultQueued) return null
        val startedAt = startedAtEpochMillis ?: return null
        val winnerId = winnerId()
        resultQueued = true
        activationPending = false
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
                        },
                        intentionalLeave = playerId in intentionalLeavePlayerIds
                    )
                }
            },
            events = selectionEvents.toList(),
            eventRewards = eventRewards
        ).also { pendingCompletedMatches[it.matchId] = it }
    }

    private fun Room.winnerTeamId(): String? {
        if (maintenanceAborted) return null
        if (gameMode != com.hienthai.fastowin.protocol.ProtocolGameMode.TEAM_2V2) return null
        forcedWinnerId?.let { return teamIds[it] }
        val teamScores = playerIds().groupBy { teamIds[it] ?: "" }
            .mapValues { (_, members) -> members.sumOf { scores[it] ?: 0 } }
        val highestScore = teamScores.values.maxOrNull() ?: 0
        return teamScores.filterValues { it == highestScore }.keys.singleOrNull()
    }

    private fun Room.winnerId(): String? {
        if (maintenanceAborted) return null
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
        requestId: String? = null,
        messageArgs: Map<String, String> = emptyMap()
    ) = Delivery(
        ServerMessage.Error(
            code = code,
            message = message,
            requestId = requestId,
            messageKey = protocolTextKeyForCode(code)?.name,
            messageArgs = messageArgs
        ),
        setOf(playerId)
    )

    private data class GuestSession(
        val playerId: String,
        var resumeToken: String?,
        var displayName: String,
        var accountResumeToken: String? = null,
        var avatarId: String? = null,
        var frameId: String = "frame_default",
        var isConnected: Boolean = true,
        var disconnectedAtMillis: Long? = null,
        var latencyMillis: Long? = null,
        var protocolVersion: Int = PROTOCOL_VERSION
    )

    private data class PendingReconnectResult(
        val snapshot: GameSnapshot,
        val createdAtMillis: Long
    )

    private data class PendingAccountToken(val token: String, val createdAtMillis: Long)

    private data class HandleResult(
        val deliveries: List<Delivery>,
        val completedMatch: CompletedMatch? = null,
        val changedRoomId: String? = null,
        val preparedStart: PreparedMatchStart? = null
    )

    private data class TimedAdvance(
        val delivery: Delivery,
        val completedMatch: CompletedMatch?
    )

    private data class CleanupResult(
        val deliveries: List<Delivery>,
        val removedRoomIds: Set<String>,
        val completedMatches: List<CompletedMatch>
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
        val departedPlayerIds: MutableSet<String> = mutableSetOf(),
        val intentionalLeavePlayerIds: MutableSet<String> = mutableSetOf(),
        var forcedWinnerId: String? = null,
        val scores: MutableMap<String, Int> = mutableMapOf(hostId to 0),
        var sequence: Long = 0,
        var startedAtEpochMillis: Long? = null,
        var finishedAtEpochMillis: Long? = null,
        var resultQueued: Boolean = false,
        var maintenanceAborted: Boolean = false,
        var activationPending: Boolean = false,
        var eventRewards: MatchRewardEventSnapshot = MatchRewardEventSnapshot(),
        val readyPlayerIds: MutableSet<String> = mutableSetOf(),
        val rematchRequestedPlayerIds: MutableSet<String> = mutableSetOf(),
        var rematchExpiresAtEpochMillis: Long? = null,
        val processedRequests: MutableMap<String, ServerMessage> = mutableMapOf(),
        val selectionEvents: MutableList<MatchSelectionEvent> = mutableListOf()
    ) {
        fun playerIds(): Set<String> = setOfNotNull(hostId, guestId) + extraGuestIds
        fun activePlayerIds(): Set<String> = playerIds() - departedPlayerIds
        fun participantIds(): Set<String> = activePlayerIds() + spectatorIds

        fun targetFor(playerId: String): Int {
            val index = targetIndexes[playerId] ?: 0
            return targetOrder.getOrNull(index) ?: (GAME_NUMBER_COUNT + 1)
        }

        fun startMatch(now: Long, legacyTimeAttackMillis: Long, rewards: MatchRewardEventSnapshot = MatchRewardEventSnapshot()) {
            eventRewards = rewards
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
            departedPlayerIds.clear()
            intentionalLeavePlayerIds.clear()
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
        val displayName: String,
        val isCheckedIn: Boolean = false
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
        val visibility: TournamentVisibility = TournamentVisibility.PRIVATE,
        val maxPlayers: Int = DEFAULT_TOURNAMENT_PLAYER_COUNT,
        val entryFee: Int = 0,
        var prizePool: Int = 0,
        val participants: MutableList<TournamentParticipant>,
        val matches: MutableList<TournamentMatch>,
        var phase: TournamentPhase = TournamentPhase.LOBBY,
        var championPlayerId: String? = null,
        val createdAtMillis: Long,
        var startedAtMillis: Long? = null,
        var finishedAtMillis: Long? = null,
        val schedule: TournamentScheduleSnapshot? = null,
        val recordedPlayers: MutableList<TournamentPlayerSnapshot> = mutableListOf()
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
        val maxPlayers: Int,
        val entryFee: Int,
        val schedule: TournamentScheduleSnapshot?,
        val expiresAtMillis: Long
    ) {
        fun snapshot(serverTime: Long) = TournamentInvitationSnapshot(
            invitationId = id,
            tournamentId = tournamentId,
            tournamentName = tournamentName,
            hostPlayerId = hostId,
            hostDisplayName = hostDisplayName,
            gameMode = gameMode,
            maxPlayers = maxPlayers,
            expiresAtEpochMillis = expiresAtMillis,
            entryFee = entryFee,
            schedule = schedule,
            serverTimeEpochMillis = serverTime
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
        if (name.isBlank() || name.length > 32) return listOf(error(playerId, "INVALID_CLAN_NAME", legacyFallback("Tên clan không hợp lệ.")))
        return when (clanRepository.createClan(playerId, name, description).status) {
            ClanCreationStatus.CREATED -> listOf(Delivery(ServerMessage.ClanActionResult(
                true,
                legacyFallback("Tạo clan thành công"),
                "create_clan",
                TextKey.ClanCreatedNotice.name
            ), setOf(playerId)))
            ClanCreationStatus.INSUFFICIENT_FUNDS -> listOf(
                error(
                    playerId,
                    "CLAN_CREATION_INSUFFICIENT_FUNDS",
                    legacyFallback("Cần 2.000 vàng và 20 Gem để tạo bang.")
                )
            )
            ClanCreationStatus.ALREADY_MEMBER ->
                listOf(error(playerId, "ALREADY_IN_CLAN", legacyFallback("Bạn đã tham gia một clan.")))
            ClanCreationStatus.PLAYER_NOT_FOUND ->
                listOf(error(playerId, "PROFILE_NOT_FOUND", legacyFallback("Không tìm thấy hồ sơ người chơi.")))
            ClanCreationStatus.FAILED ->
                listOf(error(playerId, "CREATE_CLAN_FAILED", legacyFallback("Tạo clan thất bại. Có thể tên đã được sử dụng.")))
        }
    }

    private suspend fun joinClan(playerId: String, clanId: String): List<Delivery> {
        val clan = clanRepository.getClanById(clanId)
            ?: return listOf(error(playerId, "CLAN_NOT_FOUND", legacyFallback("Không tìm thấy clan.")))
        return when (clanRepository.requestJoinClan(playerId, clanId)) {
            ClanJoinRequestResult.REQUESTED -> {
                val requester = playerProfileRepository.findByPlayerId(playerId)
                val requesterName = requester?.displayName ?: legacyFallback("Một người chơi")
                val notification = NotificationSnapshot(
                    id = UUID.randomUUID().toString(),
                    kind = NotificationKind.CLAN_INVITATION,
                    title = legacyFallback("Yêu cầu vào clan"),
                    message = legacyFallback("$requesterName muốn vào clan ${clan.name}."),
                    createdAtEpochMillis = nowMillis(),
                    destination = NotificationDestination.CLAN,
                    actionData = clanId,
                    titleKey = TextKey.PendingApproval.name,
                    messageKey = TextKey.NotificationClanJoinRequestMessage.name,
                    messageArgs = mapOf(
                        "player" to requesterName,
                        "clan" to clan.name
                    )
                )
                notificationRepository.createNotifications(clan.ownerId, listOf(notification))
                val updatedClan = clanRepository.getClanById(clanId)
                buildList {
                    add(Delivery(
                        ServerMessage.ClanActionResult(
                            success = true,
                            message = legacyFallback("Đã gửi yêu cầu. Chờ bang chủ duyệt."),
                            action = "request_join_clan",
                            messageKey = TextKey.ClanJoinRequestedNotice.name
                        ),
                        setOf(playerId)
                    ))
                    updatedClan?.let {
                        add(Delivery(ServerMessage.ClanInfoData(it), setOf(clan.ownerId)))
                    }
                    add(Delivery(
                        ServerMessage.NotificationsData(notificationRepository.loadNotifications(clan.ownerId)),
                        setOf(clan.ownerId)
                    ))
                }
            }
            ClanJoinRequestResult.CLAN_NOT_FOUND ->
                listOf(error(playerId, "CLAN_NOT_FOUND", legacyFallback("Không tìm thấy clan.")))
            ClanJoinRequestResult.CLAN_FULL ->
                listOf(error(playerId, "CLAN_FULL", legacyFallback("Clan đã đủ thành viên.")))
            ClanJoinRequestResult.OWN_CLAN ->
                listOf(error(playerId, "OWN_CLAN_JOIN_REQUEST", legacyFallback("Bạn không thể xin vào bang do chính mình tạo.")))
            ClanJoinRequestResult.ALREADY_MEMBER ->
                listOf(error(playerId, "ALREADY_IN_CLAN", legacyFallback("Bạn đã tham gia một clan.")))
            ClanJoinRequestResult.FAILED ->
                listOf(error(playerId, "JOIN_CLAN_FAILED", legacyFallback("Không thể gửi yêu cầu vào clan này.")))
        }
    }

    private suspend fun respondClanJoinRequest(
        playerId: String,
        command: ClientMessage.RespondClanJoinRequest
    ): List<Delivery> {
        val clan = clanRepository.getClanById(command.clanId)
            ?: return listOf(error(playerId, "CLAN_NOT_FOUND", legacyFallback("Không tìm thấy clan.")))
        if (clan.ownerId != playerId) {
            return listOf(error(playerId, "NOT_CLAN_OWNER", legacyFallback("Chỉ bang chủ mới có thể duyệt thành viên.")))
        }
        val requester = playerProfileRepository.findByPlayerId(command.userId)
            ?: return listOf(error(playerId, "PLAYER_NOT_FOUND", legacyFallback("Không tìm thấy người chơi này.")))

        return when (val result = clanRepository.respondJoinRequest(
            clanId = command.clanId,
            ownerId = playerId,
            userId = command.userId,
            accept = command.accept
        )) {
            ClanJoinResponseResult.APPROVED,
            ClanJoinResponseResult.REJECTED -> {
                val approved = result == ClanJoinResponseResult.APPROVED
                val requesterMessage = if (approved) {
                    legacyFallback("Yêu cầu vào clan ${clan.name} đã được duyệt.")
                } else {
                    legacyFallback("Yêu cầu vào clan ${clan.name} đã bị từ chối.")
                }
                val notification = NotificationSnapshot(
                    id = UUID.randomUUID().toString(),
                    kind = NotificationKind.CLAN_INVITATION,
                    title = if (approved) legacyFallback("Đã vào clan") else legacyFallback("Yêu cầu bị từ chối"),
                    message = requesterMessage,
                    createdAtEpochMillis = nowMillis(),
                    destination = NotificationDestination.CLAN,
                    actionData = command.clanId,
                    titleKey = if (approved) {
                        TextKey.NotificationClanJoinApprovedTitle.name
                    } else {
                        TextKey.NotificationClanJoinRejectedTitle.name
                    },
                    messageKey = if (approved) {
                        TextKey.ClanJoinApprovedNotice.name
                    } else {
                        TextKey.ClanJoinRejectedNotice.name
                    },
                    messageArgs = mapOf("clan" to clan.name)
                )
                notificationRepository.createNotifications(command.userId, listOf(notification))
                val updatedClan = clanRepository.getClanById(command.clanId)
                buildList {
                    add(Delivery(
                        ServerMessage.ClanActionResult(
                            success = true,
                            message = if (approved) legacyFallback("Đã duyệt ${requester.displayName}.") else legacyFallback("Đã từ chối ${requester.displayName}."),
                            action = "respond_clan_join_request",
                            messageKey = if (approved) {
                                TextKey.ClanJoinApprovedOwnerNotice.name
                            } else {
                                TextKey.ClanJoinRejectedOwnerNotice.name
                            },
                            messageArgs = mapOf("player" to requester.displayName)
                        ),
                        setOf(playerId)
                    ))
                    add(Delivery(
                        ServerMessage.ClanActionResult(
                            success = true,
                            message = requesterMessage,
                            action = if (approved) "join_clan_approved" else "join_clan_rejected",
                            messageKey = if (approved) {
                                TextKey.ClanJoinApprovedNotice.name
                            } else {
                                TextKey.ClanJoinRejectedNotice.name
                            },
                            messageArgs = mapOf("clan" to clan.name)
                        ),
                        setOf(command.userId)
                    ))
                    updatedClan?.let {
                        add(Delivery(ServerMessage.ClanInfoData(it), setOf(playerId)))
                    }
                    add(Delivery(
                        ServerMessage.NotificationsData(notificationRepository.loadNotifications(command.userId)),
                        setOf(command.userId)
                    ))
                    if (approved) addAll(loadProfile(command.userId))
                }
            }
            ClanJoinResponseResult.REQUEST_NOT_FOUND ->
                listOf(error(playerId, "CLAN_JOIN_REQUEST_NOT_FOUND", legacyFallback("Yêu cầu không còn tồn tại.")))
            ClanJoinResponseResult.CLAN_FULL ->
                listOf(error(playerId, "CLAN_FULL", legacyFallback("Clan đã đủ thành viên.")))
            ClanJoinResponseResult.ALREADY_MEMBER ->
                listOf(error(playerId, "ALREADY_IN_CLAN", legacyFallback("Người chơi đã tham gia một clan khác.")))
            ClanJoinResponseResult.FAILED ->
                listOf(error(playerId, "CLAN_JOIN_RESPONSE_FAILED", legacyFallback("Không thể xử lý yêu cầu lúc này.")))
        }
    }

    private suspend fun leaveClan(playerId: String): List<Delivery> {
        val success = clanRepository.leaveClan(playerId)
        return if (success) {
            listOf(Delivery(ServerMessage.ClanActionResult(
                true,
                legacyFallback("Đã rời clan"),
                "leave_clan",
                TextKey.ClanLeftNotice.name
            ), setOf(playerId)))
        } else {
            listOf(error(playerId, "LEAVE_CLAN_FAILED", legacyFallback("Rời clan thất bại.")))
        }
    }

    private suspend fun getClanInfo(playerId: String, clanId: String): List<Delivery> {
        val clan = clanRepository.getClanById(clanId)
        return if (clan != null) {
            listOf(Delivery(ServerMessage.ClanInfoData(clan), setOf(playerId)))
        } else {
            listOf(error(playerId, "CLAN_NOT_FOUND", legacyFallback("Không tìm thấy clan.")))
        }
    }

    private suspend fun donateToClan(
        playerId: String,
        command: ClientMessage.DonateToClan
    ): List<Delivery> {
        val result = clanRepository.donateToClan(
            userId = playerId,
            clanId = command.clanId,
            requestId = command.requestId,
            currency = command.currency,
            amount = command.amount
        )
        return buildList {
            add(
                Delivery(
                    ServerMessage.ClanDonationResult(
                        requestId = command.requestId,
                        status = result.status,
                        currency = command.currency,
                        experienceGranted = result.experienceGranted,
                        clanLevel = result.clanLevel
                    ),
                    setOf(playerId)
                )
            )
            if (result.status == ClanDonationStatus.APPLIED || result.status == ClanDonationStatus.DUPLICATE) {
                clanRepository.getClanById(command.clanId)?.let { clan ->
                    add(Delivery(ServerMessage.ClanInfoData(clan), setOf(playerId)))
                }
                addAll(loadProfile(playerId))
            }
        }
    }

    private suspend fun getClanList(playerId: String, query: String? = null): List<Delivery> {
        val list = clanRepository.getClanList(50, 0, query)
        val pendingJoinClanIds = clanRepository.getPendingJoinClanIds(playerId)
        return listOf(Delivery(ServerMessage.ClanListData(list, pendingJoinClanIds), setOf(playerId)))
    }

    private suspend fun kickClanMember(playerId: String, clanId: String, memberId: String): List<Delivery> {
        val clan = clanRepository.getClanById(clanId)
            ?: return listOf(error(playerId, "CLAN_NOT_FOUND", legacyFallback("Không tìm thấy bang hội.")))
        if (clan.ownerId != playerId) {
            return listOf(error(playerId, "NOT_CLAN_OWNER", legacyFallback("Chỉ bang chủ mới có thể mời thành viên rời bang.")))
        }
        val success = clanRepository.kickMember(clanId, playerId, memberId)
        return if (success) {
            getClanInfo(playerId, clanId)
        } else {
            listOf(error(playerId, "KICK_FAILED", legacyFallback("Không thể mời thành viên này rời bang.")))
        }
    }

    private suspend fun inviteToClan(playerId: String, playerCode: String): List<Delivery> {
        val inviterProfile = playerProfileRepository.findByPlayerId(playerId)
        val clanId = inviterProfile?.clanId
            ?: return listOf(error(playerId, "NOT_IN_CLAN", legacyFallback("Bạn chưa tham gia bang hội nào.")))
        
        val clan = clanRepository.getClanById(clanId)
            ?: return listOf(error(playerId, "CLAN_NOT_FOUND", legacyFallback("Không tìm thấy bang hội.")))

        val targetPlayer = playerProfileRepository.findByPlayerCode(playerCode)
            ?: return listOf(error(playerId, "PLAYER_NOT_FOUND", legacyFallback("Không tìm thấy người chơi này.")))
        
        if (targetPlayer.clanId != null) {
            return listOf(error(playerId, "ALREADY_IN_CLAN", legacyFallback("Người chơi này đã có bang hội.")))
        }

        val notification = NotificationSnapshot(
            id = UUID.randomUUID().toString(),
            kind = NotificationKind.CLAN_INVITATION,
            title = legacyFallback("Lời mời vào bang"),
            message = legacyFallback("${inviterProfile.displayName} mời bạn vào bang ${clan.name}"),
            createdAtEpochMillis = System.currentTimeMillis(),
            destination = NotificationDestination.CLAN,
            actionData = clanId,
            titleKey = TextKey.InviteToClan.name,
            messageKey = TextKey.NotificationClanInvitationMessage.name,
            messageArgs = mapOf(
                "player" to inviterProfile.displayName,
                "clan" to clan.name
            )
        )

        notificationRepository.createNotifications(targetPlayer.userId, listOf(notification))
        
        return listOf(
            Delivery(ServerMessage.NotificationsData(notificationRepository.loadNotifications(targetPlayer.userId)), setOf(targetPlayer.userId)),
            error(playerId, "INVITE_SENT", legacyFallback("Đã gửi lời mời."))
        )
    }



    private suspend fun updateAvatar(playerId: String, base64: String): List<Delivery> {
        val success = avatarService.upload(playerId, base64)
        if (success) {
            return loadProfile(playerId)
        }
        return listOf(error(playerId, "UPLOAD_FAILED", legacyFallback("Không thể tải ảnh đại diện lên.")))
    }


    private fun rejectLegacyCosmeticPurchase(playerId: String): List<Delivery> {
        // Kept as a protocol compatibility path for older clients. Legacy card backs
        // and board skins remain equippable when already owned, but are no longer sold.
        return listOf(error(playerId, "BUY_FAILED", legacyFallback("Không thể mua vật phẩm này.")))
    }

    private suspend fun equipCosmetic(playerId: String, cosmeticId: String): List<Delivery> {
        val item = com.hienthai.fastowin.protocol.SHOP_ITEMS.find { it.id == cosmeticId } ?: return emptyList()
        val success = playerProfileRepository.equipCosmetic(playerId, cosmeticId, item.type.name)
        if (success) {
            if (item.type == CosmeticType.FRAME) {
                val activeRoomId = mutex.withLock {
                    sessionsByPlayerId[playerId]?.frameId = cosmeticId
                    roomFor(playerId)?.id
                }
                activeRoomId?.let { persistRoom(it) }
            }
            return loadProfile(playerId)
        }
        return emptyList()
    }
    private suspend fun updateClanLogo(playerId: String, clanId: String, logoId: String): List<Delivery> {
        val clan = clanRepository.getClanById(clanId)
            ?: return listOf(error(playerId, "CLAN_NOT_FOUND", legacyFallback("Không tìm thấy bang hội.")))
        if (clan.ownerId != playerId) {
            return listOf(error(playerId, "NOT_CLAN_OWNER", legacyFallback("Chỉ đội trưởng mới có thể đổi logo.")))
        }
        val success = clanRepository.updateLogoId(clanId, logoId)
        if (success) {
            return getClanInfo(playerId, clanId)
        }
        return listOf(error(playerId, "UPLOAD_FAILED", legacyFallback("Không thể cập nhật logo.")))
    }

    private suspend fun claimClanQuestReward(playerId: String, clanId: String): List<Delivery> {
        val clan = clanRepository.getClanById(clanId)
            ?: return listOf(error(playerId, "CLAN_NOT_FOUND", "Kh\u00f4ng t\u00ecm th\u1ea5y bang h\u1ed9i."))
        
        val member = clan.members.find { it.userId == playerId }
            ?: return listOf(error(playerId, "NOT_IN_CLAN", "B\u1ea1n kh\u00f4ng n\u1eb1m trong bang n\u00e0y."))

        val quest = clan.quest
            ?: return listOf(error(playerId, "NO_QUEST", "Bang h\u1ed9i kh\u00f4ng c\u00f3 nhi\u1ec7m v\u1ee5."))

        if (quest.progress < quest.target) {
            return listOf(error(playerId, "QUEST_NOT_FINISHED", "Nhi\u1ec7m v\u1ee5 ch\u01b0a ho\u00e0n th\u00e0nh."))
        }

        if (member.questRewardClaimed) {
            return listOf(error(playerId, "ALREADY_CLAIMED", "B\u1ea1n \u0111\u00e3 nh\u1eadn th\u01b0\u1edfng r\u1ed3i."))
        }

        val claimed = clanRepository.claimQuestReward(clanId, playerId)
        if (claimed) {
            return getClanInfo(playerId, clanId) + loadProfile(playerId)
        }
        
        return listOf(error(playerId, "CLAIM_FAILED", "Kh\u00f4ng th\u1ec3 nh\u1eadn th\u01b0\u1edfng."))
    }

    private companion object {
        const val MAX_PLAYER_NAME_LENGTH = 32
        const val MAX_ROOM_NAME_LENGTH = 48
        const val SCORE_PER_NUMBER = 10
        const val MAX_REQUEST_ID_LENGTH = 64
        const val MAX_REWARDED_AD_TRANSACTION_ID_LENGTH = 256
        const val MAX_REWARDED_AD_PROOF_LENGTH = 2_048
        const val MAX_REQUESTS_PER_MATCH = 2_000
        const val MAX_NOTIFICATION_SYNC_BATCH = 20
        const val LEADERBOARD_SIZE = 100
        const val DEFAULT_TOURNAMENT_PLAYER_COUNT = 4
        val SUPPORTED_TOURNAMENT_PLAYER_COUNTS = setOf(4, 8, 16)
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

    private fun NotificationSnapshot.hasAllowedSyncTemplate(): Boolean {
        if (titleKey == null && messageKey == null) {
            return titleArgs.isEmpty() && messageArgs.isEmpty()
        }
        val expected = when (kind) {
            NotificationKind.MISSION -> TextKey.MissionCompleted to TextKey.NotificationMissionMessage
            NotificationKind.ACHIEVEMENT -> TextKey.AchievementsTitle to TextKey.NotificationAchievementMessage
            NotificationKind.COSMETIC -> TextKey.Unlocked to TextKey.NotificationCosmeticMessage
            else -> return false
        }
        if (titleKey != expected.first.name || messageKey != expected.second.name || titleArgs.isNotEmpty()) {
            return false
        }
        val validArguments = when (kind) {
            NotificationKind.MISSION -> {
                val required = setOf("code", "title", "rewardGold", "rewardXp", "rewardGems")
                val allowed = required + "titleKey"
                messageArgs.keys.containsAll(required) &&
                    messageArgs.keys.all { it in allowed } &&
                    listOf("rewardGold", "rewardXp", "rewardGems").all { argument ->
                        messageArgs.getValue(argument).toIntOrNull()?.let { it >= 0 } == true
                    } &&
                    messageArgs["titleKey"]?.let { titleKeyName ->
                        titleKeyName in setOf(
                            TextKey.MissionPlayThree.name,
                            TextKey.MissionWinOne.name,
                            TextKey.MissionCorrectHundred.name,
                            TextKey.MissionPerfectWin.name
                        )
                    } != false
            }
            NotificationKind.ACHIEVEMENT -> {
                val required = setOf("code", "title", "description")
                val allowed = required + setOf("titleKey", "descriptionKey")
                val definition = messageArgs["code"]?.let(::achievementDefinition)
                messageArgs.keys.containsAll(required) &&
                    messageArgs.keys.all { it in allowed } &&
                    messageArgs["titleKey"]?.let { it == definition?.titleKey } != false &&
                    messageArgs["descriptionKey"]?.let { it == definition?.descriptionKey } != false
            }
            NotificationKind.COSMETIC -> {
                val required = setOf("id", "name")
                val allowed = required + "nameKey"
                val definition = messageArgs["id"]?.let { id ->
                    (FRAME_CATALOG + TITLE_CATALOG).firstOrNull { it.id == id }
                }
                messageArgs.keys.containsAll(required) &&
                    messageArgs.keys.all { it in allowed } &&
                    messageArgs["nameKey"]?.let { it == definition?.nameKey } != false
            }
            else -> false
        }
        return validArguments &&
            messageArgs.all { (key, value) -> key.length <= 64 && value.length <= 300 }
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
