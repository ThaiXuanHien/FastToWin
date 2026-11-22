package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.TournamentScheduleSnapshot
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.time.TestTimeSource

class TournamentScheduleTest {
    private val schedule = TournamentScheduleSnapshot(1_000_000, "UTC", 100_000, 100_000)

    @Test fun `registration and check in use exact half open deadlines`() {
        assertEquals(TournamentScheduleStage.REGISTRATION, schedule.stageAt(99_999))
        assertEquals(TournamentScheduleStage.CHECK_IN, schedule.stageAt(100_000))
        assertEquals(TournamentScheduleStage.CHECK_IN, schedule.stageAt(999_999))
        assertEquals(TournamentScheduleStage.STARTING, schedule.stageAt(1_000_000))
        assertEquals(TournamentScheduleStage.STARTING, schedule.stageAt(1_000_001))
    }

    @Test fun `server sample advances by monotonic elapsed time`() {
        val elapsed = TestTimeSource()
        val clock = ServerClock(100_000, elapsed.markNow())
        assertEquals(100_000, clock.now())
        assertEquals(TournamentScheduleStage.CHECK_IN, schedule.stageAt(clock.now()))
        elapsed += 1_001.milliseconds
        assertEquals(101_001, clock.now())
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

    @Test fun `summary uses each deadlines offset across a fall back`() {
        val zone = "America/New_York"
        val checkIn = Instant.parse("2026-11-01T05:45:00Z").toEpochMilliseconds()
        val start = Instant.parse("2026-11-01T06:00:00Z").toEpochMilliseconds()
        assertEquals("2026-11-01 01:45 (UTC-04:00)", tournamentLocalDateTime(checkIn, zone))
        assertEquals("2026-11-01 01:00 (UTC-05:00)", tournamentLocalDateTime(start, zone))
    }
}
