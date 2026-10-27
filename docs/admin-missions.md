# Admin-managed missions

The admin **Nhiệm vụ** page manages personal and clan mission definitions. The player app keeps its personal mission and own-clan entry points and reads authoritative instances from the server. This implementation has only been exercised locally; no production migration, catalog publication, deployment, provider operation, or emulator run is included.

## Configuration and roles

Use the existing dedicated admin configuration and bootstrap workflow (`FASTTOWIN_ADMIN_ENABLED`, `FASTTOWIN_ADMIN_ORIGIN`, and `bootstrapAdminOwner`). Durable mission storage requires the configured PostgreSQL database. Keep admin credentials in the existing password-file workflow; never put passwords or bearer/session tokens into browser traces or reports.

Owner and Operator can read, create, clone, update, preview, and delete unused drafts. Only Owner may publish, archive, or change economic fields. An Operator-created or cloned draft starts with zero rewards; editing preserves its existing rewards. Support cannot access mission management. Each mutation and preview uses the current admin session, configured Origin and CSRF header; the server rechecks authority inside the transaction.

## Authoring and publication

1. Choose **Cá nhân** or **Bang hội**, then **Tạo nhiệm vụ**. Use a unique uppercase code, supported action, cadence, difficulty, target, optional match condition/date interval, and independent XP/Gold/Gem amounts. Clan missions can also carry contribution points. Enter an internal reason.
2. Save the draft and open **Xem trước**. This is a server preview: inspect its version, generated player condition, one-time rewards, Bangkok effective period, selected codes and any pool-shortage warning.
3. Owner proceeds to a separate reasoned publication confirmation. A daily revision starts no earlier than the next Bangkok day; a weekly revision starts no earlier than the next Monday. A later configured start is normalized to the next applicable period. End dates are exclusive.
4. Editing a published mission creates a future draft. Its published revision and existing instances keep their original target, conditions and rewards. **Ngừng từ kỳ tiếp theo** archives future availability while preserving earned instances and claims. Delete is available only for a never-published draft. Clone creates a new code via the normal create endpoint.

Daily personal selection retains one easy, two normal and one hard/elite slot; weekly selection retains one normal, two hard and one elite slot. A short pool produces fewer distinct missions and a preview warning. Clan selection uses the same frozen period engine with clan/member progress identities. Changing the catalog after the period starts cannot change that frozen selection.

The supported actions are PLAY, WIN, CORRECT, ACCURACY, PERFECT_WIN, WIN_STREAK, CHECK_IN, DONATE_GOLD and DONATE_GEMS. WIN_STREAK uses the global consecutive-win counter across eligible casual and ranked matches; it cannot be restricted by match type. The admin hides and clears that condition when changing to WIN_STREAK, and server create/update/preview/publish validation rejects a non-null matchType. For example, two casual wins followed by one ranked win reach global streak 3, not a three-ranked-win streak. Clan streak targets count distinct members reaching their own global streak threshold, rather than adding streak lengths together. Mode conditions remain supported for PLAY, WIN, CORRECT, ACCURACY and PERFECT_WIN. Unsupported conditions and stale versions are rejected. A version conflict requires an authoritative reload. Identical unknown-result retries retain the entire request payload and request ID; changed input receives a new request ID.

Donation economics are deliberate: personal same-currency rewards cannot exceed the donation target; clan DONATE_GOLD cannot reward Gold, and clan DONATE_GEMS cannot reward Gems. Select XP or another asset for clan donation missions. Contribution points are separate from personal/clan XP and do not configure a weekly race or top payout.

## HTTP contracts

DTO authority is `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/MissionProtocol.kt`. Successful and failed mission queries/claims use `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`.

| Method | Path | Body / behavior |
|---|---|---|
| GET | `/admin/api/missions` | `page`, `pageSize`, optional `query`, `scope`, `state`; returns `{items,page,pageSize,total}` |
| POST | `/admin/api/missions` | `MissionDraftRequest` with `expectedVersion:0`, `draft`, `reason`, `requestId` |
| GET | `/admin/api/missions/{id}` | Current draft plus published-revision metadata |
| PUT | `/admin/api/missions/{id}/draft` | `MissionDraftRequest` using current version |
| POST | `/admin/api/missions/{id}/preview` | Server preview; Origin/CSRF required |
| POST | `/admin/api/missions/{id}/publish` | `MissionMutationRequest(expectedVersion,reason,requestId)` |
| POST | `/admin/api/missions/{id}/archive` | Same mutation contract; future availability only |
| DELETE | `/admin/api/missions/{id}/draft` | Same mutation contract; unused drafts only |
| GET | `/api/missions/personal` | Authenticated personal instance list; `/api/missions` is an alias |
| GET | `/api/missions/clan` | Current server membership; no membership returns an empty list |
| POST | `/api/missions/{instanceId}/claim` | Authenticated `{requestId}` only; recipient/reward/progress come from storage |

Player list rows include `instanceId`, stable `code`, scope/action/cadence/difficulty, exclusive period bounds, target/progress/personal contribution, reward, claim state, localization key/arguments, and optional action conditions. Claim returns `{instanceId,state,reward}`. Stable failures use `MissionApiError(code,message)`. Missing or failed mission storage is unavailable, never a successful empty-list fallback.

The app owns account/view/clan/request generations. Entry, explicit refresh and reconnect read fresh snapshots; old-account responses cannot replace the current view. A confirmed claim refreshes the authoritative profile instead of applying local asset arithmetic. Completed unclaimed older periods remain reachable. Loading, successful empty, retryable load failure, pending claim and claimed states are distinct.

## Migration, consistency and rollback

V64 creates catalog/revisions, frozen periods, instances, event/claim identities, request replay and contribution sources, and seeds the original 18 personal definitions once. V65 preserves seed ordering and owner cleanup. V66 records only clans present at cutover and their original target/rewards/member contributions/claimed flags. V1–V63 are unchanged. Startup imports legacy state idempotently before switching readers; it does not grant rewards or overwrite admin edits.

Legacy personal imports retain stored targets, rewards (including zero), progress, claim markers and old ledger keys. Legacy clan imports retain every member's old claim right, including an unclaimed member alongside already-claimed members. Leaving/rejoining does not reopen a prior claim and adds no new legacy contribution minimum. New clans have no synthetic legacy single quest; configured multiple clan missions use the new API. Older clients retain the compatible legacy snapshot/command path, which cannot represent the new configured clan list.

HTTP claims and old WebSocket personal/clan commands use one transactional claim store. New instance grants use `MISSION` plus instance UUID; imported personal grants retain `MISSION` plus `code:periodStart`; imported clan grants retain `CLAN_QUEST` plus clan UUID. Mission claims, wallet/XP changes, ledger entries and claim markers commit together. Event identity deduplicates replay while distinct committed events preserve progress. Publication is protected by an immutable-revision database trigger. Current membership is reread for clan claims. Donation and mission contribution events retain separate source identities; mission points are issued once.

Before rollout, back up the database and compare legacy/current progress, rewards, wallet and ledger identities on an owned migration fixture. Review both old-client compatibility and new-client endpoint availability. These migrations have no destructive down migration. Do not delete mission tables/revisions, undo grants, reset claim markers, or edit Flyway history as an application rollback. A rollback build must preserve the new schema and shared claim bridge; reverting to a pre-bridge writer could reopen an independent reward path. Rollout and any operational rollback require their own approval and compatibility checks. Local acceptance is not production rollout authorization.

## Reproduce local integrated acceptance

The opt-in runner `e2e/support/admin-missions-live.mjs` serves the actual built Kotlin/Wasm app and production admin assets against Ktor and PostgreSQL. It checks asset bytes and actual browser mutations, actual app HTTP responses and visible claim/reload state, a concurrent claim's literal wallet delta plus one ledger/claim row, server match/donation settlement and replay, immutable active snapshots after edit/archive, genuine storage failure/retry, membership change, legacy HTTP/WebSocket claims, and visible account switching.

The test-only `MissionAcceptanceLauncher.kt` lives under `server/src/test`, uses a guarded loopback database UUID schema, and is absent from `installDist`. Only the injected mission clock is file controlled: publish at yesterday's Bangkok noon, then advance it to today's noon. Authentication/admin clocks remain real time. Normal repository settlement emits current-time events. Test-source stdin invokes the actual committed match and donation repositories; no production clock/test endpoint, OS clock change, or published-revision rewrite is used.

Build sequentially with the project's JDK:

```powershell
./gradlew.bat :server:missionAcceptanceClasspath :server:installDist :webApp:wasmJsBrowserDevelopmentWebpack --console=plain --max-workers=1 --no-parallel
$env:E2E_POSTGRES_SETTINGS='<local validated test-settings JSON>'
$env:E2E_PSQL='<local psql executable>'
pnpm.cmd --dir e2e test:admin-missions-live
```

The settings must meet `scripts/lib/load-test-utils.mjs` guards (owned loopback `15432/admin_test`, private password file). The caller supplies `JAVA_HOME` and any existing JVM socket directory configuration. Ports 18086 and 18087 must be free. Keep the owned PostgreSQL process alive while the runner executes; it owns only its child schema, backend, frontend, browser contexts and temporary Owner password. Cleanup failures fail acceptance and retain a schema if its backend cannot be stopped.

Evidence is local under `.artifacts/admin-missions/task-4-live/`: sanitized `acceptance.json`, backend log, settled 375/1440px admin and actual Wasm screenshots. Tracing is disabled; tokens exist only in process/browser memory. The runner observes the app's current bearer for the concurrent request because a new login revokes previous sessions and reload rotates tokens. Legacy socket checks use another synthetic account to respect the existing single-connection behavior. No provider credentials or external URLs are inherited by the test backend.

The live account-switch case waits for the new token-free account metadata, then explicitly reloads before checking the second account mission and absence of the first account instance. It proves durable identity after reload; controller tests separately cover stale responses and immediate context clearing.

Host/browser rendering does not establish physical Android/iOS accessibility, touch, lifecycle or provider acceptance. Android emulator/device tests remain opt-in. Full suite counts, XML verification, screenshot inspection and known limitations for this delivery are recorded in the local Task 4 report.
