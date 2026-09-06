package com.hienthai.fastowin.localization

/** Stable, renderable text reference. Arguments may contain values, never secrets. */
data class LocalizedText(
    val key: TextKey,
    val arguments: Map<String, Any?> = emptyMap()
)
