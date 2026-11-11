# Cloud two-backend and fault rehearsal proposal

Status: **local implementation verified; cloud execution remains unapproved**. The accepted one-backend Railway baseline is complete and its single-attempt grant is consumed. Task 5 passed the six-service local fixture, all eight fault cases and the full two-backend load using frozen application JAR/pools and bounded fixture-only JDBC/PG settings. A later paid rehearsal still needs its own exact service prefix, frozen manifests, resource scope, deadline and explicit execution approval.

Local acceptance evidence is package `room-supervised-cb7e69f5e44e` with independent receipt verification in `.artifacts/room-supervised-local/room-supervised-cb7e69f5e44e/independent.json`. The load reached 60,000 ACKs, 1,200 matches and three recoveries below 25 seconds; all controlled roles had zero OOM kills. PostgreSQL nevertheless reached its 512 MB ceiling, so this result does not claim memory headroom or production capacity. The exact future cloud candidate is `.artifacts/task5-future-cloud-scope-20261111.json`; it is not executed and requires separate approval. The fixture-only authentication timeout and PostgreSQL memory settings are not production changes.

Independent design review identified and corrected PostgreSQL workers escaping the initial process group. The revision also makes supervisor runtime/UID handling, fault-lease expiry and pause-only store evidence explicit. Re-review found no remaining design blocker for local implementation approval. Kernel process-handle support, complete descendant control and unprivileged entrypoint initialization remain required local feasibility gates; they have not yet been tested or claimed as working.

## Goal and existing evidence

Preserve the accepted application JAR (`d4b639e`, SHA-256 `8fff8ae123902032d98a92190b0bff3df87e234f213b25a298f4768c7e40e818`) and backend database pool 20. Extend only the test harness so the existing two-backend load and eight fault cases can run against Railway's private network. Keep the full 20-room/600-second/60,000-ACK gates, three owner outages and recovery limit of 25 seconds.

The current Railway runner supports smoke and a one-backend baseline. `RoomAcceptanceFixture` invokes Docker for process lifecycle and uses two local Toxiproxy URLs for selective PostgreSQL partition. The fault suite also intentionally pauses/stops stores while retaining their data. These are concrete capabilities to adapt; changing the suite label or accepting a provider deployment status is insufficient.

## Approaches considered

| Approach | Benefit | Limitation |
| --- | --- | --- |
| Host controller stops/redeploys services through Railway API | Reuses the current management boundary | Does not prove a process was frozen or killed at the intended boundary; a store redeploy must not be assumed to preserve an ephemeral filesystem |
| Host uses Railway SSH for each process operation | Can target a live deployment instance | Requires a registered SSH key and verified noninteractive access; stopping PID 1 or a PostgreSQL parent alone cannot be assumed to suspend the entire service |
| Private test supervisor inside fixture images | Fixed process operations are available directly to the credential-free runner | Adds a small test-only process component and image variants that must be verified before any paid rehearsal |

**Proposed approach:** the test supervisor. Do not add control routes to the production application or distribute Railway management credentials to the runner.

## Test process control

Run a small supervisor as container PID 1 and launch the existing application/store entrypoint in a separate process group. The supervisor is packaged only into explicitly marked fixture images. Its private control port accepts a fixed operation enum, a fixture identifier, a case/request identifier, a deadline and a per-run authentication value. Configuration fixes the executable/arguments at build/startup; requests cannot choose commands, PIDs, paths, arbitrary URLs or service identities.

The proposed runtime is a small statically linked Go Linux binary, built with a digest-pinned toolchain and locked dependency checksums. It adds no JVM to the store containers. Supervisor and child run under the same existing role account (`fasttowin`, `postgres`, or `redis`); fixture-image build steps prepare required directories without runtime root, host privileges or extra capabilities. The proxy variant uses its own fixed unprivileged account. Record actual UID/GID and process ownership in local capability evidence. If the original entrypoint cannot initialize under that account, revise the fixture build before approval of a cloud package rather than silently running the controlled service as root.

For backend A/B, operations are `pause`, `resume`, `kill` and `start`. A paused JVM can return after takeover so the test can exercise old-fence rejection. Killing a backend group does not automatically restart it; the test explicitly starts the same frozen application and confirms the new process identity/readiness. For PostgreSQL and Redis, expose **pause/resume only**. Do not kill or redeploy a store as a substitute for a temporary outage. Resume must preserve the marked database, synthetic rows and existing socket/session evidence.

PostgreSQL children create their own sessions/process groups, so signalling the postmaster group alone is insufficient. Its adapter must stop the postmaster first to prevent new accepts/forks, then repeatedly enumerate and stop all owned descendants, including descendants in other groups/sessions, until every tracked identity is stopped and the descendant set is stable. Bind an identity to PID plus Linux process start time and the supervisor's child generation, and signal through kernel process handles (`pidfd`) so a recycled PID cannot be targeted between validation and signalling. Verify kernel support before any game fault; an unsupported runtime fails closed. Handle orphaned descendants through the supervisor's child-reaping/tracking model. Failure to enumerate, signal or verify the complete tree fails capability verification and triggers restoration/cleanup; no best-effort partial pause can count as a store outage. Resume the exact tracked identities, restore the postmaster and verify the original database marker/data. Parent-exit cleanup must cover these escaped groups too.

Prove this against the pinned PostgreSQL 18 image using connections established before the fault, new connections and actual application writes. Readiness failure alone does not prove existing PostgreSQL workers stopped. Local tests must cover a fork during enumeration, worker exit, PID reuse, cancellation halfway through pause and complete restoration. If the runtime's same-container signalling/visibility permissions cannot satisfy these checks without privileged host access, stop and revise the design before cloud execution.

Use one active fault per controlled role, idempotent request identifiers, bounded request/history sizes and deadlines. A duplicate request must not restart or kill a newly created process generation. Reject stale generations and cross-fixture actions. Status replies are fixed technical fields only. Parent shutdown resumes and terminates its own group, then escalates if needed; parent death must not leave an uncontrolled child. A hard fixture deadline terminates child groups and the supervisor. The independent host controller still owns service teardown if the runner or a supervisor fails.

Each intentional pause/partition has a maximum 60-second fault lease that the runner cannot extend indefinitely. On lease expiry, the supervisor restores the paused processes or proxy listener and permanently marks that case expired/failed; automatic restoration cannot count as a successful test acknowledgement. This bounds a fault even if the runner disappears. At the overall fixture deadline, resume as needed for teardown, terminate all managed processes and exit. Capability checks must prove this timer/restoration behavior locally for each role, including proxy listener isolation and established PostgreSQL sessions.

The existing store case names `redis-stop-fails-closed` and `postgres-stop-no-optimistic-ack` must not be exported unchanged for this new mechanism. The cloud adapter records `redis-process-pause-fails-closed` and `postgres-process-pause-no-optimistic-ack`, together with the verified injection mechanism, while keeping their fail-closed/no optimistic ACK/exactly-once assertions. A process-pause pass does not claim data durability across a store crash/redeploy; that remains separate scope.

Before exercising game faults, a capability check must prove the requested operation actually occurred: the child generation/state changes as expected, the selected target becomes unavailable, the survivor stays reachable, and resume restores readiness. No-op pause/kill, a skipped injection, or an unverified target fails the case. Recovery timing starts immediately before the fault operation is requested and includes control acknowledgement, takeover and reconnection. Do not remove control/network latency from the 25-second result.

## Selective database partition

Add one digest-pinned private Toxiproxy service with two independent proxy listeners. Backend A uses listener A, backend B listener B; the runner connects directly to the marked PostgreSQL service for authoritative checks. The harness may disable only the chosen owner's listener and must prove the other listener/backend remains usable. Restore the listener in `finally` and exercise the former owner's captured fence after connectivity returns. A common PostgreSQL process pause remains a different test from partitioning one backend's path.

Run the same supervisor binary in proxy mode inside that sixth service. Toxiproxy's configuration API binds only to its container loopback; the authenticated private control port exposes only block/unblock of the two immutable listeners. The supervisor applies the same fault-lease expiry and marks expired cases failed, including if the runner dies after blocking a listener. It cannot create arbitrary proxies or change upstream destinations. This adds no seventh service.

This gives **six potential services** for a future reviewed scope: PostgreSQL, Redis, A, B, runner and the dual-listener proxy. Supervisors run inside A/B, the two stores and the proxy, not as additional Railway services. No public ingress, host PID access, privileged network mode or persistent volume is needed.

## Reuse of the acceptance code

Extract a small internal test-control interface for backend pause/resume/kill/start and store pause/resume. Keep the existing Docker adapter as the local implementation, adding a fixture-image adapter for the private supervisor. Preserve the current game, replay, epoch, membership, quota, wallet and economy assertions. Test helpers that capture diagnostics must not export raw SQL, request bodies, account identities or authentication material to provider logs.

Add a separately selected staging profile for two-backend load/fault work. The one-backend baseline stays strict and continues rejecting backend B. The new profile accepts only the exact marked A/B/store/proxy hostnames, private distinct addresses, the fixed staging project/environment and a bounded deadline. It still rejects Railway tokens and external database settings. Both backends must be ready before each case.

Reuse complete checksum chunk exports for load, latency, failure and a new bounded fault receipt. Fault exports contain case names, pass/fail, technical proof flags, counts and durations; sensitive fixture IDs from the local receipt are omitted or transformed before provider logging. The host requires the exact case set, full chunk sets, the expected backend count, actual pool 20 on both backends, fixed manifests and all three load outages. A single-backend or shortened receipt cannot pass the new gate.

## Proposed verification sequence

1. Write failing tests for the control contract: authentication, fixture/generation binding, idempotency, deadlines, operation allowlists and bounded receipts.
2. Verify the supervisor with disposable local containers: freeze the JVM/Redis groups and the complete PostgreSQL descendant tree across separate sessions, crash/restart only backend children, preserve marked PostgreSQL/Redis data across pause/resume, and clean every tracked group/process/container after cancellation or parent exit. Verify already-open SQL sessions cannot execute during the pause.
3. Exercise selective proxy isolation and survivor readiness on the private local network. Verify an untriggered operation fails acceptance.
4. Run all existing activity/ownership tests, receipt readers and fixture-isolation checks. Confirm the production server JAR hash remains unchanged.
5. Run all eight fault cases and the full two-backend 20-room/600-second load through the new adapter locally. Retain original and independently decoded receipts and zero-resource cleanup proof.
6. Freeze supervisor, backend-wrapper, store-wrapper, proxy and runner manifests. Review a concrete **separate** cloud scope only after the local gates pass. No cloud resources or SSH keys are created during steps 1–5.

The eventual cloud resource proposal starts with A/B each 3 CPU/1 GB and pool 20, PostgreSQL 2 CPU/0.512 GB, Redis 0.25 CPU/0.25 GB, runner 0.5 CPU/0.5 GB, and proxy 2 CPU/0.128 GB. These are **design inputs, not approved capacity or an execution grant**. Measure supervisor overhead locally and change the proposal before approval if needed. A single capped cloud attempt should run capability checks, then the fault suite, then the full load; any failed prerequisite stops later stages and triggers cleanup. Preserve the existing nine service identities, stopped compute and October volume metadata/attachments.

## Decision and next step

Approve or revise this design for **local implementation only**. After approval, write the implementation plan and execute it with regression tests and independent review. After local verification, prepare an exact cloud scope for a separate execution decision. Production rollout remains outside this proposal.

## Provider references checked on 10 November 2026

- [Railway SSH](https://docs.railway.com/cli/ssh): registered key requirement, specific deployment-instance targeting and noninteractive commands. No key has been registered or SSH session opened during this design work.
- [Railway deployment actions](https://docs.railway.com/deployments/deployment-actions): restart/redeploy semantics do not establish equivalence to the local process-fault assertions.
- [Railway healthchecks](https://docs.railway.com/deployments/healthchecks): checks run during deployment; ongoing process-fault detection must be verified by the harness.
- [Railway restart policy](https://docs.railway.com/deployments/restart-policy): automatic process restart policy is distinct from explicitly controlled child-process lifecycle.
- [PostgreSQL 18 `InitPostmasterChild` source](https://raw.githubusercontent.com/postgres/postgres/REL_18_STABLE/src/backend/utils/init/miscinit.c): child session creation explains why postmaster process-group suspension alone is not a verified database outage.

Installed CLI help and read-only provider schema checks confirmed that deployment stop/restart/redeploy are available. No cloud mutation or test deployment was performed to investigate these capabilities.
