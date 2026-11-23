package com.hienthai.fastowin.protocol

import kotlinx.serialization.Serializable

/** Clients below this version never receive chat, mute or new moderation target messages. */
const val CLAN_CHAT_PROTOCOL_VERSION = 46
const val CLAN_CHAT_MAX_LENGTH = 300
const val CLAN_CHAT_MAX_LINE_BREAKS = 5
const val CLAN_CHAT_PAGE_SIZE = 50
const val CLAN_CHAT_RETENTION_MILLIS = 30L * 24 * 60 * 60_000
const val CLAN_CHAT_MAX_STORED_MESSAGES = 500
const val CLAN_CHAT_PUSH_INTERVAL_MILLIS = 30L * 60_000
const val CLAN_MAX_CO_LEADERS = 3
const val CLAN_LOGO_DAILY_CHANGE_LIMIT = 5
const val CLAN_LOGO_MAX_EDGE = 256

@Serializable enum class ClanChatMessageKind { USER, SYSTEM }

@Serializable
enum class ClanChatSystemEvent {
    MEMBER_JOINED,
    MEMBER_LEFT,
    MEMBER_KICKED,
    LOGO_CHANGED,
    LEVEL_UP,
    CO_LEADER_APPOINTED,
    CO_LEADER_DISMISSED
}

/** Allowed mute lengths. Zero lifts an active mute. */
val CLAN_CHAT_MUTE_MINUTES: Set<Int> = setOf(0, 60, 24 * 60)

/**
 * One chat line. System lines carry an event plus arguments; the client localizes them, so a
 * stored line reads correctly in every language. Player arguments keep a `playerId` reference.
 */
@Serializable
data class ClanChatMessageSnapshot(
    val id: String,
    val clanId: String,
    val kind: ClanChatMessageKind,
    val createdAtEpochMillis: Long,
    val senderId: String? = null,
    val senderName: String = "",
    val text: String = "",
    val systemEvent: ClanChatSystemEvent? = null,
    val systemArgs: Map<String, String> = emptyMap()
)

/** Leader and co-leaders moderate chat and manage the logo. */
fun ClanRole.canModerateClan(): Boolean = this == ClanRole.LEADER || this == ClanRole.CO_LEADER

/** The server keeps text as typed apart from control characters, blank edges and extra line breaks. */
fun normalizeClanChatText(raw: String): String {
    var breaks = 0
    val cleaned = buildString {
        raw.replace("\r\n", "\n").forEach { char ->
            when {
                char == '\n' -> if (breaks < CLAN_CHAT_MAX_LINE_BREAKS) { breaks++; append(char) } else append(' ')
                char == '\t' -> append(' ')
                char.isISOControl() -> Unit
                else -> append(char)
            }
        }
    }
    return cleaned.trim()
}

fun clanLogoImagePath(clanId: String, revision: String): String = "/api/clan-logo/$clanId?v=$revision"
