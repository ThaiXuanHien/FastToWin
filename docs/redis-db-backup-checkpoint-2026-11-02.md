# Redis rollout: application DB backup checkpoint

The Owner explicitly approved one attended backup/access/isolated-restore operation, for at most two hours immediately after approval. It completed on **02 November 2026 at 22:44 Viet Nam time (UTC+07)**. This closes the current DB-backup prerequisite only; the backend still uses memory and full Redis cutover remains disabled.

## Verified result

- Confirmed the exact production backend-to-Postgres database binding. PostgreSQL major 18, public schema, Flyway V69 and zero failed migrations matched the approved source.
- Exported the full application DB once using read-only `pg_dump` custom format. It is a consistent snapshot of the running database, not a quiesced snapshot. No application data was written on production.
- Stored only the DPAPI CurrentUser-encrypted archive under `.artifacts/redis-production-rollout-20261102/task4/private/production-v69-pre-redis.dump.dpapi`. The owner-only local archive is 449,686 bytes. No local plaintext dump was written.
- Ciphertext SHA-256: `64882b40ff7931c1f46944faaa3c70268a94b9ae251323ce88230da179ec1fb7`. Parent independently matched it to both backup and restore receipts.
- Archive inventory and isolated local PostgreSQL 18 restore passed; V69/Flyway history was unchanged. No app workers started and no migrations were applied. Owned cluster, password, server/control logs and transient API/key files were independently verified absent; the exact loopback port returned connection refused.
- Temporary Railway account key `7f3e38e9-ebde-4275-9e36-953a4f25a7dd`, fingerprint `SHA256:fehAk7nhpMnXZyRHFXWl1ew7XlQiTb85/hqNAerrl1Q`, had acknowledged creation and deletion. A separate complete public-key inventory confirmed both its exact name and fingerprint absent at 22:45. There was no ambiguous registration or cleanup outcome.
- Backend `86f53b3f-7af5-4a76-8b8d-6a1e4d8f32d7`, Redis `e9b49229-5e77-4f87-95aa-6aec62ff353c`, Web `800d8bc5-51fb-4ca7-82e9-63b26e71a065` and Postgres `a090be65-cd83-4aa6-8a3a-94186431df78` remained SUCCESS with their existing deployment IDs. Backend and Redis remained one replica. No maintenance, deployment, Redis-secret access or CI occurred.

## Retention and evidence

The dump was created at **22:43:59 on 02 November 2026**. Delete it no later than **22:43:59 on 16 November 2026**, a conservatively rounded maximum 14-day deadline. No automatic deletion scheduler was created. Restore/decryption requires this Windows user's DPAPI context. No key remains registered from this operation.

Ignored evidence under `.artifacts/redis-production-rollout-20261102/task4/`: `owner-grant.json`, `backup.json`, `restore.json`, `key-register-ack.json`, `key-registration-observed.json`, `key-revoke-ack.json`, `key-absent.json`, `parent-owned-cleanup.json`, `parent-key-revocation.json` and `parent-post-operation-metadata.json`. Their hashes are bound in `parent-backup-completion.json`. The reviewed helper hashes remain in `parent-backup-preparation-gate.json`; it is a historical preparation receipt and its pending-grant fields do not describe the later execution state.

The one-operation grant is consumed. A later cutover must recheck backup freshness (maximum 24 hours), the unresolved maintenance no-forced-abort procedure, actual rollback availability and its separate access/deployment permission. There is no automatic production restore, quota reset or RAM fallback. Redis manual backups at 18:30 on request remain a separate operating agreement; automatic cloud backups remain deferred.
