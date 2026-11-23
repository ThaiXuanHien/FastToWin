package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.CLAN_CHAT_MAX_STORED_MESSAGES
import com.hienthai.fastowin.protocol.ClanChatMessageKind
import com.hienthai.fastowin.protocol.ClanChatMessageSnapshot
import com.hienthai.fastowin.protocol.ServerMessage

/** A line typed here that the server has not confirmed yet. */
data class PendingClanChatMessage(
    val requestId: String,
    val text: String,
    val createdAtEpochMillis: Long,
    val failure: String? = null
)

/**
 * Chat for the player's own clan. [isVisible] is true while the chat tab is on screen: lines that
 * arrive then count as read, otherwise they raise [unreadCount].
 */
data class ClanChatState(
    val clanId: String? = null,
    val messages: List<ClanChatMessageSnapshot> = emptyList(),
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val unreadCount: Int = 0,
    val mutedUntilEpochMillis: Long? = null,
    val mutedMembers: Map<String, Long> = emptyMap(),
    val pending: List<PendingClanChatMessage> = emptyList(),
    val isVisible: Boolean = false
) {
    val newestMessageId: String? get() = messages.lastOrNull()?.id

    fun isMuted(nowMillis: Long): Boolean = (mutedUntilEpochMillis ?: 0) > nowMillis

    /** Starts loading [clanId]; switching clans drops the previous clan's lines. */
    fun loading(clanId: String): ClanChatState =
        if (this.clanId == clanId) copy(isLoading = messages.isEmpty())
        else ClanChatState(clanId = clanId, isLoading = true, isVisible = isVisible)

    fun withHistory(history: ServerMessage.ClanChatHistory): ClanChatState {
        if (history.clanId != clanId) return this
        return if (history.isOlderPage) {
            val known = messages.mapTo(mutableSetOf()) { it.id }
            copy(
                messages = (history.messages.filterNot { it.id in known } + messages).takeLast(CLAN_CHAT_MAX_STORED_MESSAGES),
                hasMore = history.hasMore,
                isLoadingOlder = false
            )
        } else {
            // A fresh first page replaces the list but keeps lines that arrived while it loaded.
            val page = history.messages.mapTo(mutableSetOf()) { it.id }
            val newer = messages.filter { it.id !in page && it.createdAtEpochMillis >= (history.messages.lastOrNull()?.createdAtEpochMillis ?: 0) }
            copy(
                messages = history.messages + newer,
                hasMore = history.hasMore,
                isLoading = false,
                isLoadingOlder = false,
                unreadCount = if (isVisible) 0 else history.unreadCount,
                mutedUntilEpochMillis = history.mutedUntilEpochMillis,
                mutedMembers = history.mutedMembers
            )
        }
    }

    fun withMessage(message: ClanChatMessageSnapshot, requestId: String?, currentUserId: String?): ClanChatState {
        if (message.clanId != clanId) return this
        val remaining = if (requestId == null) pending else pending.filterNot { it.requestId == requestId }
        if (messages.any { it.id == message.id }) return copy(pending = remaining)
        val countsAsUnread = !isVisible && message.kind == ClanChatMessageKind.USER && message.senderId != currentUserId
        return copy(
            messages = (messages + message).sortedWith(compareBy({ it.createdAtEpochMillis }, { it.id }))
                .takeLast(CLAN_CHAT_MAX_STORED_MESSAGES),
            pending = remaining,
            unreadCount = if (countsAsUnread) (unreadCount + 1).coerceAtMost(99) else unreadCount
        )
    }

    fun withoutMessage(clanId: String, messageId: String): ClanChatState =
        if (clanId != this.clanId) this else copy(messages = messages.filterNot { it.id == messageId })

    fun withMute(change: ServerMessage.ClanChatMuteChanged, currentUserId: String?): ClanChatState {
        if (change.clanId != clanId) return this
        val until = change.mutedUntilEpochMillis
        return copy(
            mutedUntilEpochMillis = if (change.memberId == currentUserId) until else mutedUntilEpochMillis,
            mutedMembers = if (until == null) mutedMembers - change.memberId else mutedMembers + (change.memberId to until)
        )
    }

    fun sending(requestId: String, text: String, nowMillis: Long): ClanChatState =
        copy(pending = pending + PendingClanChatMessage(requestId, text, nowMillis))

    fun failed(requestId: String, failure: String): ClanChatState =
        copy(pending = pending.map { if (it.requestId == requestId) it.copy(failure = failure) else it })

    fun discard(requestId: String): ClanChatState = copy(pending = pending.filterNot { it.requestId == requestId })

    fun hasPending(requestId: String?): Boolean = requestId != null && pending.any { it.requestId == requestId }

    fun shown(visible: Boolean): ClanChatState = copy(isVisible = visible, unreadCount = if (visible) 0 else unreadCount)
}
