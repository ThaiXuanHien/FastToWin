# Redis presence production handoff

This document is the local handoff for enabling Redis-backed presence in the Fast To Win backend. It contains no credentials and does not authorize a production mutation.

## Current implementation

- `RedisPresenceStore` uses the dedicated presence ACL role, Lua renew/read scripts, 200 ms operation budgets and a shared connection handshake.
- Redis bootstrap creates `ftw_presence` only when `FASTTOWIN_REDIS_PRESENCE_PASSWORD` is present. The role is restricted to `ftw:<namespace>:presence:v1:*` and cannot read or write rate-limit keys.
- Production roster reads fail closed when Redis returns unavailable. Friend and clan responses are omitted until a later read succeeds; the engine does not fabricate `ONLINE` from local memory in production mode.
- `/internal/metrics` exports `fasttowin_presence_redis_failures_total` with `operation="read"` and `operation="write"`.

## Runtime configuration

Redis service:

```text
FASTTOWIN_REDIS_PRESENCE_PASSWORD=<96 lowercase hexadecimal characters>
```

Backend, using one URL source only:

```text
FASTTOWIN_PRESENCE_STORE=redis
FASTTOWIN_PRESENCE_NAMESPACE=prod_main
FASTTOWIN_PRESENCE_REDIS_URL=redis://ftw_presence:<secret>@rate-limit-redis.railway.internal:6379/0
```

Do not put the presence secret in source, logs, command output or the rate-limit role. Keep `FASTTOWIN_PRESENCE_REDIS_URL_FILE` as the alternative secret channel if the deployment platform requires file-backed secrets; never set both URL forms.

## Production preflight

1. Re-read the current Redis service, volume, backend service and private DNS metadata. Historical IDs are comparison points only.
2. Create a fresh encrypted Redis/database backup and complete a local restore check with checksums.
3. Verify the exact backend image/source revision, migration head, Docker base digest and runtime manifest.
4. Check Redis memory, evictions, AOF enabled/current/base size, fsync and rewrite state. Confirm the rate-limit namespace is unchanged.
5. Drain the backend through the existing maintenance path. Do not restart the shared Redis volume while active work or pending settlement remains.
6. Restart Redis with the bootstrap above, then verify AOF recovery, `ftw_app`, `ftw_presence`, a quota probe and a synthetic presence lease.
7. Deploy the backend with the three presence settings and verify health, `presenceRefreshSupported`, metrics and logs.
8. Run two-account QA for friend/clan online, room and playing transitions, socket replacement and offline expiry. Observe at least 15 minutes.

## Rollback

If only presence fails while rate limiting remains healthy, drain and restart the backend with:

```text
FASTTOWIN_PRESENCE_STORE=memory
```

Do not reset quota keys, change the rate-limit namespace, restore an old AOF over live data or revoke the presence role until the incident receipt records the decision. Existing Redis leases expire within the configured 60-second TTL.

## Local verification

The local suite covers Redis configuration, engine fail-closed behavior, WebSocket presence behavior and metrics wiring. A fresh Redis ACL/restart fixture passed **90 checks** with the pinned Redis base image, including role isolation, Lua renew/read/revoke, AOF restart, quota preservation, backup checksum and secret redaction. Receipt: `.artifacts/redis-presence-production-20261104/acl/final-20261111.json`; context hashes: `.artifacts/redis-presence-production-20261104/acl/final-20261111-context.sha256`; Docker resources and the private fixture directory were removed. Redis integration tests requiring `FASTTOWIN_TEST_PRESENCE_REDIS_URL` still need an isolated fixture when run through Gradle; skipped fixture tests are not production evidence. Production enablement still requires the fresh backup, metadata, outage rehearsal and QA receipts above.
