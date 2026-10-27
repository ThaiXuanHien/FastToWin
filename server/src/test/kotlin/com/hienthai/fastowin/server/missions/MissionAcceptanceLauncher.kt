package com.hienthai.fastowin.server.missions

import com.hienthai.fastowin.server.*
import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.protocol.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Loopback-only acceptance assembly. This test source is absent from installDist. */
fun main() {
    check(System.getenv("FASTTOWIN_ENV") == "dev")
    check(System.getenv("SERVER_HOST") == "127.0.0.1")
    val settings = requireNotNull(DatabaseSettings.fromEnvironment("dev"))
    check(settings.url.startsWith("jdbc:postgresql://127.0.0.1:15432/admin_test?currentSchema=webe2e_"))
    check(Regex("webe2e_[a-f0-9]{32}").matches(settings.url.substringAfter("currentSchema=")))
    val clockFile = Path.of(requireNotNull(System.getenv("MISSION_FIXTURE_CLOCK"))).toAbsolutePath()
    val today = LocalDate.now(ZoneId.of("Asia/Bangkok"))
    val clock = {
        val date = LocalDate.parse(Files.readString(clockFile).trim())
        check(date >= today.minusDays(1) && date <= today.plusDays(8))
        date.atTime(12, 0).atZone(ZoneId.of("Asia/Bangkok")).toInstant().toEpochMilli()
    }
    val database = DatabaseRuntime.open(settings)
    val configuration = requireNotNull(AdminConfiguration.fromEnvironment("dev"))
    check(configuration.origin == "http://127.0.0.1:18086")
    val admins = AdminService(database.adminRepository, configuration)
    runBlocking {
        admins.bootstrapOwner(requireNotNull(System.getenv("MISSION_FIXTURE_OWNER_EMAIL")),
            Files.readString(Path.of(requireNotNull(System.getenv("MISSION_FIXTURE_OWNER_PASSWORD_FILE")))).trim())
    }
    val engine = GameEngine(identityRepository = database.identityRepository,
        matchResultRepository = database.matchResultRepository, playerProfileRepository = database.playerProfileRepository,
        leaderboardRepository = database.leaderboardRepository, friendRepository = database.friendRepository,
        activeRoomRepository = database.activeRoomRepository, notificationRepository = database.notificationRepository,
        tournamentRepository = database.tournamentRepository, clanRepository = database.clanRepository,
        playQuotaRepository = database.playQuotaRepository)
    val server = embeddedServer(Netty, host = "127.0.0.1", port = 18086) {
        gameModule(engine = engine, authService = AuthenticationService(database.authRepository),
            adminService = admins, missionService = MissionService(database.missionRepository, admins, clock),
            allowedWebOriginsValue = "http://127.0.0.1:18087,http://127.0.0.1:18086")
    }.start(wait = false)
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(100, 1000); database.close() })
    System.`in`.bufferedReader().forEachLine { line ->
        val command = Json.parseToJsonElement(line).jsonObject
        val player = UUID.fromString(command.getValue("player").jsonPrimitive.content).toString()
        runBlocking {
            when (command.getValue("operation").jsonPrimitive.content) {
                "match" -> {
                    val other = UUID.fromString(command.getValue("other").jsonPrimitive.content).toString()
                    val now = System.currentTimeMillis()
                    val match = CompletedMatch(UUID.randomUUID().toString(), "Owned mission acceptance", ProtocolGameMode.ORDER,
                        now - 1000, now, player, listOf(CompletedMatchPlayer(player, "Synthetic Player", 260, MatchOutcome.WIN),
                            CompletedMatchPlayer(other, "Synthetic Opponent", 240, MatchOutcome.LOSS)), matchType = MatchType.CASUAL)
                    database.matchResultRepository.save(match); database.matchResultRepository.save(match)
                }
                "donation" -> {
                    val clan = UUID.fromString(command.getValue("clan").jsonPrimitive.content).toString()
                    val request = UUID.randomUUID().toString()
                    check(database.clanRepository.donateToClan(player, clan, request, ClanDonationCurrency.GOLD, 100).status == ClanDonationStatus.APPLIED)
                    check(database.clanRepository.donateToClan(player, clan, request, ClanDonationCurrency.GOLD, 100).status == ClanDonationStatus.DUPLICATE)
                }
                else -> error("Unknown owned fixture operation")
            }
        }
        println("MISSION_FIXTURE_${command.getValue("operation").jsonPrimitive.content.uppercase()}_DONE")
    }
}
