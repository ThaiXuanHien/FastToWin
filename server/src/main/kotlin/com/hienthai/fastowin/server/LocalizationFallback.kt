package com.hienthai.fastowin.server

/**
 * Marks Vietnamese wire/data copy retained for clients that predate structured
 * localization keys. New clients render the accompanying stable key instead.
 */
internal fun legacyFallback(message: String): String = message
