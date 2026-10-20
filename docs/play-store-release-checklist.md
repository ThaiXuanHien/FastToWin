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

Fast To Win hỗ trợ tiếng Việt và tiếng Anh. Các tính năng trực tuyến cần kết nối Internet và tài khoản đã xác minh email; chế độ luyện tập offline không cần đối thủ trực tuyến.

Asset gate: 512×512 icon from the existing brand, 1024×500 feature graphic, 2–8 truthful phone screenshots. Never show another player's private information or unshipped ads/features. Confirm the selected screenshots match the release submitted.

## Console completion and review gate

The initial console checkpoint was 8/11 tasks. App access, category/contact, IARC and target ages were saved. Privacy policy, Data safety and store listing remain to be verified in Console after public URLs/assets are available. No public review submission is authorized by saving a draft.

Data safety worksheet: account name/email/user ID/gender, avatar photos, interactions/other user content, purchase history, FCM installation/device identifiers. Distinguish collection from sharing, providers from independent third parties, required/optional data by actual flow, and ephemeral processing from retained logs. Do not mark all data optional merely because offline play exists.

UGC reporting and terms acceptance before content creation are tracked separately in #53; publishing a terms page alone does not implement that gate. Keep #28 open for Play products/pricing/API/license/IAP testing and device acceptance. Do not close these based on metadata completion.

Sources: Google Play account deletion https://support.google.com/googleplay/android-developer/answer/13327111 ; Data safety https://support.google.com/googleplay/android-developer/answer/10787469 ; assets https://support.google.com/googleplay/android-developer/answer/9866151 ; Railway backups https://docs.railway.com/volumes/backups .
