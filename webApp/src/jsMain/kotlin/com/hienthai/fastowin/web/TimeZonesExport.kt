@file:OptIn(kotlin.js.ExperimentalJsExport::class)

package com.hienthai.fastowin.web

import kotlin.js.JsExport

// Retain the module initializer in the JavaScript fallback executable too.
@JsExport
val jsJodaTimeZones = JsJodaTimeZoneModule
