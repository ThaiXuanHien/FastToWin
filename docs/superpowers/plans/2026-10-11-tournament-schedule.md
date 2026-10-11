# Tournament Schedule Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement task-by-task. Steps use checkbox syntax.

**Goal:** Implement approved #13 scheduling, check-in, durable automatic start/cancellation and usable shared UI.

**Architecture:** Add optional schedule fields to existing tournament snapshots and a check-in command. Reuse persistenceMutex and atomic tournament progression/refund repository operations, keeping manual tournaments compatible. Shared controller/UI consumes the same schedule across Android/iOS/Web.

**Tech Stack:** Kotlin Multiplatform, kotlinx.serialization/datetime, Ktor, PostgreSQL, Compose Material3, Playwright.

**Spec:** `docs/tournament-schedule.md` (authority: GitHub #13 and user approval).

## Global Constraints

- Preserve `.idea/appInsightsSettings.xml` and unrelated backlog-readiness changes. No production writes or secret output.
- Work on `codex/backlog-readiness`; do not commit other tasks' files. Controller performs final commits after review.
- Optional schedule, protocol 44/minimum 38; old manual tournaments still work. Add kotlinx-datetime 0.8.0 to shared commonMain for IANA timezone and DST handling; no server dependency additions.
- Check-in 15 minutes before, registration closes then. Existing 4/8/16 formats require full eligible roster or cancellation with all recorded fees refunded once.
- Publish scheduled mutations only after durable acknowledgement. Maintenance wins. Actual iOS/device/provider acceptance remains external.
- Read UI skill `.agents/skills/ui-ux-pro-max/SKILL.md` before UI changes. Existing dark design system and 48dp touch targets.
- Issue30 verification exposed separate Windows WebKit snapshot and Compose Web semantics-owner bugs. Pin Web-only Compose1.13.0-alpha02/Skiko0.153.0 and exact test-only Playwright1.65.0-alpha-2026-10-11; Android/iOS keep current dependencies. These prerelease choices require full Web/JS regression and review before integration. Restore background Back accessibility and inspect actual screenshot pixels; never waive Home/browser errors to pass.

## Task 1: Protocol and durable scheduled tournament lifecycle

**Files:** Modify `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GameProtocol.kt`, localization `TextKey.kt` and catalogs; `server/src/main/kotlin/com/hienthai/fastowin/server/GameEngine.kt`, `TournamentRepository.kt`, `Application.kt`; add focused server/protocol tests and extend existing PostgreSQL tournament tests. Document actual behaviour in `docs/tournament-schedule.md` only if a necessary implementation decision must be recorded.

**Interfaces:** Produces schedule request/snapshot, optional schedule fields, `isCheckedIn`, `serverTimeEpochMillis`, CheckInTournament exactly as spec. Produces `GameEngine.advanceScheduledTournaments(): List<Delivery>` for Application's supervised one-second loop. Localization keys for schedule UI/errors can be added in a dedicated catalog with Vietnamese/English and explicit existing language fallback; preserve catalog completeness tests. UI task may extend these catalogs after this task finishes.

- [x] Write focused failures in `ScheduledTournamentTest.kt`, following existing `GameEngineTest` fixture clock/accounts, covering boundary registration/check-in, full roster auto start exactly once, insufficient cancellation full recorded refunds including a leaver, repeat tick, host cancel scheduled refund and manual compatibility. Add PostgreSQL transaction-failure and restore tests with UUID schemas; no skipped DB tests.

```kotlin
// Illustrative public API assertions; use real existing fixture helpers.
clockMillis = startsAt - 15 * 60_000
engine.handle(playerId, ClientMessage.CheckInTournament(id))
clockMillis = startsAt
val deliveries = engine.advanceScheduledTournaments()
assertEquals(TournamentPhase.RUNNING, deliveries.filterIsInstanceTournamentUpdate().single().phase)
assertTrue(engine.advanceScheduledTournaments().isEmpty())
```

- [x] Run the new failing test first: `./gradlew :server:test --tests '*ScheduledTournamentTest*' :protocol:jvmTest`, with local TEST_DATABASE settings for DB tests. Record failure evidence.
- [x] Add additive protocol fields and protocol 44 capability gating. Validate future range and ZoneId server-side. Preserve snapshot restoration/check-in fields and derive close/open from start rather than accepting inconsistent client deadlines.

```kotlin
val opensAt = request.startsAtEpochMillis - 15 * 60_000L
require(now <= opensAt && request.startsAtEpochMillis <= now + 365L * 24 * 60 * 60_000)
java.time.ZoneId.of(request.timeZoneId)
```

- [x] Implement check-in candidate snapshot under persistence serialization, save then publish; enforce deadlines again at admission and invitation acceptance. A scheduled start is staged off live state, creates rooms through existing safe progression patterns, atomically commits snapshot/rooms then publishes. Failures retain retryable lobby and never expose created rooms. Validate start against existing availability/maintenance checks. Exclude absentees before evaluating selected format; cancellation records original paid membership for history/refunds.
- [x] Extend cancellation repository with a reason-neutral method or wrapper and a **common refund idempotency source** shared with maintenance, retaining existing tests/API compatibility. Both cancellation reasons must never refund the same entry twice. Use PostgreSQL transaction, tournament row locking, actual payment amounts, and existing uncertain-commit safety. Scheduled host cancel uses the same path. Do not alter voluntary manual tournament refund semantics.
- [x] Add Application one-second scheduler with per-tick error logging and retry, so one storage failure does not kill future scheduling. On restore due schedules are processed through the same path; do not run destructive catch-up while restoration incomplete.
- [x] Verify focused tests, full `:server:test :protocol:jvmTest`, and existing maintenance/admission/progression tests with PostgreSQL enabled. Record counts/commands in `.tmp/tournament-task1-report.md`; no commit until parent review.

## Task 2: Shared countdown, schedule creation and check-in UI

**Files:** `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameController.kt`, `GameState.kt`, `ui/screens/TournamentScreen.kt`, `FastToWinApp.kt`; shared focused tests, protocol localization catalogs/TextKey if UI copy expands; screenshot fixture and e2e tournament test.

**Interfaces:** Consumes Task 1 exact schedule protocol. Add optional schedule to existing controller `createTournament` parameters and screen callback at the end; default null avoids disrupting screenshot/test call sites. Add `checkInTournament()`, wiring CheckInTournament active id. Consume snapshot serverTimeEpochMillis to calibrate countdown (prefer shared current clock abstraction); derive countdown status from immutable deadline fields.

- [ ] Read UI skill and existing design-system MASTER; add failing controller tests confirming schedule serialized, check-in targets active tournament and offline/busy states behave consistently. Add pure status/deadline formatting tests around open/start instants and clock skew.
- [ ] Add schedule toggle inside existing create modal, local date/time input `YYYY-MM-DD HH:mm`, IANA timezone default `TimeZone.currentSystemDefault().id`, parse with existing kotlinx.datetime. Reject malformed/nonexistent local time and show useful inline copy. Pre-submit summary shows registration close/check-in open/start, full roster/refund rule. Avoid exposing implementation/server internals to players.

```kotlin
val local = LocalDateTime.parse(input.trim().replace(' ', 'T'))
val zone = TimeZone.of(timeZoneText.trim())
val instant = local.toInstant(zone)
check(instant.toLocalDateTime(zone) == local) // reject gap normalization
val request = TournamentScheduleRequest(instant.toEpochMilliseconds(), zone.id)
```

- [ ] Add compact schedule panel/countdown to active lobby and public listing, show registration closed and checked-in counts; expose check-in button only during window with connected/maintenance rules. Scheduled host sees automatic-start copy instead of manual Start. Disable late join/leave actions and display refund rule. Preserve gold/XP icons and existing dark Material/modals.
- [ ] Localize all added labels/errors; content descriptions for icons, large-font narrow-screen scroll and 48dp controls. Update shared fixtures without breaking old callback defaults.
- [ ] Run shared/Android compilation and relevant tests; add deterministic scheduled lobby screenshot phone/tablet and validate changed baselines only. Build Web and execute real schedule creation/check-in UI E2E locally (use bounded test clock only if fixture infrastructure supports it; don't add production debug clock). View screenshots on narrow phone and desktop and record evidence `.tmp/tournament-task2-report.md`.
- [ ] Update `docs/tournament-schedule.md` with verified behavior/test evidence. Parent reviews full diff, commits, pushes and updates GitHub #13 with exact completed versus external device checks.
