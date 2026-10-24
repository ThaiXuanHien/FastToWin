package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.moderation.ContentSuppression
import com.hienthai.fastowin.server.moderation.contentRevision
import kotlinx.serialization.json.*
import java.util.UUID

/** A single projection boundary covers live snapshots, nested history, invitations and social lists. */
internal fun sanitizeContent(message: ServerMessage, suppressions: List<ContentSuppression>): ServerMessage {
    if (suppressions.isEmpty()) return message
    val byType = suppressions.groupBy(ContentSuppression::targetType)
    fun suppressed(type: UgcTargetType, id: String?, text: String? = null, legacy: Boolean = false): Boolean {
        val fingerprint = text?.let(::contentRevision)
        return byType[type].orEmpty().any {
            (it.targetId == id || (legacy && id == null && fingerprint != null)) &&
                (fingerprint == null || it.revision == fingerprint || it.nameRevision == fingerprint)
        }
    }
    fun project(element: JsonElement): JsonElement {
        if (element is JsonArray) return JsonArray(element.map(::project))
        if (element !is JsonObject) return element
        fun text(key: String): String? = (element[key] as? JsonPrimitive)?.contentOrNull
        val result = element.mapValues { project(it.value) }.toMutableMap()
        fun neutralName(field: String, type: UgcTargetType, id: String?, neutral: String, legacy: Boolean = false) {
            val value = text(field) ?: return
            if (suppressed(type, id, value, legacy)) result[field] = JsonPrimitive(neutral)
        }
        val playerId = text("userId") ?: text("playerId") ?: text("id").takeIf { "score" in element }
        neutralName("displayName", UgcTargetType.PLAYER_PROFILE, playerId, "Player")
        if ("score" in element) neutralName("name", UgcTargetType.PLAYER_PROFILE, playerId, "Player")
        neutralName("playerName", UgcTargetType.PLAYER_PROFILE, text("playerId"), "Player", legacy = true)
        neutralName("opponentName", UgcTargetType.PLAYER_PROFILE, text("opponentUserId"), "Player", legacy = true)
        neutralName("hostDisplayName", UgcTargetType.PLAYER_PROFILE, text("hostPlayerId"), "Player")
        neutralName("fromDisplayName", UgcTargetType.PLAYER_PROFILE, text("fromUserId"), "Player")
        neutralName("hostName", UgcTargetType.PLAYER_PROFILE, text("hostId"), "Player", legacy = true)
        if ("avatarId" in element && suppressed(UgcTargetType.PLAYER_AVATAR, playerId)) result["avatarId"] = JsonPrimitive(MODERATED_AVATAR_ID)
        val tournamentId = text("tournamentId")
        neutralName("tournamentName", UgcTargetType.TOURNAMENT, tournamentId, "Tournament")
        if (tournamentId != null) neutralName("name", UgcTargetType.TOURNAMENT, tournamentId, "Tournament")
        neutralName("roomName", UgcTargetType.ROOM, text("roomId"), "Room", legacy = "opponentName" in element)
        if ("hostName" in element) neutralName("name", UgcTargetType.ROOM, text("id"), "Room")
        val clanId = text("clanId") ?: text("id").takeIf { "members" in element || "memberCount" in element }
        neutralName("clanName", UgcTargetType.CLAN, clanId, "Clan")
        if (clanId != null && "name" in element) {
            val fullText = text("description")?.let { text("name").orEmpty() + "\n" + it } ?: text("name")
            if (suppressed(UgcTargetType.CLAN, clanId, fullText)) {
                result["name"] = JsonPrimitive("Clan")
                if ("description" in element) result["description"] = JsonPrimitive("")
                if ("logoId" in element) result["logoId"] = JsonNull
            }
        }
        // Trusted target references scope new notifications precisely. Historical notifications
        // without references retain exact content-fingerprint matching.
        var changedArgs = false
        for (field in listOf("titleArgs", "messageArgs")) {
            val args = result[field] as? JsonObject ?: continue
            val safe = args.mapValues { (key, value) ->
                val kind = when (key) {
                    "player", "opponent", "host" -> Triple(UgcTargetType.PLAYER_PROFILE, "Player", "playerId")
                    "room" -> Triple(UgcTargetType.ROOM, "Room", "roomId")
                    "tournament" -> Triple(UgcTargetType.TOURNAMENT, "Tournament", "tournamentId")
                    "clan" -> Triple(UgcTargetType.CLAN, "Clan", "clanId")
                    else -> null
                }
                val hasReference = kind != null && args.containsKey(kind.third)
                val rawReference = kind?.third?.let { (args[it] as? JsonPrimitive)?.contentOrNull }
                val reference = rawReference?.let { raw ->
                    runCatching { UUID.fromString(raw).toString() }.getOrNull()?.takeIf { it.equals(raw, ignoreCase = true) }
                }
                if (kind != null && suppressed(kind.first, reference, (value as? JsonPrimitive)?.contentOrNull, legacy = !hasReference)) {
                    changedArgs = true; JsonPrimitive(kind.second)
                } else value
            }
            result[field] = JsonObject(safe)
        }
        if (changedArgs) {
            if ("title" in element) result["title"] = JsonPrimitive("Notification")
            if ("message" in element) result["message"] = JsonPrimitive("Content updated.")
        }
        return JsonObject(result)
    }
    return ProtocolJson.decodeFromJsonElement<ServerMessage>(project(ProtocolJson.encodeToJsonElement<ServerMessage>(message)))
}


/** Readable fallback is retained for clients released before versioned content terms existed. */
internal fun publicModerationMessage(code: String): String = when (code) {
    "CONTENT_TERMS_REQUIRED" -> legacyFallback("Vui lòng cập nhật ứng dụng và đồng ý Quy tắc nội dung trước khi tạo hoặc sửa nội dung công khai.")
    "CONTENT_TERMS_VERSION_INVALID" -> legacyFallback("Quy tắc nội dung đã thay đổi. Vui lòng cập nhật ứng dụng và đọc phiên bản mới.")
    "ACCOUNT_BLOCKED" -> legacyFallback("Tài khoản đã bị khóa. Vui lòng liên hệ hỗ trợ.")
    else -> code
}
