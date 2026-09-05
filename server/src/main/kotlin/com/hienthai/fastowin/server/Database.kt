package com.hienthai.fastowin.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway

data class DatabaseSettings(
    val url: String,
    val user: String,
    val password: String,
    val maximumPoolSize: Int
) {
    companion object {
        fun fromEnvironment(
            environment: String,
            values: Map<String, String> = System.getenv(),
            fileReader: (String) -> String = { java.nio.file.Files.readString(java.nio.file.Path.of(it)) }
        ): DatabaseSettings? {
            val url = values["DATABASE_URL"]?.takeIf(String::isNotBlank)
            if (url == null) {
                require(environment != "prod") { "DATABASE_URL is required when FASTTOWIN_ENV=prod." }
                return null
            }
            val password = readEnvironmentSecret("DATABASE_PASSWORD", values, fileReader)
                ?: if (environment == "dev") "fasttowin" else error(
                    "DATABASE_PASSWORD or DATABASE_PASSWORD_FILE is required in production."
                )
            return DatabaseSettings(
                url = url,
                user = values["DATABASE_USER"] ?: "fasttowin",
                password = password,
                maximumPoolSize = values["DATABASE_POOL_SIZE"]?.toIntOrNull()?.coerceIn(2, 50) ?: 10
            )
        }
    }
}

class DatabaseRuntime private constructor(
    val authRepository: AuthRepository,
    val identityRepository: GuestIdentityRepository,
    val matchResultRepository: MatchResultRepository,
    val playerProfileRepository: PlayerProfileRepository,
    val friendRepository: FriendRepository,
    val notificationRepository: NotificationRepository,
    val leaderboardRepository: LeaderboardRepository,
    val activeRoomRepository: ActiveRoomRepository,
    val tournamentRepository: TournamentRepository,
    val clanRepository: ClanRepository,
    val seasonLifecycleRepository: SeasonLifecycleRepository,
    private val dataSource: HikariDataSource
) : AutoCloseable {
    override fun close() = dataSource.close()

    companion object {
        fun open(settings: DatabaseSettings): DatabaseRuntime {
            val dataSource = HikariDataSource(HikariConfig().apply {
                jdbcUrl = settings.url
                username = settings.user
                password = settings.password
                maximumPoolSize = settings.maximumPoolSize
                minimumIdle = 1
                connectionTimeout = 7_000
                validationTimeout = 3_000
                poolName = "fasttowin-db"
            })

            try {
                Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate()
                return DatabaseRuntime(
                    authRepository = PostgresAuthRepository(dataSource),
                    identityRepository = PostgresGuestIdentityRepository(dataSource),
                    matchResultRepository = PostgresMatchResultRepository(dataSource),
                    playerProfileRepository = PostgresPlayerProfileRepository(dataSource),
                    friendRepository = PostgresFriendRepository(dataSource),
                    notificationRepository = PostgresNotificationRepository(dataSource),
                    leaderboardRepository = PostgresLeaderboardRepository(dataSource),
                    activeRoomRepository = PostgresActiveRoomRepository(dataSource),
                    tournamentRepository = PostgresTournamentRepository(dataSource),
                    clanRepository = PostgresClanRepository(dataSource),
                    seasonLifecycleRepository = PostgresSeasonLifecycleRepository(dataSource),
                    dataSource = dataSource
                )
            } catch (error: Throwable) {
                dataSource.close()
                throw error
            }
        }
    }
}
