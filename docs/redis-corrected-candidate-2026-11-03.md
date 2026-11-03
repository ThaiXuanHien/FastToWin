# Corrected Redis backend candidate: local checkpoint

Source `79d227afcad580986e90f51f4df84744a53d2b24` contains the reviewed PLANNED maintenance fix. The offline distribution build succeeded once. Its log records the pre-existing Gradle configuration warning that `android.experimental.enableScreenshotTest=true` is experimental; the warning did not prevent the successful build. Its prior focused validation passed 84 maintenance tests, including 12 real PostgreSQL cases; packaging did not rerun tests or repeat runtime validation.

Artifacts are under `.artifacts/redis-production-rollout-20261102/task4/candidate-79d227a/`:

| Package | ZIP SHA-256 | Contents |
| --- | --- | --- |
| `backend-release.zip` | `22cb29aaf6e09572cc9d4b0e1a36c4f912bf008922865c091a1ecabf4c2a2fbd` | 179 files; 176 distribution files, 174 JARs |
| `recovery-release.zip` | `6dd8d71ff72a69ea55db9c5e97ff28670cec21cfe8bde14d3e7bbf88bb34d0d0` | 173 files; 170 original production distribution files |

The release manifest SHA-256 is `794b84f09d3cecc8bb5e5e2a77a18f82c3c51a370f9d7b518b65d612f191bd0e`. Candidate context manifest SHA-256 is `2db89e4de8a3df437b8a8ba10b4b8e0f2e39b7ecc9719d95e68e1647aeefcffd`; recovery context manifest SHA-256 is `42f07d9d03a55abc681f154ecc63a55ed6f2da75202da2ad4daaa690cfc51f46`.

Compared with the frozen Task2 distribution, only `server.jar` changed; 173 dependency JARs and all 68 SQL migrations remain identical. Schema remains V69. Original Task2 contexts and archives remain unchanged. The recovery package preserves application bytes from `6eb2d2f`; only its Dockerfile pins the cached Java base. It is a separate portable fallback, not proof of availability of the original Railway image or a successful production startup.

Historical local image inspection verified nonroot UID100/GID101, Java17 and all distribution hashes using network-disabled shells without starting the app or supplying DB/secrets. The inspection containers were removed. Current reinspection could not run because Docker's engine pipe was absent; no repair/restart was attempted. Parent independently rechecked the ZIP/context hashes, CRCs, file sets and frozen artifacts on 03 November (Viet Nam time), with PASS recorded in `parent-package-verification.json`.

The [first-replacement proposal](redis-first-replacement-2026-11-02.md) and a separately approved recovery choice remain required. Full-cutover helpers remain disabled. This checkpoint does not authorize push, CI, production access, maintenance, upload or deployment. The DB snapshot from 02 November 22:43:59 Viet Nam time must be younger than 24 hours at replacement; a new backup needs a new grant if stale. Manual Redis backup requests remain at 18:30; automatic backup stays deferred.
