package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.GameSocketClient
import com.hienthai.fastowin.data.network.ResumeTokenStore
import com.hienthai.fastowin.data.network.SocketConnectionState
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.GameSnapshot
import com.hienthai.fastowin.protocol.MAX_PROFILE_DISPLAY_NAME_LENGTH
import com.hienthai.fastowin.protocol.PROFILE_AVATAR_IDS
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.RematchEvent
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.protocol.ServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class GameController(
    serverUrl: String,
    resumeTokenStore: ResumeTokenStore,
    accountDisplayName: String? = null,
    accessTokenProvider: (suspend (forceRefresh: Boolean) -> String?)? = null,
    onAccountSessionExpired: (() -> Unit)? = null,
    private val onProfileDisplayNameChanged: (String) -> Unit = {}
) {
    private var accountDisplayName = accountDisplayName
    private val _uiState = MutableStateFlow(GameState())
    val uiState: StateFlow<GameState> = _uiState.asStateFlow()

    private val socket = GameSocketClient(
        serverUrl,
        resumeTokenStore,
        accessTokenProvider,
        onAccountSessionExpired
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var playerId: String? = null
    private var sessionJob: Job? = null
    private var messageJob: Job? = null
    private var connectionJob: Job? = null
    private var timerJob: Job? = null
    private var countdownJob: Job? = null
    private var latencyJob: Job? = null
    private var gameStarted = false

    init {
        accountDisplayName?.let { displayName ->
            _uiState.update { it.copy(player = PlayerState(displayName)) }
            ensureSocketSession(displayName)
        }
    }

    fun selectMode(mode: GameMode) {
        val displayName = accountDisplayName
        if (displayName == null) {
            _uiState.update { it.copy(gameMode = mode, lobbyStage = LobbyStage.ENTER_NAME) }
        } else {
            _uiState.update { it.copy(gameMode = mode) }
            enterRoomBrowser(displayName)
        }
    }

    fun backToModeSelection() {
        _uiState.update { it.copy(lobbyStage = LobbyStage.SELECT_MODE, error = null) }
    }

    fun openRoomBrowser(playerName: String) {
        val normalizedName = playerName.trim()
        if (normalizedName.isEmpty()) return

        enterRoomBrowser(normalizedName)
    }

    private fun enterRoomBrowser(normalizedName: String) {

        _uiState.update {
            it.copy(
                player = PlayerState(normalizedName),
                opponent = PlayerState(DEFAULT_OPPONENT_NAME),
                lobbyStage = LobbyStage.ROOM_BROWSER,
                isSearching = true,
                availableRooms = emptyList(),
                currentRoomId = null,
                currentRoomName = null,
                isRoomHost = false,
                error = null
            )
        }
        ensureSocketSession(normalizedName)
    }

    private fun ensureSocketSession(displayName: String = _uiState.value.player.name) {
        if (sessionJob?.isActive == true) {
            requestRoomList()
            return
        }

        messageJob?.cancel()
        messageJob = scope.launch {
            socket.messages.collect(::handleMessage)
        }
        connectionJob?.cancel()
        connectionJob = scope.launch {
            socket.connectionState.collect(::handleConnectionState)
        }
        sessionJob = scope.launch {
            socket.connect(displayName)
        }
    }

    private fun handleConnectionState(socketState: SocketConnectionState) {
        val status = when (socketState) {
            SocketConnectionState.DISCONNECTED -> ConnectionStatus.DISCONNECTED
            SocketConnectionState.CONNECTING -> ConnectionStatus.CONNECTING
            SocketConnectionState.AUTHENTICATING -> ConnectionStatus.AUTHENTICATING
            SocketConnectionState.CONNECTED -> ConnectionStatus.CONNECTED
            SocketConnectionState.RECONNECTING -> ConnectionStatus.RECONNECTING
        }
        _uiState.update { state ->
            val waitingForConnection = status != ConnectionStatus.CONNECTED &&
                state.lobbyStage in setOf(LobbyStage.ROOM_BROWSER, LobbyStage.ROOM_WAITING)
            val reconnectMessage = when {
                status == ConnectionStatus.RECONNECTING && state.isMatchStarted ->
                    RECONNECTING_MATCH_MESSAGE
                status == ConnectionStatus.CONNECTED && state.message == RECONNECTING_MATCH_MESSAGE -> null
                else -> state.message
            }
            state.copy(
                connectionStatus = status,
                isSearching = waitingForConnection,
                message = reconnectMessage
            )
        }
    }

    fun requestRoomList() {
        if (sessionJob?.isActive != true) {
            _uiState.update { it.copy(isSearching = true, error = null) }
            ensureSocketSession()
            return
        }
        scope.launch { socket.sendMessage(ClientMessage.ListRooms) }
    }

    fun openProfile() {
        _uiState.update {
            it.copy(
                isProfileOpen = true,
                isProfileLoading = true,
                isLeaderboardOpen = false,
                isFriendsOpen = false,
                isNotificationsOpen = false,
                error = null
            )
        }
        if (sessionJob?.isActive != true) {
            ensureSocketSession(accountDisplayName ?: _uiState.value.player.name)
            return
        }
        if (playerId == null) return
        scope.launch { socket.sendMessage(ClientMessage.GetProfile) }
    }

    fun closeProfile() {
        _uiState.update {
            it.copy(isProfileOpen = false, isProfileLoading = false, isProfileSaving = false, profileNotice = null)
        }
    }

    fun updateProfile(displayName: String, avatarId: String?) {
        val safeName = displayName.trim()
        if (safeName.isEmpty() || safeName.length > MAX_PROFILE_DISPLAY_NAME_LENGTH) {
            _uiState.update {
                it.copy(error = "Biệt danh phải có từ 1 đến $MAX_PROFILE_DISPLAY_NAME_LENGTH ký tự.")
            }
            return
        }
        if (avatarId != null && avatarId !in PROFILE_AVATAR_IDS) {
            _uiState.update { it.copy(error = "Ảnh đại diện không hợp lệ.") }
            return
        }
        _uiState.update { it.copy(isProfileSaving = true, profileNotice = null, error = null) }
        scope.launch { socket.sendMessage(ClientMessage.UpdateProfile(safeName, avatarId)) }
    }

    fun openLeaderboard() {
        _uiState.update {
            it.copy(
                isLeaderboardOpen = true,
                isLeaderboardLoading = true,
                isProfileOpen = false,
                isFriendsOpen = false,
                isNotificationsOpen = false,
                error = null
            )
        }
        if (sessionJob?.isActive != true) {
            ensureSocketSession(accountDisplayName ?: _uiState.value.player.name)
            return
        }
        if (playerId == null) return
        scope.launch { socket.sendMessage(ClientMessage.GetLeaderboard) }
    }

    fun closeLeaderboard() {
        _uiState.update { it.copy(isLeaderboardOpen = false, isLeaderboardLoading = false) }
    }

    fun openFriends() {
        _uiState.update {
            it.copy(
                isFriendsOpen = true,
                isFriendsLoading = true,
                isProfileOpen = false,
                isLeaderboardOpen = false,
                isNotificationsOpen = false,
                error = null
            )
        }
        if (sessionJob?.isActive != true) {
            ensureSocketSession(accountDisplayName ?: _uiState.value.player.name)
            return
        }
        if (playerId == null) return
        scope.launch {
            socket.sendMessage(ClientMessage.GetFriends)
            socket.sendMessage(ClientMessage.GetRoomInvitations)
        }
    }

    fun closeFriends() {
        _uiState.update { it.copy(isFriendsOpen = false, isFriendsLoading = false, error = null) }
    }

    fun openHome() {
        _uiState.update {
            it.copy(
                isProfileOpen = false,
                isProfileLoading = false,
                isLeaderboardOpen = false,
                isLeaderboardLoading = false,
                isFriendsOpen = false,
                isFriendsLoading = false,
                isNotificationsOpen = false,
                error = null
            )
        }
    }

    fun openNotifications() {
        _uiState.update {
            it.copy(
                isNotificationsOpen = true,
                isProfileOpen = false,
                isProfileLoading = false,
                isLeaderboardOpen = false,
                isLeaderboardLoading = false,
                isFriendsOpen = false,
                isFriendsLoading = false,
                error = null
            )
        }
        if (accountDisplayName != null) {
            scope.launch { socket.sendMessage(ClientMessage.GetNotifications) }
        }
    }

    fun closeNotifications() {
        _uiState.update { it.copy(isNotificationsOpen = false) }
    }

    fun markAllNotificationsRead() {
        _uiState.update { state ->
            state.copy(notifications = state.notifications.map { it.copy(isRead = true) })
        }
        if (accountDisplayName != null) {
            scope.launch { socket.sendMessage(ClientMessage.MarkNotificationsRead()) }
        }
    }

    fun dismissNotification(notificationId: String) {
        _uiState.update { state ->
            state.copy(
                notifications = state.notifications.filterNot { it.id == notificationId },
                dismissedNotificationIds = state.dismissedNotificationIds + notificationId
            )
        }
        if (accountDisplayName != null) {
            scope.launch { socket.sendMessage(ClientMessage.DismissNotifications(notificationId)) }
        }
    }

    fun clearNotifications() {
        _uiState.update { state ->
            state.copy(
                notifications = emptyList(),
                dismissedNotificationIds = state.dismissedNotificationIds + state.notifications.map { it.id }
            )
        }
        if (accountDisplayName != null) {
            scope.launch { socket.sendMessage(ClientMessage.DismissNotifications()) }
        }
    }

    fun openNotification(notificationId: String) {
        val notification = _uiState.value.notifications.firstOrNull { it.id == notificationId } ?: return
        _uiState.update { state ->
            state.copy(
                notifications = state.notifications.map {
                    if (it.id == notificationId) it.copy(isRead = true) else it
                },
                isNotificationsOpen = false
            )
        }
        if (accountDisplayName != null) {
            scope.launch { socket.sendMessage(ClientMessage.MarkNotificationsRead(notificationId)) }
        }
        when (notification.destination) {
            AppNotificationDestination.FRIENDS -> openFriends()
            AppNotificationDestination.PROFILE -> openProfile()
        }
    }

    fun sendFriendRequest(playerCode: String) {
        _uiState.update { it.copy(isFriendsLoading = true, error = null, socialNotice = null) }
        scope.launch { socket.sendMessage(ClientMessage.SendFriendRequest(playerCode)) }
    }

    fun respondFriendRequest(requestId: String, accept: Boolean) {
        _uiState.update { state ->
            state.copy(
                isFriendsLoading = true,
                error = null,
                socialNotice = null,
                notifications = state.notifications.map {
                    if (it.id == "friend:$requestId") it.copy(isRead = true) else it
                }
            )
        }
        scope.launch { socket.sendMessage(ClientMessage.RespondFriendRequest(requestId, accept)) }
    }

    fun cancelFriendRequest(requestId: String) {
        _uiState.update { it.copy(isFriendsLoading = true, error = null, socialNotice = null) }
        scope.launch { socket.sendMessage(ClientMessage.CancelFriendRequest(requestId)) }
    }

    fun removeFriend(friendUserId: String) {
        _uiState.update { it.copy(isFriendsLoading = true, error = null, socialNotice = null) }
        scope.launch { socket.sendMessage(ClientMessage.RemoveFriend(friendUserId)) }
    }

    fun blockPlayer(playerUserId: String) {
        _uiState.update { it.copy(isFriendsLoading = true, error = null, socialNotice = null) }
        scope.launch { socket.sendMessage(ClientMessage.BlockPlayer(playerUserId)) }
    }

    fun unblockPlayer(playerUserId: String) {
        _uiState.update { it.copy(isFriendsLoading = true, error = null, socialNotice = null) }
        scope.launch { socket.sendMessage(ClientMessage.UnblockPlayer(playerUserId)) }
    }

    fun inviteFriend(friendUserId: String) {
        val roomId = _uiState.value.currentRoomId ?: return
        scope.launch { socket.sendMessage(ClientMessage.InviteFriend(friendUserId, roomId)) }
    }

    fun respondRoomInvitation(invitationId: String, accept: Boolean) {
        val invitationExists = _uiState.value.roomInvitations.any { it.invitationId == invitationId }
        if (!invitationExists) return
        _uiState.update { state ->
            state.copy(
                roomInvitations = state.roomInvitations.filterNot { it.invitationId == invitationId },
                roomInvitationPrompt = state.roomInvitationPrompt?.takeUnless { it.invitationId == invitationId },
                notifications = state.notifications.map {
                    if (it.id == "room:$invitationId") it.copy(isRead = true) else it
                },
                error = null
            )
        }
        scope.launch { socket.sendMessage(ClientMessage.RespondRoomInvitation(invitationId, accept)) }
    }

    fun dismissRoomInvitationPrompt() {
        _uiState.update { it.copy(roomInvitationPrompt = null) }
    }

    fun createRoom(roomName: String, password: String) {
        if (roomName.isBlank() || password.isEmpty()) {
            _uiState.update { it.copy(error = "Vui lòng nhập tên và mật khẩu phòng.") }
            return
        }
        _uiState.update { it.copy(isSearching = true, error = null) }
        scope.launch {
            socket.sendMessage(
                ClientMessage.CreateRoom(
                    roomName = roomName,
                    password = password,
                    gameMode = _uiState.value.gameMode.toProtocol()
                )
            )
        }
    }

    fun joinRoom(roomId: String, password: String) {
        val room = _uiState.value.availableRooms.firstOrNull { it.id == roomId }
        if (room == null) {
            _uiState.update { it.copy(error = "Phòng không còn khả dụng.") }
            return
        }
        _uiState.update {
            it.copy(
                gameMode = room.gameMode,
                lobbyStage = LobbyStage.ROOM_WAITING,
                currentRoomId = room.id,
                currentRoomName = room.name,
                isRoomHost = false,
                isSearching = true,
                error = null
            )
        }
        scope.launch { socket.sendMessage(ClientMessage.JoinRoom(roomId, password)) }
    }

    fun leaveRoom() {
        val roomId = _uiState.value.currentRoomId
        if (roomId != null) scope.launch { socket.sendMessage(ClientMessage.LeaveRoom(roomId)) }
        returnToRoomBrowser()
    }

    fun requestRematch() {
        val state = _uiState.value
        val roomId = state.currentRoomId ?: return
        if (!state.isGameOver || state.isRematchRequestedByMe) return
        scope.launch { socket.sendMessage(ClientMessage.RespondRematch(roomId, accept = true)) }
    }

    fun declineRematch() = respondToRematch(accept = false)

    fun cancelRematch() = respondToRematch(accept = false)

    private fun respondToRematch(accept: Boolean) {
        val state = _uiState.value
        val roomId = state.currentRoomId ?: return
        if (!state.isGameOver || (!state.isRematchRequestedByMe && !state.isRematchRequestedByOpponent)) return
        scope.launch { socket.sendMessage(ClientMessage.RespondRematch(roomId, accept)) }
    }

    fun connectWithOpponent() {
        val state = _uiState.value
        val opponentId = state.opponent.id ?: return
        when (state.postMatchFriendStatus) {
            PostMatchFriendStatus.REQUEST_RECEIVED -> {
                val request = state.social.incomingRequests.firstOrNull { it.userId == opponentId } ?: return
                respondFriendRequest(request.requestId, accept = true)
            }
            PostMatchFriendStatus.AVAILABLE -> {
                val recent = state.social.recentPlayers.firstOrNull { it.userId == opponentId } ?: return
                sendFriendRequest(recent.playerCode)
            }
            else -> Unit
        }
    }

    fun blockOpponentAfterMatch() {
        val state = _uiState.value
        val opponentId = state.opponent.id ?: return
        val roomId = state.currentRoomId
        scope.launch {
            socket.sendMessage(ClientMessage.BlockPlayer(opponentId))
            if (roomId != null) socket.sendMessage(ClientMessage.LeaveRoom(roomId))
            returnToRoomBrowser()
            _uiState.update { it.copy(socialNotice = "Đã chặn người chơi và rời phòng.") }
        }
    }

    fun openMatchDetail(matchId: String) {
        _uiState.update { it.copy(matchDetail = null, isMatchDetailLoading = true, error = null) }
        scope.launch { socket.sendMessage(ClientMessage.GetMatchDetail(matchId)) }
    }

    fun closeMatchDetail() {
        _uiState.update { it.copy(matchDetail = null, isMatchDetailLoading = false) }
    }

    fun equipCosmetics(frameId: String, titleId: String) {
        _uiState.update { it.copy(isProfileLoading = true, error = null) }
        scope.launch { socket.sendMessage(ClientMessage.EquipCosmetics(frameId, titleId)) }
    }

    fun setReady(ready: Boolean) {
        val roomId = _uiState.value.currentRoomId ?: return
        scope.launch { socket.sendMessage(ClientMessage.SetReady(roomId, ready)) }
    }

    fun startMatchmaking(mode: GameMode) {
        if (accountDisplayName == null) return
        _uiState.update {
            it.copy(
                gameMode = mode,
                lobbyStage = LobbyStage.MATCHMAKING,
                isMatchmaking = true,
                matchmakingStartedAtMillis = epochMillis(),
                matchmakingRatingRange = 100,
                error = null
            )
        }
        scope.launch { socket.sendMessage(ClientMessage.JoinMatchmaking(mode.toProtocol())) }
    }

    fun cancelMatchmaking() {
        _uiState.update {
            it.copy(
                lobbyStage = LobbyStage.SELECT_MODE,
                isMatchmaking = false,
                matchmakingStartedAtMillis = null,
                error = null
            )
        }
        scope.launch { socket.sendMessage(ClientMessage.CancelMatchmaking) }
    }

    fun kickOpponent() {
        val state = _uiState.value
        val roomId = state.currentRoomId ?: return
        val opponentId = state.opponent.id ?: return
        scope.launch { socket.sendMessage(ClientMessage.KickPlayer(roomId, opponentId)) }
    }

    private suspend fun handleMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.SessionReady -> {
                val wasRecoveringRoom = _uiState.value.currentRoomId != null
                playerId = message.playerId
                _uiState.update { it.copy(isSearching = false, error = null) }
                startLatencyMonitoring()
                if (accountDisplayName != null) {
                    scope.launch {
                        socket.sendMessage(ClientMessage.ListRooms)
                        socket.sendMessage(ClientMessage.GetProfile)
                        socket.sendMessage(ClientMessage.GetFriends)
                        socket.sendMessage(ClientMessage.GetRoomInvitations)
                        socket.sendMessage(ClientMessage.GetNotifications)
                        socket.sendMessage(ClientMessage.GetLeaderboard)
                    }
                }
                val currentGame = message.currentGame
                if (currentGame != null) {
                    val game = currentGame
                    if (game.phase == RoomPhase.PLAYING || game.phase == RoomPhase.FINISHED) {
                        startGameWithSnapshot(game)
                    } else {
                        applyWaitingSnapshot(game)
                    }
                } else if (wasRecoveringRoom) {
                    returnToRoomBrowser("Phòng đã đóng vì quá thời gian kết nối lại.")
                }
            }

            is ServerMessage.RoomList -> {
                _uiState.update { state ->
                    state.copy(
                        isSearching = false,
                        availableRooms = message.rooms.map { room ->
                            AvailableRoom(
                                id = room.id,
                                name = room.name,
                                hostName = room.hostName,
                                gameMode = room.gameMode.toUi(),
                                requiresPassword = room.requiresPassword,
                                lastSeenAtMillis = epochMillis()
                            )
                        }
                    )
                }
            }

            is ServerMessage.ProfileData -> {
                val wasSaving = _uiState.value.isProfileSaving
                val newNotifications = progressionNotifications(
                    previous = _uiState.value.profile,
                    current = message.profile,
                    nowMillis = epochMillis()
                )
                accountDisplayName = message.profile.displayName
                onProfileDisplayNameChanged(message.profile.displayName)
                _uiState.update { state ->
                    val completedMatch = state.currentMatchId?.takeIf { state.isGameOver }?.let { matchId ->
                        message.profile.recentMatches.firstOrNull { it.matchId == matchId }
                    }
                    state.copy(
                        profile = message.profile,
                        player = state.player.copy(name = message.profile.displayName),
                        isProfileLoading = false,
                        isProfileSaving = false,
                        profileNotice = if (wasSaving) "Đã lưu hồ sơ." else null,
                        lastMatchEloChange = completedMatch?.eloChange ?: state.lastMatchEloChange,
                        lastMatchEloRating = if (completedMatch != null) {
                            message.profile.statistics.eloRating
                        } else {
                            state.lastMatchEloRating
                        },
                        notifications = mergeNotifications(
                            state.notifications,
                            newNotifications,
                            state.dismissedNotificationIds
                        ),
                        error = null
                    )
                }
                if (newNotifications.isNotEmpty()) {
                    socket.sendMessage(ClientMessage.SyncNotifications(
                        newNotifications.map(AppNotification::toNotificationSnapshot)
                    ))
                }
            }
            is ServerMessage.MatchDetailData -> {
                _uiState.update {
                    it.copy(matchDetail = message.detail, isMatchDetailLoading = false, error = null)
                }
            }

            is ServerMessage.LeaderboardData -> {
                _uiState.update {
                    it.copy(leaderboard = message.leaderboard, isLeaderboardLoading = false, error = null)
                }
            }

            is ServerMessage.FriendsData -> {
                _uiState.update { state ->
                    state.copy(
                        social = message.social,
                        isFriendsLoading = false,
                        notifications = if (accountDisplayName == null) mergeNotifications(
                            state.notifications,
                            friendRequestNotifications(message.social.incomingRequests, epochMillis()),
                            state.dismissedNotificationIds
                        ) else state.notifications,
                        error = null
                    )
                }
            }

            is ServerMessage.RoomInvitation -> {
                _uiState.update { state ->
                    state.copy(
                        roomInvitations = (state.roomInvitations.filterNot {
                            it.invitationId == message.invitationId || it.fromUserId == message.fromUserId
                        } + message),
                        roomInvitationPrompt = state.roomInvitationPrompt?.takeUnless {
                            it.fromUserId == message.fromUserId
                        } ?: message,
                        notifications = mergeNotifications(
                            state.notifications,
                            listOf(roomInvitationNotification(message, epochMillis())),
                            state.dismissedNotificationIds
                        ),
                        socialNotice = null
                    )
                }
            }

            is ServerMessage.RoomInvitationsData -> {
                _uiState.update { state ->
                    val invitationIds = message.invitations.mapTo(mutableSetOf()) { it.invitationId }
                    state.copy(
                        roomInvitations = message.invitations,
                        notifications = if (accountDisplayName == null) mergeNotifications(
                            state.notifications,
                            message.invitations.map { roomInvitationNotification(it, epochMillis()) },
                            state.dismissedNotificationIds
                        ) else state.notifications,
                        roomInvitationPrompt = state.roomInvitationPrompt?.takeIf {
                            it.invitationId in invitationIds
                        }
                    )
                }
            }

            is ServerMessage.NotificationsData -> {
                _uiState.update { state ->
                    state.copy(
                        notifications = message.notifications.map { it.toAppNotification() },
                        dismissedNotificationIds = emptySet()
                    )
                }
            }

            is ServerMessage.SocialNotice -> {
                _uiState.update { it.copy(socialNotice = message.message, isFriendsLoading = false, error = null) }
            }

            is ServerMessage.RoomCreated -> applyWaitingSnapshot(message.game)
            is ServerMessage.RoomUpdated -> applyWaitingSnapshot(message.game)
            is ServerMessage.GameStarted -> startMatchCountdown(message.game)
            is ServerMessage.GameStateUpdated -> {
                applyGameSnapshot(message.game)
                if (message.game.phase == RoomPhase.FINISHED && accountDisplayName != null) {
                    socket.sendMessage(ClientMessage.GetProfile)
                    socket.sendMessage(ClientMessage.GetFriends)
                }
            }
            is ServerMessage.GameFinished -> {
                applyGameSnapshot(message.game)
                if (accountDisplayName != null) {
                    socket.sendMessage(ClientMessage.GetProfile)
                    socket.sendMessage(ClientMessage.GetFriends)
                }
            }
            is ServerMessage.RematchStatus -> {
                applyGameSnapshot(message.game)
                _uiState.update { state ->
                    val actorIsMe = message.actorPlayerId == playerId
                    state.copy(
                        rematchNotice = when (message.event) {
                            RematchEvent.REQUESTED -> if (actorIsMe) {
                                "Đã gửi yêu cầu đấu lại."
                            } else {
                                "Đối thủ muốn đấu lại với bạn."
                            }
                            RematchEvent.CANCELLED -> if (actorIsMe) {
                                "Bạn đã hủy yêu cầu đấu lại."
                            } else {
                                "Đối thủ đã hủy yêu cầu đấu lại."
                            }
                            RematchEvent.DECLINED -> if (actorIsMe) {
                                "Bạn đã từ chối đấu lại."
                            } else {
                                "Đối thủ đã từ chối đấu lại."
                            }
                            RematchEvent.EXPIRED -> "Yêu cầu đấu lại đã hết thời gian."
                        }
                    )
                }
            }
            is ServerMessage.LatencyPong -> {
                _uiState.update {
                    it.copy(latencyMillis = (epochMillis() - message.clientSentAtEpochMillis).coerceAtLeast(0L))
                }
            }
            is ServerMessage.MatchmakingStatus -> {
                _uiState.update {
                    it.copy(
                        lobbyStage = if (message.isSearching) LobbyStage.MATCHMAKING else LobbyStage.SELECT_MODE,
                        isMatchmaking = message.isSearching,
                        gameMode = message.gameMode?.toUi() ?: it.gameMode,
                        matchmakingStartedAtMillis = if (message.isSearching) {
                            it.matchmakingStartedAtMillis ?: epochMillis()
                        } else {
                            null
                        },
                        matchmakingRatingRange = message.ratingRange,
                        error = null
                    )
                }
            }
            is ServerMessage.RoomClosed -> {
                if (_uiState.value.currentRoomId == message.roomId) {
                    returnToRoomBrowser(message.reason)
                }
            }

            is ServerMessage.Error -> handleServerError(message)
        }
    }

    private fun applyWaitingSnapshot(game: GameSnapshot) {
        gameStarted = false
        val me = game.players.firstOrNull { it.id == playerId }
        val opponent = game.players.firstOrNull { it.id != playerId }
        _uiState.update { state ->
            state.copy(
                gameMode = game.gameMode.toUi(),
                player = PlayerState(
                    name = me?.name ?: state.player.name,
                    id = me?.id,
                    isReady = me?.isReady == true,
                    score = me?.score ?: 0,
                    correctSelections = me?.correctSelections ?: 0,
                    wrongSelections = me?.wrongSelections ?: 0,
                    averageReactionMillis = me?.averageReactionMillis ?: 0
                ),
                opponent = PlayerState(
                    name = opponent?.name ?: DEFAULT_OPPONENT_NAME,
                    id = opponent?.id,
                    isReady = opponent?.isReady == true,
                    score = opponent?.score ?: 0,
                    correctSelections = opponent?.correctSelections ?: 0,
                    wrongSelections = opponent?.wrongSelections ?: 0,
                    averageReactionMillis = opponent?.averageReactionMillis ?: 0
                ),
                hasOpponent = opponent != null,
                isMatchmaking = false,
                matchmakingStartedAtMillis = null,
                numbers = emptyList(),
                currentTarget = 1,
                isGameOver = false,
                isMatchStarted = false,
                currentMatchId = game.matchId,
                isRematchRequestedByMe = false,
                isRematchRequestedByOpponent = false,
                rematchExpiresAtEpochMillis = null,
                rematchNotice = null,
                lastMatchDurationMillis = null,
                lastMatchEloChange = null,
                lastMatchEloRating = null,
                lobbyStage = LobbyStage.ROOM_WAITING,
                currentRoomId = game.roomId,
                currentRoomName = game.roomName,
                isRoomHost = game.hostId == playerId,
                isSearching = false,
                roomInvitations = emptyList(),
                roomInvitationPrompt = null,
                error = null
            )
        }
    }

    private fun startMatchCountdown(game: GameSnapshot) {
        if (gameStarted && _uiState.value.currentRoomId == game.roomId) return
        gameStarted = true
        _uiState.update { it.prepareForMatchStart() }
        countdownJob?.cancel()
        countdownJob = scope.launch {
            applyWaitingSnapshot(game)
            gameStarted = true
            _uiState.update { it.copy(lobbyStage = LobbyStage.MATCHED) }
            for (count in 3 downTo 1) {
                _uiState.update { it.copy(countdown = count) }
                delay(1_000)
            }
            startGameWithSnapshot(game)
        }
    }

    private fun startGameWithSnapshot(game: GameSnapshot) {
        countdownJob?.cancel()
        gameStarted = game.phase == RoomPhase.PLAYING
        applyGameSnapshot(game, forceStart = true)
        if (game.gameMode == ProtocolGameMode.TIME_ATTACK && game.phase == RoomPhase.PLAYING) {
            startTimer(game.startedAtEpochMillis)
        }
    }

    private fun applyGameSnapshot(game: GameSnapshot, forceStart: Boolean = false) {
        val me = game.players.firstOrNull { it.id == playerId }
        val opponent = game.players.firstOrNull { it.id != playerId }
        val finished = game.phase == RoomPhase.FINISHED
        _uiState.update { state ->
            state.copy(
                numbers = if (game.numbers.isNotEmpty()) game.numbers else state.numbers,
                currentTarget = game.currentTarget,
                score = me?.score ?: 0,
                isGameOver = finished,
                gameMode = game.gameMode.toUi(),
                player = PlayerState(
                    name = me?.name ?: state.player.name,
                    id = me?.id,
                    score = me?.score ?: 0,
                    currentTarget = game.currentTarget,
                    correctSelections = me?.correctSelections ?: 0,
                    wrongSelections = me?.wrongSelections ?: 0,
                    averageReactionMillis = me?.averageReactionMillis ?: 0
                ),
                opponent = PlayerState(
                    name = opponent?.name ?: DEFAULT_OPPONENT_NAME,
                    id = opponent?.id,
                    score = opponent?.score ?: 0,
                    currentTarget = game.currentTarget,
                    correctSelections = opponent?.correctSelections ?: 0,
                    wrongSelections = opponent?.wrongSelections ?: 0,
                    averageReactionMillis = opponent?.averageReactionMillis ?: 0
                ),
                lobbyStage = LobbyStage.MATCHED,
                currentRoomId = game.roomId,
                currentRoomName = game.roomName,
                isRoomHost = game.hostId == playerId,
                hasOpponent = opponent != null,
                isMatchmaking = false,
                matchmakingStartedAtMillis = null,
                isSearching = false,
                isMatchStarted = forceStart || state.isMatchStarted,
                currentMatchId = game.matchId,
                isRematchRequestedByMe = playerId?.let { it in game.rematchRequestedPlayerIds } == true,
                isRematchRequestedByOpponent = game.rematchRequestedPlayerIds.any { it != playerId },
                rematchExpiresAtEpochMillis = game.rematchExpiresAtEpochMillis,
                lastMatchDurationMillis = if (finished) {
                    val startedAt = game.startedAtEpochMillis
                    val finishedAt = game.finishedAtEpochMillis
                    if (startedAt != null && finishedAt != null) {
                        (finishedAt - startedAt).coerceAtLeast(0L)
                    } else {
                        state.lastMatchDurationMillis
                    }
                } else null,
                lastMatchEloChange = if (finished) state.lastMatchEloChange else null,
                lastMatchEloRating = if (finished) state.lastMatchEloRating else null,
                countdown = null,
                message = if (finished) null else state.message,
                error = null
            )
        }
        if (finished) {
            gameStarted = false
            timerJob?.cancel()
        }
    }

    private fun handleServerError(error: ServerMessage.Error) {
        if (error.code == "WRONG_NUMBER") {
            _uiState.update { it.copy(message = error.message) }
            scope.launch {
                delay(1_000)
                _uiState.update { it.copy(message = null) }
            }
            return
        }

        if (error.code in setOf("WRONG_PASSWORD", "ROOM_NOT_FOUND", "ROOM_FULL", "ALREADY_IN_ROOM")) {
            returnToRoomBrowser(error.message)
        } else {
            _uiState.update {
                it.copy(
                    isSearching = false,
                    isMatchmaking = false,
                    matchmakingStartedAtMillis = null,
                    lobbyStage = if (it.lobbyStage == LobbyStage.MATCHMAKING) {
                        LobbyStage.SELECT_MODE
                    } else {
                        it.lobbyStage
                    },
                    isProfileSaving = false,
                    isMatchDetailLoading = false,
                    isFriendsLoading = false,
                    error = error.message
                )
            }
        }
    }

    private fun startTimer(startedAtEpochMillis: Long?) {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (gameStarted) {
                val elapsed = startedAtEpochMillis?.let { epochMillis() - it } ?: 0L
                val remaining = (60_000L - elapsed).coerceAtLeast(0L)
                _uiState.update { it.copy(timeLeftMillis = remaining) }
                if (remaining == 0L) {
                    _uiState.update { it.copy(message = "Đang chờ kết quả từ máy chủ...") }
                    break
                }
                delay(250)
            }
        }
    }

    fun onNumberClicked(number: Int) {
        val state = _uiState.value
        val roomId = state.currentRoomId ?: return
        if (!gameStarted || state.isGameOver || state.connectionStatus != ConnectionStatus.CONNECTED) return
        scope.launch {
            socket.sendMessage(
                ClientMessage.SelectNumber(
                    roomId = roomId,
                    number = number,
                    requestId = randomId()
                )
            )
        }
    }

    fun resetGame() {
        val roomId = _uiState.value.currentRoomId
        val displayName = accountDisplayName ?: _uiState.value.player.name
        val activeSession = sessionJob
        timerJob?.cancel()
        countdownJob?.cancel()
        gameStarted = false
        _uiState.value = GameState(player = PlayerState(displayName))

        sessionJob = null
        scope.launch {
            if (roomId != null) socket.sendMessage(ClientMessage.LeaveRoom(roomId))
            socket.disconnect()
            activeSession?.cancel()
            ensureSocketSession(displayName)
        }
    }

    fun close() {
        timerJob?.cancel()
        countdownJob?.cancel()
        latencyJob?.cancel()
        messageJob?.cancel()
        connectionJob?.cancel()
        sessionJob?.cancel()
        socket.close()
        scope.cancel()
    }

    private fun startLatencyMonitoring() {
        latencyJob?.cancel()
        latencyJob = scope.launch {
            while (true) {
                socket.sendMessage(ClientMessage.MeasureLatency(epochMillis()))
                delay(5_000)
            }
        }
    }

    private fun returnToRoomBrowser(error: String? = null) {
        gameStarted = false
        timerJob?.cancel()
        countdownJob?.cancel()
        _uiState.update {
            it.copy(
                numbers = emptyList(),
                opponent = PlayerState(DEFAULT_OPPONENT_NAME),
                lobbyStage = LobbyStage.ROOM_BROWSER,
                currentRoomId = null,
                currentRoomName = null,
                isRoomHost = false,
                hasOpponent = false,
                isSearching = false,
                isMatchStarted = false,
                isGameOver = false,
                countdown = null,
                error = error
            )
        }
        requestRoomList()
    }

    private fun GameMode.toProtocol(): ProtocolGameMode = when (this) {
        GameMode.ORDER -> ProtocolGameMode.ORDER
        GameMode.TIME_ATTACK -> ProtocolGameMode.TIME_ATTACK
    }

    private fun ProtocolGameMode.toUi(): GameMode = when (this) {
        ProtocolGameMode.ORDER -> GameMode.ORDER
        ProtocolGameMode.TIME_ATTACK -> GameMode.TIME_ATTACK
    }

    private fun randomId(): String = buildString {
        repeat(2) { append(Random.nextLong().toULong().toString(16).padStart(16, '0')) }
    }

    private companion object {
        const val RECONNECTING_MATCH_MESSAGE =
            "Mất kết nối. Đang khôi phục trận, phòng được giữ tối đa 30 giây..."
    }
}
