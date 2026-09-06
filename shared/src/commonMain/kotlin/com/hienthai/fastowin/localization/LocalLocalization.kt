package com.hienthai.fastowin.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

// Screens rendered in isolation keep the legacy language; the app root always resolves preferences.
val LocalLocalization = staticCompositionLocalOf { LocalizationService(AppLanguage.VIETNAMESE) }

@Composable
fun localized(key: TextKey, vararg arguments: Pair<String, Any?>): String =
    LocalLocalization.current.text(key, arguments.toMap())

@Composable
fun localized(text: LocalizedText): String =
    LocalLocalization.current.text(text.key, text.arguments)

@Composable
fun localizedQuantity(key: QuantityKey, count: Int, vararg arguments: Pair<String, Any?>): String =
    LocalLocalization.current.quantity(key, count, arguments.toMap())

@Composable
fun ProvideLocalization(language: AppLanguage, content: @Composable () -> Unit) {
    val service = remember(language) { LocalizationService(language) }
    CompositionLocalProvider(LocalLocalization provides service, content = content)
}
