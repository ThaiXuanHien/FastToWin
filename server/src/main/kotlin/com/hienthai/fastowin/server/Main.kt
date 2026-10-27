package com.hienthai.fastowin.server

import com.hienthai.fastowin.server.avatars.*
import com.hienthai.fastowin.server.admin.AdminConfiguration
import com.hienthai.fastowin.server.admin.AdminService
import com.hienthai.fastowin.server.admin.InMemoryAdminRepository
import com.hienthai.fastowin.server.announcements.AnnouncementService
import com.hienthai.fastowin.server.announcements.AnnouncementPushDelivery
import com.hienthai.fastowin.server.maintenance.*
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
    val adminConfiguration = AdminConfiguration.fromEnvironment(environment)
    val avatarConfiguration = AvatarStorageConfiguration.fromEnvironment()
    val databaseSettings = DatabaseSettings.fromEnvironment(environment)
    avatarConfiguration.validateDatabase(databaseSettings != null)
    val adMobConfiguration = com.hienthai.fastowin.server.ads.AdMobConfiguration.fromEnvironment(environment, databaseSettings != null)
    val database = databaseSettings?.let(DatabaseRuntime::open)
    val avatarObjects = try { avatarConfiguration.r2?.let(S3AvatarObjectStore::open) }
        catch (failure: Throwable) { database?.close(); throw failure }
    val (adminService, moderationService) = createModerationRuntime(database, adminConfiguration)
    val authService = AuthenticationService(database?.authRepository ?: InMemoryAuthRepository(), moderationService = moderationService)
    val authEmailSender = configuredAuthEmailSender()
    val identityRepository = database?.identityRepository ?: InMemoryGuestIdentityRepository()
    val matchResultRepository = database?.matchResultRepository ?: NoOpMatchResultRepository
    val playerProfileRepository = database?.playerProfileRepository ?: NoOpPlayerProfileRepository
    val avatarService = AvatarService(database?.avatarRepository ?: ProfileAvatarRepository(playerProfileRepository),
        avatarObjects, objectWrites = avatarConfiguration.mode == AvatarStorageMode.R2,
        publicationAllowed = { moderationService.requireContentTerms(it) },
        contentSuppressed = { id, revision -> moderationService.isSuppressed(com.hienthai.fastowin.protocol.UgcTargetType.PLAYER_AVATAR, id, revision) })
    val leaderboardRepository = database?.leaderboardRepository ?: NoOpLeaderboardRepository
    val friendRepository = database?.friendRepository ?: NoOpFriendRepository
    val notificationRepository = database?.notificationRepository ?: NoOpNotificationRepository
    val activeRoomRepository = database?.activeRoomRepository ?: NoOpActiveRoomRepository
    val tournamentRepository = database?.tournamentRepository ?: InMemoryTournamentRepository()
    val clanRepository = database?.clanRepository ?: NoOpClanRepository
    val seasonLifecycleRepository = database?.seasonLifecycleRepository ?: NoOpSeasonLifecycleRepository
    val storage = if (database == null) "memory" else "postgresql"
    val storePurchaseVerifier = configuredStorePurchaseVerifier(environment)
    val playQuotaRepository = database?.playQuotaRepository ?: InMemoryPlayQuotaRepository()
    val adMobRepository = database?.adMobRepository ?: com.hienthai.fastowin.server.ads.InMemoryAdMobRepository(playQuotaRepository)
    val rewardedAdVerifier = configuredRewardedAdVerifier(environment, adMobConfiguration?.let { com.hienthai.fastowin.server.ads.AdMobRewardedAdVerifier(adMobRepository) })
    val rewardedAdAvailability = configuredRewardedAdAvailability(environment, adMobConfiguration != null)
    val pushNotificationService = FirebasePushNotificationService()
    val pushReminderService = if (database == null) {
        NoOpPushReminderService
    } else {
        DailyPushReminderService(playerProfileRepository, pushNotificationService)
    }
    val storePurchaseService = StorePurchaseService(playerProfileRepository, storePurchaseVerifier)
    val financialSync = database?.let {
        val configured = com.hienthai.fastowin.server.analytics.configuredFinancialSource()
        com.hienthai.fastowin.server.analytics.FinancialSyncService(it.financialSyncRepository,
            configured?.first, configured?.second)
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
        storePurchaseService = storePurchaseService,
        avatarService = avatarService,
        moderationService = moderationService,
        playQuotaRepository = playQuotaRepository,
        rewardedAdVerifier = rewardedAdVerifier,
        rewardedAdAvailability = rewardedAdAvailability,
        storeSandboxEnabled = environment == "dev",
        eventRewardSource = database?.rewardEventRepository ?: com.hienthai.fastowin.server.events.NoRewardEventSource
    )
    runBlocking {
        seasonLifecycleRepository.maintain()
        engine.restoreActiveRooms()
    }
    val environmentStatus = serviceStatusFromEnvironment()
    val maintenanceService = MaintenanceService(database?.maintenanceRepository ?: UnavailableMaintenanceRepository,
        adminService, environmentOverride = environmentStatus.maintenance,
        environmentMessageVi = environmentStatus.message, environmentMessageEn = environmentStatus.message)
    // Memory/dev operation still uses environment policy; durable admin controls explicitly return 503.
    val maintenanceRuntime = MaintenanceRuntime(engine, maintenanceService.takeIf { database != null }, ::serviceStatusFromEnvironment)

    val emailDelivery = if (authEmailSender.isConfigured) configuredAuthEmailProvider().name.lowercase() else "disabled"
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
                pushReminderService = pushReminderService,
                adminService = adminService,
                announcementService = AnnouncementService(notificationRepository.announcements, adminService,
                    pushConfigured = { pushNotificationService.announcementConfigured }),
                announcementPushDelivery = database?.let {
                    AnnouncementPushDelivery(notificationRepository.announcements, pushNotificationService)
                },
                analyticsService = adminService?.let { com.hienthai.fastowin.server.analytics.AnalyticsService(
                    database?.analyticsRepository ?: com.hienthai.fastowin.server.analytics.UnavailableAnalyticsRepository, it) },
                activityTracker = com.hienthai.fastowin.server.activity.ActivityTracker(
                    database?.activityRepository ?: com.hienthai.fastowin.server.activity.UnavailableActivityRepository),
                financialSyncService = financialSync,
                giftService = com.hienthai.fastowin.server.gifts.GiftService(database?.giftRepository ?: com.hienthai.fastowin.server.gifts.UnavailableGiftRepository, adminService, newIntentAllowed = engine::newIntentAllowed),
                compensationIncidentService = adminService?.let { com.hienthai.fastowin.server.compensation.CompensationIncidentService(
                    database?.giftRepository ?: com.hienthai.fastowin.server.gifts.UnavailableGiftRepository, it) },
                compensationRoundService = adminService?.let { com.hienthai.fastowin.server.compensation.CompensationRoundService(
                    database?.giftRepository ?: com.hienthai.fastowin.server.gifts.UnavailableGiftRepository, it) },
                giftDeliveryEnabled = database != null,
                missionService = com.hienthai.fastowin.server.missions.MissionService(database?.missionRepository, adminService),
                clanRaceService = com.hienthai.fastowin.server.clanrace.ClanRaceService(database?.clanRaceRepository, adminService),
                clanRaceSettlement = database?.clanRaceRepository?.let { com.hienthai.fastowin.server.clanrace.ClanRaceSettlement(it) },
                clanRaceDelivery = database?.clanRaceRepository?.let { com.hienthai.fastowin.server.clanrace.ClanRaceDelivery(it) },
                rewardEventService = com.hienthai.fastowin.server.events.RewardEventService(
                    database?.rewardEventRepository ?: com.hienthai.fastowin.server.events.UnavailableRewardEventRepository, adminService),
                adMobService = adMobConfiguration?.let { com.hienthai.fastowin.server.ads.AdMobService(it, adMobRepository,
                    playQuotaRepository, newIntentAllowed = engine::newIntentAllowed,
                    rewardedAdAvailability = rewardedAdAvailability) },
                maintenanceService = maintenanceService,
                maintenanceRuntime = maintenanceRuntime
            )
        }.start(wait = true)
    } finally {
        avatarObjects?.close()
        database?.close()
    }
}

/** Shared startup assembly for admin and moderation services. */
internal fun createModerationRuntime(
    database: DatabaseRuntime?,
    adminConfiguration: AdminConfiguration?
): Pair<AdminService?, com.hienthai.fastowin.server.moderation.ModerationService> {
    val memoryAdmins = if (database == null) InMemoryAdminRepository() else null
    val adminService = adminConfiguration?.let { configuration ->
        AdminService(database?.adminRepository ?: checkNotNull(memoryAdmins), configuration)
    }
    val moderationService = com.hienthai.fastowin.server.moderation.ModerationService(
        database?.moderationRepository ?: com.hienthai.fastowin.server.moderation.InMemoryModerationRepository(checkNotNull(memoryAdmins)),
        adminService, enforcementEnabled = { contentTermsEnforced() })
    return adminService to moderationService
}
