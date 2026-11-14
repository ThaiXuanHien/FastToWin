# Production recovery record — 2026-11-14

The approved ownership rollout `e163f2d8663cdb449aa9d154ca9fd61916a4535b2e0e9e14c782d51653a9d4cd` was stopped after both new processes failed before application startup. Each process attempted to resolve the other peer while both private services were down, producing `UnknownHostException`. No owned route was published and no business data was deleted.

The legacy backend was restored from the retained distribution in deployment `193b5b38-b459-42b1-af89-28008b34a1b7`. The measured JAR hash is `a5472cdcaf49effd94f1f290d7c21c1b7a762468e32bbb3d4c3b033786c2e6d5`. Maintenance reopened at version 41. PostgreSQL remains on schema 74; owned admission remains `STOP_NEW` generation 1 with zero owned rooms, memberships, settlements, and create operations.

Recovery evidence is recorded in `.artifacts/room-production-release/20261114-cee03e9a-draft/production-access/production-recovery-execution.json`. Sixteen samples over 15 minutes kept public health at HTTP 200, the recovered deployment `SUCCESS`, and Redis presence read/write failure counters at zero. Grafana showed the Redis collector Fresh, AOF Enabled, last write/rewrite OK, 1.76 MiB used of 64 MiB, and a 3.12 MiB AOF.

QA verified both accounts after recovery. Web and Galaxy A36 joined the same casual classic room; Web reload returned to the live match, and the match finished 50/50 correct with the expected winner/loser result on both clients. The six-character room code was not found by Android, while the full room UUID worked; this is recorded as a separate UI issue.

The temporary key `ftw-prod-ownership-20261114-r1` was revoked and its private key was removed locally. The new worker remains stopped. Issue #15 is not ready to close: the next change must add a separately reviewed bootstrap/DNS readiness sequence and receive a new production scope before another ownership attempt.
