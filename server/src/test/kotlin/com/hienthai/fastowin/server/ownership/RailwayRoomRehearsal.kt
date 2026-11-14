package com.hienthai.fastowin.server.ownership

import java.net.InetAddress
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import kotlin.system.exitProcess
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import org.postgresql.ds.PGSimpleDataSource

private val receiptJson = Json { encodeDefaults = true }

internal const val REHEARSAL_JAR_SHA = "8fff8ae123902032d98a92190b0bff3df87e234f213b25a298f4768c7e40e818"

internal interface RehearsalRuntimeSettings {
    val profile: String
    val manifestSha: String
    val deadline: Instant
    fun endpoints(): Map<String, String>
    fun acceptance(addresses: Map<String, String>): RoomAcceptanceSettings
    fun control(addresses: Map<String, String>): SupervisorRoomFaultControl
}

internal class RailwayRehearsalSettings private constructor(
    override val profile: String, override val manifestSha: String, val runName: String, val fixture: UUID,
    private val accounts: List<UUID>, private val dbPassword: String, private val password: String,
    private val hosts: Map<String,String>, private val token: String?, override val deadline: Instant
) : RehearsalRuntimeSettings {
    override fun endpoints(): Map<String, String> = endpoints { InetAddress.getAllByName(it).toList() }

    fun endpoints(resolve: (String) -> List<InetAddress>): Map<String,String> {
        val resolved = hosts.mapValues { (_, host) -> privateAddresses(host, resolve) }
        require(resolved.values.flatten().distinct().size == resolved.values.sumOf { it.size }) { "Overlapping fixture addresses" }
        return resolved.mapValues { (_, ips) -> ips.sortedBy(InetAddress::getHostAddress).first().socketHost() }
    }

    fun seed() {
        val host = endpoints().getValue("pg")
        val source = PGSimpleDataSource().apply {
            setUrl("jdbc:postgresql://$host:5432/fasttowin_room_pilot")
            user = "fasttowin_room_pilot"
            password = dbPassword
            connectTimeout = 4
            socketTimeout = 15
        }
        seedRoomOwnershipFixture(source, fixture, accounts, password)
    }

    override fun acceptance(addresses: Map<String,String>) = RoomAcceptanceSettings(
        project = runName, composeFiles = null, receipts = Path.of("receipts"),
        urls = listOf("a","b").map { "http://${addresses.getValue(it)}:8080" }, proxyUrls = emptyList(),
        postgresPort = 5432, suite = if (profile == "fault") "Fault" else "Load", backendCount = 2,
        durationSeconds = 600, roomCount = 20, seed = 73, fixtureId = fixture, accountIds = accounts,
        databasePassword = dbPassword, accountPassword = password, databaseHost = addresses.getValue("pg"))

    override fun control(addresses: Map<String,String>) = SupervisorRoomFaultControl(fixture.toString(), requireNotNull(token),
        deadline.toEpochMilli(), PrivateControlTransport(addresses))

    companion object {
        fun parse(values: Map<String,String>, now: Instant = Instant.now(), seed: Boolean = false): RailwayRehearsalSettings {
            fun value(key: String) = requireNotNull(values[key]) { "Missing $key" }
            require(values["RAILWAY_PROJECT_ID"] == STAGING_PROJECT && values["RAILWAY_ENVIRONMENT_ID"] == STAGING_ENVIRONMENT &&
                values["RAILWAY_ENVIRONMENT_NAME"] == "staging") { "Unexpected staging environment" }
            require(values.keys.none { it.startsWith("DATABASE_") || it in setOf("JDBC_DATABASE_URL","PGHOST","PGPORT",
                "PGDATABASE","PGUSER","PGPASSWORD","PGSERVICE","PGSERVICEFILE","RAILWAY_TOKEN","RAILWAY_API_TOKEN") })
            val profile = value("FTW_PILOT_SUITE")
            require(profile in setOf("two-backend","fault") && value("FTW_PILOT_BACKEND_POOL_SIZE") == "20")
            val fixture = requireNotNull(parsePilotUuid(value("FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID")))
            val run = "room-pilot-" + fixture.toString().replace("-","").take(12)
            require(value("RAILWAY_SERVICE_NAME") == "$run-runner")
            val deadline = requireNotNull(runCatching { Instant.parse(value("FTW_PILOT_DEADLINE_UTC")) }.getOrNull())
            require(deadline in now.plusSeconds(900)..now.plusSeconds(7200))
            val accounts = value("FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS").split(',').map { requireNotNull(parsePilotUuid(it)) }
            require(accounts.size == 40 && accounts.toSet().size == 40)
            val hex = Regex("[a-f0-9]{64}")
            val dbPassword = value("FASTTOWIN_ROOM_OWNERSHIP_DATABASE_PASSWORD")
            val password = value("FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_PASSWORD")
            val manifest = value("FTW_REHEARSAL_MANIFEST_SHA256")
            require(hex.matches(dbPassword) && password.matches(Regex("Pilot1![a-f0-9]{64}")) && hex.matches(manifest))
            val token = values["ROOM_FAULT_TOKEN"]
            require(if (seed) token == null else token != null && hex.matches(token))
            val keys = mapOf("pg" to "DATABASE","redis" to "REDIS","a" to "BACKEND_A","b" to "BACKEND_B","proxy" to "PROXY")
            val hosts = buildMap {
                for ((role,suffix) in keys) {
                    val host = values["FTW_PILOT_${suffix}_HOST"]
                    if (seed && role != "pg") require(host == null)
                    else {
                        require(host == "$run-$role.railway.internal") { "Unexpected fixture host" }
                        put(role, host)
                    }
                }
            }
            return RailwayRehearsalSettings(profile,manifest,run,fixture,accounts,dbPassword,password,hosts,token,deadline)
        }
    }
}

internal fun observedRehearsalPools(metrics: List<String>): List<Int> {
    require(metrics.size == 2)
    val label = "fasttowin_room_ownership_pool_max_connections "
    return metrics.map { text ->
        val value = text.lineSequence().single { it.startsWith(label) }.removePrefix(label).toDouble()
        check(value == 20.0) { "Each backend must expose a pool maximum of 20" }
        value.toInt()
    }
}

/** Dedicated fixture process. The provider sees only bounded, classified exports. */
fun main(args: Array<String>) {
    val receiptsOut = System.out
    val diagnosticOut = System.err
    val sink = PrintStream(OutputStream.nullOutputStream())
    System.setOut(sink)
    System.setErr(sink)
    val code = try {
        val mode = args.singleOrNull()
        require(args.isEmpty() || mode in setOf("--seed", "--capability"))
        val settings = RailwayRehearsalSettings.parse(System.getenv(), seed = mode == "--seed")
        if (mode == "--seed") {
            settings.seed()
            receiptsOut.println("""{"fixtureSeeded":true}""")
        } else if (mode == "--capability") {
            val endpoints = settings.endpoints()
            val redisPassword = requireNotNull(System.getenv("FTW_FIXTURE_REDIS_PASSWORD"))
            var stage = PrivateCapabilityStage.READY
            try {
                runBlocking {
                    withTimeout(120_000) {
                        RoomAcceptanceFixture(settings.acceptance(endpoints), settings.control(endpoints)).use { fixture ->
                            fixture.verifyPrivateCapabilities(endpoints, redisPassword) { stage = it }
                        }
                    }
                }
            } catch (_: Exception) {
                throw PrivateCapabilityFailure(stage)
            }
            receiptsOut.println("""{"privateCapabilityVerified":true,"manifestSha256":"${settings.manifestSha}","profile":"${settings.profile}"}""")
        } else runRehearsal(settings, receiptsOut)
        0
    } catch (failure: Throwable) {
        when {
            failure is PrivateCapabilityFailure -> receiptsOut.println("Private capability failed: ${failure.stage.name}")
            args.contentEquals(arrayOf("--capability")) -> receiptsOut.println("Private capability failed: READY")
            else -> diagnosticOut.println("Room rehearsal failed")
        }
        1
    } finally {
        System.setOut(receiptsOut)
        System.setErr(diagnosticOut)
        sink.close()
    }
    if (code != 0) exitProcess(code)
}

internal fun runRehearsal(
    settings: RehearsalRuntimeSettings,
    out: PrintStream,
    jarSha256: String = REHEARSAL_JAR_SHA,
    production: Boolean = false,
    preflightOnly: Boolean = false
) {
    val path = Path.of("receipts")
    Files.createDirectories(path)
    val latency = List(2) { BaselineLatencyRecorder() }
    var fixtureMetrics = emptyMap<String, Double>()
    var phase = "private-dns"
    val cases = mutableListOf<JsonObject>()
    var progress: BaselineLoadProgress? = null
    fun export(kind: String, payload: JsonObject) {
        val lines = emitRehearsalReceipt(settings.manifestSha, settings.profile, kind, payload, jarSha256).map {
            if (production) it.replaceFirst("FTW_REHEARSAL_", "FTW_PRODUCTION_REHEARSAL_") else it
        }
        val original = Base64.getDecoder().decode(lines.joinToString("") { it.substringAfterLast(' ') })
        Files.write(path.resolve("rehearsal-$kind.json"), original)
        lines.forEach(out::println)
    }
    try {
        val endpoints = settings.endpoints()
        val control = settings.control(endpoints)
        val result = runBlocking {
            withTimeout(minOf(900000L, settings.deadline.toEpochMilli() - System.currentTimeMillis())) {
                RoomAcceptanceFixture(settings.acceptance(endpoints), control).use { fixture ->
                    phase = "fixture-marker-and-readiness"
                    fixture.verifyFixture()
                    fixture.awaitReady(0)
                    fixture.awaitReady(1)
                    control.verifyHealthy()
                    val pools = observedRehearsalPools(fixture.metrics())
                    fixture.fundQuota()
                    if (fixture.settings.soleIngress) {
                        phase = "topology-preflight"
                        val preflight = runProductionTopologyPreflight(fixture)
                        if (preflightOnly) return@use preflight
                        export("topology-preflight", preflight)
                    }
                    check(!preflightOnly) { "Golden preflight requires sole ingress topology" }
                    if (settings.profile == "topology") {
                        check(production && fixture.settings.soleIngress)
                        phase = "topology-presence"
                        export(phase, productionPresenceAcceptance(fixture))
                        phase = "topology-outages"
                        export(phase, productionIngressAcceptance(fixture))
                        phase = "topology-lanes"
                        export(phase, productionLaneAcceptance(fixture))
                        phase = "topology-drain"
                        return@use productionDrainAcceptance(fixture)
                    }
                    phase = "latency"
                    withTimeout(60000) {
                        for (node in 0..1) fixture.measureBaselineLatency(latency[node], node)
                    }
                    fixture.resetDatabaseTimings()
                    phase = if (settings.profile == "fault") "fault" else "load"
                    try {
                        val payload = if (phase == "fault") {
                            var cursor = 0
                            val suite = runRoomOwnershipFaults(fixture) { receipt ->
                                val operations = control.verifiedMutations()
                                cases += faultCaseProjection(receipt, operations.drop(cursor))
                                cursor = operations.size
                                control.verifyHealthy()
                            }
                            check(suite.accepted && cases.map { it.getValue("name").jsonPrimitive.content } == rehearsalCases)
                            buildJsonObject {
                                put("backendCount",fixture.backendCount); put("pools", Json.encodeToJsonElement(pools))
                                put("cases",JsonArray(cases))
                            }
                        } else {
                            val load = runRoomOwnershipLoad(fixture) { progress = it }
                            rehearsalLoadProjection(load,control.verifiedMutations())
                        }
                        control.verifyHealthy()
                        fixture.requireBothReady()
                        observedRehearsalPools(fixture.metrics())
                        payload
                    } finally { fixtureMetrics = fixture.databaseTimingMetrics() }
                }
            }
        }
        val kind = phase
        phase = "receipt"
        export(kind, result)
    } catch (failure: Throwable) {
        Files.writeString(path.resolve("failure-sites.json"), rehearsalFailureDiagnostic(failure))
        printRehearsalFailureDiagnostic(out, failure)
        val category = rehearsalFailureCategory(failure)
        export("failure", buildJsonObject {
            put("phase",phase); put("category", category); put("completedCases",cases.size)
            put("progress",receiptJson.encodeToJsonElement(progress))
        })
        throw IllegalStateException("Rehearsal failed")
    } finally {
        export("latency",buildJsonObject {
            put("nodes",receiptJson.encodeToJsonElement(latency.map { it.receipt(fixtureMetrics) }))
        })
    }
}
