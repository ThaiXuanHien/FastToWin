# Redis Production Rollout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prepare and, only after separate authorization, enable the existing 18 shared rate-limit buckets on one private Railway Redis alongside the single production backend.

**Architecture:** One Redis node in the backend's production environment/region, persistent AOF and `noeviction`; one existing game backend. Keep room/match ownership, presence and all other local limits unchanged. Prepare a production bootstrap independently of the expiring cloud-test bootstrap; qualify the actual candidate/backend resource limits before cutover.

**Tech Stack:** Redis digest `sha256:be7c1cada5a6eb1656adfa2635b99296af725881301b21f84f35a71fac072c7f`, JVM 17/Lettuce 7.9.0.RELEASE, Railway private WireGuard, encrypted local manual AOF backup on Hobby.

**Spec:** [Approved runtime contract](../specs/2026-11-02-redis-rate-limit-local-design.md), [selected provider/budget](../../redis-production-options-2026-11-02.md), [cloud evidence and limitations](../../redis-cloud-acceptance-2026-11-02.md).

## Global Constraints

- **Owner approved Task 1 local preparation and then Task 2 release/baseline/approval-package preparation.** Local master is `d0385f7797175af98c140b343702ea987c522fda`; server tests 995/995 and coverage passed after local merge. Task 2 permitted read-only deployment/resource metadata and local packaging. The owner subsequently explicitly approved the Task 3 provisioning/synthetic-preflight subset: frozen-context uploads, one retained private Redis (0.25 vCPU/250 MB/5-GB volume) with three generated role secrets and one temporary 8-vCPU/8-GB probe for at most 30 minutes, target under $1. Current game backend remains in memory mode. SSH, production backups/data access, backend cutover, push/CI and Tasks 4–5 remain outside this approval.
- Owner previously selected Railway single node/private WireGuard and proposed Redis usage increase of $5/month, with review before $10/month. This is neither permission to provision production nor a workspace hard spending cap. Keep Hobby; no automatic Pro upgrade for managed backups.
- Only the 18 entry buckets listed in `RateLimitRuntime.kt` move to Redis. Keep 200-ms command, 2-second startup/connect and 128 pending limits; errors reject new work, never replay or silently fall back to RAM. No global IPv6 JVM preference.
- Cloud cold-start qualification passed only for a 2-vCPU test runner. It does not establish the actual backend's resource requirement or authorize resizing it. Validate current backend limits and image; stop if its startup gate fails.
- No database migration, schema change, Web/Play release, staging activation, new game replica, AdMob/UGC/LIVE-revenue flag change, presence or #15 work belongs to this rollout. Current preparation does not authorize player-data export or production DB backup. The repository backup-before-update policy remains an external release prerequisite, satisfied only under separate explicit data/access authorization before any backend replacement.
- No public Redis domain/TCP proxy or public metrics. WireGuard is network encryption, not Redis TLS. Keep one backend and disable sleep for Redis; preserve existing Web/DB settings.
- Restoring old quota, changing namespace or reverting to memory can grant fresh quota or over-restrict users. Never do those automatically. Single node/AOF/manual backup is not HA or zero-loss recovery.
- When asked to run CI, dispatch all jobs once with `run_android_ui=true`, return its link and leave it running. Do not inspect CI unless the owner reports completion/errors or asks to check.

## Target and proposed configuration

The following IDs are a **02 November rehearsal snapshot**, not a claim of current live state. Recheck names, environment and region before the approval request and every mutation.

| Target | Identity / proposed value |
| --- | --- |
| Production project | Display name `FastToWin Alpha`, `8daf3d1f-df4c-4f39-a07b-61becae674fa` |
| Production environment | `351ee85f-7760-4b57-9649-9693fdcdbfea` |
| Existing server | `c08bdb48-d6ff-4816-b6bb-d3cdabb794b6`; one replica; snapshot region SFO |
| Existing Web / Postgres | `3c5d76ab-734e-4ccc-a4c0-4116b5d58d27` / `244e1f14-0e25-43ed-9409-39420dee9fe3`; observe, do not redeploy |
| New proposed service | `rate-limit-redis`; exactly one new production service; region must equal the verified backend region |
| Redis resources | Initial 0.25-vCPU / 250,000,000-byte limit; 64-MiB maxmemory; one `/data` volume; Hobby default capacity may be 5 GB, usage billed separately |
| Backend resources | Preserve verified current limits; any increase is a separately priced owner decision, not an automatic 2-vCPU setting |
| Redis runtime | `appendonly yes`, `appendfsync everysec`, `save ""`, `maxmemory-policy noeviction`, protected mode, default user off |
| Namespace | Proposed stable `prod_main`; preserve on every restart/deploy. Never use a rehearsal namespace |
| Backend variables | `FASTTOWIN_RATE_LIMIT_STORE=redis`, `FASTTOWIN_RATE_LIMIT_NAMESPACE=prod_main`; exactly one of `FASTTOWIN_RATE_LIMIT_REDIS_URL` / `_FILE` |
| Connection | `redis://` private DNS read from the new service metadata, port 6379, DB 0, user `ftw_app`; secret supplied through Railway variables/file, never shell arguments or evidence |
| Backup proposal | Manual encrypted AOF archive before maintenance affecting Redis; initial maximum interval 24 hours, 14-day local retention; operated explicitly, no scheduler created here |
| Preflight proposal | One temporary startup-probe service, at most 30 minutes, matching the current verified backend ceilings of 8 vCPU/8 GB; target under $1 including associated test storage/network/build. This is a revised proposal after Task 2 metadata, not an approved cloud action or backend resize; recheck limits before requesting execution |

The 24-hour interval is a proposed operating requirement, **not an existing backup schedule**. Do not enable production unless a named operator accepts it or a separately reviewed automated backup mechanism is available. Old backups are last-resort quota recovery; an empty Redis is a separate owner-approved quota reset, never a disguised successful restore.

## File boundaries

| File | Responsibility / status |
| --- | --- |
| `deploy/railway/redis/Dockerfile` | Create in Task 1: production image pinned to the accepted Redis digest |
| `deploy/railway/redis/boot.sh` | Create in Task 1: production config/ACL initialization and privilege drop; no test deadline or automatic restore |
| `deploy/railway/redis/backup.sh` | Create in Task 1: bounded, fsynced, same-connection AOF snapshot; archive must exclude credentials/config |
| `scripts/tests/RedisProductionPackage.Tests.ps1` | Create in Task 1: owned-container negative/positive, ACL, restart and backup verification |
| `docs/redis-production-operations.md` | Create in Task 1: backup, incident, credential rotation and explicit restore procedure |
| `docs/redis-production-release-preparation-2026-11-02.md` | Create in Task 2: reviewable source/artifact/baseline/approval summary; no secret values |
| `server/src/main/kotlin/com/hienthai/fastowin/server/ratelimit/` | Existing runtime contract; no behavior changes planned |
| `.artifacts/redis-production-rollout-20261102/` | Ignored release contexts, checksums and sanitized approval/evidence; `private/` for authorized encrypted backups only |

The reviewed expiring rehearsal scripts remain evidence under `.artifacts/redis-cloud-acceptance-20261102/cloud/`. They are references, **not production deploy contexts**. Do not upload the repository, fixture credentials, test runner, inspector or test restore tag as the Redis production service.

## Task 1: Prepare and verify production assets locally

**Consumes:** Approved runtime contract, pinned Redis digest and reviewed AOF backup invariants. **Produces:** The five files above, immutable local Redis context/checksums and a no-secret local test report. Requires owner approval of this plan's local implementation scope before starting.

- [x] Create failing local checks for missing/invalid credential or namespace, default-user access, wrong-password access, app administrative/global commands, inherited test deadline and automatic restore. Test credentials must be generated, fixture IDs uniquely labelled and ports loopback-only; clean only the fixture IDs owned by this run.
- [x] Create the pinned Dockerfile and production bootstrap. Initialize writable config/ACL files under `/run/ftw-redis`, fix `/data` ownership and then use the official entrypoint to run Redis as its `redis` user. Repeating startup must succeed with existing files/AOF; the running Redis process must not remain root.
- [x] Accept namespace from `FASTTOWIN_RATE_LIMIT_NAMESPACE` with the runtime's 1–48 lowercase ASCII/suffix rule. Accept independent 48-byte random hex secrets through `FASTTOWIN_REDIS_APP_PASSWORD`, `FASTTOWIN_REDIS_MONITOR_PASSWORD`, `FASTTOWIN_REDIS_BACKUP_PASSWORD`, outside command arguments; reject missing, malformed or equal role secrets. The local harness generates them. Use the configured namespace in ACL patterns; the release proposal remains `prod_main`. Runtime permissions start with this contract and must pass the actual application handshake/EVAL tests:

```text
user default off
ftw_app:     ~ftw:prod_main:rl:v1:* ; +hello +client|setinfo +ping +select +eval +type +hlen +hmget +hset +pexpire +time
ftw_monitor: no key patterns       ; +ping +info
ftw_backup:  ~ftw:prod_main:ops:backup-fence ; +ping +set +waitaof +client|pause +client|unpause +info
```

This is the ACL capability contract, not literal `users.acl` syntax: bootstrap must render `user <name> on >... <patterns> <commands>` with the validated secret and never log it. App must be denied `FLUSHDB`, `FLUSHALL`, `CONFIG`, `ACL`, backup fence keys and keys outside its namespace. Monitor/backup credentials must not be supplied to the game backend.

- [x] Implement the backup script with a same-connection fence write → `WAITAOF` acknowledgment → write pause bounded to 10 seconds. Copy a stable AOF manifest/files and recheck file set/hashes within the pause; trap unpause on success/failure, allow Redis's pause expiry as a final bound. Reject symlinks/nonregular/duplicate/traversing entries, more than 1,024 data files, and compressed or uncompressed data over 256 MiB. A backup with changed files, failed fsync or expired pause is failed, not “best effort.”
- [x] Exercise the boot/config/ACL paths, repeated restart preserving marker/exhausted quota, valid archive verification and induced archive failure. Keep the production bootstrap free of `FTW_REDIS_DEADLINE_EPOCH`, `timeout`-driven process expiry, `FASTTOWIN_EXCLUSIVE_REDIS_GUARD`, restore tags and OOM/config-mutation probes. Preserve 200-ms/2-second application limits.
- [x] Run the local package verification from the repo root:

```powershell
pwsh -NoProfile -File ./scripts/tests/RedisProductionPackage.Tests.ps1
```

Expected: zero failures/skips, actual Redis auth and application EVAL pass, all forbidden ACL operations denied, repeated restart preserves quota, backup positive/negative paths verified, no generated secrets in output, all owned fixtures removed. The script is created by this task; it does not exist in the candidate at plan-writing time.

- [x] Write the operations document: 24-hour proposed maximum backup interval, 14-day encrypted local retention, same-volume/manual-restore limitations, owner-authorized quota reset alternative, no automatic fallback, credential rotation overlap/revocation and stop conditions. For planned app credential rotation, bootstrap may accept one optional validated `FASTTOWIN_REDIS_APP_PREVIOUS_PASSWORD` distinct from all current role secrets; render both app passwords during the approved overlap, update the backend, then remove the previous value/restart and verify old authentication denied. Missing optional previous value means only the current app password works. Monitor/backup credential changes use a stopped maintenance operation; do not invent online rotation endpoints or an admin Redis user. No cron/reminder, account key or real backup is created by writing the document.
- [x] Review and commit these local files on an isolated `codex/` branch. Record candidate revision, exact Redis context file set and SHA-256 manifest. No cloud upload yet. Any production-runtime code change discovered necessary returns to design review rather than being hidden in packaging.

### Local result — 02 November 2026

Task 1 local preparation is complete at implementation/test revision `9b4f5177b09a57ab2fe478fe61ed2ae65e4078a2`; independent task and final reviews found no blocking defect, and the scoped final re-review found no new Critical/Important issue. The controller's fresh package gate passed 53 assertions, including actual JVM EVAL, ACL boundaries, quota persistence, CRLF packaging, rotation/revocation and mutation-sensitive priority backup rejection checks. All owned local fixture containers, volumes, images and credential directories were independently verified absent afterward. No application runtime source, master branch, cloud/CI or production setting changed.

The three-file context frozen from `c751d44e7f6543c76168986905777f963985bdaf` is unchanged by the test-only fix. Its manifest SHA-256 is `089a860881a3c25d96ef55d2bb82f669981086bf9009f95d17276fc38522ca45`; the fresh verification log SHA-256 is `630769dd1b7fff55821b44f3e9d1a123021a8858318577fe05e9f11d8fa396dd`. Local evidence is under `.artifacts/redis-production-rollout-20261102/` (`parent-final-summary.json`, `parent-final-cleanup.json`, `parent-final-verification.json` and log).

Evidence qualification: the initial observed preimplementation RED covered the inherited deadline, not every later check. The final tests prove source-change, synthetic clock advancement and injected archive-sync failure rejection with deliberately broken copied-script comparisons; they do not simulate physical disk failure, actual pause expiry or every manifest/count/size limit. These disclosed historical/Minor coverage limits are accepted for local integration only, with the risk that an unexercised environmental branch can still fail. Carry them into the future production approval. The completed checklist marks the local deliverables, not an invented complete per-case RED history or production readiness.

See [operations contract](../../redis-production-operations.md). Task 2 release/baseline/approval-package preparation was subsequently approved and is recorded below. Tasks 3–5 remain unexecuted and need separate cloud/data/cutover authorization.

## Task 2: Freeze release, baseline and approval package

**Consumes:** Reviewed Task 1 artifacts and local master `d0385f7` or an explicitly selected descendant. **Produces:** `target.json`, `release-manifest.json`, `approval-summary.md` and rollback variables metadata under the ignored evidence root.

- [x] Read only production deployment/region/replica/resource metadata and names of rate-limit variables. Record the current backend deployment/source revision, actual limits, pinned rollback artifact/deployment, health settings, restart policy and current memory-mode configuration. Do not dump environment values, database rows, tokens or unrelated SSH keys.
- [x] Compare the selected backend source to the deployed source and enumerate all production-runtime differences. If the revision cannot be established or the delta includes unrelated unreviewed work, stop candidate selection; do not treat the old rehearsal deployment ID as sufficient provenance.
- [x] Build the selected backend distribution locally using `:server:installDist`; package only `distribution/`, a release-context copy of the existing server Dockerfile and Railway config. Pin that copy's FROM to the currently verified JRE repo digest without altering its runtime flags or the repository Dockerfile; record this packaging-only delta and the resolved base identity. Record immutable file hashes and image identity. Tests/coverage must cover that selected source; reuse the 995/995 merged-source evidence only while runtime inputs/source remain identical. Run affected checks for any new packaging/code changes.
- [x] Conditional CI gate assessed: no push/CI requested or authorized in Task 2, so none executed. If subsequently requested and authorized, use full `workflow_dispatch`, `run_android_ui=true`, once. Give the owner the link and wait for their report before checking results. Current local-merge authorization alone is not a new push instruction.
- [x] Prepare one concrete production authorization request containing exact image/artifact hashes, production target/new service, volume capacity, resources, private variables, projected Redis cost and any backend resource change; no secrets in the request. Include the preflight test resource/time/budget proposal, 5-minute warning/up-to-15-minute natural drain, a proposed 10-minute backend maintenance window after drain and 15-minute attended observation. If that window expires, keep maintenance and report the incident rather than blindly reopening or extending resource scope. Include manual backup access/retention and rollback quota consequences. Make acceptance of the backup operator/interval explicit.
- [x] Record the backup-before-update gate from [production policy](../../production-deployment.md), also applied in the previous Railway backend rollout. A current production DB backup/checksum/format verification receipt is required before backend replacement. The policy documents isolated restore drills separately; this rollout additionally proposes a local isolated restore check, following the previous Railway release, as an explicit scope to approve rather than a per-update mandate quoted from that policy. Current Task 2 does not read the DB or any existing encrypted archive. If a new backup is needed, prepare a separate concrete grant for full FastToWin application DB data (accounts/sessions, profiles, wallets/transactions, gameplay/clans and related application records), one temporary account SSH key if required, DPAPI CurrentUser destination `.artifacts/redis-production-rollout-20261102/private/production-v69-pre-redis.dump.dpapi`, retention 14 days, isolated local restore verification and key/restore-fixture cleanup. No automatic production DB restore is authorized. Historical V69 receipts are provenance, not a fresh backup gate.
- [x] Keep the package blocked until that authorization arrives. A previous cloud-test SSH grant is not valid for production. If an account SSH key becomes necessary, list its fingerprint and exact Redis-only access, encrypted destination/retention and revocation; ask for that access before registering or using it. Proposed authorized backup destination is `.artifacts/redis-production-rollout-20261102/private/redis-aof-<UTC timestamp>.tar.gz.dpapi`, DPAPI-protected for the current Windows user, maximum 14 days; timestamp is generated at backup time. Redis hashes/quota are security data and still require explicit backup access approval.

### Local package result — 02 November 2026

Local build and offline image inspections are complete. Independent task review, its scoped re-review and the final integration review found no remaining Critical/Important defect in Tasks 1–2 local preparation. The backend ZIP and all 178 context files match their frozen manifest, and all 176 in-image distribution files match the context. Backend inspection runs as UID 100 on Java 17; no app or database was initialized. Redis image scripts match the reviewed Task 1 context; its running privilege-drop proof remains the Task 1 evidence, not the Task 2 shell override. Owned inspection containers are absent. Local image IDs and exact checksums are recorded in [the release preparation summary](../../redis-production-release-preparation-2026-11-02.md) and the ignored Task 2 manifest.

Current production metadata and masked variable names were observed read-only; no value map or production database was read. The package remains blocked for provisioning/cutover until separate authorization and the proposed preflight/backup/rollback gates are met. No cloud upload, service creation, SSH key, production data backup, push, merge or CI action occurred. The current backend custom Firebase start command must be preserved. Tasks 3–5 below remain unexecuted.

## Task 3: Authorized provisioning and backend cold preflight

**Consumes:** Exact production grant and frozen artifacts. **Produces:** Guarded new service/volume IDs, sanitized actual-resource startup/ACL/latency evidence and a go/no-go record. No game cutover occurs in this task. The owner approved provisioning and synthetic preflight; the backup/isolated-restore step below is explicitly deferred until separate data/access authorization. A passing preflight does not supply the missing backup gate or permit cutover.

- [x] Recheck target identities/current backend deployment immediately before creation. Create exactly the approved Redis service/volume in the production environment/backend region. Set the proposed config/resources and independent credentials; keep public domains/TCP proxies absent. Record all created IDs as owned production resources, **not disposable rehearsal resources**.
- [x] Verify runtime AOF/noeviction/maxmemory, mount, auth/default-user denial, namespace boundary, UID and sleep/restart settings. UID 999 was directly inspected. Everysec is supported by the INFO-reported configuration file in use matching the exact frozen bootstrap content; it is a qualified inference, not CONFIG GET. Keep this evidence limitation visible and keep the game backend on memory mode.
- [x] Use only the explicitly authorized temporary preflight service, with the selected backend JRE/dependency layer and **actual backend CPU/memory limits**, default JVM settings and production app ACL. Run three fresh JVM cold opens without a prior warm connection; each must pass the existing 2-second startup gate. Record actual deployment resource metadata alongside each timing. Do not use prior-image redeploy metadata or warmed startup as proof.
- [x] Exercise actual application EVAL/TTL/quota using UUID-isolated synthetic bucket identifiers beneath the exact stable production namespace/app ACL; allow their bounded TTLs to expire. The bootstrap supports one app namespace, so this uses the reviewed ACL without a fourth account or a namespace change; do not read player keys or clear keys to repair measurements. Do not load player keys or import rehearsal data. Run bounded load at no more than 20 consumes/s for 10 seconds, record p99 target below 100 ms and zero unavailable results. Do not run OOM, shutdown or fault injection on the provisioned production Redis.
- [x] Conditional startup/latency stop gate assessed: both passed within the approved limits, with no automatic configuration change. If subsequently either fails, remain on memory mode and stop the rollout. Do not increase timeouts, enable global IPv6 preference or auto-resize the backend. Present the measured resource/configuration change and incremental cost for a separate owner decision; if no viable preflight is approved, retain/release the newly provisioned Redis only under its approved cleanup scope.
- [x] One authorized synthetic AOF backup and isolated local restore passed, including encrypted archive/hash/file-set checks and recovery of the exhausted quota. Owner accepted the manual operating arrangement: send a backup request daily at 18:30 Viet Nam time and follow the reported result; assistant executes attended runs on request with authorized access. First requested run is 03 November 2026 at 18:30, before the 24-hour completion deadline of 19:51. No production restore or automatic daily schedule exists.
- [x] Remove temporary preflight resources by their owned IDs, retaining the approved production Redis and its volume. Revoke any one-operation SSH key at the end of the approved access period. Save a no-go/go checklist; `/health` alone does not prove Redis remains available after startup.

### Authorized subset result — 02 November 2026

Synthetic production preflight and independent task review passed. Retained Redis service is `a0d91efb-205d-4250-80f4-4b6d218afc6d`, deployment `e9b49229-5e77-4f87-95aa-6aec62ff353c`; its SFO `/data` volume is `3d5db918-8ffa-47ba-8677-3d81f3dc6a99` (5,000 MB). Current retained service limits are 0.25 vCPU/250,000,000 bytes; it is one replica, sleep off and private only. This is an ongoing authorized resource; actual billing was not measured.

Three distinct fresh JVM deployments opened in 587/1,327/787 ms at the actual 8-vCPU/8-GB probe limits. The bounded load produced 198 allowed requests, zero limited/unavailable, p99 7.205 ms. One-token quota, outside-namespace denial and default-user denial passed. Monitor INFO reported AOF enabled, 64-MiB maxmemory/noeviction and aggregate zero-to-two expiring synthetic keys; this is not individual-key TTL or live appendfsync proof. Probe `fa9f65c0-a835-4e23-b50d-a2dff713cb82` was deleted at 19:34:08 Viet Nam time, before its 19:58:32 deadline. Parent read-only assertions independently confirmed absence, retained resource settings and unchanged backend/Web/DB deployments. No game traffic switched over.

**Status at the initial preflight handoff:** direct inspection and backup/restore were still unapproved; they were subsequently authorized and completed as recorded below. Do not repeat creation or the completed synthetic preflight when resuming; use the retained IDs and finish only the remaining operator and release gates under their proper authorization. Evidence and costs/limitations: [preflight handoff](../../redis-production-preflight-2026-11-02.md), ignored `task3/parent-final-verification.json`, `parent-evidence-check.json` and the sanitized result manifest.

### Redis-only inspection and backup result — 02 November 2026

The owner separately authorized the exact temporary account SSH key and Redis-only inspection/one AOF backup/local restore. Live Redis UID is 999. The configuration file reported by INFO server was `/run/ftw-redis/redis.conf`; its SHA-256 matched the exact frozen bootstrap here-document, including `appendfsync everysec`. Existing ACLs and deployment were unchanged. This is qualified file-in-use/startup evidence, not a successful CONFIG GET and not authority to expand ACLs or restart Redis.

The reviewed backup helper ran once. Its 2,544-byte archive passed checksum, manifest/file-set, path/type and size validation. The local retained copy is DPAPI CurrentUser encrypted; its SHA-256 is `a8ec3692d1bf70a9ba4b668a76bde94e2490ba043c169a5b54b91244a8ccab9d`. Retention ends no later than **16 November 2026 at 19:51 Viet Nam time**. No automatic deletion or recurring backup scheduler was created. The owned remote temporary archive was removed after verified transfer.

Restore used the pinned Redis image, fresh local credentials and no external network. The archived UUID synthetic quota remained exhausted, and a fresh control bucket was allowed. Owned local containers, volumes, plaintext key, transfer archive and restore staging directories were independently verified absent. Key `3ccd5e00-8dc1-43ca-b250-313a27cd4fbe` was revoked; a complete account-key metadata query independently confirmed both ID and fingerprint absent. The read-only local key ACL initially prevented deletion; access on that owned file was restored and it was immediately removed. No key remains registered from this operation.

Independent backup-scope review found no findings. Sanitized evidence is under ignored `task3/inspection-backup/`, including `parent-verification.json`, `parent-key-revocation.json` and `parent-local-cleanup.json`; the detailed report is `task-3-backup-report.md` in the evidence root. No Postgres access, current backend/Web deploy, production restore, ACL change, Redis restart, CI, push or merge occurred. **Status at the inspection handoff:** operator acceptance was pending then; the owner subsequently committed to daily 18:30 chat requests as recorded below. Cutover still requires the separate current DB-backup/release grants. Keep the plan workspace and resume only the missing release gates.

## Task 4: Planned cutover of the single backend

**Consumes:** Passing actual-resource preflight, backed-up Redis, approved maintenance window and frozen backend artifact. **Produces:** One backend running Redis mode, matching production Redis/namespace, plus observed recovery checks.

- [ ] Verify the separately authorized current DB backup/checksum/format receipt is complete, plus an isolated restore receipt if included in the approved scope, before any backend replacement; if absent, remain on the existing backend even when Redis preflight passes.
- [ ] Recheck live backend identity, rollback artifact and zero unrelated staged variable/deploy changes. Confirm one replica and Redis ready. Capture saved/effective maintenance policy and trusted runtime active-match count through the existing Owner admin UI/API; follow [durable maintenance contract](../../admin-maintenance.md).
- [ ] Start planned maintenance with **5 minutes warning and up to 15 minutes drain**, bilingual update messages and an advisory end. Wait for the runtime to observe the saved version, block new work and report zero active/pending settlement work. If drain cannot finish naturally, stop and ask before extending or aborting paid/ranked matches; no forced abort is authorized by the normal cutover plan.
- [ ] Once maintenance is effective and drained, stop the one backend, then stage only these Redis variables with the exact new private endpoint/secret: store `redis`, namespace `prod_main`, one URL source. Preserve every other backend setting. Deploy the frozen backend artifact; no Web or database deploy.
- [ ] Verify the new deployment is the approved image/revision, effective resource limits/replica count and successful health startup. Confirm finite Redis shared counters on the **existing protected** `/internal/metrics`, default Redis user denied, runtime connection healthy and no credential-bearing log. Never expose metrics publicly to simplify a check.
- [ ] While maintenance is still enabled, verify status/admission and independent non-business synthetic quota; maintenance can reject game entry before hitting a quota, so do not call that proof of a game route's Redis wiring.
- [ ] Open maintenance through the normal persisted policy flow, then perform one approved QA login, WebSocket connection and harmless room create/join/leave. Confirm shared allowed/limited counters change and any real denial remains 429; do not intentionally exhaust a real user's login quota. No purchase, gift, donation or wallet adjustment belongs to this check.
- [ ] Observe for **15 minutes** during the attended window: Redis unavailable deltas, CPU/memory, volume/AOF growth and entry errors; preserve existing room/match behavior. Any OOM/eviction, repeated unavailable outcomes, crash loop, unexpected replica or missing persistent mount closes the success gate and enters Task 5.

## Task 5: Incident decisions, completion and handoff

**Consumes:** Recorded prior configuration, durable maintenance and cutover observations. **Produces:** An explicit success or incident handoff with remaining resources/costs and backup responsibility.

- [ ] On a failure before cutover, keep the existing memory backend and report preflight failure. On a failure after cutover, announce/drain maintenance where possible, stop new entry work and diagnose Redis/config/network without replaying business operations or clearing data. Maintenance admin availability can itself be affected by shared admin-login limits, so an already-authorized Owner session and Railway control access must be available before cutover.
- [ ] Reapply a prior backend image/config only after confirming it is compatible with the unchanged application DB and explicitly approved rollback scope. Redis-to-memory creates fresh local quota; namespace changes/Redis reset/old AOF restore change quota. Obtain the owner's explicit choice before any of those, including the expected burst/security effect. Do not automatically restore the DB, reset Redis or claim memory fallback preserves the quota.
- [ ] Manual production restore requires a stopped/drained backend, separately approved Redis-data read/write and key access, an archive with verified provenance/hash and a preserved current AOF copy. Validate exact regular files/size/manifest in an isolated fixture first; keep maintenance if any rename/applied-marker step fails. No production restore is authorized simply because the rehearsal passed.
- [ ] At success record deployment/image IDs, namespace (not URL/password), actual resource limits/cold timings, relevant counter snapshots, backup hash/location/retention/due time/operator and remaining cost responsibility. Verify original Web/DB/staging and one backend replica are unchanged; record SSH key revocation and absence of temporary preflight resources.
- [ ] Update `docs/redis-rate-limit.md` and create a dated production handoff only after real cutover evidence exists. Keep #12 open for presence/remaining local limits and #15 open for distributed ownership. Do not post issue updates or close issues without authorization to publish them.

## Original planning-time self-review (historical)

- Runtime/provider/cloud constraints map to Tasks 1, 3 and 4; backup/restore limitations and owner permissions map to Tasks 1, 2 and 5.
- All future files have an owning task; current snapshot/source and future effective deployment metadata are distinguished. Local master integration is complete; push/CI/provisioning/cutover are separate gates.
- At the time this plan was written, production assets were not yet implemented; the plan does not treat the expiring test bootstrap, test-only inspector or managed Hobby backup as production-ready.
- No production change, cloud lookup, SSH registration, CI dispatch/inspection or scheduler was performed while writing this plan. At that time, the next reviewable scope was **Task 1 local preparation only**; authorization for the actual release comes after exact artifacts and the Task 2 package exist.

Current status: Tasks 1–2 and the authorized Task 3 provisioning, synthetic preflight, Redis-only inspection and one-shot backup/isolated restore have passed review. Task 3 manual operator/first due time was subsequently accepted: Owner sends daily 18:30 Viet Nam chat requests and follows the result; first requested run is 03 November 2026 at 18:30, with the current maximum completion deadline 19:51. This is an attended manual arrangement, not an automatic schedule. Tasks 4–5 and game cutover still require their separate DB/release grants and execution gates. No game traffic has switched to Redis.
