package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.TournamentScheduleSnapshot
import kotlin.test.*

class TournamentScheduleTest {
    private val schedule = TournamentScheduleSnapshot(1_000_000, "UTC", 100_000, 100_000)

    @Test fun `registration and check in use exact half open deadlines`() {
        assertEquals(TournamentScheduleStage.REGISTRATION, schedule.stageAt(99_999))
        assertEquals(TournamentScheduleStage.CHECK_IN, schedule.stageAt(100_000))
        assertEquals(TournamentScheduleStage.CHECK_IN, schedule.stageAt(999_999))
        assertEquals(TournamentScheduleStage.STARTING, schedule.stageAt(1_000_000))
        assertEquals(TournamentScheduleStage.STARTING, schedule.stageAt(1_000_001))
    }

    @Test fun `server sample corrects a skewed local clock and advances by elapsed time`() {
        val clock = TournamentServerClock(100_000, 9_000_000)
        assertEquals(100_000, clock.now(9_000_000))
        assertEquals(101_001, clock.now(9_001_001))
        assertEquals(TournamentScheduleStage.CHECK_IN, schedule.stageAt(clock.now(9_000_000)))
    }

    @Test fun `countdown rounds up seconds and never goes negative`() {
        assertEquals("00:01", tournamentCountdown(1))
        assertEquals("01:01", tournamentCountdown(60_001))
        assertEquals("25:00:00", tournamentCountdown(90_000_000))
        assertEquals("00:00", tournamentCountdown(-1))
    }

    @Test fun `summary shows the scheduled time in the named zone`() {
        assertEquals("2026-10-15 12:00", tournamentLocalDateTime(1_792_040_400_000, "Asia/Bangkok"))
        assertEquals("2026-10-15 05:00", tournamentLocalDateTime(1_792_040_400_000, "UTC"))
    }
}
