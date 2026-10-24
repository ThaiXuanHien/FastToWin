# Android AdMob Rewarded Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Integrate Android rewarded ads and signed server verification to grant exactly two online matches once, with privacy handling for the approved 13+ audience.

**Architecture:** Preserve the existing gateway/verifier/quota boundaries. Add authenticated ad sessions and an atomic signed callback grant in a focused server package; Android SDK works through a fakeable driver and shared HTTP gateway. Existing WebSocket receipt handling refreshes already committed rewards.

**Tech Stack:** Kotlin/KMP, Ktor, PostgreSQL/Flyway, Compose, GMA Next-Gen stable SDK, UMP, JUnit/kotlin.test, physical Samsung.

**Spec:** docs/superpowers/specs/2026-10-20-admob-android-rewarded-design.md

## Global Constraints

- Each verified ad grants exactly 2 online matches; 10 base matches/day and Asia/Bangkok reset remain unchanged.
- User explicitly approved Android + signed SSV + 13+ privacy + Samsung test ads on 2026-10-20.
- No client-only or unsigned production reward; disabled configuration fails closed; dev simulation and Web/iOS behavior remain compatible.
- Callback persistence and quota grant are atomic and idempotent; no cross-account receipt or stale client callback.
- No Android emulator tests; CI Android emulator is opt-in only and remains off.
- All new user-facing text uses TextKey/catalogs in all 12 languages; use existing dark Arcade UI and ui-ux-pro-max.
- No production app uninstall/data deletion, no live-ad clicks, no secrets in Git/logs, no changes to user-owned IDE file.
- Spec defines exact DTOs, routes, age/privacy gate, callback validation, config and acceptance. It is the binding source of truth.

## Task 1: Durable server session and verified quota grant

**Files:** create protocol AdMobProtocol.kt and server `ads/` configuration, verifier, service, memory/Postgres repositories and routes with tests; add a new migration; integrate Database, Main, gameModule, RewardedAdVerifier and rate limits. Do not change Android/shared UI files.

**Interfaces:** Produces exact DTOs/routes in the Spec; RewardedAdSessionView and RewardedAdSessionStatus; server SSV enabled availability and owner-bound existing receipt verifier. Consumes existing PlayQuotaRules, auth access-token service, maintenance admission and Database datasource.

- [ ] Read spec and current route/database/quota patterns. Write failing tests first: a correctly signed callback for an authenticated created session grants exactly two; wrong signature/unit/reward/account never grants; concurrent replay and second transaction on same session never recredit. Cover auth and config off.
- [ ] Run focused tests and retain the expected failure evidence. Implement the smallest service/repository/signature boundary needed, preserving unmodified raw query for verification and fixed Google key URL with bounded cache/network.
- [ ] Add PostgreSQL transaction tests using existing isolated TEST_DATABASE settings; signed callback persistence and rewarded_ad_grants/daily row update must roll back together. Include replay after date change, invalid encodings/duplicate security fields, delayed/cancelled callback and unknown-session probe.
- [ ] Wire routes/configuration into actual server entrypoints; add explicit emulator opt-in to `.github/workflows/ci.yml` per direct user instruction, without weakening other checks. Verify existing quota and route tests plus protocol roundtrip and server build.
- [ ] Run `:protocol:jvmTest :server:test --tests '*AdMob*' --tests '*RewardedAdVerifierTest' --tests '*PlayQuota*' --no-daemon --no-configuration-cache` with bounded workers and appropriate JDK. Do not claim PostgreSQL coverage unless tests actually run with isolated PostgreSQL.
- [ ] Commit scoped code; write report with changed files, exact commands/results, red-green evidence and limitations. Review spec and task quality before moving on.

## Task 2: Android SDK, privacy and lifecycle integration

**Files:** shared `RewardedAdApiClient.kt`, existing RewardedAds common/actual gateways, new Android SDK driver/config/privacy/session storage, FastToWinApp and Settings/quota UI, localized TextKeys/catalogs, app/shared build configuration and manifest, focused tests. Do not modify server implementation except communicating any contract mismatch to the controller.

**Interfaces:** Consumes exact Task 1 DTOs/routes. Produces RewardedAdSessionGateway prepare/status/close and new optional sessionGateway parameter in rememberRewardedAdGateway. Native SDK returns only an owner/session-fenced receipt after server GRANTED; FastToWinApp constructs authenticated API using current token provider.

- [ ] Read spec, Task 1 report and interfaces; read ui-ux-pro-max and search focused loading/privacy interaction and Jetpack Compose guidance.
- [ ] Write failing host tests for API auth/401 refresh/timeouts/cancellation/owner fields and SDK-driver fake lifecycle: double show, no-fill/cancel, pending SSV, grant exactly once, owner switch/close, stale callbacks, age unknown/under13 and missing test-device gate.
- [ ] Implement current stable GMA Next-Gen + UMP from official docs. Pin versions from official source; only initialize after privacy decision/canRequestAds and correct request flags. Store age band (no DOB) and pending session per account locally, with finite polling and retry UX.
- [ ] Keep existing dark Arcade quota dialog, add specific loading/pending/unavailable feedback and Settings privacy options only where supported/required. Add all 12 translations and scanner checks. Do not add banners/interstitials or Web SDKs.
- [ ] Configure disabled/demo/test/live builds with no implicit live ads, test-device allowlist in owned test mode; preserve dev simulation by default. Read public app/unit IDs from build properties/resources; never read/copy signing passwords into tracked files. Increase next publishable versionCode above 1.
- [ ] Run common host tests, localization scanner, Android compilation and prod release bundle/lint; compile Web/iOS boundary as feasible. No emulator/device instrumentation yet while controller operates Samsung. Capture exact evidence and commit scoped code, then report limitations and suggested physical journey.

## Task 3: Provider setup, physical acceptance and delivery

**Files:** docs/admob-android.md, issue/PR descriptions, local ignored evidence; fix only review findings through the responsible implementer. Controller owns provider/browser operations.

**Interfaces:** Consumes backend callback URL/config and Android build properties. Produces provider app/unit IDs, verified test-device setup, local signed build and evidence; PR for review. Live monetization requires separate readiness gates in Spec.

- [ ] In existing approved AdMob account create Android Fast To Win unpublished app and rewarded online_matches unit amount 2. Stop for human only on new binding terms/security-sensitive access or missing legal data. Publish app-ads.txt/consent messages only with actual reviewed values, not fabricated publisher/legal details.
- [ ] Use isolated staging for signed SSV test endpoint; production backend/database unchanged until explicitly authorized deployment. Preserve/stage backup/restore safeguards and stop short-lived staging compute after QA. Configure owned ad unit SSV only after endpoint verification; use registered Samsung test device, or demo ads with clearly documented SSV limitation.
- [ ] Verify no unsigned callback grants, SDK test ad display/cancel/complete, exactly +2 on signed callback, replay no extra, network/process restart recovery, large-font Settings/quota screens. Never uninstall production app or clear its data; use own Dev QA package and remove only QA artifacts/apps afterwards.
- [ ] Run complete relevant host/SQL/compile checks once the reviewed code is integrated. Obtain a broad final review, resolve material findings through one fix wave/re-review, and keep exact unresolved provider/device limitations visible.
- [ ] Update GitHub checklist only on proven outcomes. Push a draft PR if ready and attach it; do not claim merge/deploy/live AdMob or #28 Gem tests complete prematurely. Hand back a reviewable result and the exact remaining provider/legal action, if any.
