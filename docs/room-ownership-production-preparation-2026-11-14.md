# Room ownership production preparation — 14 November 2026

Issue: [#15](https://github.com/ThaiXuanHien/FastToWin/issues/15). Production is unchanged. The package is ready for final Owner scope review; it is not deployment authorization. No issue was closed, no production service was created, and no deployment, admission binding or business-data write was performed during preparation.

## Frozen release and accepted evidence

- Backend source `cee03e9a3accf5b7eab8205a9a4773e04715705a`, JAR `129cf6678b9ed8695e1173fc6ec7eb6ed785496905157c50a4cc44936204275b`, runtime candidate `18e2b13791d26389e90fd044105e15c3c278663d3f5ffdb76b728c24bf6744a7`.
- R14 Cloud Topology scope `7abdefa8b8da92f224c13202f69157da65f4470a55d96c51bd2266b641489c8a` is accepted. It covers private B replacement/DNS, ingress interruptions and deadline recovery, account lanes, presence and signed drain. Full cloud Load remains deferred by the Owner; the earlier R10 dedicated Fault receipt is not current-candidate acceptance.
- Current production image passed all12 local PostgreSQL initialization/runtime cases, retaining40 synthetic users/profiles and STOP_NEW until a separate signed local resume. All temporary resources were removed.
- Retained current-production distribution was checked against the historical deployment/receipt/checksum manifest, rebuilt with a pinned JRE, and passed a13-case local run including legacy startup against V74, retaining40 users/profiles and unchanged policy. Recovery uses a frozen-context re-upload. The exact Railway provider image was not pulled.
- Sender changes passed14 renderer/launcher/context tests. Real pinned Alloy validated the config and shipped distinct A/B metrics plus Redis into an isolated Prometheus receiver; unknown metric names/labels were absent. Temporary containers, network and volumes were removed. Existing ownership alert-rule tests passed in promtool. This is local acceptance; live Grafana shipping and production alert installation are not claimed.

## Production identities and backup

Read-only Railway metadata pins six existing services and three existing volumes/attachments. Backend deployment is `1774934a-45c6-43b2-af30-0463acd9130e`; Web remains `e7ee8ca3-13d6-4c81-a894-ed687b4944ee` and forwards only to `server.railway.internal:8080`. Redis rate-limit/presence namespaces remain `prod_main`. No worker exists yet. The existing metrics sender WAL volume is `dd784402-7c69-4dc3-a30f-f75b512e53cd`, attached at `/var/lib/alloy`; preserve it.

The replacement PostgreSQL-only access scope `8a81c89b70930346779f87fbe7bec71a607c39aafd90eaf915d58464af778d88` completed within its approved window. At18:52, the read-only consistent PostgreSQL18 custom dump was encrypted with CurrentUser DPAPI and an owner-only ACL, then restored in an isolated local cluster with unchanged V69/Flyway history. No application started or migration ran on that restored production data. Ciphertext SHA-256 is `cc1448ea01be23203aa92fd65b73e54ddff503c371c78d221a3050d865766ed0`. The backup was not quiesced; cutover must recheck its24-hour freshness gate and planned maintenance.

Delete the archive by **28 November2026 at18:52:05 Viet Nam time**. Decryption requires the same Windows user's DPAPI context. No automatic retention job was created. Temporary key revocation occurred before local restore; independent Railway inventory confirmed zero keys. Local key files, restore cluster/password/logs/listener were verified absent. The first access attempt had failed at backend SSH binding; it was reconciled and its key removed before the replacement. The later binding verifies Railway configured DATABASE_URL, not the failed backend runtime environment; final rollout must verify runtime binding.

## Proposed initial rollout

Use one public ingress A and one private B, one replica each, no overlapping A processes. Keep A's existing8vCPU/8GB ceiling and pool4; add one B service capped at3vCPU/1GB and pool4, no public domain/TCP proxy/volume. Both listeners explicitly use SERVER_HOST=::. B receives only its database and ownership runtime settings. The new production worker remains after a successful rollout and incurs ongoing usage charges until a later authorized change or rollback.

Owned admissions are limited to verified active `hienqp` and `Play Reviewer`, private CASUAL/ORDER1v1. Both belong to one clan, but currently have no accepted friendship; establish it through normal app QA before friend-push checks. The actual release classes verified identical A/B policy/topology bindings and routes: hienqp creates on A, Play Reviewer creates on B. All other accounts retain legacy routing.

Update only the existing metrics sender image and its paired private backend-target variables. Preserve its token, destination, volume/WAL and Redis pipeline. Local behavior without backend targets matches the prior rendered Redis configuration byte-for-byte. Live Grafana series must be verified in the attended window; metrics shipping does not install Grafana alert rules. Codex observes the existing alert conditions during the window; an unverified cloud stream does not count as accepted or authorize unattended ownership operation.

## Exact scope for final approval

Proposed window: **14 November2026,20:00–22:00 Viet Nam time**. Latest start20:30; accept or begin rollback by21:15. Operator: Codex in this chat with Owner attending console and Android/Web QA. Temporary key `ftw-prod-ownership-20261114-r1` is prepared locally under owner-only ACL and DPAPI, not registered. Its maximum window is20:00–22:00; revoke by22:00. Railway provides no provider-side key TTL; the key technically reaches services available to the account, while commands are limited to approved A/B/PostgreSQL operations.

Final ignored package: `.artifacts/room-production-release/20261114-cee03e9a-draft/`. Review `deployment-runbook.md`, `rollback.md`, `qa-checklist.md`, `manifest.json`, `monitor-verification.json` and `rollback-recovery-verification.json`. Final scope SHA-256: `e163f2d8663cdb449aa9d154ca9fd61916a4535b2e0e9e14c782d51653a9d4cd`. Its45 input hashes, frozen backend/rollback distribution hashes and encrypted backup freshness passed the read-only package verifier; eight negative/approval/window gate tests passed. An external Owner receipt must bind this immutable scope; approved=false is never edited to bypass the hash.

The runbook requires planned global maintenance, fresh effective policy, zero aggregate active/pending/prepared work and WebSocket metrics before stopping old A. Startup migrates V69→V74. Native initialization binds the exact fresh unbound STOP_NEW row without seeding or automatic ACTIVE. Signed current-process status precedes explicit resume. QA must exercise both owners, reconnect, terminal/settlement behavior and friends/clan presence, followed by at least15minutes of live observation. Production fault injection is excluded. Any failed gate follows signed stop/drain; incomplete zero-work proof retains routes/STOP_NEW/maintenance, never a forced abort or journal deletion. Recovery preserves V74 and does not restore production DB, reset Redis or recreate volumes.

Production QA, final Owner execution approval and issue closure remain pending.

Final review found no remaining blocker after fixing scope-object versus file binding, with a red/green regression. The production recovery procedure now explicitly stops both A/B by journaled deployment identity after completed zero-work proof and keeps B stopped in legacy recovery.
