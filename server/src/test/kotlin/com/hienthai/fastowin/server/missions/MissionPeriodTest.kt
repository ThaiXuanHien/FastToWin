package com.hienthai.fastowin.server.missions

import com.hienthai.fastowin.protocol.*
import java.time.Instant
import java.time.LocalDate
import kotlin.test.*

class MissionPeriodTest {
    @Test fun `Bangkok midnight and Monday select independent day and week periods`() {
        assertEquals(LocalDate.of(2026,10,25),missionDate(Instant.parse("2026-10-25T16:59:59.999Z").toEpochMilli()))
        val monday=missionDate(Instant.parse("2026-10-25T17:00:00Z").toEpochMilli())
        assertEquals(LocalDate.of(2026,10,26),monday)
        assertEquals(LocalDate.of(2026,10,19),periodStart(MissionCadence.WEEKLY,LocalDate.of(2026,10,25)))
        assertEquals(LocalDate.of(2026,10,26),periodStart(MissionCadence.WEEKLY,monday))
        assertEquals(LocalDate.of(2026,11,2),nextPeriod(MissionCadence.WEEKLY,monday))
    }
    @Test fun `short pools return available missions without crashing or duplicates`() {
        assertEquals(listOf("easy","normal"),selectMissions(MissionCadence.DAILY,LocalDate.of(2026,10,24),listOf("easy","normal")) { if(it=="easy") MissionDifficulty.EASY else MissionDifficulty.NORMAL })
        assertEquals(emptyList(),selectMissions(MissionCadence.WEEKLY,LocalDate.of(2026,10,24),emptyList<String>()) { MissionDifficulty.HARD })
    }
    @Test fun `accuracy uses configured threshold and large selection counts without overflow`() {
        val config=MissionConfig("ACCURACY",MissionScope.PERSONAL,MissionAction.ACCURACY,MissionCadence.DAILY,MissionDifficulty.HARD,1,MissionReward(),accuracyPercent=93)
        assertEquals(0,config.increment(MissionProgressEvent("low","player",0,played=true,correct=92,wrong=8)))
        assertEquals(1,config.increment(MissionProgressEvent("large","player",0,played=true,correct=2_000_000_000,wrong=1)))
    }
}
