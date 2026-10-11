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

    @Test fun `local schedule converts selected zone and rejects invalid or normalized gap times`() {
        assertEquals(1_792_040_400_000, parseTournamentSchedule("2026-10-15 12:00", "Asia/Bangkok").getOrThrow().startsAtEpochMillis)
        assertEquals("UTC", parseTournamentSchedule("2026-10-15 12:00", " UTC ").getOrThrow().timeZoneId)
        assertTrue(parseTournamentSchedule("2026-10-15", "UTC").isFailure)
        assertTrue(parseTournamentSchedule("2026-13-15 12:00", "UTC").isFailure)
        assertTrue(parseTournamentSchedule("2026-10-15 12:00", "Invalid/Zone").isFailure)
        assertTrue(parseTournamentSchedule("2026-03-08 02:30", "America/New_York").isFailure)
    }

    @Test fun `summary preserves named zone and the fifteen minute interval`() {
        val request = parseTournamentSchedule("2026-10-15 12:00", "Asia/Bangkok").getOrThrow()
        val snapshot = request.toScheduleSnapshot()
        assertEquals(request.startsAtEpochMillis - 900_000, snapshot.registrationClosesAtEpochMillis)
        assertEquals(snapshot.registrationClosesAtEpochMillis, snapshot.checkInOpensAtEpochMillis)
        assertEquals("2026-10-15 12:00", tournamentLocalDateTime(snapshot.startsAtEpochMillis, snapshot.timeZoneId))
    }
}
