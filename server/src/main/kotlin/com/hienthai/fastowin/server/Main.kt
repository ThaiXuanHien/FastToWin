package com.hienthai.fastowin.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking

fun main() {
    val environment = System.getenv("FASTTOWIN_ENV")?.lowercase() ?: "dev"
    require(environment in setOf("dev", "prod")) {
        "FASTTOWIN_ENV must be either 'dev' or 'prod'."
    }
    if (environment == "prod") validateProductionEnvironment()
    val host = System.getenv("SERVER_HOST") ?: "0.0.0.0"
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val trustProxyHeaders = System.getenv("FASTTOWIN_TRUST_PROXY_HEADERS")
        ?.equals("true", ignoreCase = true) == true
    val database = DatabaseSettings.fromEnvironment(environment)?.let(DatabaseRuntime::open)
    val authService = AuthenticationService(database?.authRepository ?: InMemoryAuthRepository())
    val authEmailSender = configuredAuthEmailSender()
    val identityRepository = database?.identityRepository ?: InMemoryGuestIdentityRepository()
    val matchResultRepository = database?.matchResultRepository ?: NoOpMatchResultRepository
    val playerProfileRepository = database?.playerProfileRepository ?: NoOpPlayerProfileRepository
    val leaderboardRepository = database?.leaderboardRepository ?: NoOpLeaderboardRepository
    val friendRepository = database?.friendRepository ?: NoOpFriendRepository
    val notificationRepository = database?.notificationRepository ?: NoOpNotificationRepository
    val activeRoomRepository = database?.activeRoomRepository ?: NoOpActiveRoomRepository
    val tournamentRepository = database?.tournamentRepository ?: InMemoryTournamentRepository()
    val clanRepository = database?.clanRepository ?: NoOpClanRepository
    val seasonLifecycleRepository = database?.seasonLifecycleRepository ?: NoOpSeasonLifecycleRepository
    val storage = if (database == null) "memory" else "postgresql"
    val storePurchaseVerifier = configuredStorePurchaseVerifier(environment)
    val pushNotificationService = FirebasePushNotificationService()
    val pushReminderService = if (database == null) {
        NoOpPushReminderService
    } else {
        DailyPushReminderService(playerProfileRepository, pushNotificationService)
    }
    val engine = GameEngine(
        identityRepository,
        matchResultRepository,
        playerProfileRepository,
        leaderboardRepository,
        friendRepository,
        activeRoomRepository,
        notificationRepository,
        tournamentRepository,
        clanRepository,
        pushNotificationService,
        storePurchaseVerifier = storePurchaseVerifier,
        storeSandboxEnabled = environment == "dev"
    )
    runBlocking {
        seasonLifecycleRepository.maintain()
        engine.restoreActiveRooms()
    }

    val emailDelivery = if (authEmailSender.isConfigured) "smtp" else "disabled"
    println(
        "Starting Fast To Win server: environment=$environment, host=$host, port=$port, " +
            "storage=$storage, email=$emailDelivery"
    )
    try {
        embeddedServer(Netty, host = host, port = port) {
            gameModule(
                engine = engine,
                authService = authService,
                authEmailSender = authEmailSender,
                environment = environment,
                trustProxyHeaders = trustProxyHeaders,
                seasonLifecycleRepository = seasonLifecycleRepository,
                pushReminderService = pushReminderService
            )
        }.start(wait = true)
    } finally {
        database?.close()
    }
}
