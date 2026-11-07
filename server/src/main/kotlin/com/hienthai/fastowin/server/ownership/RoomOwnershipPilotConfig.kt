package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.server.readEnvironmentSecret
import com.hienthai.fastowin.server.ServerRateLimitPolicies
import com.hienthai.fastowin.server.RateLimitPolicy
import java.net.URI
import java.util.Base64
import java.util.UUID

internal class RoomOwnershipPilotConfig(
    val nodeId: UUID,
    val peerId: UUID,
    val peerUri: URI,
    peerSecret: ByteArray,
    val fixtureId: UUID,
    allowedAccounts: Set<UUID>,
    val acceptanceTraffic: Boolean = false
) {
    val peerSecret = peerSecret.copyOf()
    val allowedAccounts = allowedAccounts.toSet()

    init {
        require(nodeId != peerId) { "Pilot node IDs must differ" }
        require(peerUri.scheme in setOf("http", "https") && peerUri.host != null && peerUri.port in 1..65535 &&
            peerUri.rawUserInfo == null && peerUri.rawPath in setOf("", "/") &&
            peerUri.rawQuery == null && peerUri.rawFragment == null
        ) { "A private pilot peer URI with an explicit port is required" }
        require(this.peerSecret.size == 32) { "Pilot requires a dedicated 32-byte peer secret" }
        require(this.allowedAccounts.isNotEmpty() && this.allowedAccounts.size <= 256) { "Invalid pilot account allowlist" }
    }

    fun rateLimitPolicies(): ServerRateLimitPolicies {
        val defaults = ServerRateLimitPolicies()
        if (!acceptanceTraffic) return defaults
        return defaults.copy(
            loginPerIp = RateLimitPolicy(512, 60_000),
            websocketConnectPerIp = RateLimitPolicy(5_000, 60_000),
            websocketMessagesPerIp = RateLimitPolicy(5_000, 1_000),
            createRoomPerPlayer = RateLimitPolicy(1_000, 60_000),
            createRoomPerIp = RateLimitPolicy(5_000, 60_000),
            joinRoomPerPlayer = RateLimitPolicy(1_000, 60_000),
            joinRoomPerIp = RateLimitPolicy(5_000, 60_000),
            selectNumberPerIp = RateLimitPolicy(5_000, 1_000)
        )
    }

    companion object {
        fun fromEnvironment(
            environment: String,
            values: Map<String, String> = System.getenv()
        ): RoomOwnershipPilotConfig? {
            return when (values["FASTTOWIN_ROOM_OWNERSHIP_MODE"]?.trim()) {
                null, "", "legacy" -> null
                "pilot" -> {
                    require(environment == "dev") { "The room ownership pilot requires a fixture environment" }
                    val encoded = requireNotNull(readEnvironmentSecret("FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET", values)) {
                        "A dedicated pilot peer secret is required"
                    }
                    val secret = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
                        ?: error("Pilot peer secret must be Base64")
                    RoomOwnershipPilotConfig(
                        values.pilotUuid("NODE_ID"), values.pilotUuid("PEER_ID"),
                        runCatching { URI(values.requiredPilotValue("PEER_URL")) }.getOrNull()
                            ?: error("Invalid pilot peer URI"),
                        secret, values.pilotUuid("FIXTURE_ID"),
                        values.requiredPilotValue("ACCOUNTS").split(",").map { requireUuid(it.trim(), "account ID") }.toSet(),
                        values["FASTTOWIN_ROOM_OWNERSHIP_ACCEPTANCE"]?.toBooleanStrict()
                            ?: false
                    )
                }
                else -> error("Unknown FASTTOWIN_ROOM_OWNERSHIP_MODE")
            }
        }

        private fun Map<String, String>.requiredPilotValue(name: String): String =
            get("FASTTOWIN_ROOM_OWNERSHIP_$name")?.trim()?.takeIf { it.isNotEmpty() }
                ?: error("Missing pilot setting $name")

        private fun Map<String, String>.pilotUuid(name: String) = requireUuid(requiredPilotValue(name), name)

        private fun requireUuid(value: String, name: String): UUID =
            parsePilotUuid(value) ?: error("Invalid pilot $name")
    }
}

internal fun parsePilotUuid(value: String): UUID? =
    runCatching { UUID.fromString(value) }.getOrNull()?.takeIf { it.toString() == value.lowercase() }
