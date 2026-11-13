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
    private val hosts: Map<String, String>,
    private val runtimePolicy: RoomOwnershipProductionPolicy,
    val topologyIdentity: RehearsalTopologyIdentity?
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
        databaseHost = addresses.getValue("pg"), productionLike = true, soleIngress = topologyIdentity != null,
        topologyStatus = topologyIdentity?.let { RehearsalStatusConfig(it, seedSettings.admissionBinding,
            runtimePolicy, addresses) }
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
            val topology = when (values["FTW_REHEARSAL_TOPOLOGY"]) {
                null -> null
                "sole-ingress" -> {
                    val generation = value("FTW_REHEARSAL_ADMISSION_GENERATION").toLongOrNull()
                    require(generation != null && generation > 0)
                    val candidate = value("FTW_REHEARSAL_CANDIDATE_SHA256")
                    require(hex.matches(candidate))
                    values["FTW_REHEARSAL_TOPOLOGY_SHA256"]?.let { require(it == seed.admissionBinding.topologySha256) }
                    values["FTW_REHEARSAL_POLICY_SHA256"]?.let { require(it == seed.admissionBinding.policySha256) }
                    values["FTW_REHEARSAL_CLASSPATH_SHA256"]?.let { classpath ->
                        require(hex.matches(classpath))
                        val source = "fasttowin/candidate/v1\n$jar\n$classpath\n".toByteArray(Charsets.UTF_8)
                        val measured = MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it) }
                        require(candidate == measured) { "Candidate manifest binding mismatch" }
                    }
                    RehearsalTopologyIdentity(generation, candidate)
                }
                else -> throw IllegalArgumentException("Unknown rehearsal topology")
            }
            return ProductionRehearsalSettings(seed, profile, deadline, run, jar, redis, token, hosts, requireNotNull(RoomOwnershipProductionPolicy.fromEnvironment(values)), topology)
        }
    }
}

internal data class RehearsalTopologyIdentity(val generation: Long, val candidateSha256: String)

internal fun verifiedRehearsalProcess(response: OwnedControlResponse?, node: java.util.UUID,
    identity: RehearsalTopologyIdentity, binding: OwnedAdmissionBinding): java.util.UUID {
    check(response != null && response.operation == "status" && response.generation == identity.generation &&
        response.outcome == "ok" && response.nodeId == node.toString() &&
        response.candidateSha256 == identity.candidateSha256 && response.topologySha256 == binding.topologySha256 &&
        response.policy?.generation == identity.generation && response.policy.admits(binding)) {
        "Signed rehearsal status does not match committed candidate and admission"
    }
    return checkNotNull(response.processId?.let(::parsePilotUuid)) { "Invalid signed rehearsal process identity" }
}

internal class RehearsalStatusConfig(
    private val identity: RehearsalTopologyIdentity,
    private val binding: OwnedAdmissionBinding,
    private val config: RoomOwnershipProductionPolicy,
    private val addresses: Map<String, String>
) {
    fun targetPair(accounts: List<java.util.UUID>, slot: Int, node: Int) = targetedRehearsalPair(accounts, slot, config, node)

    suspend fun process(node: Int): java.util.UUID {
        require(node in 0..1)
        val target = if (node == 0) config.nodeId else config.peerId
        val sender = if (node == 0) config.peerId else config.nodeId
        val uri = java.net.URI("http://${addresses.getValue(if (node == 0) "a" else "b")}:8080")
        val request = OwnedControlRequest(java.util.UUID.randomUUID().toString(), "status", identity.generation)
        return OwnerPeerTransport(sender, target, uri, config.peerSecret).use { transport ->
            verifiedRehearsalProcess(transport.forwardControl(request), target, identity, binding)
        }
    }
}
internal fun targetedRehearsalPair(accounts: List<java.util.UUID>, slot: Int,
    config: RoomOwnershipProductionPolicy, node: Int): List<Int> {
    require(node in 0..1 && slot >= 0 && slot * 2 + 1 < accounts.size)
    val indices = listOf(slot * 2, slot * 2 + 1)
    val expected = if (node == 0) config.nodeId else config.peerId
    val host = indices.single { RoomOwnershipRouting(config).createNode(accounts[it].toString()) == expected }
    return listOf(host, indices.single { it != host })
}
