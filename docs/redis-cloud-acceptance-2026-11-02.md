# Redis cloud acceptance — 2 November 2026

The isolated Railway rehearsal passed the exercised rate-limit, fault, persistence and manual AOF recovery checks. **Cold startup qualified only at a 2-vCPU runner limit**; actual 0.5/1-vCPU trials failed the existing 2-second startup gate. This is not production rollout approval or a capacity/HA certification.

## Scope and authorization

The owner explicitly authorized upload to two new services in FastToWin Staging, test data only, at most two hours and a target below $1, followed by cleanup. A later explicit grant allowed one temporary account SSH key solely for the owned Redis test volume's backup/restore. No production credentials or player data were used. Production deployments and replica counts were unchanged; the four original staging services remained off. No CI status or logs were inspected.

Both test services used SFO and private networking, with no public domain or Redis TCP proxy. Transport was Railway private WireGuard, **not Redis TLS**. Redis had a 0.25-vCPU/250-MB container limit, 64-MiB maxmemory, `noeviction`, AOF enabled and `appendfsync everysec`. The runner had a 1-GB memory limit; its CPU limit progressed from 0.5 to 1 to 2 vCPU. Runtime limits remained 2 seconds for startup, 200 ms per command and 128 configured pending operations.

## Results

| Check | Observed result |
| --- | --- |
| Functional runner | 13 cases passed, none failed or skipped; two independent loopback Netty applications shared the remote Redis quota; all 18 entry buckets and namespace isolation exercised |
| Fault handling | Real hold/drop/malformed responses, cancellation/overload and disconnect/reconnect; no RAM fallback or replay; new entry actions unavailable during outage while an existing match continued |
| Short baseline | 10 seconds at a configured maximum 20 consumes/s, 198 actual calls, 198 allowed, zero unavailable; p50 2.531 ms, p95 6.933 ms, p99 12.090 ms |
| Concurrency measurement | Load issued at most one call at a time; 128 is the configured pending limit, not measured SDK occupancy; functional overload is a separate bounded burst |
| Cold startup | Actual 0.5-vCPU samples 6,599/4,218 ms and actual 1-vCPU sample 3,906 ms failed; 2-vCPU sample 1,142 ms passed; a fresh default-JVM sample passed at 945 ms |
| OOM/noeviction | Bounded test filled only owned keys; rejected writes returned unavailable, existing quota keys survived, maxmemory was restored and a new request succeeded |
| AOF restart | Baseline marker and an exhausted 24-hour bucket survived redeploy and a real same-container restart; bucket remained denied |
| Managed Railway backup | **Not run:** API rejected manual/scheduled backups on Hobby as Pro-only; no plan upgrade or managed snapshot was created |
| Manual AOF backup/restore | Fenced, fsynced, bounded write-paused archive downloaded and hash-verified; post-backup marker mutation failed the baseline check as expected; restore on the same exclusive test volume recovered the baseline and exhausted quota |
| Restore idempotence | After restore, marker was changed again and Redis restarted with the restore tag still set; changed marker survived, proving the backup was not applied twice |

The functional phase used a temporary IPv6-preference JVM flag during diagnosis. That flag was subsequently removed; default-JVM cold startup and separate persistence operations passed. The 13-case functional phase was not repeated after flag removal. No global IPv6 JVM setting is proposed for production. Functional `startup_ms=58` is warmed and must not be presented as the cold result.

Deployment resource metadata matters: redeploying a previous image reused its older CPU metadata even after the service override changed. A fresh upload established the actual 1/2-vCPU trials. The apparent 6,057-ms “1 CPU” attempt was still limited to 0.5 CPU and is excluded from the 1-vCPU evidence.

## Backup boundaries

The owner-approved alternative used a manual AOF archive, not a Railway managed snapshot or a fresh-volume recovery. A separate inspector credential performed a same-connection fence/`WAITAOF`/bounded write pause. The archive contained only the AOF files and checksums, no ACL or credential configuration. Restore validated exact regular filenames and hashes, preserved the previous AOF directory, and used a one-shot marker. The local transfer ran with Python `-I` so its assertions remained enabled.

The archive was 14,271 compressed bytes with three data files and 242,773 uncompressed data bytes. SHA-256: `38ef61e094c6c9c47e8aa94fcf193d3cbcb3fda16cf974d0d25e2735060f488a`.

AOF everysec can lose recent writes on crash. An older backup can reset or over-restrict quota. The manual directory swap is not an atomic HA mechanism; a crash between renames and the applied marker can require manual recovery. Managed backup, fresh-volume restore, TLS, failover and distributed game ownership remain unqualified.

## Resources and cleanup

The rehearsal finished in **76.2 minutes**, before its original two-hour deadline. Railway's sampled Redis container memory peaked at 39.81 MB; test volume usage peaked at 86.654 MB. Railway Hobby allocated a 5,000-MB volume and did not support downsizing; test data stayed below 1 GB. These are short test observations, not production sizing guarantees.

A conservative full-limit compute estimate was **$0.1014**, excluding build, network, storage and invoice settlement. It is not a final bill or a provider hard spending cap. Workspace usage is shared and cannot be treated as this rehearsal's cost.

Cleanup was verified at `2026-11-02T04:48:07Z`:

- Both owned test services were absent; staging had zero active deployments and its original services/Postgres volume were preserved.
- The temporary account SSH key was revoked and absent from the account; generated local credentials and key files were deleted. Hobby remained the active plan.
- Deletion of the test volume was acknowledged, detached and marked pending. Railway reported deletion scheduled for `2026-11-04T04:45:31.369Z`; **physical deletion was not yet verified**. Residual storage may remain billable until provider deletion.
- The small test-only AOF archive and sanitized evidence remain local in the ignored artifact directory. Production deployment IDs and replica counts matched the before snapshot.

## Reproducibility and evidence

Application runner revision: `67d11901bae53b1df3fb8f0497e561360a2956cf`. Its exact 203-file manifest SHA-256 is `305c199a3568aa43513ecbad713f266194f178c230ce9dddae7c86ff63cf118d`. The application bundle remained unchanged; cloud operations used separately reviewed helper artifacts:

- Probe manifest: `7c3f2c154698ef1e7e421fe853641b0b831dcc99d87506d99c13b9869d429604`.
- AOF helper manifest: `ad8dc23ec8482f6508a99e8e28881faed578ac6c83851cd7347e4c7e05ab8c78`.

Ignored evidence root: `.artifacts/redis-cloud-acceptance-20261102/cloud/`. Key files: `phase-functional-reports.json`, `phase-inspect-{2,4,6}-reports.json`, `phase-default-jvm-inspect-reports.json`, `phase-oom-reports.json`, `phase-aof-verify-after-restart-reports.json`, `aof-backup-verified.json`, `phase-pre-restore-negative-reports.json`, `phase-restore-verify-reports.json`, `restore-idempotency-pass.json`, resource summaries, `cleanup-verified.json` and `account-final-sanitized.json`. Full service/deployment identities are in the ignored `target.json` and snapshots.

The Pro-only API rejection and actual 1/2-vCPU deployment metadata were observed directly during execution and recorded in the execution notes; their raw API responses were not retained as standalone files. The upload records identify those fresh deployments and the saved phase JSON retains their timings. This limits independent offline replay of the resource/plan checks.

## Decisions retained from execution

1. Keep the reviewed application bundle unchanged and add a separate operations probe in the same test runner service. This avoids broadening the application's ACL; if wrong, the cost is additional test packaging/verification work.
2. Accept Hobby's default 5-GB capacity while bounding actual test data below 1 GB and retaining the original time/budget controls. If wrong, the cost is additional allocation headroom/metadata storage; this grants no budget increase.
3. Qualify a runner limit up to 2 vCPU after lower limits failed, retaining all failures and the existing timeouts. If wrong, the cost is bounded extra test compute and results that do not transfer to lighter backend limits.

## Next gate

Prepare a separately reviewable production change: same-region Redis, exact image/configuration, restricted credential and namespace, backup/recovery operating procedure compatible with Hobby, cold-start validation at the actual backend resources, monitoring, drain and rollback consequences. Provisioning and switching production need separate owner authorization. Keep memory mode and one production backend meanwhile. Presence in #12 and distributed room/match ownership in #15 remain separate unfinished work.
