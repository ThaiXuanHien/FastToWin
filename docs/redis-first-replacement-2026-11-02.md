# Redis: proposed first replacement of the old backend

Status: **local preparation, not production authorization**. This procedure is a proposed amendment for replacing the old backend once. The newly fixed PLANNED behavior only applies after the corrected backend is deployed; it cannot protect the old process retroactively. Existing cutover helper actions remain disabled.

## Packages and prerequisite decisions

Use only the separately verified corrected candidate and recovery packages under `.artifacts/redis-production-rollout-20261102/task4/candidate-79d227a/`. Their manifests bind the exact source, context, ZIP and local-image identities. Preserve the original Task 2 artifacts: they do not contain the PLANNED fix and must never be relabelled as corrected.

The recovery package contains the archived production application from `6eb2d2f`, with its application/distribution bytes preserved and the Java base pinned in a new Dockerfile. It is a separate local fallback, not the original Railway image. Local inspection does not prove a production startup or provider-registry pull. Railway's retained-image rollback remains a distinct recovery option, subject to its actual eligibility/retention and configuration checks. [Railway deployment actions](https://docs.railway.com/deployments/deployment-actions), [image retention policy](https://docs.railway.com/pricing/plans#image-retention-policy).

Before any maintenance or backend stop, the Owner must approve the exact corrected upload and choose an explicit recovery scope: a verified retained-image rollback, or the reviewed portable recovery package if that original image is unavailable. The latter must not silently waive the existing image-availability gate. Both recovery options require a deliberate decision about returning to memory rate limits and losing the shared Redis quota history. Neither is an automatic fallback or permission to restore a database.

Recheck the current backend, Web/Postgres/Redis deployment identities, one replica, resource limits, persistent Redis mount, private connectivity, unrelated staged changes and Owner control access. Inspect only masked variable names and approved nonsecret settings; never export the entire secret-variable map. Preserve the existing Firebase start command, health settings and every unrelated flag. The proposed mutation remains the three Redis variables only, with the app credential from its separately authorized source.

The application DB backup from 02 November at 22:43:59 Viet Nam time is a verified snapshot, not standing access permission. Its hash/restore/cleanup receipt must still match and it must be less than 24 hours old at the actual replacement. An expired/stale or changed receipt blocks replacement and requires a newly approved backup operation. Recheck the separate Redis backup/operator prerequisite as well.

## First-window timing proposal

For this old-backend replacement only, propose **5 minutes warning, a 15-minute operational natural-drain budget, and a 60-minute persisted drain limit**. Sixty minutes is the existing API maximum; it is safety headroom for cancellation, not permission to wait an hour for deployment. This differs from the original persisted 15-minute drain setting and must be explicitly included in the future Owner approval. New intent is still admitted during ANNOUNCING and blocked once DRAINING is observed.

Use server-persisted timestamps, not an estimated local countdown:

- Read the saved `startedAtEpochMillis`, `warningMinutes`, `drainMinutes` and `drainDeadlineEpochMillis`; verify the approved 5/60 configuration and current policy version. Derive the warning-to-drain transition as `startedAtEpochMillis + 5 minutes`, and require the persisted drain deadline to equal that value plus 60 minutes.
- Do not use the UI's generic `transitionAtEpochMillis` as the warning-to-drain timestamp after announcement: DRAINING changes that field to the hard drain deadline, and MAINTENANCE clears it. The saved start/settings and separate drain deadline remain the timing evidence.
- The operational cutoff is the warning transition plus 15 minutes. It is normally 45 minutes before the old backend's hard drain deadline.
- Stop waiting and begin cancellation immediately if trusted observations become unavailable/stale, the saved/effective versions diverge, or unexpected configuration appears. Do not wait until the operational cutoff to respond to an error.
- While the operation is attended, make bounded explicit status observations. This is deployment supervision, not a background monitor; do not create an automation or unattended loop.

Require the existing Owner Admin session and Railway control access to remain available for the whole operation. Bilingual public maintenance text must describe an advisory end and possible postponement. Setting an advisory end never reopens the service automatically.

## Before stopping anything

Start planned maintenance through the existing persisted Owner UI flow only after approval. Follow the committed policy to DRAINING. A policy write acknowledgement alone is not runtime application; re-read the saved/effective/runtime observations.

Proceed only when all of the following are fresh and consistent:

1. Saved and effective MAINTENANCE, matching saved/runtime versions, available runtime observation and trusted aggregate work count zero. `activeMaintenanceMatchCount()` includes admitted matches, pending completed results, pending tournament progression and prepared starts.
2. The protected backend-local `/internal/metrics` reports zero WebSocket connections. The Admin UI has no socket-closure confirmation field; its static disclaimer is not proof. Use only separately approved backend SSH for this aggregate read, never expose metrics or copy session cookies.
3. The exact candidate/recovery/backup checks, resource settings, preserved start command and absence of unrelated staged changes still pass. Both the backup and rollback/recovery gates are independent of an empty runtime.

The old backend can reach MAINTENANCE early once its drain is truly empty. Once fully closed and verified, retain maintenance while stopping the old process, staging the exact three Redis variables and uploading the approved corrected package. Do not reopen merely because a health check passes.

## Cancel the first window when drain does not finish

If the operational cutoff arrives with work still present, **cancel the deployment window and disable maintenance through the Owner flow before stopping the backend**. The approval must expressly allow this cancellation/reopening action. Do not escalate to EMERGENCY, force-abort matches or extend the window automatically.

After cancellation, read the persisted/effective/runtime state and verify the newer version is ACTIVE. If a write response is ambiguous, reconcile by reading; do not automatically submit another mutation. A confirmed ACTIVE cancellation leaves the original backend serving and permits planning a different window.

If cancellation or control access cannot be confirmed, do not stop/upload/deploy, and notify the Owner that the saved policy remains unresolved. The old hard deadline still exists: this attended procedure reduces the exposure but **cannot guarantee no abort if management control is unavailable through that deadline**. It must never be presented as the new runtime's indefinite safe-drain guarantee. If the Owner requires that guarantee for the old process under control failure too, this first replacement remains blocked pending another approved method.

## After the approved replacement

Verify the exact runtime files, one replica/resources, unchanged Web/Postgres, health, protected finite shared counters, Redis app connectivity/default-user denial and credential-safe startup logs. Use independent synthetic quota evidence while still closed; a maintenance rejection does not prove game-route quota wiring.

Reopen only through the persisted Owner flow after the postflight gates pass. Perform the approved QA login/WebSocket/private-room checks and 15 minutes attended observation. A normal future PLANNED window can use the corrected zero-work rule; first-window 60-minute headroom is not a new global default.

If failure occurs after fully closed replacement, retain maintenance and report. An old-image or portable-package recovery, Redis-to-RAM quota reset, namespace change, Redis data restore or application DB restore each needs its explicit approved scope; no automatic retry/replay is permitted. Temporary keys must be revoked and verified absent even when a window is cancelled or fails.
