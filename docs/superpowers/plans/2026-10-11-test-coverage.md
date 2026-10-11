# Test coverage implementation plan

> **For agentic workers:** Use executing-plans to implement this plan task-by-task.

**Goal:** Deliver separate, reproducible server/shared logic coverage reports and CI gates for Issue #4.

**Architecture:** Kover instruments JVM server and Android shared host tests. Reports retain separate module denominators; CI publishes HTML/XML even when verification fails. Thresholds come from measured full local suites with PostgreSQL, not guessed percentages.

**Tech Stack:** Gradle 9.5, Kotlin 2.4.10, AGP 9.1, Kover 0.9.11.

**Spec:** GitHub Issue #4; `docs/backlog-audit-2026-10-11.md`.

## Constraints

- Do not mutate production, enable billing or include secrets.
- Exclude generated resources/serializers and Compose rendering, explain scope precisely.
- Do not exclude business code to inflate percentages; shared controller/network/state remain measured.
- Missing PostgreSQL must not become a passing release baseline.
- Preserve unrelated `.idea/appInsightsSettings.xml`.

## Task 1: Reproducible reports and CI verification

**Files:** `gradle/libs.versions.toml`, root/server/shared build scripts, `.github/workflows/ci.yml`, `docs/test-coverage.md`, `docs/testing.md`.

- [x] Confirm report tasks are absent in the existing project (`.tmp/coverage-before.log`).
- [x] Apply supported Kover and generate separate HTML/XML using full server/host suites with local PostgreSQL enabled.
- [x] Inspect included classes, denominators and current line/branch counters; document exclusions and observed baseline.
- [x] Configure module-specific line thresholds below measured baseline with small documented headroom.
- [x] Prove normal gates pass and a deliberately impossible threshold fails using the same measured report; restore normal threshold.
- [x] Add CI report/gate and always-upload artifacts; existing test and screenshot jobs remain intact.
- [x] Review scoped diff and update #4 with executable evidence and CI limits.
