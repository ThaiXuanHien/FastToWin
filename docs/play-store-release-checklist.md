# Google Play setup — approved implementation, 2026-10-20

Scope: Android internal 1.0 (1), package `com.hienthai.fastowin`. Support: xtit6789@gmail.com.

## Public documents

- `/privacy`, `/delete-account`, `/terms`: Vietnamese; append `/en` for English.
- Static HTML, no login/JavaScript required, network-only in the new PWA worker.
- Settings links use the selected VI/EN language. Existing game/admin design is preserved.
- Policy covers account/profile/photo/activity/purchase/FCM data and the current Railway/R2/Brevo providers.
- Current internal 1.0 (1) has no active AdMob. Before shipping #51/#52, re-audit that actual SDK configuration and update the policy/Data safety. Do not silently add advertising declarations for an unshipped branch.
- Deletion by verified support request: within 7 working days after ownership verification. In-app deletion remains password-protected and immediate in the active database; The current R2 avatar queues for cleanup immediately on account deletion (V61); previously replaced avatars may wait 24 hours. Retries/manual handling may delay physical deletion.
- Retention is 90 days for authenticated connection history, not all player data. Anonymous statistics may remain. FastToWin-managed recovery backups: 14 days, subject to the operational gate below.

## Backup operational gate before publication

Verified 2026-10-20:

- Railway production Postgres Backups UI: **No Backups**; current plan asks for Pro to create automatic volume backups. No plan upgrade purchased and no managed backup deleted.
- Main checkout inventory contained 12 PostgreSQL custom dumps. Two dated 2026-09-12 were permanently deleted with explicit owner approval; ten newer dumps remained.
- `scripts/local-backup-retention.ps1` defaults to reporting. `-Apply` uses ShouldProcess, caps retention at 14 days, rejects paths outside project `.artifacts` / `deploy/backups`, skips links and unrecognized formats, and rechecks timestamps before deletion.
- Owner approved daily/logon Windows cleanup of expired `.dump` backups in those two directories. Installed `FastToWin-LocalBackupRetention`: daily 01:30 local workstation time plus logon, StartWhenAvailable, Limited/Interactive principal, PowerShell 7. Verified scheduled run result **0** on 2026-10-20; ten current dumps and zero expired dumps. Private evidence: main checkout `.artifacts/privacy-ops/task-verification.json` and `task-definition.xml`. The installed script is a private copy under `.artifacts/privacy-ops`; after script updates, redeploy that copy and verify a scheduled run again.
- A powered-off or signed-out workstation cannot execute cleanup; StartWhenAvailable/logon handles the next session. Before taking this workstation offline for an extended period, remove any retained player-data dumps or transfer them only to storage with an approved expiry rule. Do not assume local scheduling is always-on cloud retention.
- New backup formats, exported data, worktree copies and off-host destinations must be inventoried and assigned expiry; the dump cleaner does not claim to erase these automatically. Do not upload database backups to Git, issues or Play Console.

Restore rule: keep service in maintenance after restoring an older backup, reconcile every account deletion since that backup, reapply deletion/identity unlink/avatar cleanup, verify the result, then reopen service. If deletion records cannot be reconciled, do not reopen the restored data; select a clean recovery point or resolve the gap first. Support should retain only the minimum private deletion reconciliation record until all potentially affected backups expire. A restore is not permission to reactivate deleted accounts.

## Store listing draft (Vietnamese)

Name: Fast To Win

Short description:

Thử thách tìm số 1–50, đấu bạn bè và chinh phục bảng xếp hạng.

Full description:

Fast To Win là trò chơi tìm số theo tốc độ. Quan sát bảng số, tìm các số từ 1 đến 50 theo thứ tự và hoàn thành thử thách thật nhanh.

Chọn cách chơi phù hợp với bạn:

- Đấu trực tuyến 1 đối 1, tạo phòng hoặc tham gia cùng bạn bè.
- Thi đấu xếp hạng và theo dõi Elo, thành tích qua các mùa giải.
- Luyện tập offline để thử sức với bảng 50 số mà không ảnh hưởng Elo.
- Tham gia giải loại trực tiếp dành cho 4, 8 hoặc 16 người chơi.
- Tham gia bang hội, hoàn thành nhiệm vụ và nhận phần thưởng trong game.
- Tùy chỉnh hồ sơ và mở khóa vật phẩm bằng Vàng/Gem.

Fast To Win hỗ trợ tiếng Việt và tiếng Anh. Chế độ trực tuyến cần kết nối Internet. Ghép cặp tự động và đấu xếp hạng cần tài khoản đã xác minh email; bạn có thể luyện tập offline hoặc dùng phòng ở chế độ khách.

Asset gate: 512×512 icon from the existing brand, 1024×500 feature graphic, 2–8 truthful phone screenshots. Never show another player's private information or unshipped ads/features. Confirm the selected screenshots match the release submitted.

## Console completion and review gate

The initial console checkpoint was 8/11 tasks. On 2026-10-20, privacy URL, Data safety and the Vietnamese store listing/assets were saved. The dashboard now says “Sẵn sàng xuất bản trò chơi” and no longer shows the setup checklist. Changes have not been sent for Google review; the app remains a draft and production access is inactive. Console currently requires a closed test with at least 12 opted-in testers continuously for 14 days (currently zero closed-test opt-ins). Internal testers do not satisfy that separate gate. No public review submission is authorized by saving metadata.

Data safety worksheet: account name/email/user ID/gender, avatar photos, interactions/other user content, purchase history, FCM installation/device identifiers. Distinguish collection from sharing, providers from independent third parties, required/optional data by actual flow, and ephemeral processing from retained logs. Do not mark all data optional merely because offline play exists.

UGC reporting and terms acceptance before content creation are tracked separately in #53; publishing a terms page alone does not implement that gate. Keep #28 open for Play products/pricing/API/license/IAP testing and device acceptance. Do not close these based on metadata completion.

Sources: Google Play account deletion https://support.google.com/googleplay/android-developer/answer/13327111 ; Data safety https://support.google.com/googleplay/android-developer/answer/10787469 ; assets https://support.google.com/googleplay/android-developer/answer/9866151 ; Railway backups https://docs.railway.com/volumes/backups .

## Data safety draft checkpoint — 12 types completed in Console

All entries: collected, no third-party sharing declared under service-provider/user-initiated transfer exceptions. No advertising purpose is declared for internal 1.0 (1). No precise/approximate location, phone address book access, installed-app inventory, audio/video, Crashlytics/Analytics SDK, or independent security certification is asserted.

| Type | Required/optional | Ephemeral | Purposes |
|---|---|---|---|
| Nickname/name | Required, including persistent guest nickname | No | Functionality, account management |
| Email | Optional account-only flow; guest rooms work without signup | No | Functionality, account management, security |
| User IDs | Required, including guest/account IDs | No | Functionality, account management, analytics, security |
| Other personal info (profile gender selection) | Optional account-only profile flow | No | Functionality, account management, personalization |
| Purchase history | Optional purchase flow | No | Functionality, security |
| Photos | Optional avatar upload | No | Functionality, account management, personalization |
| Contacts | Required in-game social graph, including automatic recent opponents | No | Functionality |
| App interactions | Required guest/account session handling and authenticated activity | No | Functionality, analytics, security |
| In-app search history | Optional clan search terms | Yes | Functionality |
| Other user-generated content | Optional clan/profile text | No | Functionality |
| Other actions | Required core gameplay/progression | No | Functionality, analytics, security |
| Device/other IDs | FCM/Firebase Installations automatically collected | No | Functionality, developer communications |

Source clarification: `GameProtocol.GetClanList(query)`, `GameEngine.getClanList` and `PostgresClanRepository.getClanList` use a transient SELECT/ILIKE query, with no search-history write in that flow. `FriendSnapshot`, friend requests and `PostgresFriendRepository` persist the in-game social graph. Google includes social-graph information in its Contacts definition; this does not imply READ_CONTACTS or address-book upload. Firebase official guidance documents automatic FID collection. Delivery metrics require enabling BigQuery export; notification interaction events require the Analytics SDK. Neither configuration is enabled here.

Console declarations were saved after reviewing the final preview and live privacy/deletion URLs. This is not Google review approval. Guest players can create/join online rooms; automatic matchmaking/ranked play requires a verified registered account. Nicknames, guest/account IDs, recent-opponent relationships and gameplay/session data still have required collection paths. Email and profile gender are optional at app level because guest rooms work without registration. Reassess collection and optionality when these flows change.


## Release evidence and remaining gates — 2026-10-20

- Web deployment `b285de01-34f5-45bd-9807-46ee0c6f3b86`: SUCCESS. All six public legal URLs returned HTTP 200 as static HTML with `Cache-Control: no-cache`; home/admin/health also returned 200. Backend/admin were not redeployed.
- Public entry points: https://fasttowin.up.railway.app/privacy , https://fasttowin.up.railway.app/delete-account , https://fasttowin.up.railway.app/terms ; append `/en` for English.
- Source: PR #54, `codex/play-store-legal`. Web compatibility distribution, Android production-debug compilation, JS/Wasm compilation, 108 protocol JVM tests, 8 worker tests, both Caddy routing suites and local retention safety checks passed before deployment.
- CI screenshot comparison found exactly two Settings previews changed by the new service-information section (phone and large text). Rendered output/diffs were inspected; only those two references were updated from canonical CI renders. This is host-rendered preview coverage, not physical-device acceptance.
- Store phone images are 840×1491 native Compose fixture renders, with fully opaque alpha removed without changing RGB pixels. They meet basic upload dimensions; they do not satisfy the separate 1080×1920 recommendation target. Their provenance is recorded in `artwork/play-store/README.md`.
- Current Play internal version remains 1.0 (1); no new AAB was uploaded. The new Settings links require a subsequent signed Android internal release. No Samsung was connected during this verification.
- Keep #53 open for UGC reporting/moderation and acceptance before content creation. Keep #28 open for Play products/prices/provider configuration, actual purchase tests and device acceptance. Metadata completion does not close these gates or authorize a public review submission.
- Evidence is private in the main checkout `.artifacts/play-legal-20261020/`; never upload reviewer credentials or database dumps to public issues.
