package com.hienthai.fastowin.server.ratelimit

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.turbo.TurboFilter
import ch.qos.logback.core.spi.FilterReply
import org.slf4j.LoggerFactory
import org.slf4j.Marker

/** Lettuce logs URIs and channel addresses independently, including from async IO threads. */
internal object RedisLogProtection {
    @Synchronized fun install() {
        val context = LoggerFactory.getILoggerFactory() as? LoggerContext
            ?: error("Redis rate limiting requires the configured Logback logger.")
        if (context.turboFilterList.any { it is LettuceLogFilter }) return
        context.addTurboFilter(LettuceLogFilter().apply { start() })
    }

    private class LettuceLogFilter : TurboFilter() {
        override fun decide(marker: Marker?, logger: Logger, level: Level, format: String?, params: Array<out Any>?, throwable: Throwable?): FilterReply =
            if (logger.name == "io.lettuce" || logger.name.startsWith("io.lettuce.")) FilterReply.DENY else FilterReply.NEUTRAL
    }
}
