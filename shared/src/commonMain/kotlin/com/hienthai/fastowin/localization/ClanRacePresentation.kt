package com.hienthai.fastowin.localization

/** Decimal placement only: preserve the server's millionth units without numeric conversion. */
fun raceUnitsText(units: String): String {
    val digits = units.padStart(7, '0')
    val fraction = digits.takeLast(6).trimEnd('0')
    return digits.dropLast(6) + if (fraction.isEmpty()) "" else ".$fraction"
}
