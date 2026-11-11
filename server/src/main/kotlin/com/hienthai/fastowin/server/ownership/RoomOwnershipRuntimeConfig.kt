package com.hienthai.fastowin.server.ownership

import java.util.UUID
import com.hienthai.fastowin.protocol.ProtocolGameMode

internal interface RoomOwnershipRuntimeConfig {
    val nodeId: UUID
    val peerId: UUID
    val peerUri: java.net.URI
    val peerSecret: ByteArray
    val allowedAccounts: Set<UUID>
    val fixtureId: UUID?
    val acceptanceTraffic: Boolean
    fun supportsMode(mode: ProtocolGameMode): Boolean
    fun rateLimitPolicies(): com.hienthai.fastowin.server.ServerRateLimitPolicies
}

internal object RoomOwnershipRuntimeConfigLoader {
    fun fromEnvironment(environment: String, values: Map<String, String> = System.getenv()): RoomOwnershipRuntimeConfig? =
        when (values["FASTTOWIN_ROOM_OWNERSHIP_MODE"]?.trim()) {
            null, "", "legacy" -> null
            "pilot" -> RoomOwnershipPilotConfig.fromEnvironment(environment, values)
            "production" -> {
                require(environment == "prod") { "Production room ownership requires a production environment" }
                RoomOwnershipProductionPolicy.fromEnvironment(values)
            }
            else -> error("Unknown room ownership mode")
        }
}
