package com.hienthai.fastowin.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.time

@OptIn(ExperimentalForeignApi::class)
actual fun epochMillis(): Long = time(null) * 1_000L
