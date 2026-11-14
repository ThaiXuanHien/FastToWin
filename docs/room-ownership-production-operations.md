# Room ownership production operations

The supported topology has one public ingress A and one private worker B. B has no public application handlers. A is a single point of failure: an A outage interrupts public connections. Never overlap two A instances; stop the previous A before starting its replacement, because legacy state has one authority.

## Before a replacement

Drain or stop the old A, verify it is stopped, and only then start the replacement. Keep the existing owned configuration and routes installed. Peer transport construction does not require the other private hostname to resolve: each process may start healthy with readiness false, then lazily resolves and pins the full private DNS answer set before its first hop or inbound authenticated request. When B's private address changes, A revalidates the full DNS answer set and replaces its pinned client. Wait for signed status and a successful B-owned create through A before accepting rejoin. Failed or mixed DNS answers make the peer unavailable until a valid private answer returns. Confirm both services run the same approved server JAR and ordered runtime distribution, with the intended node IDs, peer credentials, accounts, policy and topology. Do not use a prior candidate's fault/load receipts as acceptance of a new distribution.

## Health and monitoring

Set the public reverse proxy's `BACKEND_UPSTREAM` to A's private address. Use `/health`
for the proxy and backend startup checks. Check `/ready` and signed status on both
private services before opening owned admission. `/ready` can return 503 during an
intentional stop or drain. Use `/health` for process liveness during those phases.

The existing cloud metrics sender can add both backend targets with
`FASTTOWIN_BACKEND_METRICS_INGRESS=server.railway.internal:8080` and
`FASTTOWIN_BACKEND_METRICS_WORKER=owned-worker.railway.internal:8080`.
Set both or leave both absent. Invalid, incomplete, identical or public targets
stop sender startup. The Redis pipeline remains installed, and the backend
pipeline keeps only operational counters/gauges, fixed outcome/operation values
and `instance="a"`/`instance="b"`. Scraping uses 15-second intervals, five-second
timeouts, no redirects or environment proxies. Applying the new sender image
and variables is part of the approved rollout; a local receipt is not Grafana
Cloud acceptance. Verify both backend `up` series and the unchanged Redis
series after deployment. Shipping metrics does not install Grafana alert rules;
confirm those separately or retain an attended observer for the approved window.

Scrape `/internal/metrics` from A and B on the private network as job
`fasttowin-server`, retaining their separate instance labels. The [Prometheus rules](../deploy/monitoring/alerts.yml) cover repeated unavailable routes, settlement failures, and a queued
event age above 25 seconds for one minute. The backlog threshold uses the local
acceptance bound for recovery. Validate the thresholds with Staging traffic
before applying them in production. During drain, compare any settlement alert
with the signed durable pending count before changing routes. Keep the existing
public proxy block on `/internal/*`.

## Trusted B-local control

Run each command inside the existing B service environment. The entrypoint uses B's configured peer credential and contacts A over the existing private authenticated control surface. It starts no server and exposes no new route. Use the **exact ordered classpath from the installed `bin/server` launcher**; do not replace it with `lib/*`, whose order is unspecified. For the standard trusted Gradle distribution in `/opt/fasttowin`, the following obtains the single generated classpath assignment from the installed launcher (inspect that assignment as part of distribution review):

```sh
cd /opt/fasttowin
APP_HOME=$PWD
line=$(sed -n '/^CLASSPATH=/p' bin/server)
[ "$(printf '%s\n' "$line" | wc -l)" -eq 1 ] || exit 1
eval "$line"
java -cp "$CLASSPATH" com.hienthai.fastowin.server.ownership.OwnedClusterControlMainKt status "$OPERATION_ID" "$GENERATION"
```

The CLI accepts `status`, `stop_new`, `step`, or `resume`, followed by an operation UUID and expected generation; mutations additionally require A's current process UUID from a fresh signed `status` response. For example, replace the final line above with `java -cp "$CLASSPATH" com.hienthai.fastowin.server.ownership.OwnedClusterControlMainKt step "$OPERATION_ID" "$GENERATION" "$PROCESS_ID"`. Never pass secrets as arguments or substitute a caller-supplied candidate hash. The process hashes its actual server code source and ordered runtime JAR contents. Each individual request has a 15-second transport bound. The optional `await <operation-uuid> <generation> <timeout-millis>` command performs bounded status/stop/step progress (duration from 1 to 3,600,000 milliseconds, not an absolute timestamp) and returns a JSON outcome of `completed`, `deadline`, `unavailable`, or `conflict` with its last verified status when one exists. On deadline, unavailable, or conflict, reconcile fresh status before a new operation; the last status predates any ambiguous in-flight request. Identical pending steps reuse their request identity. Await follows only transitions acknowledged by its own requests and stops on an unexpected generation or coordinator process. The other commands execute one request and exit, with a 30-second total bound. The coordinator retains a bounded current-generation fingerprint window and eligible transition replays; obsolete generations are retired. If a live window is exhausted, the request returns unavailable and operators must reconcile the current non-admitting phase.

## Stop and drain

1. Obtain a fresh signed status for the current generation. If it is stale, use the returned policy generation and repeat status. Record the candidate, topology, A process ID, operation UUID and policy. Stop if identity or configuration differs from the approved deployment.
2. Send `stop_new` with that generation and A process ID. Record the returned STOP_NEW generation. On a lost response, get fresh signed status; do not infer success from a timeout. Reuse an identical request only while its process identity and policy remain current; after restart reconcile the authoritative status and start a new operation UUID.
3. Until an operator-chosen deadline, request `step` for the latest observed generation and current process ID. A pending or unavailable response is incomplete. Re-observe status after a timeout, restart, stale response or phase change before issuing another bounded step; use a new operation UUID when the request fingerprint changes. STOP_NEW closes waiting owned rooms and waits for all active games, settlement and lease work. QUIESCING drains both processes. Keep owned routes installed throughout. Do not disable routing, clear journals, force zero, or automatically resume on deadline or storage/peer failure.
4. Accept only a transport-verified `completed` proof with the approved candidate and topology, both configured node IDs and fresh process IDs, matching acknowledged and disabled policy generations, and zero open rooms, pending settlements and live leases. `INITIAL_DRAIN` binds the quiescing and disabled generations. A lost proof after coordinator restart is not replayed as current: with DISABLED still authoritative, get fresh status and issue a new `step` using a new UUID; a `REVALIDATION` proof binds newly quiesced current processes and a newly checked zero to that exact disabled generation. Do not mistake it for the old proof.
5. Only after inspecting the current proof and confirming the intended one-backend configuration may operators remove owned routing and return to a single legacy backend. A stays the only public ingress; B never accepts users. Store the verified response and operational observations with the rollout record.

## Explicitly resume owned admissions

There is no automatic ACTIVE transition. If the policy is STOP_NEW, verify both current nodes are accepting and use a fresh status and explicit `resume`. A process that has begun draining cannot be reopened: after QUIESCING or DISABLED, stop the old processes and start fresh A and B instances, honoring the sole-A no-overlap rule and private-address revalidation. Confirm the actual candidate/topology, fresh process IDs and accepting state, then explicitly `resume` with the current generation and A process ID. For QUIESCING or DISABLED, explicit recovery first checks durable global zero and prepares a fresh STOP_NEW generation; a failed gate check leaves that non-admitting generation in place. The coordinator checks both current gates against that generation, then explicitly allocates a new ACTIVE generation. A delayed old-generation quiesce cannot close a gate after preparation; ordinary process shutdown can still interrupt availability, and this is not a two-node high-availability guarantee. Pending/unavailable/stale is not success; reconcile status and retry only within the operator's deadline.
