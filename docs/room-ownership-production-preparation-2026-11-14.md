# Production preparation checkpoint — 14 November 2026

Issue: [#15](https://github.com/ThaiXuanHien/FastToWin/issues/15).

The current production image remains a draft. No production deployment, policy change, service creation or database write was performed in this preparation.

- Candidate code: `cee03e9a3accf5b7eab8205a9a4773e04715705a`; server JAR `129cf6678b9ed8695e1173fc6ec7eb6ed785496905157c50a4cc44936204275b`; runtime candidate `18e2b13791d26389e90fd044105e15c3c278663d3f5ffdb76b728c24bf6744a7`.
- R14 Cloud Topology acceptance is retained under scope `7abdefa8b8da92f224c13202f69157da65f4470a55d96c51bd2266b641489c8a`. Full cloud Load is deferred by the Owner; it is not a passed gate.
- The exact production image passed all 12 local PostgreSQL initialization/runtime cases. Forty synthetic users/profiles were retained; STOP_NEW remained authoritative until a separate signed local resume. All owned containers, networks, images and volumes were removed.
- The first helper compilation mistakenly used Java25 bytecode while the pinned runtime is Java17. That attempt failed before tests and was cleaned. The accepted receipt follows recompilation with `javac --release 17`.
- Fresh Railway metadata at `2026-11-14T11:22:07.624Z` shows the existing six services and old backend deployment `1774934a-45c6-43b2-af30-0463acd9130e`. Web still forwards to `server.railway.internal:8080`; backend pool size is4; Redis rate-limit and presence namespaces remain `prod_main`. No worker B exists yet.
- Proposed first rollout: keep the single ingress A and existing resources; add one private `owned-worker` B, one replica, maximum3vCPU/1GB, pool4. Limit owned admission to the two existing verified QA accounts, private CASUAL/ORDER 1v1. Exact IDs and routing node IDs are pending fresh database verification. No general-audience admission is proposed while full Load is deferred.

Ignored evidence is under `.artifacts/room-production-release/20261114-cee03e9a-draft/`: `manifest.json`, `production-inventory-current.json`, `configuration-*-current.json`, `configuration-proposal.json`, `migration-inventory.json`, `initialization/initialization-manifest.json`, and `runtime-smoke/postgres-test-4834d784cb77.json`.

The PostgreSQL service has no public TCP proxy. Railway currently has no registered SSH keys. Prior 60-minute access was consumed/expired. A separate reviewed access package is prepared under `access-preparation/` for exact QA mapping, a read-only consistent pg_dump, CurrentUser-DPAPI encryption with owner-only ACL and maximum14-day retention, isolated local PostgreSQL18 restore, and immediate SSH-key revocation before restore. This package does not deploy, mutate production business data, seed accounts, reset Redis or authorize database restore on production. Railway keys have account-level service reach and no provider-enforced TTL; the attended helper enforces a60-minute budget with cleanup reserves. No key is registered and no execution grant exists.

Remaining before final production approval: finish QA identity mapping and backup/restore; bind topology/policy hashes; finalize private monitoring for both backend nodes (the current cloud sender covers Redis), old-image rollback evidence, maintenance window, rollback deadline and operator. Production acceptance and issue closure remain pending observed execution evidence.
