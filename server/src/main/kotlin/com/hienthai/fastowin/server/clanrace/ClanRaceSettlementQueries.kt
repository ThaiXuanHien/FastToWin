package com.hienthai.fastowin.server.clanrace

import com.hienthai.fastowin.protocol.*
import kotlinx.serialization.json.Json
import java.sql.Date
import java.time.LocalDate

internal fun JdbcClanRaceStore.settlementResult(week:LocalDate):ClanRaceSettlementResult? = rows(
    "SELECT payload FROM clan_race_settlements WHERE week_start=?",listOf(Date.valueOf(week))
) { Json.decodeFromString<ClanRaceSettlementResult>(it.getString(1)) }.singleOrNull()

internal fun JdbcClanRaceStore.settlementStatus(week:LocalDate):ClanRaceSettlementStatus? {
    val work=rows("SELECT state,attempts,last_error_code FROM clan_race_settlement_work WHERE week_start=?",listOf(Date.valueOf(week))) {
        ClanRaceSettlementStatus(ClanRaceSettlementState.valueOf(it.getString(1)),it.getInt(2),it.getString(3))
    }.singleOrNull()?:return null
    val delivery=rows("""SELECT
        count(*) FILTER(WHERE o.state='PENDING') AS pending,
        count(*) FILTER(WHERE o.state='DELIVERED') AS delivered,
        count(*) FILTER(WHERE o.state='ACCOUNT_UNAVAILABLE') AS unavailable,
        count(*) FILTER(WHERE o.state='PENDING' AND o.last_error_code='DELIVERY_FAILED') AS failed,
        count(*) FILTER(WHERE o.state='PENDING' AND o.last_error_code='REGISTRATION_REQUIRED') AS waiting_registration,
        count(*) FILTER(WHERE r.claimed_at IS NOT NULL) AS claimed
        FROM clan_race_entitlements e JOIN clan_race_outbox o ON o.entitlement_id=e.id
        LEFT JOIN admin_gift_recipients r ON r.campaign_id=e.id AND r.user_id=e.user_id WHERE e.week_start=?""",listOf(Date.valueOf(week))) {
        ClanRaceDeliverySummary(it.getInt("pending"),it.getInt("delivered"),it.getInt("unavailable"),it.getInt("failed"),it.getInt("claimed"),it.getInt("waiting_registration"))
    }.single()
    return work.copy(result=settlementResult(week),delivery=delivery)
}
