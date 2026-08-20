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
    val clanRepository = database?.clanRepository ?: object : ClanRepository {
        override suspend fun createClan(ownerId: String, name: String, description: String): String? = null
        override suspend fun joinClan(userId: String, clanId: String): Boolean = false
        override suspend fun leaveClan(userId: String): Boolean = false
        override suspend fun getClanByUserId(userId: String): com.hienthai.fastowin.protocol.ClanSnapshot? = null
        override suspend fun getClanById(clanId: String): com.hienthai.fastowin.protocol.ClanSnapshot? = null
        override suspend fun getClanList(limit: Int, offset: Int): List<com.hienthai.fastowin.protocol.ClanSummarySnapshot> = emptyList()
        override suspend fun kickMember(clanId: String, currentUserId: String, targetUserId: String): Boolean = false
    }
    val storage = if (database == null) "memory" else "postgresql"
    val engine = GameEngine(
        identityRepository,
        matchResultRepository,
        playerProfileRepository,
        leaderboardRepository,
        friendRepository,
        activeRoomRepository,
        notificationRepository,
        tournamentRepository,
        clanRepository
    )
    runBlocking { engine.restoreActiveRooms() }

    println("Starting Fast To Win server: environment=$environment, host=$host, port=$port, storage=$storage")
    try {
        embeddedServer(Netty, host = host, port = port) {
            gameModule(
                engine = engine,
                authService = authService,
                environment = environment
            )
        }.start(wait = true)
    } finally {
        database?.close()
    }
}
