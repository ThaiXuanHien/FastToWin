# Play release integration implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Existing feature designs are approved; this plan coordinates integration, remaining verification, and an internal release.

**Goal:** Integrate PR54 legal pages, PR55 UGC, and PR52 AdMob with the shipped Billing fix, then reconcile Play declarations and publish a new signed internal build.

**Architecture:** Preserve the existing legal/UGC/AdMob modules and their runtime gates. Update the existing branches with merge commits, review each integration diff, then integrate in dependency order. Keep native ads disabled while app readiness and provider/device gates remain outstanding.

**Tech Stack:** Kotlin Multiplatform, Compose, Ktor, PostgreSQL/Flyway, static Caddy web pages, Google Play/AdMob.

**Spec:** User request dated 2026-10-23 selects steps 1, 3, 4 from the proposed order; owner initially reported AdMob app under review. Controller read-only verification on 2026-10-23 shows the AdMob account approved, but Fast To Win app status is “Yêu cầu xem xét” (Requires review), with store details empty and SSV unset. Evidence: main checkout `.artifacts/play-integration-20261023/admob-app-requires-review.png`. Existing approved designs: `docs/superpowers/specs/2026-10-20-ugc-moderation-design.md` on UGC branch; `docs/superpowers/specs/2026-10-20-admob-android-rewarded-design.md` on AdMob branch. Legal scope is PR54 plus `docs/play-store-release-checklist.md` on that branch.

## Global Constraints

- Preserve game Dark Arcade UI and admin white Material UI; apply UI/UX Pro Max for any interface changes.
- Preserve Billing fix code3 -> BillingIncomplete, FEATURE_NOT_SUPPORTED -> BillingUnavailable, cancellation/restoring and verification/consume behavior from master `6190aeaa958ca5a2e6d7581b97c3a4a84ea5353d`.
- Preserve current production Google Play QA gate to one selected player, separate Billing/Firebase credentials, and disabled LIVE financial collector. No real-money QA transactions.
- No public Play release or review submission; internal release only. No new paid service, subscription or domain purchase.
- AdMob live remains disabled until the app reaches Ready and remaining consent/provider/device gates are met. Account approval alone is insufficient; app currently Requires review per controller verification. Do not represent SDK fake-driver tests as provider/device acceptance.
- UGC content-terms enforcement remains false until compatible-client and physical-device rollout gate is approved; reporting/moderation itself must work with compatible clients and authorized admins.
- Keep production and synthetic staging data separate. No destructive database migration, data wipe or real-user moderation for QA.
- User selected no Samsung Billing acceptance in this task. Do not require or repeatedly ask for Samsung; host/Web checks may progress. Emulator exception in prior session was for Billing; do not expand emulator UGC/AdMob QA silently.
- Do not edit main checkout user changes `.idea/appInsightsSettings.xml` or `.scratch/`. No secrets, raw receipts, callback queries or player report contents in Git/issues/log output.
- Controller owns pushes, CI selection, PR merges and deployment. Implementers commit only their assigned branch and do not spawn subagents.
- Use JAVA_HOME `C:/Program Files/Android/Android Studio/jbr`, ANDROID_HOME `C:/Users/Hien Hue/AppData/Local/Android/Sdk`, JAVA_TOOL_OPTIONS `-Djdk.net.unixdomain.tmpdir=D:/HienTX/Work/Android/FastToWin/.tmp/jvm-sockets`; bounded Gradle flags and separate socket directory already used by this project.
- Logs/evidence in `D:/HienTX/Work/Android/FastToWin/.artifacts/play-integration-20261023/`; credentials remain in existing private locations outside Git. Managed worktree edits may need escalated execution.

### Task 1: Integrate legal branch with current master

**Workspace:** `C:/Users/Hien Hue/.codex/worktrees/play-store-legal/FastToWin`, branch `codex/play-store-legal`, starting HEAD `336dd534db92e74596393bc493c03d84ef74cfc8`.

**Files:** Existing PR54 legal pages/worker/Caddy/Settings links, docs release checklist, screenshot references; `.github/workflows/ci.yml` if merge overlaps. Include this integration plan in the commit.

**Interfaces:** Consumes master Billing fix/version 1.0.1 (2), produces an updated legal branch for UGC integration. Keep permanent legal URLs/privacy/deletion support address/14-day backup and 7-working-day email deletion policy unchanged.

- [ ] Read AGENTS/domain docs and `docs/play-store-release-checklist.md`. Inspect full PR54 diff against its merge base and relevant scripts/package tasks before selecting commands.
- [ ] Verify clean tracked baseline apart from this new plan. Merge exact master commit `6190aeaa958ca5a2e6d7581b97c3a4a84ea5353d` into existing legal branch. Resolve overlapping workflow changes retaining emulator opt-in only, Billing changes, and all legal routes/links.
- [ ] If integration introduces a behavior defect, reproduce with a focused existing/new test before implementing its minimal fix; do not add tests mirroring static version values.
- [ ] Run legal worker tests, Caddy routing/retention safety tests documented in branch, `:shared:testAndroidHostTest`, `:protocol:jvmTest`, localization scanner, and client compilation sufficient for changed integration paths. Existing PR feature acceptance may be reused as historical evidence; do not rerun unrelated Web E2E locally when CI will run it.
- [ ] Inspect existing phone/desktop Settings references for legal links; if no UI layout changes, preserve references and report that fact. Do not update goldens solely to hide a regression.
- [ ] Self-review diff, commit all assigned integration/plan changes, report commands/counts/logs plus any remaining gate. Do not push, merge PR, deploy or mutate another worktree.

### Task 2: Integrate and finish remaining UGC verification

**Workspace:** Reuse `C:/Users/Hien Hue/.codex/worktrees/ugc-moderation/FastToWin` after controller switches clean checkout from merged Billing branch back to `codex/ugc-moderation`, starting feature HEAD `6d4ab3457f7fc6d3cb8cbe18817aff005a4d362d`.

**Files:** UGC protocol/client/server/admin, `server/src/main/resources/db/migration/`, UGC verification/rollout docs, existing UGC browser E2E, and integration conflicts. Existing design remains binding; do not redesign UI.

**Interfaces:** Consumes Task1 updated legal branch (controller supplies exact SHA), produces legal+UGC+master compatible code. Retain content terms `2026-10-20`, 90-day closed-report retention, server receipts/retry, RBAC and exact-content-revision moderation. Read `docs/ugc-moderation-rollout.md` and `docs/ugc-moderation-verification.md`.

- [ ] Merge controller-supplied Task1 SHA into UGC branch, retaining original feature commits and Billing fixes/version. Resolve schema/protocol/client integration without dropping existing behavior.
- [ ] Audit remaining verification matrix: profile/name/avatar, clan, room and tournament report entry points; false/true consent gate; registered upgrade/reconnect and guest restart; blocked self-report and roles; real avatar endpoint invalidation. Map each to existing executed evidence or a concrete new isolated host/Web test. Never invent physical-device evidence.
- [ ] Run the previously unexecuted admin UGC Web E2E against local isolated PostgreSQL/backend. Inspect package scripts/config to use exact existing command and synthetic fixtures; if a coverage gap is real, add focused behavioral tests and follow RED/GREEN for any runtime fix.
- [ ] Execute UGC PostgreSQL tests with no skipped DB tests, targeted host/client/protocol tests and localization/compile checks. Run screenshot comparisons for affected components; inspect phone/desktop images if UI changes.
- [ ] Update UGC verification/rollout docs with exact executed commands/results and remaining physical/internal gates. Keep `FASTTOWIN_CONTENT_TERMS_ENFORCED=false` production policy.
- [ ] Self-review, commit integration/verification changes and return report. Controller owns feature CI, staging/prod operations and dependency retarget after Task1 merges.

### Task 3: Integrate AdMob while keeping live disabled

**Workspace:** `C:/Users/Hien Hue/.codex/worktrees/activity-history/FastToWin`, branch `codex/admob-android-rewarded`, starting HEAD `61cc3c4fe8214d011b30b7d8efc90b07b8144759`.

**Files:** AdMob Android/backend/protocol/preferences/UI integration, Flyway migrations, app/build.gradle.kts, docs/admob-android.md, matching tests and integration plan updates only if facts changed.

**Interfaces:** Consumes Task2 reviewed UGC+legal+master SHA (controller supplies exact SHA). Produces final integrated release branch. Reward remains 2 online matches; base quota 10/day Asia/Bangkok, Google signed SSV only. Owned public IDs remain from approved design; no credential enters code.

- [ ] Read approved AdMob spec/runbook and distinguish the owner's initial under-review report from controller verification: account approved, app Requires review, store details empty, SSV unset. Keep live ads disabled until app Ready and existing consent/provider/device gates are met. Merge Task2 SHA into existing AdMob branch; retain UGC consent/report routes and Billing error fix.
- [ ] Audit migration numbering before merge: UGC and AdMob independently used V62 on staging, while production has not received either feature. If collision confirmed, retain UGC V62 and assign AdMob V63 on the final tree; update docs/tests accordingly. Do not rewrite an applied production migration or run clean/repair on production. Any old staging fixture database is disposable only when separately authorized; prefer a fresh isolated test schema/database.
- [ ] Set next Android internal versionCode 3 and versionName 1.0.2 because Play already published 1.0.1 (2). Keep native release mode `disabled`; do not enable backend ads or configure live callback as a side effect of source integration.
- [ ] Preserve AdMob owner/generation fencing, HTTP->WebSocket GRANTED receipt handoff/reconnect, local account age bands/UMP. Review Settings legal links alongside privacy options so both remain accessible.
- [ ] Run focused real PostgreSQL SSV/replay/concurrency and migration tests plus Android host/protocol/localization tests. Confirm fresh schema applies V62 and V63 and rerun Billing regression cases to detect integration loss. Run production bundle/lint/default-disabled verification and affected screenshot comparisons.
- [ ] Update runbook stale version/readiness/CI statements with precise new evidence, leaving UMP/provider no-fill/early dismissal and signed final-device acceptance explicitly pending. No live ad clicks or emulator ad QA.
- [ ] Self-review and commit; return exact final SHA/version/migration list and test results. Do not push/deploy or change AdMob/Play Console.

### Task 4: Final review, controlled deployment and Play internal release

**Workspace:** Controller uses reviewed feature trees, existing staging tooling, production Railway IDs, Play Console and private permanent upload key.

**Files:** Final release/UGC/AdMob verification docs and Play release checklist. Policy audit outputs are confined to that skill's own generated `.scratch/play_policy_insights_<uuid>` directory.

**Interfaces:** Consumes Task1/2/3 reviewed commits, CI, migration/package verification. Produces updated master, safe backend/Web rollout with both feature gates off, actual-build declarations and internal 1.0.2 (3).

- [ ] Run Play Policy Insights on final integrated source with its mandatory workers/critic pipeline; reconcile results against actual SDK initialization/build flags, existing Privacy/Data safety declarations and reviewer access. Browse official primary sources for current policy/SDK facts needed for decisions.
- [ ] Run current final CI once per changed head; preserve assertions, skip only Android emulator UI via existing opt-in design. Review integration fixes and final whole-feature range. Merge PR54 -> PR55 -> PR52 only after relevant reviewed/tested trees pass; retarget dependent PR after dependency merge, verify merged trees. Avoid force-push.
- [ ] Validate on isolated staging with synthetic fixtures, backup of the actual application database, migration V62/V63, UGC report/admin/avatar endpoint and ads-disabled behavior. Stop owned staging compute after QA, revoke session SSH keys, retain volumes only as already configured. Use browser snapshots for actual UI proof.
- [ ] Before production cutover, prepare exact packages/rollback and obtain any new security-sensitive temporary SSH/backup authorization necessary; prior QA production permission was SELECT-only and already revoked. Back up actual application database, verify archive and restore compatibility, deploy backend/Web with UGC enforcement false and AdMob disabled, health/read compatibility checks. No silent expansion of Billing test access or LIVE collector.
- [ ] In Play Console reconcile privacy, Contains ads/Advertising ID/Data safety and reviewer instructions against final ads-disabled binary. Save truthful drafts, do not submit public review or buy anything. AdMob account is approved, but app Requires review per controller verification; store details are empty and SSV unset. Prepare remaining live checklist and keep ads disabled until app Ready and existing provider/device gates are met; do not imply app approval.
- [ ] Build/sign version 1.0.2 (3) using existing permanent upload key, verify package/version/cert/hash, publish internal only after CI and deployment health are confirmed. Do not substitute internal/emulator acceptance for unavailable Samsung/UMP provider gates.
- [ ] Update PR/issue progress with evidence and limits; close only completed scopes. Sync local master safely after merge, preserve user's `.idea` and `.scratch` changes. Return internal link, shipped version, tests and concrete external gates.
