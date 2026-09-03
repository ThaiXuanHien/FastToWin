package com.hienthai.fastowin.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking

fun main() {
    val environment = System.getenv("FASTTOWIN_ENV")?.lowercase() ?: "dev"
    require(environment in setOf("dev", "prod")) {
        "FASTTOWIN_ENV must be either 'dev' or 'prod'."
    }
    val host = System.getenv("SERVER_HOST") ?: "0.0.0.0"
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val database = DatabaseSettings.fromEnvironment(environment)?.let(DatabaseRuntime::open)
    val authService = AuthenticationService(database?.authRepository ?: InMemoryAuthRepository())
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

    println("Starting Fast To Win server: environment=$environment, host=$host, port=$port, storage=$storage")
    try {
        embeddedServer(Netty, host = host, port = port) {
            gameModule(
                engine = engine,
                authService = authService,
                environment = environment,
                seasonLifecycleRepository = seasonLifecycleRepository,
                pushReminderService = pushReminderService
            )
        }.start(wait = true)
    } finally {
        database?.close()
    }
}
