package com.hienthai.fastowin.localization

import com.hienthai.fastowin.protocol.AccountActionResponse
import com.hienthai.fastowin.protocol.ServerMessage

class LocalizedMessageMapper(
    private var localization: LocalizationService
) {
    val language: AppLanguage get() = localization.language

    fun updateLanguage(language: AppLanguage) {
        if (localization.language != language) localization = LocalizationService(language)
    }

    fun text(key: TextKey, arguments: Map<String, Any?> = emptyMap()): String =
        localization.text(key, arguments)

    fun message(message: ServerMessage.Error): String = resolve(
        keyName = message.messageKey ?: protocolTextKeyForCode(message.code)?.name,
        arguments = message.messageArgs,
        fallback = message.message
    )

    fun message(message: ServerMessage.SocialNotice): String =
        resolve(message.messageKey ?: message.code?.let(::protocolTextKeyForCode)?.name, message.messageArgs, message.message)

    fun message(message: ServerMessage.TournamentNotice): String =
        resolve(message.messageKey ?: message.code?.let(::protocolTextKeyForCode)?.name, message.messageArgs, message.message)

    fun message(message: ServerMessage.ClanActionResult): String =
        resolve(message.messageKey ?: protocolTextKeyForCode(message.action)?.name, message.messageArgs, message.message)

    fun message(message: ServerMessage.RoomClosed): String =
        resolve(message.messageKey ?: message.code?.let(::protocolTextKeyForCode)?.name, message.messageArgs, message.reason)

    fun message(message: ServerMessage.StorePurchaseResult): String =
        resolve(message.messageKey, message.messageArgs, message.message)

    fun message(message: AccountActionResponse): String =
        resolve(message.messageKey, message.messageArgs, message.message)

    fun message(
        code: String?,
        keyName: String?,
        arguments: Map<String, String>,
        fallback: String
    ): String = resolve(keyName ?: code?.let(::protocolTextKeyForCode)?.name, arguments, fallback)

    private fun resolve(keyName: String?, arguments: Map<String, String>, fallback: String): String {
        val safeFallback = fallback.takeIf(String::isNotBlank)
            ?: localization.text(TextKey.UnknownError)
        val key = keyName?.let { candidate -> TextKey.entries.firstOrNull { it.name == candidate } }
            ?: return safeFallback
        return runCatching { localization.text(key, arguments) }.getOrElse { safeFallback }
    }
}
