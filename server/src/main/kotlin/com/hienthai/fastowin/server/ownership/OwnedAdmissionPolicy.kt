package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ProtocolGameMode
import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable
internal enum class OwnedAdmissionState { ACTIVE, STOP_NEW, QUIESCING, DISABLED }

/** A committed authority observation; carrying it never substitutes for the transaction guard. */
@Serializable
internal data class OwnedAdmissionPolicy(
    val generation: Long,
    val state: OwnedAdmissionState,
    val topologySha256: String?,
    val policySha256: String?
) {
    fun admits(binding: OwnedAdmissionBinding): Boolean = generation > 0 && state == OwnedAdmissionState.ACTIVE &&
        topologySha256 == binding.topologySha256 && policySha256 == binding.policySha256
}

internal data class OwnedAdmissionBinding(val topologySha256: String, val policySha256: String) {
    companion object {
        fun from(config: RoomOwnershipRuntimeConfig): OwnedAdmissionBinding {
            require(config.fixtureId == null) { "Fixture admission has no production authority" }
            require(config.nodeId != config.peerId && config.allowedAccounts.isNotEmpty())
            require(config.supportsMode(ProtocolGameMode.ORDER) &&
                ProtocolGameMode.entries.filter { it != ProtocolGameMode.ORDER }.none(config::supportsMode)) {
                "Unsupported production ownership slice"
            }
            val ingress = config.runtimeRole == RoomOwnershipRuntimeRole.INGRESS
            val topology = "fasttowin/owned-topology/v1\n" +
                "ingress=${if (ingress) config.nodeId else config.peerId}\n" +
                "owned-worker=${if (ingress) config.peerId else config.nodeId}\n"
            val policy = "fasttowin/owned-policy/v1\nmode=production\nmatch=CASUAL\ngame=ORDER\nplayers=2\n" +
                "registered=true\nemail-verified=true\nprivate-password=true\n" +
                "accounts=${config.allowedAccounts.map { it.toString() }.sorted().joinToString(",")}\n"
            return OwnedAdmissionBinding(sha256(topology), sha256(policy))
        }

        private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

internal class OwnedAdmissionRejected(message: String) : IllegalStateException(message)

internal fun requireOwnedNewIntent(
    actual: OwnedAdmissionPolicy?, expected: OwnedAdmissionPolicy?, binding: OwnedAdmissionBinding
) {
    if (actual == null || expected == null || !actual.admits(binding) || !expected.admits(binding) ||
        actual.generation != expected.generation
    ) throw OwnedAdmissionRejected("Owned admission policy is unavailable, stopped, mismatched or stale")
}
