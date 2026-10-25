package com.hienthai.fastowin.server.clanrace

import java.math.BigDecimal
import java.math.BigInteger
import java.time.*
import java.time.temporal.TemporalAdjusters

val RACE_UNITS_PER_POINT:BigInteger=BigInteger.valueOf(1_000_000)
private val maximumRaceUnits=BigInteger.TEN.pow(38)-BigInteger.ONE
val RACE_ZONE:ZoneId=ZoneId.of("Asia/Bangkok")
fun checkedRaceUnits(value:BigInteger):BigInteger {
    require(value.signum()>=0 && value<=maximumRaceUnits) { "Race score exceeds 38 decimal digits." };return value
}
fun parseRaceRateUnits(text:String):BigInteger {
    require(text.length<=39 && text.matches(Regex("[0-9]+(?:\\.[0-9]{1,6})?"))) { "Rate must be a positive decimal with at most six decimal places." }
    return checkedRaceUnits(BigDecimal(text).movePointRight(6).toBigIntegerExact()).also { require(it.signum()>0) }
}
fun raceDonationUnits(amount:Long,rateUnits:BigInteger):BigInteger {
    require(amount>=0 && rateUnits.signum()>0);return checkedRaceUnits(BigInteger.valueOf(amount)*rateUnits)
}
fun racePoints(value:BigInteger):String=BigDecimal(checkedRaceUnits(value),6).stripTrailingZeros().toPlainString()
fun raceWeekStart(nowMillis:Long):LocalDate=Instant.ofEpochMilli(nowMillis).atZone(RACE_ZONE).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
fun raceWeekMillis(start:LocalDate):Long=start.atStartOfDay(RACE_ZONE).toInstant().toEpochMilli()
