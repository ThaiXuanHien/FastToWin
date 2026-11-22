package com.hienthai.fastowin.platform

import kotlin.time.Clock

actual fun epochMillis(): Long = Clock.System.now().toEpochMilliseconds()
