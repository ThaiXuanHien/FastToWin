@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.hienthai.fastowin.platform

actual fun epochMillis(): Long = browserEpochMillis().toLong()

private fun browserEpochMillis(): Double = js("Date.now()")
