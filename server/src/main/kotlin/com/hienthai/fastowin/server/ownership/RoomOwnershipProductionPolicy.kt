package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.server.AuthenticatedAccount
import com.hienthai.fastowin.server.readEnvironmentSecret
import com.hienthai.fastowin.protocol.ProtocolGameMode
import java.net.URI
import java.util.Base64
import java.util.UUID

internal class RoomOwnershipProductionPolicy private constructor(
    override val nodeId: UUID,
    override val peerId: UUID,
    override val peerUri: URI,
    peerSecret: ByteArray,
    allowedAccounts: Set<UUID>
) : RoomOwnershipRuntimeConfig {
    override val peerSecret = peerSecret.copyOf()
    override val allowedAccounts = allowedAccounts.toSet()
    override val fixtureId: UUID? = null
    override val acceptanceTraffic: Boolean = false

    init {
        require(nodeId != peerId) { "Production node IDs must differ" }
        require(peerUri.scheme in setOf("http", "https") &&
            peerUri.host?.endsWith(".railway.internal") == true &&
            peerUri.port in 1..65535 &&
            peerUri.rawUserInfo == null &&
            peerUri.rawPath in setOf("", "/") &&
            peerUri.rawQuery == null &&
            peerUri.rawFragment == null
        ) { "Production peer URI must use a private Railway domain" }
        require(peerSecret.size == 32) { "Production peer secret must be 32 bytes" }
        require(allowedAccounts.isNotEmpty() && allowedAccounts.size <= 256) {
            "Invalid production account opt-in set"
        }
    }

    fun allows(account: AuthenticatedAccount, mode: ProtocolGameMode): Boolean =
        account.emailVerified && account.userId in allowedAccounts && mode == ProtocolGameMode.ORDER

    fun routeKey(accountId: UUID): String = accountId.toString()

    override fun rateLimitPolicies() = com.hienthai.fastowin.server.ServerRateLimitPolicies()

    override fun supportsMode(mode: ProtocolGameMode): Boolean = mode == ProtocolGameMode.ORDER

    companion object {
        fun fromEnvironment(values: Map<String, String>): RoomOwnershipProductionPolicy? {
            return when (values["FASTTOWIN_ROOM_OWNERSHIP_MODE"]?.trim()) {
                null, "", "legacy" -> null
                "production" -> parse(values)
                "pilot" -> null
                else -> error("Unknown room ownership mode")
            }
        }

        private fun parse(values: Map<String, String>): RoomOwnershipProductionPolicy {
            require(values["FASTTOWIN_ENV"]?.trim() == "prod") {
                "Production room ownership requires FASTTOWIN_ENV=prod"
            }
            require(values["FASTTOWIN_ROOM_OWNERSHIP_ACCEPTANCE"] == null) {
                "Production room ownership cannot use fixture acceptance settings"
            }
            require(values.keys.none { it in setOf(
                "FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID",
                "FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_PASSWORD"
            ) }) { "Production room ownership cannot use fixture settings" }

            fun required(name: String) =
                values["FASTTOWIN_ROOM_OWNERSHIP_$name"]?.trim()?.takeIf { it.isNotEmpty() }
                    ?: error("Missing production room ownership setting $name")

            fun uuid(name: String): UUID =
                runCatching { UUID.fromString(required(name)) }.getOrNull()
                    ?: error("Invalid production room ownership $name")

            val encodedSecret = readEnvironmentSecret("FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET", values)
                ?: error("Missing production room ownership peer secret")
            val secret = runCatching { Base64.getDecoder().decode(encodedSecret) }.getOrNull()
                ?: error("Production peer secret must be Base64")
            val accounts = required("ACCOUNTS").split(',')
                .map { value -> runCatching { UUID.fromString(value.trim()) }.getOrNull()
                    ?: error("Invalid production room ownership account") }
                .toSet()
            val peerUri = runCatching { URI(required("PEER_URL")) }.getOrNull()
                ?: error("Invalid production room ownership peer URI")

            return RoomOwnershipProductionPolicy(
                nodeId = uuid("NODE_ID"),
                peerId = uuid("PEER_ID"),
                peerUri = peerUri,
                peerSecret = secret,
                allowedAccounts = accounts
            )
        }
    }
}
