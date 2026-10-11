@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.hienthai.fastowin.web

import kotlin.js.JsModule

// kotlinx-datetime needs this module loaded before resolving named IANA zones.
@JsModule("@js-joda/timezone")
external object JsJodaTimeZoneModule

private val jsJodaTz = JsJodaTimeZoneModule
