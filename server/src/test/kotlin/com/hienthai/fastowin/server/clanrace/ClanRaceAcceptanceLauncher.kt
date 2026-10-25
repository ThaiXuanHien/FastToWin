package com.hienthai.fastowin.server.clanrace

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.gifts.*
import com.hienthai.fastowin.server.missions.*
import com.zaxxer.hikari.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/** Owned loopback assembly, deliberately absent from production distributions. */
fun main() {
    check(System.getenv("FASTTOWIN_ENV") == "dev" && System.getenv("SERVER_HOST") == "127.0.0.1")
    val settings = requireNotNull(DatabaseSettings.fromEnvironment("dev"))
    check(Regex("jdbc:postgresql://127\\.0\\.0\\.1:15432/admin_test\\?currentSchema=webe2e_[a-f0-9]{32}").matches(settings.url))
    val clockFile = Path.of(requireNotNull(System.getenv("RACE_FIXTURE_CLOCK")))
    val clock = { Instant.parse(Files.readString(clockFile).trim()).toEpochMilli() }
    val database = DatabaseRuntime.open(settings)
    val source = HikariDataSource(HikariConfig().apply {
        jdbcUrl = settings.url; username = settings.user; password = settings.password
        maximumPoolSize = 6; minimumIdle = 1
    })
    val configuration = requireNotNull(AdminConfiguration.fromEnvironment("dev"))
    check(configuration.origin == "http://127.0.0.1:18088")
    // Authentication remains on wall time; only economy and durable roster time move.
    val admins = AdminService(database.adminRepository, configuration)
    runBlocking { admins.bootstrapOwner(requireNotNull(System.getenv("RACE_FIXTURE_OWNER_EMAIL")),
        Files.readString(Path.of(requireNotNull(System.getenv("RACE_FIXTURE_OWNER_PASSWORD_FILE")))).trim()) }
    val clans = PostgresClanRepository(source, clock)
    val races = PostgresClanRaceRepository(source)
    val engine = GameEngine(identityRepository = database.identityRepository,
        matchResultRepository = database.matchResultRepository, playerProfileRepository = database.playerProfileRepository,
        leaderboardRepository = database.leaderboardRepository, friendRepository = database.friendRepository,
        activeRoomRepository = database.activeRoomRepository, notificationRepository = database.notificationRepository,
        tournamentRepository = database.tournamentRepository, clanRepository = clans,
        playQuotaRepository = database.playQuotaRepository)
    val server = embeddedServer(Netty, host = "127.0.0.1", port = 18088) {
        gameModule(engine = engine, authService = AuthenticationService(database.authRepository), adminService = admins,
            missionService = MissionService(database.missionRepository, admins, clock),
            clanRaceService = ClanRaceService(races, admins, clock),
            giftService = GiftService(database.giftRepository, admins, clock),
            // Explicit stdin ticks let acceptance observe pending and late/restarted workers.
            giftDeliveryEnabled = false,
            allowedWebOriginsValue = "http://127.0.0.1:18089,http://127.0.0.1:18088")
    }.start(wait = false)
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(100, 1000); source.close(); database.close() })
    System.`in`.bufferedReader().forEachLine { line ->
        val command = Json.parseToJsonElement(line).jsonObject
        fun id(key: String) = UUID.fromString(command.getValue(key).jsonPrimitive.content).toString()
        val operation = command.getValue("operation").jsonPrimitive.content
        runBlocking {
            when (operation) {
                "donation" -> {
                    val request = id("request")
                    val amount = command["amount"]?.jsonPrimitive?.int ?: 100
                    check(clans.donateToClan(id("player"), id("clan"), request, ClanDonationCurrency.GOLD, amount).status == ClanDonationStatus.APPLIED)
                    check(clans.donateToClan(id("player"), id("clan"), request, ClanDonationCurrency.GOLD, amount).status == ClanDonationStatus.DUPLICATE)
                }
                "kick" -> check(clans.kickMember(id("clan"), id("owner"), id("player")))
                "leave" -> check(clans.leaveClan(id("player")))
                "join" -> {
                    check(clans.requestJoinClan(id("player"), id("clan")) == ClanJoinRequestResult.REQUESTED)
                    check(clans.respondJoinRequest(id("clan"), id("owner"), id("player"), true) == ClanJoinResponseResult.APPROVED)
                }
                "settle" -> coroutineScope { List(2) { async(Dispatchers.IO) { ClanRaceSettlement(races, clock).settleDueWeeks() } }.awaitAll() }
                "deliver" -> coroutineScope { List(2) { async(Dispatchers.IO) { ClanRaceDelivery(races, clock).deliverDueBatch() } }.awaitAll() }
                "mixed-mailbox-fixture" -> {
                    // Fixture arrangement, not an admin/compensation publication acceptance claim.
                    val player = id("player")
                    database.giftRepository.transaction {
                        for (compensated in listOf(false, true)) {
                            val gift = UUID.randomUUID().toString()
                            val now = clock()
                            val draft = GiftDraftRequest("LOCAL fixture", "LOCAL fixture", "LOCAL acceptance", "LOCAL acceptance",
                                "Owned mixed mailbox fixture", if (compensated) 17 else 11, 0,
                                audience = GiftAudience.USER_IDS, userIds = listOf(player),
                                availableAtEpochMillis = now, expiresAtEpochMillis = now + 604800000,
                                requestId = UUID.randomUUID().toString())
                            val context = if (compensated) CompensationMailboxContext(UUID.randomUUID().toString(),
                                "LOCAL_RACE_ACCEPTANCE", 1, now - 1000, now, "LOCAL fixture", "LOCAL fixture") else null
                            putCampaign(GiftCampaign(gift, 1, CampaignState.PUBLISHED, draft, now, now, compensation = context))
                            freeze(gift, listOf(player)); putRecipient(GiftRecipient(gift, player, "DELIVERED"))
                        }
                    }
                }
                else -> error("Unknown owned race fixture operation")
            }
        }
        println("RACE_FIXTURE_${operation.uppercase()}_DONE")
    }
}
