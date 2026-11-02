# Local Redis acceptance runner

This test-only distribution runs existing real-Redis acceptance cases in a one-shot JVM. It is separate from the production application distribution. It has not been deployed or accepted on cloud. It does not test TLS, AOF, backup, restore or platform configuration. The byte fault proxy accepts plain `redis://` only; for the proposed deployment the transport protection is the private WireGuard network, not TLS hostname verification.

Build from a committed revision with the project's existing Gradle dependencies available. On Windows use JBR 17 and the task-local temporary directory; run from the repository root:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:JAVA_TOOL_OPTIONS = '"-Djdk.net.unixdomain.tmpdir=C:/Users/Hien Hue/.codex/worktrees/clan-weekly-race/FastToWin/.artifacts/jtmp"'
./gradlew.bat :server:redisAcceptanceBundle --no-daemon --no-configuration-cache --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8'
./scripts/test-redis-acceptance-container.ps1
```

The bundle is `.artifacts/redis-cloud-acceptance-20261102/runner`. It contains the test and application JARs, their runtime JAR dependencies, `REVISION`, and `SHA256SUMS`. The manifest covers all other files and its own SHA-256 is the artifact identifier in the report. The entry point checks the complete file set and every digest before opening Redis. Build only from clean committed source: `REVISION` records Git HEAD, not uncommitted changes. Runtime launches Java directly and does not use Gradle or download dependencies. The Dockerfile pins an official JRE digest and uses UID/GID 10001 without a Docker socket.

The local script generates a private fixture credential and ignored env files, starts its own Redis container on an internal Docker network with no published port, and deletes the fixture and network after verifying their IDs and labels. Redis is limited to 128 MiB/1 CPU with 64 MiB maxmemory and noeviction. Its application ACL is restricted to `ftw:*` and the read/write, EVAL, PING, TIME, SELECT, HELLO and client-info operations needed by the tests. Never use an existing data set or production credentials. The script checks successful auth, wrong password, missing URL, tampered checksum, process output for credential sentinels, and the image user. Sanitized JSON evidence is written beside the bundle under `.artifacts/redis-cloud-acceptance-20261102/`.

For a separately authorized isolated environment, supply `FASTTOWIN_TEST_REDIS_URL` through the platform secret environment or a local ignored env file, never as a command-line argument. The runner creates test keys with random namespaces; the credential needs permission for those namespaces. `REDIS_ACCEPTANCE_DURATION_SECONDS` is 1–600 (default 10) and `REDIS_ACCEPTANCE_CONSUMES_PER_SECOND` is 1–20 in total across both runtime connections (default 10). Functional fault and overload cases include a deliberate separate burst of at most 200 concurrent requests; the sustained load respects the configured rate. The local container check uses 1 second at 10 consumes per second, not a ten-minute load test.

The 13 named cases cover shared bucket behavior, namespace isolation, refill/TTL and corrupt state, all 18 entry buckets, transport faults and overload/cancellation, plus two loopback-only Netty applications for login, WebSocket connection, admin and room actions, outage and recovery. A missing, skipped, failed or timed-out case fails the run. Each case has a 60-second deadline, the load has a 605-second deadline, and the process has a 25-minute watchdog. Reports use fixed labels and numeric fields: elapsed startup for two runtime connections, total load calls, allowed/limited/unavailable outcomes, failure reason, latency percentiles, and the configured pending limit. The separate load issues at most one call at a time; the reported value is not a measurement of the SDK internal queue.
