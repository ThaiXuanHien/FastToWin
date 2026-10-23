# UGC safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Implement approved #53 moderation, versioned content terms and player reporting without a paid service.

**Architecture:** A dedicated moderation repository/service owns persisted terms, reports, content suppression and account restrictions. Authenticated WebSocket commands reuse established player identities; admin routes use existing cookie/CSRF/RBAC under the same transaction authorization lock. Clients project server-authoritative content suppression and preserve existing UI.

**Tech Stack:** Kotlin Multiplatform/Compose, Ktor, PostgreSQL/Flyway, vanilla admin JS/CSS.

**Spec:** docs/superpowers/specs/2026-10-20-ugc-moderation-design.md (approved issue comment #6291437295).

## Global Constraints

- Keep current game/admin visual design; use ui-ux-pro-max, VI/EN localized copy and accessible modals.
- Reports cover player profiles/avatar, clans, rooms, tournaments; server derives reporter identity.
- Limit 5/10 minutes and 20/day per identity, plus gateway IP limits; request ID replay and one open report per reporter/target/type.
- Store report text <=500 characters; never snapshot/upload avatar image bytes or log report PII.
- SUPPORT triages; OWNER/OPERATOR remove content and restrict accounts; validate origin/CSRF/version/request ID and audit minimal metadata.
- Keep active reports; erase report contents/identities 90 days after close. Account deletion removes reporter identity and identifying evidence; backup retention stays 14 days.
- Versioned acceptance before any new/edited public UGC, including guest/signup names. Enforcement defaults off until compatible clients have been released and verified. Viewing/deletion/help remains available.
- No emulator, paid services, production mutations, master merge or public Play submission in implementation verification.

## Contract and file boundaries

Task 1 owns `protocol/.../UgcProtocol.kt`, `server/.../moderation/*`, migration V62 and moderation tests. Contract names: `CONTENT_TERMS_VERSION`; `UgcTargetType { PLAYER_PROFILE, PLAYER_AVATAR, CLAN, ROOM, TOURNAMENT }`; `UgcReportReason`; `ContentReportRequest(targetType,targetId,reason,note,requestId)`; `ContentReportReceipt`; `ContentTermsStatus(requiredVersion,acceptedVersion,enforced)`; `ModerationService.terms(playerId)`, `.acceptTerms(playerId,version)`, `.requireContentTerms(playerId)`, `.report(playerId,request)`, `.isBlocked(playerId)`, `.sanitize(message:ServerMessage):ServerMessage`, `.cleanup()`. Constructor repository/admin/enforcement/time injection. Repository provides PostgreSQL and in-memory behavior with the same security semantics. Admin endpoints `/api/admin/moderation/reports`, `/reports/{id}`, `/reports/{id}/resolve`, `/users/{id}/restriction`.

Task 2 owns wiring in GameProtocol/AuthProtocol, Application/production wiring, GameEngine, auth/guest/account/profile/avatar repositories. Commands `GetContentTerms`, `AcceptContentTerms(version)`, `ReportContent(request)` and messages `ContentTermsData(status)`, `ContentReportResult(requestId,receipt,errorCode)` are added here. RegisterRequest adds nullable acceptedContentTermsVersion; ConnectGuest adds nullable acceptedContentTermsVersion. Service is injected with safe default enforcement disabled; production config has explicit `FASTTOWIN_CONTENT_TERMS_ENFORCED=false`. Use mutation/publication checks; block account login/refresh/active WS; scrub content in output and avatar reads; deletion cleanup is transactional.

Task 3 owns shared app/controllers/UI/localization and consumes the exact protocol from task 2; no server changes. Shared reusable report and consent modals integrate all visible UGC surfaces and signup/guest entry. Retry uses stable request IDs; no false success before server receipt. UI retains drafts on errors.

Task 4 owns `server/src/main/resources/admin/admin-moderation.js`, admin shell integration and UI tests; consumes task 1 admin DTOs/routes. Modal details/actions, list filters and pagination; SUPPORT read/triage only, mutating actions confirmed with reason; use DOM textContent and existing authenticated request helper.

Task 5 owns legal/docs/release verification; no production enablement. Update privacy data retention/reviewer instructions and audit API declarations; record acceptance limits. Independent review before PR; retain source of PR54 separately as dependency.

### Task 1: Domain, storage and admin service

- [x] Add failing service tests: duplicate request returns same ID, changed payload conflicts, sixth/10min and twenty-first/day rejected, absent target rejected, SUPPORT cannot remove, stale revision cannot mutate, expired closed reports removed, account erasure removes identity/evidence. Test names describe these actual behaviors with in-memory fixtures and PostgreSQL integration where available.
- [x] Run `:server:test --tests '*Moderation*'` and confirm missing-feature failure before implementation.
- [x] Implement module/migration/service and safe admin routes using existing advisory lock `7421092187349`; transactionally read current admin role before each mutation. Persist rate counters separately from report retention, with bounded expiry. Store suppression keyed by target/type/revision; no wallet changes.
- [x] Run domain tests and migration tests, inspect SQL constraints, commit scoped files.

### Task 2: Server integration and protocol

- [x] Add tests where unsigned/guest identity spoofing fails, old acceptance cannot publish when enforcement enabled, unaccepted login/delete still work, blocked active WS refuses action, deleting account scrubs reports, suppressed avatar cannot be read. Run failing tests first.
- [x] Add protocol commands/messages and auth/guest accepted version fields. Wire service through production configuration/Application/GameEngine and every UGC mutation. Sanitize nested snapshots/invitations/history, not only list pages. Gate content revisions on moderation actions.
- [x] Verify protocol serialization/backward defaults, auth/WebSocket integration, targeted PostgreSQL tests; commit scoped files.

### Task 3: Player consent/report UI

- [x] Add failing state/protocol tests proving stable report request ID, success only on receipt, stale errors do not clear drafts, consent version server controlled.
- [x] Implement shared report modal and content terms consent modal; links open trusted VI/EN legal URLs. Connect guest/signup acceptance and existing player publication interception; expose report action on profile/avatar/clan/room/tournament visible surfaces with block action separately available.
- [x] Compile Android/JS/Wasm and run state tests; inspect phone/desktop output without emulator. Commit scoped files.

### Task 4: Admin queue

- [x] Add failing admin UI/network tests for escaped report text, SUPPORT restrictions, request reason/version/CSRF payload and failed action retention.
- [x] Implement queue/detail/action modals using existing admin components; state NEW/REVIEWING/RESOLVED/DISMISSED; enforce server RBAC regardless of button visibility.
- [x] Run Node and browser fixture tests, inspect phone/desktop screenshots, commit scoped files.

### Task 5: Integration, privacy and release evidence

- [x] Verify every approved spec bullet maps to implemented/tested behavior. Run protocol/server/state tests, client compilation, JS security tests and static route checks.
- [x] Review migration/retention/account deletion and account restrictions with an independent reviewer. Resolve important findings and rerun affected tests.
- [x] Update legal copy for reports/terms and 90-day retention; no claim of live enforcement until rollout switch enabled. Document how client-first rollout and old-client upgrade work.
- [ ] Create dependency-aware draft PR and update #53/#28 with concrete evidence, remaining device/staging/rollout gates and CI status. Do not close #53 prematurely.

## Source verification record

See [UGC verification](../../ugc-moderation-verification.md) for executed results,
independent review findings and explicit device/staging/CI limits. Completion of
source tasks does not authorize production enforcement or close #53.
