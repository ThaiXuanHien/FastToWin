# Closed Testing v10 Preparation Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to execute the following tasks sequentially with review checkpoints.

**Goal:** Prepare a signed normal-release Android 1.0.9 (10) candidate and a Closed testing readiness packet locally.

**Architecture:** Reuse the current isolated checkout and existing upload key. Change only Android version metadata; explicitly build with auth diagnostics off and AdMob disabled. The approved runtime remains master `ea1a02139d3d2fbca124dd87180e576dc442f855`; existing local AGENTS preferences remain intact.

**Tech Stack:** Android Gradle/JBR, Kotlin Multiplatform, existing upload signing tools, Markdown/CSV readiness packet.

**Spec:** Owner direction on 31 October 2026: no tester group yet; prepare release first. Existing gates: `docs/ugc-moderation-rollout.md`, `docs/play-store-release-checklist.md`, and `.artifacts/ugc-rollout-audit-20261031/rollout-status.md`.

## Global Constraints

- Package `com.hienthai.fastowin`, proposed versionCode `10`, versionName `1.0.9`, minSdk `29`, targetSdk `37`.
- Production endpoint `wss://fasttowin.up.railway.app/game`; diagnostics `off`; AdMob `disabled`.
- Local preparation only. No GitHub push/CI, Play upload/track/tester edits, production configuration/DB changes or Samsung operations.
- Do not claim versionCode 10 reserved/available until a later live Console preflight. Internal 1.0.8 (9) is the latest recorded release, not a new Console readback.
- Existing Samsung acceptance is cancelled, not passed. Mandatory UGC enforcement stays outside this preparation.
- Preserve existing upload certificate and all protected backups. Never record signing passwords, keys, reviewer credentials or purchase tokens in public files.

## Task 1: Freeze local candidate and readiness packet

**Files:** Modify `app/build.gradle.kts` version metadata; create ignored packet `.artifacts/closed-testing-v10-20261031/`.

- [x] Confirm clean existing linked worktree and create branch `codex/android-closed-testing-v10` from current reviewed checkout.
- [x] Replace `versionCode = 8` with `versionCode = 10`, and `versionName = "1.0.7"` with `versionName = "1.0.9"`. Assert no other app build-file change.
- [x] Prepare checklist, Data safety delta worksheet, VI/EN reviewer instructions and blank tester/feedback ledgers. Keep unknown credentials, opt-in dates and release approval visibly pending rather than inventing them.

## Task 2: Build and sign exact local AAB

**Files:** Ignored `prepare.py`, `sign.py`, build/manifest/signature evidence and signed AAB under the packet root. No new runtime modules or tests.

- [x] Execute `:app:bundleProdRelease :app:lintVitalProdRelease -PFASTTOWIN_AUTH_DIAGNOSTICS=off -PFASTTOWIN_ADMOB_MODE=disabled -PFASTTOWIN_PROD_WS_URL=wss://fasttowin.up.railway.app/game --no-daemon --no-configuration-cache --max-workers=1 --no-parallel` with the existing JBR/SDK and approved local JVM socket directory.
- [x] Require build exit 0 and BUILD SUCCESSFUL; inspect generated prodRelease BuildConfig for diagnostics false, ads disabled/IDs empty and exact endpoint.
- [x] Inspect AAB protobuf manifest for package/version/SDK/nondebuggable, validate ZIP and reject credential/backup entries. Sign using the existing upload key with process-environment password passing only.
- [x] Verify unchanged upload certificate, jarsigner verification and every original application entry hash after signing. Record signed AAB size/SHA-256 and exact source file hashes.

## Task 3: Review and local handoff

**Files:** Local candidate evidence, plan checklist and packet handoff.

- [x] Request independent review of source scope, binary evidence and readiness packet; resolve concrete findings before readiness claims.
- [x] Run `git diff --check`, inspect diff and record clean/local commit state after checks. Do not push or start CI under this local-only scope.
- [x] Hand off exact artifact/hash and remaining gates: fresh Console code/track check, declarations/reviewer credentials, approved Closed testing distribution, tester recruitment and continuous opt-ins. Internal opt-ins do not count as Closed testing opt-ins; no start/end date is claimed before real evidence.
