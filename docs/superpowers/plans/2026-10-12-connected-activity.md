# Connected account activity implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Collect90 days of authenticated game connection activity for honest analytics and incident compensation.
**Architecture:** Server-observed durable intervals and explicit healthy coverage windows; consumers share JDBC readers in their existing transactions. No external provider or client telemetry.
**Tech Stack:** Kotlin/JVM, Ktor, PostgreSQL/Flyway, existing anonymous analytics subjects, native admin JS/SVG, Playwright.
**Spec:** docs/superpowers/specs/2026-10-12-connected-activity-design.md

## Global Constraints

- Activity = accepted registered-account `/game` connection, including idle/lobby; no guests/failed auth/HTTP-only login/client times.
- Checkpoint every30 seconds; gaps exceeding60 seconds/failure/restart must never claim continuous coverage.
- Retention90 days, query cutoff immediate, cleanup at most hourly; no backfill, no invented zero or revenue.
- Subject keys reuse analytics privacy/erasure locks; no email/name/token/IP stored or logged.
- Checkpoint facts and coverage atomic. Compensation preview/publication use the same existing gift JDBC transaction; publication freezes recipients and retains claim-once/replay/current-role rules.
- Protocol44/min38 unchanged; existing payload readers/defaults stay compatible. One backend only; no Redis/provisioning/payment/provider activation or live campaigns.
- Admin white Material, existing design system,48px labelled controls/table chart alternative; mandatory UI UX Pro Max, phone/desktop screenshots.
- Keep the primary checkout's unrelated .idea/appInsightsSettings.xml unchanged; only stage explicit task files.

## Task 1: Durable connected-account source and runtime

**Files:** Create server/.../activity/ActivityModels.kt, ActivityRepository.kt, PostgresActivityRepository.kt, ActivityTracker.kt, JdbcActivityReader.kt; migration V60__persist_connected_account_activity.sql. Modify Database.kt, Main.kt, Application.kt only for source construction/lifecycle/hooks. Tests in server/.../activity/ and focused Application socket test.

**Interfaces:** Produce injectable ActivityTracker with no-op/default unavailable construction; methods for successful connection ID/account ID/start, disconnection of that connection ID, checkpoint and lifecycle. Produce JdbcActivityReader(Connection) with coverage(from:Long,until:Long,now:Long):AnalyticsCoverage, registeredRecipients(from,until,now):List<String>, distinct count helper for arbitrary/bucket ranges. Shared constants30s/60s/90days. Exact signatures can follow repository style but document them in report for Task2. Source uses AnalyticsFacts.subjectKey and existing CoverageStatus/AnalyticsCoverage.

- [ ] Read spec and current source seams; write deterministic failing tests for idle/replacement/short connection, half-open boundaries, no fabricated history, failure/restart gap and retention.
- [ ] Run focused RED; implement minimal collector/schema/JDBC reader with sorted user-row locking, atomic coverage, safe failure isolation, no-op default, and real accepted-account Application hooks. Include genuine lifecycle checkpoint (not a message-only collector).
- [ ] Add real PostgreSQL tests for full-range coverage union/gap, deletion anonymous totals vs recipients, transaction rollback and cleanup. Add Application-level accepted account/guest/rejected auth socket proof.
- [ ] Run focused GREEN with local PostgreSQL; then affected server suite once. Self-review, commit explicit files, report exact commands/counts/no skips and public signatures. No consumer/UI additions yet.

## Task 2: Activity analytics and compensation authority

**Files:** Modify analytics/AnalyticsModels.kt and PostgresAnalyticsRepository.kt; compensation/CompensationModels.kt, CompensationGiftContent.kt, CompensationIncidentService.kt, CompensationRoundService.kt; gifts/GiftModels.kt and PostgresGiftRepository.kt (memory unavailable default). Add/update focused PG/service/route tests and docs/admin-activity.md.

**Consumes:** Task1 JDBC activity reader and source constants. **Produces:** ACTIVE_PLAYERS metric with existing AnalyticsResponse; ACTIVITY compensation selectable only through verified range, optional round audience metadata with legacy inference. Existing HTTP routes unchanged.

- [ ] Read Task1 interface report and spec; write failing tests for ACTIVE_PLAYERS summary vs bucket distinct, cross-boundary buckets, retention/gaps/no source and rejected filters/RBAC; ACTIVITY real PG preview/publication/replay/source/range binding and deleted recipient handling.
- [ ] Implement minimal dedicated activity aggregate path within the existing repeatable-read analytics transaction. Preserve financial classification (avoid ordinal errors), current-role/audit/no-store, safe exact strings/coverage and comparison rules. No sum of bucket distincts.
- [ ] Implement activity selection on the caller's gift JDBC connection, explicit persisted round source default-compatible with legacy rounds; preserve content generation, preview freshness/current role/idempotency/frozen recipients. Missing data returns safe ACTIVITY_SOURCE_UNAVAILABLE/ACTIVITY_RANGE_INCOMPLETE; never fall back to all registered. Capability describes current source freshness and per-incident checks. No added input limit that silently truncates cohorts.
- [ ] Run focused RED/GREEN then relevant analytics/compensation/gift suites with real PG. Self-review, commit explicit files and report interfaces, test commands/counts/no skips and limitations.

## Task 3: Admin activity UI and integrated verification

**Files:** Modify server/src/main/resources/admin/admin-analytics.js and admin-compensation.js; relevant e2e admin tests/fixtures, docs/admin-analytics.md, docs/admin-compensation.md and docs/admin-activity.md. Reuse existing CSS/components; no broad visual redesign.

**Consumes:** ACTIVE_PLAYERS API, ACTIVITY capability, round source metadata and safe source errors. **Produces:** supported activity metric and incident targeting visible in existing screens.

- [ ] Read/apply UI UX Pro Max and existing MASTER/admin override; targeted guidance for chart coverage/forms, not a replacement design system.
- [ ] Add failing browser tests: active users metric with exact count and chart/table, no currency filter; ACTIVITY supported selector/draft/detail/preview; unavailable or incomplete interval error cannot publish; no stale source/recipient state after account/role/view changes.
- [ ] Implement clear VI copy for connected registered accounts/90 days/affected interval and actual saved source; controls depend on capability rather than a hardcoded unavailable list. Preserve accessible/48px interactions and pending modal behavior.
- [ ] Run focused admin browser tests and inspect phone/desktop captures (including unavailable coverage); run integrated server tests and changed admin asset/build check, without live actions. Self-review, commit explicit files; report commands/counts and screenshot paths.

## Final integration

- [ ] Review each task against spec and quality, resolve findings per SDD ledger; final whole-branch review includes #31 base0a88a75.
- [ ] Run final appropriate unit/PG/browser/coverage gates once; do not claim unrun physical/provider/staging acceptance.
- [ ] Update #9/#17–24 with per-issue evidence and remaining provider/device scope; no blanket checkbox edits/issue closure.
- [ ] Deliver infrastructure decision guide for #6/#11/#12→#15, and update roadmap with cancelled #10/#16/deferred provider/devices.
- [ ] Preserve review ledger/evidence when cleaning transient plan workspace after integration. No deployments/provisioning/billing/live campaigns solely to verify these changes.
