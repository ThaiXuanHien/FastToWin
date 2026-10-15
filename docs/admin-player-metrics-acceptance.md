# Admin player metrics — acceptance, 2026-10-15

Tracked in [#43](https://github.com/ThaiXuanHien/FastToWin/issues/43).
The owner supplied an updated admin interface in the working checkout. This
change keeps that interface and connects the player screen to system data.
`admin.css` and `admin-analytics.js` are byte-identical to the owner's starting
versions. The local `.idea/appInsightsSettings.xml` change is excluded.

## Metric definitions

| Visible metric | Source / meaning |
| --- | --- |
| Tổng người chơi | All `users JOIN profiles` rows, matching the directory population, including guests, blocked and deleted accounts. Independent of search and pagination. |
| Trận đấu hôm nay | Unique persisted `matches` with `ended_at` in `[Bangkok midnight, snapshotAt)`, across match modes. Participants do not multiply the match count. |
| Tài khoản đã đăng ký / Tài khoản khách | Registered/guest accounts in that same profile population. These are account types, not a historical signup cohort. |
| Kết nối game hôm nay | Distinct accounts observed by the existing `ACTIVE_PLAYERS` server source today. It does not mean currently online or currently in a match. |
| Kết nối game 7 ngày | Distinct accounts over the entire requested seven-day window, including the current partial day; never the sum of daily unique counts. |
| Đăng ký mới hôm nay | Existing `REGISTRATIONS` source for today, with its own coverage and freshness, independent of the activity source. |
| Điểm Elo trung bình | Average current Elo across the directory population; a missing stats row has the existing profile default of 1000. |
| Tỉ lệ thắng đã ghi nhận | Sum of persisted player wins / sum of player `total_matches`. Participant counters are distinct from the unique match count above. |
| Hồ sơ đã thi đấu | Profiles whose persisted `total_matches` is positive / all profiles. |
| Thành viên bang hội | Distinct current clan members in the profile population / all profiles. Clan count includes persisted empty clans. |
| Tỉ lệ tài khoản đăng ký | Registered accounts / all profiles. |
| Player level and results | Level derived from stored XP; lifetime matches/wins/losses/draws from `player_stats`, not the paginated recent-match history. |

The daily bar chart uses actual `ACTIVE_PLAYERS` series values, day boundaries
in `Asia/Bangkok` (UTC+7), and a scale calculated from those values. The text
table exposes exact values to keyboard and screen-reader users. Account totals
are decimal strings and chart calculations use `BigInt`, preserving values
above JavaScript's safe integer limit. The percentage comparison is displayed
only when both current and comparison coverage are complete.

No source currently supplies total playtime, VIP conversion, retention or
session duration on this screen. The previous sample figures are removed or
replaced by the supported metrics above. Missing data remains unknown (`—`),
distinct from an actual zero. Partial activity/registration sources are labeled
as observed counts; both sources show their own freshness. Connection history
has the existing 90-day retention limit. Aggregates and analytics use separate
read snapshots and show the relevant time, rather than claiming atomicity
across endpoints.

## API and access

`GET /admin/api/player-overview` uses the existing authenticated transaction,
Owner/Operator access, and `PLAYER_OVERVIEW` audit event. Support receives 403
and the frontend does not request aggregates for that role. PostgreSQL computes
the profile aggregates in one query, using decimal totals to avoid integer
overflow. The in-memory repository leaves unavailable counters null.

The frontend keeps existing session/request fences: a delayed response cannot
repopulate a logged-out or replaced screen. No database migration or provider
configuration is required.

`node scripts/start-admin-preview.mjs` is a mock preview bound to `127.0.0.1`
(default port 8080). Its title and console explicitly identify sample data;
arbitrary preview credentials are not production authentication. Unsupported
analytics return unavailable rather than sample history presented as current.

## Verification

- Full `:server:test`: **719 tests, 111 suites, zero failures/errors/skips**;
  includes 200 tests in 44 PostgreSQL suites against local PostgreSQL.
- New admin backend coverage includes real PostgreSQL boundaries, duplicate
  participants, deleted/blocked/guest populations, missing stats, XP-derived
  levels, large totals, authorization and audit behavior.
- Full admin Playwright suite: **265 passed, 2 skipped** (267 cases). The two
  optional live-backend cases require a separately configured HTTP backend;
  browser metrics tests use explicit controlled fixtures, not production data.
- Eight player-metric cases cover phone/desktop layout, real response values,
  search independence, distinct period totals, null/zero/large values, roles,
  profile lifetime results, late responses and independent source coverage.
- Behavioral failures were captured before the metric wiring and before the
  registration-coverage correction; the same assertions pass afterward.
- `:shared:checkLocalizedUiText` and its scanner fixtures pass.
- Screenshots reviewed at 360px and 1440px; no horizontal overflow. CSS and
  analytics-module hashes match the owner's starting files.
- Independent code review completed; its registration-coverage finding was
  fixed and re-reviewed with no remaining finding in that scope.

Local ignored evidence: `.artifacts/admin-player-metrics-20261015/` (baseline
copies/hashes, browser logs and screenshots) and
`.tmp/player-metrics-full-server-summary.json` / server log. These artifacts
are local evidence and are not committed. No production data was accessed or
deployment performed for this acceptance.
