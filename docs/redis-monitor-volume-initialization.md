# Monitoring sender volume initialization

Owner approved local preparation of this bounded adjustment on 2026-11-03. It does not authorize a Railway service/volume, real credentials, upload, metrics shipping, CI or deployment. The local collector/Alloy transport, allowlist, freshness and queue semantics remain those in [the monitoring handoff](redis-cloud-monitoring.md).

## Revised deployment proposal

Use one private collector and one private sender in the existing production Redis region. Proposed maximum per service is **0.25 vCPU / 0.25 GB RAM**, matching the smallest displayed setting inspected in Railway. Test at a conservative **250,000,000-byte** local memory ceiling; this does not assume whether the provider's displayed GB is decimal or binary. The accepted earlier 0.1-CPU fixture remains unchanged for comparison.

The sender would receive a new dedicated **5-GB Hobby volume** at `/var/lib/alloy`, replacing the original 1-GB proposal. Storage charges follow actual used bytes including filesystem metadata. The 5m/5m/2h WAL retention does not impose a 5-GB or 1-GB software quota. Restart gaps remain accepted; initialization never deletes or repairs WAL.

The incremental USD1–3/month estimate remains conditional on actual average CPU/RAM, storage and egress. These ceilings are not reserved usage or a billing cap. Continuous use of both full CPU/RAM ceilings mechanically reaches USD15/month compute before storage/egress at the checked rates; 5 GB fully used adds USD0.75/month. Review a forecast above USD3 and obtain a new decision before accepting USD5/month or increased resources. No automatic spending cutoff is provided.

## Separate initializer, then nonroot sender

The existing sender image stays `USER 65532:65532` with the same daemon entrypoint. A separate command, `initialize-sender-volume.mjs`, is included in that image but is never called by the normal launcher.

After a later concrete production grant, the operator would use this sequence on the **new sender service only**:

1. Confirm the new volume is attached at exactly `/var/lib/alloy`, its identity is recorded, no sender deployment is running or restarting, and no Grafana/Redis/other secret has been supplied or linked to this service. Set restart policy Never for the initialization phase. Do not use pre-deploy: it cannot access the volume.
2. Temporarily select UID0 only for initialization and replace the start command with the fixed secret-free command below. It must exit0 with the fixed success message. Do not start Alloy or accept a process-health signal as ownership proof.
3. Before supplying the sender token, restore the original start command (clear the override), select UID65532, confirm image UID/GID65532 and verify the actual process identity. Restore On Failure with at most3 retries. Any ownership/start-command mismatch stops the rollout.
4. Supply only the separately approved sender credential/config and start the ordinary nonroot sender. The collector never receives this token and needs no volume or root phase.

```text
/usr/bin/env -i PATH=/usr/local/bin:/usr/bin:/bin /usr/local/bin/node /opt/redis-monitor-cloud/initialize-sender-volume.mjs
```

Railway documents that a custom start command replaces a Docker image's ENTRYPOINT in exec form. `RAILWAY_RUN_UID` selects the main-process UID; a future concrete grant must explicitly include its temporary UID0 initialization setting and restoration before token entry. This document itself performs neither change. Never leave `RAILWAY_RUN_UID=0` on the running sender.

Initialization requires exclusive access to an actual mounted volume at the fixed path, Linux UID0, no arguments and no credential-named environment variables. It validates fixed parents and refuses symlinks, unexpected owners/modes, unknown files and invalid markers. An empty filesystem `lost+found` is allowed without changing its ownership. It changes only the top mount directory to UID/GID65532 mode0700 and creates one private role marker; there is no recursive chown, arbitrary path parameter, deletion, WAL reset, network connection or daemon startup. A repeat invocation validates the recognized top-level layout and preserves history: private `alloy_seed.json`, empty `remotecfg` and the owned `prometheus.remote_write.grafana` directory are the standard runtime entries observed with pinned Alloy. It does not traverse, relabel or repair WAL contents. An interrupted partial initialization fails closed for operator review.

This is an operational two-phase procedure. Do not run the initializer concurrently with a sender or another initializer; the code does not provide a distributed lease. If a service already contains a token, an existing WAL without the new marker or unrecognized files, stop for a separately reviewed decision. Do not retrofit the marker to claim ownership.

## Local acceptance and limits

Run the opt-in Docker tests:

```powershell
node --test scripts/tests/redis-cloud-volume.test.mjs
node --test scripts/tests/redis-cloud-config.test.mjs scripts/tests/redis-cloud-build-context.test.mjs
```

Tests create uniquely labelled, owned local volumes/containers/images and synthetic secret files under `.artifacts/redis-monitor-production-20261103/volume-local-*`. No host ports or cloud connections are used. Cleanup validates resource labels and removes only those resources and synthetic secrets; sanitized receipts remain. The actual collector/Alloy runtime check is isolated with `--network none`: it proves process readiness, UID/caps, volume writes and a normal stopped/start cycle, **not Redis readiness, successful remote write, Grafana receipt, forced-stop recovery or sustained monthly fit**. Prior local transport/storage acceptance remains separate.

Readiness and repeated WAL-size probes run outside the sender's memory cgroup, using another isolated probe container; repeated Node exec probes inside the constrained daemon were observed to charge probe RSS and substantial executable page cache to the daemon's limit. Identity checks are bounded one-shot observations. The test uses SIGTERM and a75-second observation allowance for an ordinary sender exit: pinned Alloy1.18.0 has a [one-minute remote-write flush deadline](https://raw.githubusercontent.com/grafana/alloy/v1.18.0/internal/component/prometheus/remotewrite/remote_write.go). The earlier fixture ten-second Docker stop produced137 and remains a failed normal-stop attempt. No queue setting, pin, daemon timeout or lossless shutdown guarantee was changed; the bounded local allowance is not a production draining setting.

## Checked sources

- [Railway volumes](https://docs.railway.com/volumes/reference): Hobby default5GB, usage billing, nonroot permission caveat and no downsizing.
- [Railway start command](https://docs.railway.com/deployments/start-command): Docker ENTRYPOINT override behavior.
- [Railway variables](https://docs.railway.com/variables/reference): main-process UID.
- [Railway pre-deploy](https://docs.railway.com/deployments/pre-deploy-command): volume work belongs to start, not pre-deploy.
- [Railway pricing](https://docs.railway.com/pricing/plans): checked container unit rates; all workload scenarios above are project calculations.
