# Next room-ownership staging baseline

Status: prepared for review; **not authorized or launched**. Earlier one-attempt Railway scopes have been consumed. Preparing or running the read-only preflight does not approve this new paid execution.

Run one baseline in FastToWin Staging, project `b5c5db71-2df8-46da-84fb-d9b9df6d7951`, environment `8bcf4119-3a6b-49b3-b4ff-8ea20080f3b9`. Fixture prefix: `room-pilot-94b8a75b3ef9`. Maximum lifetime is two hours from execution start, including cleanup; only one full attempt is allowed.

| Temporary service | CPU | RAM (GB) | Use |
| --- | ---: | ---: | --- |
| `room-pilot-94b8a75b3ef9-pg` | 2 | 0.512 | Fresh ephemeral PostgreSQL fixture |
| `room-pilot-94b8a75b3ef9-redis` | 0.25 | 0.25 | Dedicated synthetic Redis namespaces |
| `room-pilot-94b8a75b3ef9-a` | 3 | 1 | Measured backend; database pool 20 |
| `room-pilot-94b8a75b3ef9-b` | 1 | 1 | Private peer bootstrap; pool 20; stopped before measurement |
| `room-pilot-94b8a75b3ef9-runner` | 0.5 | 0.5 | Seed and one 20-room/600-second baseline |

Each service has one replica in the existing `sfo` region. Use 40 fresh synthetic accounts and a marked disposable database. There are no public endpoints, persistent volumes, cloud fault injections, changes to existing services or production operations. The local latency proxy is not deployed to Railway.

The full gate requires 60,000 ACKs, exactly 3,000 per room, at least 95 ACK/s, the unchanged bounded measurement window, and successful economy verification. Collect complete checksummed load/failure and numeric latency exports before cleanup. A successful receipt must report the backend's actual Hikari maximum of 20; missing metrics or pool 10 fail acceptance. The runner receives no management credentials. Cleanup removes only the five newly created, identity-verified service IDs, clears their secrets and independently checks absence.

Frozen backend source: `d4b639e91dc08c95a0af3528024c3235f199860f`. Server JAR SHA-256: `8fff8ae123902032d98a92190b0bff3df87e234f213b25a298f4768c7e40e818`. Backend manifest SHA-256: `2a7236b02f37a321b3f4450111a89dd8c30a4765dc36d2b876cf9e56659f7506`. Tested runner manifest SHA-256: `c494a0585ada59c28f6d857f24f20abe55f6201689c4240796a4a708a7d26367`.

The concrete scope is `.artifacts/room-ownership-staging-next-20261110/baseline-launch-scope.json`, SHA-256 `bd6d612849fc2ca6d72b53b69fb73cdbb4d51d71f61181a034d650d117db4dc8`. The controller is `railway-baseline-candidate.mjs` beside it. It preserves the one-attempt guard, exact project/environment/service identities, immutable upload-context checks, resource ceilings and scoped cleanup from the earlier controller. It adds backend pool 20, the current fixed timing labels and a runtime pool metric gate.

Controller SHA-256: `4c2a90031fcb3f001178aa1c8cce07ffaa3b21943654543663f80507a5b41d91`. The latest local two-backend run also passed on the same JAR/pool: 60,000 ACKs, 1,200 matches, 601.240 seconds, zero retries and three successful owner-stop recoveries. That run uses the normal local acceptance fixture rather than a Railway-equivalent resource envelope.

Offline receipt tests reject pool 10 and missing pool metrics after rebuilding valid receipt checksums, as well as missing/corrupt/mixed exports, sensitive fields and delayed-log cases. Read-only preflight has verified all frozen contexts, the unchanged nine-service staging inventory and stopped existing compute. A fresh preflight is required immediately before any separately approved launch. No launch approval is recorded in this package.

Local evidence and remaining gates are recorded in [the staging handoff](room-ownership-staging-handoff.md). GitHub issue status could not be refreshed because the bundled GitHub CLI is not currently authenticated; no issue was changed or closed.
