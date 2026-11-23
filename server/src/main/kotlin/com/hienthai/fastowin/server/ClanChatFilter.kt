package com.hienthai.fastowin.server

/**
 * Masks banned whole words with asterisks. Vietnamese words are matched with their tone marks so
 * ordinary words that only differ by accent ("các", "ngủ") are never masked; unaccented entries are
 * slang spellings that have no innocent meaning (accent-free forms of ordinary words such as
 * "buoi" or "deo" are deliberately absent).
 */
object ClanChatFilter {
    private val bannedWords: Set<String> = ClanChatFilter::class.java
        .getResourceAsStream("/moderation/clan-chat-banned-words.txt")
        ?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.map { java.text.Normalizer.normalize(it.trim(), java.text.Normalizer.Form.NFC).lowercase() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        }.orEmpty()

    private val singleWords = bannedWords.filterNot { ' ' in it }.toSet()
    private val phrases = bannedWords.filter { ' ' in it }.map { it.split(' ') }

    fun mask(text: String): String {
        val tokens = TOKEN.findAll(text).toList()
        if (tokens.isEmpty()) return text
        val masked = BooleanArray(tokens.size)
        val words = tokens.map { java.text.Normalizer.normalize(it.value, java.text.Normalizer.Form.NFC).lowercase() }
        words.forEachIndexed { index, word -> if (word in singleWords) masked[index] = true }
        phrases.forEach { phrase ->
            for (start in 0..words.size - phrase.size) {
                if (phrase.indices.all { words[start + it] == phrase[it] }) phrase.indices.forEach { masked[start + it] = true }
            }
        }
        if (masked.none { it }) return text
        val result = StringBuilder(text)
        tokens.forEachIndexed { index, token ->
            if (masked[index]) for (position in token.range) result.setCharAt(position, '*')
        }
        return result.toString()
    }

    private val TOKEN = Regex("[\\p{L}\\p{M}\\p{N}]+")
}

/** Chat lines and their moderation updates only reach clients that can decode them. */
internal fun com.hienthai.fastowin.protocol.ServerMessage.requiresClanChatClient(): Boolean = when (this) {
    is com.hienthai.fastowin.protocol.ServerMessage.ClanChatHistory,
    is com.hienthai.fastowin.protocol.ServerMessage.ClanChatMessageReceived,
    is com.hienthai.fastowin.protocol.ServerMessage.ClanChatMessageRemoved,
    is com.hienthai.fastowin.protocol.ServerMessage.ClanChatMuteChanged -> true
    is com.hienthai.fastowin.protocol.ServerMessage.ContentModerationChanged -> !readableBy(0)
    else -> false
}

internal fun com.hienthai.fastowin.protocol.ServerMessage.ContentModerationChanged.readableBy(protocolVersion: Int): Boolean =
    protocolVersion >= com.hienthai.fastowin.protocol.CLAN_CHAT_PROTOCOL_VERSION ||
        (targetType != com.hienthai.fastowin.protocol.UgcTargetType.CLAN_LOGO &&
            targetType != com.hienthai.fastowin.protocol.UgcTargetType.CLAN_MESSAGE)
