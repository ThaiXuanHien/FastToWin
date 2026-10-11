# Isolated load baseline — Issue #5

Use the existing approved issue requirements. Run a bounded synthetic workload
against a JVM spawned by the harness, never an arbitrary URL or existing server.

- [x] Tests first: refuse URL/environment/concurrency overrides that could target production;
  check percentile calculations and schema validation.
- [x] Start server with an allowlisted environment, loopback binding, bounded heap,
  no provider credentials, and a global deadline. Shut down only its own child.
- [x] HTTP registration/login/status and room list, WebSocket connect/create/join,
  50 acknowledged selections, mid-game disconnect/resume and room leave.
- [x] Keep normal server rate limits. Bound pairs, rounds and click rate; report
  rejection/errors explicitly instead of silently retrying them into success.
- [x] Optional PostgreSQL uses only the known local test database and a newly
  generated schema; collect schema-scoped relation I/O/size/rows and clean it up.
- [x] Report operation counts, throughput, p50/p95/p99, failures, JVM/process
  resources and hardware. Separate startup/setup from workload timing.
- [x] Run memory and PostgreSQL baselines, retain shareable aggregate results,
  document limitations and comparison commands; update the issue with evidence.
