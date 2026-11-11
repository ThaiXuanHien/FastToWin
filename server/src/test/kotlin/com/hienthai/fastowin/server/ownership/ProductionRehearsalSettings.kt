package com.hienthai.fastowin.server.ownership

import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant

/** Test-image adapter bound to a disposable staging run. Production rate policies stay unchanged. */
internal class ProductionRehearsalSettings private constructor(
    val seedSettings: RoomOwnershipProductionSeedSettings,
    override val profile: String,
    override val deadline: Instant,
    val runName: String,
    val jarSha256: String,
    val redisPassword: String,
    private val token: String,
    private val hosts: Map<String, String>
) : RehearsalRuntimeSettings {
    val runId get() = seedSettings.runId
    val databaseName get() = seedSettings.databaseName
    override val manifestSha get() = seedSettings.manifestSha256

    override fun endpoints(): Map<String, String> = endpoints { InetAddress.getAllByName(it).toList() }

    fun endpoints(resolve: (String) -> List<InetAddress>): Map<String, String> {
        val resolved = hosts.mapValues { (_, host) -> privateAddresses(host, resolve) }
        require(resolved.values.flatten().distinct().size == resolved.values.sumOf { it.size }) {
            "Overlapping rehearsal addresses"
        }
        return resolved.mapValues { (_, addresses) -> addresses.sortedBy(InetAddress::getHostAddress).first().socketHost() }
    }

    override fun acceptance(addresses: Map<String, String>) = RoomAcceptanceSettings(
        project = runName, composeFiles = null, receipts = Path.of("receipts"),
        urls = listOf("a", "b").map { "http://${addresses.getValue(it)}:8080" }, proxyUrls = emptyList(),
        postgresPort = 5432, suite = if (profile == "fault") "Fault" else "Load", backendCount = 2,
        durationSeconds = 600, roomCount = 20, seed = 73, fixtureId = runId, accountIds = seedSettings.accounts,
        databasePassword = seedSettings.database.password, accountPassword = seedSettings.password,
        databaseHost = addresses.getValue("pg"), productionLike = true
    )

    override fun control(addresses: Map<String, String>) = SupervisorRoomFaultControl(
        runId.toString(), token, deadline.toEpochMilli(), PrivateControlTransport(addresses)
    )

    fun verifiedJarSha(path: Path): String {
        require(Files.isRegularFile(path)) { "Missing rehearsal server JAR" }
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                digest.update(buffer, 0, size)
            }
        }
        val observed = digest.digest().joinToString("") { "%02x".format(it) }
        require(observed == jarSha256) { "Unexpected rehearsal server JAR" }
        return observed
    }

    companion object {
        fun parse(values: Map<String, String>, now: Instant = Instant.now()): ProductionRehearsalSettings {
            val seed = RoomOwnershipProductionSeedSettings.fromEnvironment(values)
            fun value(key: String) = requireNotNull(values[key]) { "Missing $key" }
            val prefix = values["FTW_REHEARSAL_PREFIX"] ?: "room-prod"
            require(prefix in setOf("room-prod", "room-pilot"))
            val run = prefix + "-" + seed.runId.toString().replace("-", "").take(12)
            require(value("RAILWAY_SERVICE_NAME") == "$run-runner")
            val profile = value("FTW_REHEARSAL_PROFILE")
            require(profile in setOf("fault", "two-backend"))
            require(value("FTW_REHEARSAL_BACKEND_POOL_SIZE") == "20")
            require((values["FTW_REHEARSAL_DURATION_SECONDS"] ?: "600") == "600" &&
                (values["FTW_REHEARSAL_ROOMS"] ?: "20") == "20" &&
                (values["FTW_REHEARSAL_BACKENDS"] ?: "2") == "2") { "A complete rehearsal profile is required" }
            require(values.keys.none { it.startsWith("FTW_ACCEPTANCE_") || it.startsWith("FTW_PILOT_") })
            require(seed.accounts.size == 40) { "Twenty independent account pairs are required" }
            val deadline = requireNotNull(runCatching { Instant.parse(value("FTW_REHEARSAL_DEADLINE_UTC")) }.getOrNull())
            require(deadline in now.plusSeconds(900)..now.plusSeconds(7200))
            val hex = Regex("[a-f0-9]{64}")
            val jar = value("FTW_REHEARSAL_JAR_SHA256")
            val token = value("ROOM_FAULT_TOKEN")
            val redis = value("FTW_REHEARSAL_REDIS_PASSWORD")
            require(hex.matches(jar) && hex.matches(token) && hex.matches(redis))
            val hosts = buildMap {
                put("pg", seed.databaseHost)
                for ((role, key) in mapOf("a" to "BACKEND_A", "b" to "BACKEND_B", "redis" to "REDIS", "proxy" to "PROXY")) {
                    val host = value("FTW_REHEARSAL_${key}_HOST")
                    require(host == "$run-$role.railway.internal") { "Unexpected rehearsal host" }
                    put(role, host)
                }
            }
            return ProductionRehearsalSettings(seed, profile, deadline, run, jar, redis, token, hosts)
        }
    }
}
