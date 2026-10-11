# Test coverage

Issue: [#4](https://github.com/ThaiXuanHien/FastToWin/issues/4).

Kover 0.9.11 measures server JVM tests and shared Android host tests separately.
It does not measure Android device tests, JavaScript/Wasm, or Kotlin/Native.
The reports describe executed code, not proof that every behavior is correct.

## Measured baseline — 2026-10-11

Measured on Windows (Gradle launched by Studio JBR 25.0.2; server toolchain 17),
with full server tests and an isolated local
PostgreSQL database configured through `TEST_DATABASE_*`.

| Module | Tests / failures / skipped | Covered / total lines | Line coverage | Branch coverage | CI minimum lines |
| --- | --- | --- | --- | --- | --- |
| server | 620 / 0 / 0 | 12,042 / 13,894 | 86.67% | 66.17% | 86% |
| shared | 234 / 0 / 0 | 2,306 / 4,410 | 52.29% | 41.56% | 52% |

The initial floors retain 0.67 and 0.29 percentage points of headroom respectively.
Shared's relatively low baseline remains visible: controllers, network code and
platform billing implementations are included even when host tests do not execute
all paths. Branch coverage is reported, but initially only lines gate CI.
Do not lower floors or exclude business code merely to pass a failing build.
Raise floors deliberately when adding meaningful coverage.

## Report scope

- Both modules exclude generated Kotlin serialization `$$serializer` classes.
- Shared also excludes generated `resources.*`, Compose `ui.*`, the
  `FastToWinAppKt*` rendering entry point and `ComposableSingletons*`.
- Server business code, admin routes, repositories and financial collectors remain
  included. Shared controllers, navigation, preferences, networking and platform
  adapters remain included. Compose rendering is protected by screenshot and
  Android UI tests instead.
- Instrumentation attaches to test JVMs; it does not instrument production
  distribution JARs or application bundles.

## Run locally

Provide a disposable local PostgreSQL database through `TEST_DATABASE_URL`,
`TEST_DATABASE_USER` and `TEST_DATABASE_PASSWORD`. PostgreSQL tests create isolated
schemas; never supply production credentials. Without these settings some
integration tests are skipped and the result is not the release baseline above.

```sh
./gradlew :server:koverHtmlReport :server:koverXmlReport \
  :shared:koverHtmlReport :shared:koverXmlReport \
  :server:koverVerify :shared:koverVerify --no-configuration-cache
```

Open `<module>/build/reports/kover/html/index.html`; machine-readable counters are
in `<module>/build/reports/kover/report.xml`. CI uses PostgreSQL and publishes
`server-and-shared-coverage`, including available reports when verification fails.

To prove failure without editing normal thresholds, use either diagnostic override:

```sh
./gradlew :server:koverVerify -PserverCoverageMinLine=100 --no-configuration-cache
./gradlew :shared:koverVerify -PsharedCoverageMinLine=100 --no-configuration-cache
```

These commands must fail with the baseline above. Normal CI never supplies these
overrides. Re-run without the properties to restore ordinary verification.

Kover configuration follows the [official Gradle plugin documentation](https://kotlin.github.io/kotlinx-kover/gradle-plugin/).
