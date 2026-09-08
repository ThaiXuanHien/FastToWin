# Task 5 report — remove recorded match replay

## RED

Updated `ProfileSectionsUiTest` first to require stable scoreboard/metrics tags
and absence of recorded-event replay text. The prescribed instrumentation RED
command could not start on this environment: Gradle failed before test
execution with `java.io.IOException: Unable to establish loopback connection`
(after the installed Android JDK was made available).

## GREEN implementation

- Removed `replayIndex`, autoplay state/timer, event-by-event panel, and
  `MatchReplayControls` from `MatchDetailDialog`.
- Added `match_detail_scoreboard` and `match_detail_metrics` semantics tags.
- Removed replay-only localization keys and catalog values:
  `NoReplayData`, `ReplayTurn`, `CorrectSelection`, `WrongSelectionNeed`,
  `StopReplay`, and recorded-match `Replay`.
- Added `RecordedMatchReplayContractTest` to scan for removed UI symbols while
  asserting `MatchEventSnapshot`, detail events, `GetMatchDetail`,
  `MatchDetailData`, repository querying, and `match_events` migration remain.
- `ReplaySameBoard` in Practice and result/rematch flows were not changed.

## Verification

- PASS: `git diff --check`.
- PASS: static `rg` scan: no recorded replay UI symbols remain; approved
  `ReplaySameBoard` remains in Practice.
- BLOCKED before Gradle test/compile execution: `:server:test`, connected UI
  test, and the requested shared/app/web verification all hit the same Gradle
  loopback startup failure.

## Residual risk

Run the focused instrumentation and full Task 5 verification command on a
machine where Gradle can establish its loopback connection and Compose
instrumentation exposes a hierarchy.
