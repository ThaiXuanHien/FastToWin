package com.hienthai.fastowin.server

import com.hienthai.fastowin.server.admin.PostgresAdminRepository
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
    val activityRepository: com.hienthai.fastowin.server.activity.PostgresActivityRepository,
    val rewardEventRepository: com.hienthai.fastowin.server.events.PostgresRewardEventRepository,
    val maintenanceRepository: com.hienthai.fastowin.server.maintenance.PostgresMaintenanceRepository,
    val giftRepository: com.hienthai.fastowin.server.gifts.PostgresGiftRepository,
    val adminRepository: PostgresAdminRepository,
    val analyticsRepository: com.hienthai.fastowin.server.analytics.PostgresAnalyticsRepository,
    val financialSyncRepository: com.hienthai.fastowin.server.analytics.PostgresFinancialSyncRepository,
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
    val playQuotaRepository: PlayQuotaRepository,
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
                    activityRepository = com.hienthai.fastowin.server.activity.PostgresActivityRepository(dataSource),
                    rewardEventRepository = com.hienthai.fastowin.server.events.PostgresRewardEventRepository(dataSource),
                    maintenanceRepository = com.hienthai.fastowin.server.maintenance.PostgresMaintenanceRepository(dataSource),
                    giftRepository = com.hienthai.fastowin.server.gifts.PostgresGiftRepository(dataSource),
                    adminRepository = PostgresAdminRepository(dataSource),
                    analyticsRepository = com.hienthai.fastowin.server.analytics.PostgresAnalyticsRepository(dataSource),
                    financialSyncRepository = com.hienthai.fastowin.server.analytics.PostgresFinancialSyncRepository(dataSource),
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
                    playQuotaRepository = PostgresPlayQuotaRepository(dataSource),
                    dataSource = dataSource
                )
            } catch (error: Throwable) {
                dataSource.close()
                throw error
            }
        }
    }
}
