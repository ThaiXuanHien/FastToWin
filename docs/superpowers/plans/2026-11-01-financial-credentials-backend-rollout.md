# Financial Billing credentials — backend rollout plan

> **For agentic workers:** Use `superpowers:executing-plans` to execute this runbook inline, with the authorization and operational checkpoints below. Do not delegate or start CI/device tests automatically.

**Goal:** Deploy the merged #24 credential-selection fix to the existing Railway backend while financial collection remains disabled.

**Architecture:** Upload a frozen JVM distribution to the existing single backend service. The Web deployment stays in place. No new migration, credential replacement, provider request or financial activation is included in the candidate.

**Tech Stack:** Kotlin/JVM 17, Ktor, PostgreSQL/Flyway V69, Railway Dockerfile runtime.

**Spec:** [Issue #24](https://github.com/ThaiXuanHien/FastToWin/issues/24), [merged PR #69](https://github.com/ThaiXuanHien/FastToWin/pull/69), `docs/financial-analytics-sources.md`, `docs/store-billing.md`, `docs/admin-maintenance.md`.

## Global constraints

- This document prepares a deployment; it is not permission to deploy, access private production data or create credentials.
- Keep `FINANCIAL_GOOGLE_ENABLED` absent/false. Do not change any Google Play, Firebase, AdMob or UGC setting.
- Preserve the existing Web, database, private networking, one backend replica, staging and Play release/review settings.
- No Android test in this step. The owner waived the failing emulator UI acceptance for this integration; the next Android acceptance is on a real device when requested.
- No CI dispatch, status query, log read, polling or monitoring without a new owner request. The recorded run is historical evidence, not a green overall result.
- Do not open old backup archives, private account/password files or provider credentials. Package only the named public runtime context.
- Do not silently reuse V69 migration/cutover scripts: their guards, manifest and deployment IDs target the earlier release.
- If any deployment/schema/source/configuration guard differs, stop before mutation and revise the concrete scope.

## Frozen candidate and evidence

Source master: `6eb2d2f6b898512cc9ac902cb66f63355c0b7b4a`.
Tree: `49ae78d33fc4d35dc8bfc88edd253ce74ee9b658`.

Local artifact root: `.artifacts/financial-source-20261101/`.

| Artifact | Purpose |
| --- | --- |
| `FastToWin-Backend-6eb2d2f-20261101.zip` | Final backend upload archive, 79,425,919 bytes |
| `backend-release-6eb2d2f/server/` | Exact Docker context to upload |
| `backend-release-6eb2d2f/manifest.json` | Hashes of all 173 context files and embedded migrations |
| `backend-candidate-result.json` | Build, archive and comparison receipt |
| `backend-candidate-build.log` | Successful `:server:installDist` build; selected test task was UP-TO-DATE |
| `prepare_backend_candidate.py` | Local packaging and comparison logic; never upload this script |
| `android-ui-owner-waiver.json` | Owner's bounded Android acceptance waiver |

Archive SHA-256: `a087a64b89d34066dd62e5647ab79fe9e61c2d25ecc1fd66c8b614a6ab7d6d05`.
Manifest SHA-256: `8e477974f8341e6db4dd8b40a6480660bcffa00d18c646d8105df6f41c98702a`.

The earlier `FastToWin-Backend-6eb2d2f.zip` / `backend-candidate-6eb2d2f/` is superseded packaging output without the established `.dockerignore`. It is not the final deployment candidate.

Compared with the frozen backend deployed for V69 (`c53dacc09a4daa9793e5d24f0388927853abfe17`), the final context differs only in `distribution/lib/server.jar`. Every dependency JAR, both launchers, Dockerfile, `.dockerignore` and `railway.json` is byte-identical. The entry set of `server.jar` is unchanged. The four differing classes belong to `FinancialSyncService.kt`; the three service/coroutine classes retain identical signatures and executable instructions according to `javap -p -c -s`. The changed top-level configuration function calls the existing Billing credential resolver.

All embedded migration bytes match both source and the old V69 manifest; maximum version is 69, with zero new migrations. No test classes, Web distribution, account data, credential files or backups are packaged. This is a static package comparison, not a fresh observation of production.

Recorded local execution passed 24 tests: configuration 11, Orders 8, Billing configuration 5. Gradle reused that same-source result for this candidate build; do not claim a second test execution. CI [41305290071](https://github.com/ThaiXuanHien/FastToWin/actions/runs/41305290071) passed Build/test, screenshots, iOS and Web E2E, and failed Android UI 10/211 with a Pixel Launcher ANR focus overlay. The owner waived Android UI for this step; the launcher hang is not fixed by this release. No local PostgreSQL suite or new runtime container smoke test is claimed here.

## Target and drift guards

| Target | Exact identity |
| --- | --- |
| Railway project | `8daf3d1f-df4c-4f39-a07b-61becae674fa` |
| Production environment | `351ee85f-7760-4b57-9649-9693fdcdbfea` |
| Backend service | `c08bdb48-d6ff-4816-b6bb-d3cdabb794b6` |
| Web service, unchanged | `3c5d76ab-734e-4ccc-a4c0-4116b5d58d27` |
| Expected existing backend deployment | `45893cf8-c33c-4164-88b8-7280ed343cd8` |
| Expected existing Web deployment | `1e588a3d-12f9-446c-a2eb-e7d94813b3c2` |
| Public origin | `https://fasttowin.up.railway.app` |

These deployment IDs come from completed handoffs on 30 October and 1 November. They have not been checked live during this preparation. A different active deployment is a stop condition, not permission to overwrite it.

## Task 1: Local preparation — complete

**Files:** the candidate artifacts above and this runbook; no production code changes.

**Consumes:** merged source and the public frozen V69 candidate.
**Produces:** immutable upload context, archive and manifest.

- [x] Confirm master/source tree identity and clean worktree before building.
- [x] Build `:server:installDist` from the merged source; record actual task outcomes.
- [x] Compare dependencies, runtime entries, migrations and deployment templates with the V69 candidate.
- [x] Normalize the generated Unix launcher, retain the established upload allowlist, reject links/potential credential filenames, and freeze only `bin/`, `lib/` and templates.
- [x] Verify ZIP integrity and every ZIP/context hash; keep manifests/scripts outside the upload root.

Recheck before any upload:

```powershell
Get-FileHash -Algorithm SHA256 .artifacts/financial-source-20261101/FastToWin-Backend-6eb2d2f-20261101.zip
Get-FileHash -Algorithm SHA256 .artifacts/financial-source-20261101/backend-release-6eb2d2f/manifest.json
```

Both results must match the frozen hashes above. Do not rebuild or rerun the packaging script over a frozen candidate.

## Task 2: Authorized production preflight and recovery readiness

**Files:** create sanitized `production-preflight.json` and a backup-readiness receipt under the artifact root after authorization.

**Consumes:** candidate manifest, exact target identities, deployment approval and any separately approved data-access scope.
**Produces:** explicit go/no-go before maintenance or upload.

- [ ] Obtain approval for this exact backend candidate, a planned maintenance/restart window, and bounded post-deploy checks. Expected interruption is 5–10 minutes after warning/drain, subject to successful preflight/build/health.
- [x] Read service/deployment metadata and confirm the expected active IDs, one backend replica, Dockerfile builder and `/health` check. Keep Web running and its image unchanged.
- [x] Check only the named nonsecret switches: financial collection disabled, AdMob disabled, mandatory UGC acceptance disabled. Do not export a full environment/secret inventory or copy credential values into receipts.
- [ ] Confirm the running backend's schema evidence remains V69 and that the candidate has zero pending migrations. Prefer existing authorized deployment/Flyway logs; direct private schema inspection requires its own granted access scope. Unknown version is a stop condition.
- [ ] Satisfy the repository's backup-before-update requirement in `docs/production-deployment.md`: record a current verified backup/recovery receipt before changing the backend. This local preparation did not create one. The historical V68 archive is not presented as a current V69 backup. If a fresh full-database backup/restore check is needed, obtain explicit authorization for that private-data access, destination, retention and temporary access before performing it; do not open or overwrite the existing V63/V68 archives.
- [ ] Confirm a usable Owner maintenance session without reading password files. If absent, request direct sign-in. Record the current maintenance version and ACTIVE state.

Stop before maintenance if any check is unknown, failed, outside granted access, or shows unexpected state. Do not trigger a database read/backup merely because this document describes the dependency.

### Read-only checkpoint — 1 November 2026

Owner approved proceeding with the preflight. Railway UI confirms the expected backend and Web deployment IDs are ACTIVE, each with one replica. Backend uses Dockerfile `/Dockerfile`, `/health`, timeout120, restart On Failure/3, Serverless off and no public exposure. The named variable filter shows no `FINANCIAL_GOOGLE_ENABLED`; the two named AdMob/UGC switches are false. No credentials were revealed. Public health returned200/OK and status200/ACTIVE/maintenance false at `2026-11-01T13:36:02.713184+00:00`.

Postgres Backups shows **No Backups** and says creating backups/schedules/PITR requires Pro. No plan upgrade, backup or restore was initiated. The existing encrypted local V68 archive still exists at429,862bytes; only file metadata and prior sanitized receipts were inspected. It predates migrationV69 and does not satisfy a fresh V69 backup checkpoint. No database rows or archive contents were read; private schema inspection and maintenance-session preflight remain pending.

Evidence: `.artifacts/financial-source-20261101/production-preflight.json`, `production-public-preflight.json`, `railway-backup-preflight.png`. Next authorization should explicitly cover temporary access, reading the full current production DB for an encrypted V69 backup retained14days and an isolated restore check, then planned backend-only deployment of the frozen archive. Do not begin these operations under the read-only preflight approval.

## Task 3: Planned maintenance and backend replacement

**Files:** record maintenance request IDs/versions, exact upload intent and deployment receipt under the artifact root.

**Consumes:** successful Task 2 and explicit deployment approval.
**Produces:** the candidate backend running under maintenance, with Web unchanged.

- [ ] Use the existing protected maintenance UI/API as Owner: planned update, reason `Deploy financial credential resolver 6eb2d2f`, warning 5 minutes, drain up to 15 minutes. Messages: VI `Hệ thống sắp bảo trì để cập nhật máy chủ. Vui lòng hoàn tất trận đang chơi.`; EN `Server maintenance will start shortly. Please finish your current match.`
- [ ] Record the fresh expectedVersion and a unique requestId; never blindly repeat a mutation after an ambiguous response. Read the current state to reconcile it first.
- [ ] Wait for persisted/effective/runtime versions to agree and the runtime to report zero active matches. Never terminate an occupied match to force this routine update; retain the current backend and stop/replan if zero is not reached safely.
- [ ] Confirm MAINTENANCE at the public status endpoint. Stop the old backend deployment before starting the replacement, preserving the single-instance constraint. Do not stop Web or Postgres.
- [ ] Revalidate every frozen context hash. Upload the exact context once to the explicit target IDs. The following is the previously established CLI shape; confirm local `up --help` before execution, without starting an upload:

```powershell
railway up .artifacts/financial-source-20261101/backend-release-6eb2d2f/server `
  --project 8daf3d1f-df4c-4f39-a07b-61becae674fa `
  --environment 351ee85f-7760-4b57-9649-9693fdcdbfea `
  --service c08bdb48-d6ff-4816-b6bb-d3cdabb794b6 `
  --path-as-root --no-gitignore --detach --json `
  --message 'FastToWin backend financial credentials 6eb2d2f'
```

- [ ] Retain the returned deployment identity. If the upload response is ambiguous, reconcile deployment inventory by time/message/target before deciding whether anything must be retried; do not upload a duplicate automatically.
- [ ] Check this exact deployment to completion with bounded operational checks. This permission would cover deployment health, not GitHub CI monitoring. If the image fails, retain maintenance and diagnose the deployment without changing provider flags.

The upload root must be the named `server/` directory, never the repository, artifact root, ZIP's parent or any private-data directory. `railway.json` is packaged metadata; actual service settings remain authoritative.

## Task 4: Postflight, reopen and receipt

**Files:** create sanitized `production-postflight.json`, `deployment-result.json`, and append a release handoff.

**Consumes:** new deployment ID and successful maintenance/runtime observations.
**Produces:** a verified ACTIVE backend, or an explicit maintenance-held failure report.

- [ ] Confirm exactly one successful backend deployment at the new ID, expected Web deployment unchanged, and no unexpected service/variable mutation.
- [ ] Check startup logs: production/PostgreSQL mode, Flyway validation successful with no newly applied migrations, no credential/startup failures. Do not dump environment values or raw request/response bodies.
- [ ] Confirm the three feature gates still match preflight and financial collection remains disabled. Do not send an Orders request to prove a feature that has not been activated.
- [ ] Check public `/health` returns HTTP 200 and body `OK`; `/status` must still report maintenance until Owner reopening. Confirm the public homepage and `/privacy/` remain served by the unchanged Web.
- [ ] Reopen through the protected maintenance control using the current version and a fresh requestId only after the above checks succeed. Confirm persisted/effective/runtime state agrees with public ACTIVE.
- [ ] Perform a bounded follow-up health/status check after 60 seconds and inspect the relevant startup/error window once. Stop repeated checking once this gate passes. Do not claim a long stability/load test.
- [ ] Save commit, candidate SHA, old/new deployment IDs, no-new-migration evidence, health/status observations and unchanged flags. Do not call this LIVE revenue acceptance or close #24.

If startup, migration validation, identity, data-access or health checks fail, keep maintenance and report the precise finding. Do not restore a database, repair Flyway, activate a provider or redeploy the old backend automatically. A rollback proposal must identify the exact prior image and reconfirm schema compatibility before owner approval.

## Next separate work: LIVE financial acceptance

After this deployment, a later scope must authorize the collector configuration, verify the selected Billing account's Orders access, choose the collection boundary, and accept authoritative amount/currency/refund data against the Owner dashboard. Do not infer revenue from Gem amounts, catalog prices or SANDBOX orders. Preserve Google known-order PARTIAL coverage and App Store UNAVAILABLE. The present candidate/deployment neither performs nor proves that acceptance.

## Preparation self-review

- Scope matches the approved credential-only implementation; package comparison excludes unrelated Web/shared changes.
- No new source changes, migrations, secrets, provider calls or production reads were made during preparation.
- Build success and cached test reuse are distinguished; Android UI failure/waiver remains explicit.
- Public candidate is frozen and hash-addressed. Deployment approval and current recovery readiness remain outstanding checkpoints.
