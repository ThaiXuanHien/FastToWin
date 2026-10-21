package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.UgcTargetType

internal data class UgcReportReference(val type: UgcTargetType, val id: String, val label: String)
private val canonicalUgcId = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

/** Only explicit target metadata is reportable; notification IDs and displayed names are not identities. */
internal fun AppNotification.reportReferences(): List<UgcReportReference> {
    if (isServerAnnouncement()) return emptyList()
    return listOf(
        Triple(UgcTargetType.PLAYER_PROFILE, "playerId", "player"),
        Triple(UgcTargetType.ROOM, "roomId", "room"),
        Triple(UgcTargetType.CLAN, "clanId", "clan")
    ).mapNotNull { (type, idKey, textKey) ->
        val targetId = messageArgs[idKey]?.takeIf(canonicalUgcId::matches) ?: return@mapNotNull null
        UgcReportReference(type, targetId, messageArgs[textKey]?.takeIf(String::isNotBlank) ?: title)
    }
}
