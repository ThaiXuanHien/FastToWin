package com.hienthai.fastowin.server

import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.server.clanrace.raceWeekMillis
import com.hienthai.fastowin.server.clanrace.raceWeekStart
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.delay

/** Runs several reminder services in one scheduler tick and adds up what they delivered. */
class CompositePushReminderService(private val services: List<PushReminderService>) : PushReminderService {
    override suspend fun sendDueReminders(): Int = services.sumOf { service ->
        runCatching { service.sendDueReminders() }
            .onFailure { System.err.println("Push reminder run failed: ${it.message}") }
            .getOrDefault(0)
    }
}

/**
 * Time-based pushes beyond the daily check-in reminder: a streak about to break, tournament
 * check-in, new mailbox gifts, the season ending and the clan race ending. Every target is keyed
 * so the 15-minute scheduler never sends the same reminder twice.
 */
class ScheduledPushReminderService(
    private val playerProfileRepository: PlayerProfileRepository,
    private val pushNotificationService: PushNotificationService,
    private val zoneId: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh"),
    private val streakReminderHour: Int = 21,
    private val clock: Clock = Clock.systemUTC()
) : PushReminderService {

    override suspend fun sendDueReminders(): Int {
        val nowMillis = clock.millis()
        val localNow = clock.instant().atZone(zoneId)
        var delivered = 0
        dueKinds(nowMillis, localNow.hour).forEach { kind ->
            val localDate = if (kind == ScheduledPushKind.CLAN_RACE_ENDING) {
                raceWeekStart(nowMillis).toString()
            } else {
                localNow.toLocalDate().toString()
            }
            delivered += sendKind(kind, nowMillis, localDate)
        }
        return delivered
    }

    internal fun dueKinds(nowMillis: Long, localHour: Int): List<ScheduledPushKind> = buildList {
        if (localHour >= streakReminderHour) add(ScheduledPushKind.STREAK_AT_RISK)
        add(ScheduledPushKind.TOURNAMENT_CHECK_IN)
        add(ScheduledPushKind.GIFT_MAILBOX)
        add(ScheduledPushKind.SEASON_ENDING)
        val raceEnd = raceWeekMillis(raceWeekStart(nowMillis).plusWeeks(1))
        if (nowMillis >= raceEnd - CLAN_RACE_ENDING_WINDOW_MILLIS && nowMillis < raceEnd) {
            add(ScheduledPushKind.CLAN_RACE_ENDING)
        }
    }

    private suspend fun sendKind(kind: ScheduledPushKind, nowMillis: Long, localDate: String): Int {
        var delivered = 0
        repeat(MAX_PAGES_PER_KIND) {
            val page = playerProfileRepository.loadScheduledPushTargets(kind, nowMillis, localDate, MAX_PUSH_BATCH)
            if (page.isEmpty()) return delivered
            var progressed = 0
            page.chunked(MAX_PUSH_BATCH).forEach { batch ->
                val statuses = pushNotificationService.sendNotifications(batch.map { scheduled ->
                    PushRequest(scheduled.target.fcmToken, scheduled.target.language, scheduledPushContent(kind, scheduled.arguments), scheduledPushDestination(kind))
                })
                batch.zip(statuses).forEach { (scheduled, status) ->
                    when (status) {
                        PushDeliveryStatus.SENT -> if (
                            playerProfileRepository.markPushReminderDelivered(scheduled.target.playerId, scheduled.reminderKey)
                        ) {
                            delivered++
                            progressed++
                        }
                        PushDeliveryStatus.INVALID_TOKEN -> if (
                            playerProfileRepository.clearFcmToken(scheduled.target.playerId, scheduled.target.fcmToken)
                        ) progressed++
                        PushDeliveryStatus.FAILED -> Unit
                    }
                }
            }
            // A page where nothing changed (only failing sends) would come back unchanged: stop.
            if (page.size < MAX_PUSH_BATCH || progressed == 0) return delivered
            delay(PAGE_PAUSE_MILLIS)
        }
        return delivered
    }

    private companion object {
        const val MAX_PAGES_PER_KIND = 20
        const val PAGE_PAUSE_MILLIS = 200L
        const val CLAN_RACE_ENDING_WINDOW_MILLIS = 2L * 60 * 60 * 1000
    }
}

internal fun scheduledPushContent(kind: ScheduledPushKind, arguments: Map<String, String>): LocalizedPushContent = when (kind) {
    ScheduledPushKind.STREAK_AT_RISK -> LocalizedPushContent(TextKey.PushStreakAtRiskTitle, TextKey.PushStreakAtRiskMessage, bodyArguments = arguments)
    ScheduledPushKind.TOURNAMENT_CHECK_IN -> LocalizedPushContent(TextKey.PushTournamentCheckInTitle, TextKey.PushTournamentCheckInMessage)
    ScheduledPushKind.GIFT_MAILBOX -> LocalizedPushContent(TextKey.PushGiftMailboxTitle, TextKey.PushGiftMailboxMessage)
    ScheduledPushKind.SEASON_ENDING -> LocalizedPushContent(TextKey.PushSeasonEndingTitle, TextKey.PushSeasonEndingMessage, bodyArguments = arguments)
    ScheduledPushKind.CLAN_RACE_ENDING -> LocalizedPushContent(TextKey.PushClanRaceEndingTitle, TextKey.PushClanRaceEndingMessage)
}

internal fun scheduledPushDestination(kind: ScheduledPushKind): String = when (kind) {
    ScheduledPushKind.STREAK_AT_RISK -> "/account/check-in"
    ScheduledPushKind.TOURNAMENT_CHECK_IN -> "/tournament"
    ScheduledPushKind.GIFT_MAILBOX -> "/gifts"
    ScheduledPushKind.SEASON_ENDING -> "/leaderboard"
    ScheduledPushKind.CLAN_RACE_ENDING -> "/clan/race"
}
