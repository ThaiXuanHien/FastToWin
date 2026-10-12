# Private R2 Avatar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement private R2 avatar storage with safe legacy fallback, bounded uploads and a manual setup wizard, ready for real-provider staging once the owner creates Cloudflare.
**Architecture:** An avatar service coordinates image validation, repository metadata, immutable R2 keys and durable cleanup. Existing WebSocket upload and HTTP image URL remain compatible; database mode is default. Wizard only assists human-only account/billing/key steps.
**Tech Stack:** Kotlin/JVM 17, Ktor, PostgreSQL/Flyway, AWS Java SDK S3, Kotlin Multiplatform/Compose, Bash.
**Spec:** docs/avatar-object-storage-design.md — explicitly approved by owner 12/10/2026.

## Global Constraints

- `FASTTOWIN_AVATAR_STORAGE=database|r2`, default `database`; no R2 account/domain exists yet.
- Maximum decoded upload/output 45 KiB; frame 64 KiB; JPEG/PNG input, normalize JPEG <=512px, source <=4096px per edge and <=8 million pixels.
- Rate limits: player 3/10 minutes, IP 10/10 minutes before decode/storage; only authenticated connection owner writes.
- Immutable keys `avatars/<player-uuid>/<random-version>.jpg`, no email; preserve cosmetic `avatar_url` semantics.
- Durable cleanup survives account cascade; old replacement keys wait 24 hours, failed upload intents use an expiry window and CAS must reject expired intent. Referenced objects must never be deleted.
- Legacy avatars stay readable; R2 read/write failure must not overwrite current avatar or silently write database blobs. Database fallback mode is not a rollback for already migrated R2 metadata.
- Private HTTPS R2 S3 endpoint for account ID, region auto, least-privilege bucket keys; no CDN/r2.dev production in this phase.
- Runtime variables: `FASTTOWIN_AVATAR_R2_ACCOUNT_ID`, `FASTTOWIN_AVATAR_R2_BUCKET`, `FASTTOWIN_AVATAR_R2_ACCESS_KEY_ID`, `FASTTOWIN_AVATAR_R2_SECRET_ACCESS_KEY`; support existing secret-file helper where appropriate.
- No production/cloud mutation, no secret in Git/chat/log; account setup and provider smoke remain pending.
- Work only in this managed worktree/branch; preserve original checkout `.idea/appInsightsSettings.xml`. No subagents from implementers. Reviewers are dispatched by controller.
- Apply UI/UX Pro Max to client interaction changes; preserve current visuals. Tests first for behavioral changes, meaningful failures, then GREEN.

### Task 1: Backend avatar storage vertical slice

**Files:** Create focused files under `server/src/main/kotlin/com/hienthai/fastowin/server/avatars/` for image normalizer, service/models, object storage/configuration, PostgreSQL metadata/cleanup repository and HTTP routes. Modify `Application.kt`, `Main.kt`, `GameEngine.kt`, `Database.kt`, `RateLimiter.kt`, `server/build.gradle.kts`, version catalog as required. Add additive next migration V61 (verify last migration first), tests under matching server test package, and configuration section in `docs/avatar-object-storage.md`.

**Interfaces:** Service owns upload and image read so GameEngine and HTTP routes use one source. Use `AvatarService.upload(playerId: String, base64: String): Boolean` and `AvatarService.read(playerId: String): AvatarReadResult` with Found(bytes/contentType), NotFound, Unavailable. Internal repository/object interfaces may be named clearly to their single purpose. Storage is injected for tests; production adapter uses official SDK. No new protocol version/message is needed. Default engine construction must still work in existing tests.

- [ ] Write failing tests for bad base64, oversized image, fake MIME/SVG, dimensions bomb, normalized JPEG with bytes/dimensions bounded; use real ImageIO encoded image fixtures.
- [ ] Run focused tests and observe missing behavior. Implement bounded metadata-first decode and normalization with cancellation preserved for suspending work.
- [ ] Write service tests for successful legacy and object writes, failed upload/DB CAS, concurrent updates, missing/deleted player, unchanged prior avatar on error, reference-safe cleanup and retry after restart.
- [ ] Implement intent-before-put / CAS-after-put repository transactions. Cleanup cannot win against a late CAS: commit requires live unexpired pending intent under lock. Queue errors do not discard work; bounded retry/backoff and manual visibility after retry cap.
- [ ] Add PostgreSQL tests for real migration, metadata CAS, account cascade queue and no delete of referenced key. Reuse localhost test fixture, never cloud DB.
- [ ] Wire lifecycle, provider config and bounded R2 adapter reads/timeouts. Validate account ID/bucket/secret without disclosing values. Use AWS SDK S3 2.55.14 and matching URL connection client, verified Maven Central and official Cloudflare Java example; do not implement custom signing.
- [ ] Wire rate limits before UpdateAvatar engine handling, preserving existing global WS guards. Add WebSocket/route tests covering own-account-only upload and 400/404/503/cache/nosniff.
- [ ] Run full `:server:test` with local PostgreSQL. Command wrapper: `D:/HienTX/Work/Android/FastToWin/.tmp/r2-gradle.ps1 -Tasks @(':server:test') -LogName backend-final`. XML must have zero failures and no DB skips attributable to missing config.
- [ ] Self-review, `git diff --check`, commit only task files; report test evidence, API/config names and any concerns. Do not start real R2 smoke.

Minimal boundary examples for tests:
```kotlin
@Test fun `oversized bytes never reach object storage`() = runTest {
    assertFalse(service.upload(playerId, Base64.getEncoder().encodeToString(ByteArray(45 * 1024 + 1))))
    assertEquals(0, objectStore.puts.size)
}
@Test fun `unavailable R2 does not switch to legacy write`() = runTest {
    objectStore.failWrites = true
    assertFalse(service.upload(playerId, validJpegBase64))
    assertEquals(originalAvatar, repository.current(playerId))
    assertEquals(0, legacyWrites)
}
```
Fixtures must define the named helpers in the test file; assertions check real service outcomes.

### Task 2: Client size bounds and reconnect refresh

**Files:** `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameController.kt`, focused common upload preparation policy + common tests, `shared/src/androidMain/kotlin/com/hienthai/fastowin/platform/ImagePicker.android.kt`. Read existing iOS/Web preparers; change them only if needed for compatible limits. Tests in shared common tests/controller tests. Keep all screen layouts/colors/assets unchanged.
**Interfaces:** Task 1 keeps UpdateAvatar wire message and image URL. Add common `prepareAvatarUpload(encode: (maxEdge: Int, quality: Double) -> ByteArray?): ByteArray?` bounded encoder loop or equivalently small named policy; Android plugs Bitmap JPEG encoder into it. Controller must reject empty/oversized bytes before send and refresh URL revision after profile data/reconnect.

- [ ] Apply required UI/UX Pro Max targeted guidance for upload validation/feedback; inspect existing design tokens and interaction states.
- [ ] Write failing common tests for exact 45 KiB acceptance, 45 KiB+1 refusal, encoder loop progress/failure, controller avoiding oversized WS send and revision refresh after reconnect/profile data.
- [ ] Implement shared policy and Android encoder; inspect bounds before full bitmap allocation, preserve aspect ratio, never return >45 KiB, keep existing cancel/error/loading behavior.
- [ ] Run focused tests then `:shared:allTests` applicable host tasks or JVM/common test task detected from Gradle. Run Android Kotlin compilation. Do not claim iOS/device verification from compilation.
- [ ] Capture/review phone and desktop existing upload flow if a local browser build is available; document real-device/provider limitation honestly. No unrelated UI redesign.
- [ ] Self-review, diff check, commit task files; report test evidence and UX guidance applied.

Minimal policy test:
```kotlin
@Test fun `encoder keeps shrinking until bytes fit`() {
    val dimensions = mutableListOf<Int>()
    val result = prepareAvatarUpload { edge, _ ->
        dimensions += edge
        ByteArray(if (edge <= 256) 40 * 1024 else 46 * 1024)
    }
    assertTrue(result!!.size <= 45 * 1024)
    assertTrue(dimensions.last() < dimensions.first())
}
```

### Task 3: Human setup wizard and runbook

**Files:** `scripts/setup-r2.sh`, scoped `.gitattributes` for LF, `docs/avatar-object-storage.md`, README link. Input research: `D:/HienTX/Work/Android/FastToWin/.tmp/cloudflare-r2-research.md`. Read wizard skill and copy its template library unchanged above STAGES marker.
**Interfaces:** Output `.artifacts/r2-setup/credentials.env` (ignored) holds public account ID/bucket names and hidden S3 key pairs separately for staging/production. Use names prefixed STAGING_/PRODUCTION_ and document mapping to Task 1 runtime variables; no CI secret is required. No `.env` sourcing or automatic upload of keys.

- [ ] Author 5 approved stages: signup/verify, R2 checkout, Standard private staging/prod buckets, bucket-scoped tokens/hidden key capture, summary/staging handoff.
- [ ] Confirm before checkout; billing entered only in Cloudflare browser. Precise dashboard paths from research; never fabricate checkout button or exact TTL widget.
- [ ] Use ask_secret for both key fields, do not print them; create ignored directory/file with restrictive permissions; validate account ID/bucket/keys before persist, require different staging/prod bucket names. Abort on unsafe output path; no overwriting unrelated env files.
- [ ] Explain free inclusion versus pay-as-you-go, budget alert does not cap usage, Railway proxy egress still applies; no domain needed yet. Offer setting optional billing alert on dashboard without silently changing billing.
- [ ] Run Git Bash `bash -n`, shellcheck if installed, check template library exact equality and statically trace inputs/output. Do not execute interactive wizard end-to-end or create account.
- [ ] Update runbook provider activation/checklist and issue status accurately; actual R2 smoke/CDN pending. Self-review, diff check and commit task files.

## Integration gate

After each task get independent spec + code review; fix important findings before next task. After all tasks run broad branch review plus required tests. Push feature branch/create draft or ready PR with `Refs #11`, not `Closes #11`; source is ready to review, real R2/CDN/provider/device acceptance remains open. Update GitHub #11 with completed versus pending scope. PR #36 staging CI is tracked independently; do not deploy production or discard its branch.
