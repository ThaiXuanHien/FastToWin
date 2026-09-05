package com.hienthai.fastowin.localization

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

actual fun platformLanguageTags(): List<String> = NSLocale.preferredLanguages.filterIsInstance<String>()

// The Compose provider changes text without replacing the native view controller.
actual fun applyPlatformLanguageTag(languageTag: String) = Unit
