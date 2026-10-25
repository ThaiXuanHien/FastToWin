package com.hienthai.fastowin.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class ClanRaceSerializationTest {
    @Test fun preciseScoresRemainStringsAndFractionalPointsStayPositive() {
        val value=ClanRaceStanding(1,"clan","Clan",null,2,"99999999999999999999999999999999999999","99999999999999999999999999999999.999999",1800000000000)
        val encoded=Json.encodeToString(value)
        assertTrue(encoded.contains("\"scoreUnits\":\"99999999999999999999999999999999999999\""))
        assertEquals(value,Json.decodeFromString<ClanRaceStanding>(encoded))
        val fraction=ClanRaceContribution("user",null,"tenure",true,true,"100","0","100","0.0001")
        assertEquals("0.0001",Json.decodeFromString<ClanRaceContribution>(Json.encodeToString(fraction)).points)
        assertTrue(Json.decodeFromString<ClanRaceContribution>(Json.encodeToString(fraction)).eligible)
    }
}
