# Rà soát phản hồi sau khi nhận thưởng — 15/11/2026

Phạm vi: app dùng chung Android/web/iOS trong `shared/src/commonMain`. Các luồng popup đã được triển khai theo receipt hoặc sự kiện cấp thưởng và kiểm tra build trên thiết bị. Các đường dẫn bên dưới tính từ gốc repository. Popup xác nhận **trước giao dịch** không đồng nghĩa đã có popup báo **nhận thành công**.

## Phạm vi thực hiện hiện tại

Đã triển khai và kiểm tra popup liệt kê số nhận được cho Shop, gift code, hòm quà, điểm danh, nhiệm vụ và mở khóa thành tích/vật phẩm. Các mục còn lại là danh sách để người dùng xem xét.

| Luồng | Phản hồi hiện tại | Đề xuất để xem xét | Trạng thái / giới hạn dữ liệu |
|---|---|---|---|
| Gem → lượt chơi, Shop/Lượt chơi | Có dialog xác nhận trước đổi; thành công mở `RewardReceiptDialog` bằng `receivedPlays`, thay text thành công cũ. | Đã triển khai popup `+N lượt tích lũy`, đóng bằng nút xác nhận. | **Đã triển khai và kiểm tra.** HTTP chỉ trả `AdminOk`; số N lấy từ offer đã chọn trong `PLAY_CREDIT_OFFERS`, không phải receipt định lượng từ server. |
| Gem → Gold, Shop/Gold | Có dialog xác nhận; `ShopScreen` mở `RewardReceiptDialog` sau receipt `GRANTED`; đóng popup tiêu thụ receipt theo `requestId`. | Đã triển khai popup `+N Gold` sau `GRANTED`. | **Đã triển khai và kiểm tra.** Dùng `receivedGold` từ server; `ALREADY_GRANTED` không mở popup nhận mới, lỗi vẫn ở text của danh sách. Receipt chưa xác nhận được giữ khi mở lại Shop. |
| Nhập gift code, Hồ sơ/Gift code | `GiftCodeEntry` mở `RewardReceiptDialog` sau redeem thành công. | Popup liệt kê từng loại nhận được. | **Đã triển khai.** Chỉ giữ receipt mới của mã vừa nhập. |
| Hòm quà: nhận một quà thường hoặc quà bồi thường | `GiftMailboxController.applyReceipt` cập nhật item thành `CLAIMED`, giảm badge và mở popup receipt. | Popup thưởng của quà vừa nhận, giữ thông tin bồi thường ở thẻ. | **Đã triển khai.** Receipt lặp không mở popup mới. |
| Hòm quà: nhận tất cả | `claimAll` nhận theo batch và mở một popup tổng các receipt thành công. | Một popup tổng các quà nhận thành công; báo rõ nhận một phần và số quà lỗi. | **Đã triển khai.** Chỉ cộng các receipt được chấp nhận trong batch hiện tại. |
| Quà đua bang hội | Dùng cùng luồng nhận một/nhận tất cả của hòm quà; thẻ dùng `RaceReward` và trạng thái đã nhận. | Dùng popup hòm quà; có thể kèm tên tuần/hạng đua. | **Đã có popup qua hòm quà.** Receipt đua vẫn kiểm tra `campaignId`, `CLAIMED`, `entitlementId` và snapshot đóng băng. |
| Điểm danh hằng ngày | `DailyCheckInResult` mở `RewardReceiptDialog` sau khi `claimed=true`. | Popup Gold/XP/Gem ngay sau nhận thành công từ Home. | **Đã triển khai.** Không suy từ lịch hay chênh lệch ví. |
| Nhiệm vụ cá nhân ngày/tuần, kể cả kỳ cũ còn được nhận | `MissionController.claim` giữ receipt mới và `ServerMissionList` mở popup Gold/XP/Gem. | Popup Gold/XP/Gem của nhiệm vụ vừa nhận. | **Đã triển khai.** Receipt được tiêu thụ khi đóng. |
| Nhiệm vụ bang hội ngày/tuần | Cùng `MissionController`/`ServerMissionList`; popup hiển thị thưởng cá nhân. | Popup thưởng cá nhân; thể hiện điểm đóng góp riêng nếu có. | **Đã triển khai.** Điểm đóng góp không gộp vào XP người chơi. |
| Nhiệm vụ cá nhân/bang hội legacy | Cá nhân: `MissionRewardResult` mở `RewardReceiptDialog` khi `claimed=true`. Bang: `claimClanQuestReward` trả `ClanQuestRewardResult` gồm Gold/XP/Gem và mở popup. | Nếu vẫn cần hỗ trợ client/đường gọi legacy, thống nhất với receipt nhiệm vụ mới. | **Đã triển khai.** Receipt cá nhân tiêu thụ theo mã nhiệm vụ, receipt bang tiêu thụ theo request id. |
| Thành tích và mở khóa avatar/khung/danh hiệu theo mốc | `progressionNotifications` phát sự kiện khi profile trước/sau phát hiện mở khóa; app giữ hàng đợi và mở `ProgressionUnlockDialog` lần lượt. | Popup gộp các thành tích/phần thưởng vừa mở, nếu người dùng muốn. | **Đã triển khai.** Không phát lại khi đồng bộ lại cùng profile hoặc đổi tài khoản. |
| Kết toán mùa | **Đã có** `SeasonRewardSummaryDialog` toàn app với receipt Gold/Gem/cosmetic, xác nhận bằng `acknowledgeSeasonReward`; thêm thẻ ở BXH và lịch sử mùa. | Giữ popup hiện có; chỉ cân nhắc đồng nhất cách trình bày khi triển khai hệ thống chung sau này. | **Đã có popup, không phải chỗ thiếu.** Dùng `latestSeasonReward`, chỉ mở khi chưa `acknowledged`; không tạo popup mới khi đọc lịch sử. |
| Mua Gem bằng Google Play/App Store | Luồng thanh toán của nền tảng; popup `RewardReceiptDialog` mở sau `StorePurchaseResult.GRANTED` với số `gemsGranted`. | Popup `+N Gem` sau server xác minh và cấp thưởng. | **Đã triển khai.** `ALREADY_GRANTED` không mở popup mới; receipt được tiêu thụ theo `requestId`, còn lỗi giữ thông báo trạng thái. |
| Xem quảng cáo nhận lượt | Dialog hết lượt vẫn giữ trạng thái tải/xác minh. Sau `RewardedAdBonusResult.GRANTED`, app mở `RewardReceiptDialog` với `grantedMatches` từ server. | Popup lượt đã cấp sau xác minh server; giữ trạng thái chờ/retry trong dialog hiện có. | **Đã triển khai.** Server trả delta lượt trong receipt, `ALREADY_GRANTED` không mở popup mới và callback đóng quảng cáo không tự tạo receipt. |
| Thưởng trận online, thưởng sự kiện nhân Gold/XP và bonus bang trong trận | Màn kết quả đã có `RewardBreakdownCard`: thưởng gốc, bonus, tổng và nguồn sự kiện; lịch sử trận giữ breakdown. Đây là thẻ trên màn kết quả, không phải dialog. | Người dùng chọn giữ màn kết quả hoặc thêm popup; tránh hiển thị hai lần cùng phần thưởng. | **Chỉ liệt kê; đã có bảng chi tiết trên màn kết quả.** Dùng `MatchRewardBreakdown` server; event không có nút claim riêng. Nhánh `currentMatchId=null` dùng hằng số ở `MatchRewardCard`, không phải receipt server để khẳng định tiền vừa cấp. |
| Giải thưởng vô địch giải đấu | Server gửi `TournamentRewardResult` riêng cho đúng nhà vô địch sau khi ledger `TOURNAMENT_PRIZE` được ghi nhận; app mở popup Gold theo `requestId`. | Popup thưởng cho đúng người vô địch sau server xác nhận giải đã hoàn tất. | **Đã triển khai.** Không suy thưởng từ `prizePool` khi chỉ xem lịch sử; receipt có khóa `tournament-prize:{id}`. |

## Các khoản nhận liên quan nhưng không phải thưởng cá nhân thông thường

- **Đóng góp bang nhận XP bang:** `ClanDonationResult.APPLIED` mở popup có nhãn **Bang hội XP** và tiêu thụ theo request id. `DUPLICATE` không mở popup mới.
- **Hoàn phí giải đấu/hủy do bảo trì:** server gửi `TournamentRewardResult.ENTRY_REFUND` riêng theo người chơi, app mở popup Gold theo request id. Ledger vẫn phân biệt `TOURNAMENT_ENTRY_REFUND` và `TOURNAMENT_MAINTENANCE_REFUND`.
- Lịch sử ví, lịch sử trận, lịch sử mùa và trang chi tiết sự kiện là nơi **xem lại**; mở lại không được kích hoạt popup nhận mới. Lưu hồ sơ, kết bạn, chấp nhận lời mời và trang bị vật phẩm không thuộc danh sách nhận thưởng.

## Điểm code đã đối chiếu

Các symbol là mốc chính để tra cứu vì số dòng có thể thay đổi cùng đợt chỉnh sửa UI:

| Khu vực | UI / handler / dữ liệu |
|---|---|
| Shop, gift code | `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/PlayCreditAndGiftCode.kt`: `PlayCreditShop`, `GiftCodeEntry`; `ui/screens/ShopScreen.kt`: `GoldExchangeList`, `GemStorePreview`; `FastToWinApp.kt`: nhánh `state.isShopOpen`, `onRedeemGiftCode`; `state/GameController.kt`: `GoldExchangeResult`, `StorePurchaseResult`, `onStorePurchaseFinalized`; `protocol/.../PlayCreditProtocol.kt`; `server/.../gifts/GiftService.kt`: `exchangePlays`, `claim`. |
| Hòm quà, đua bang | `shared/.../state/GiftMailboxController.kt`: `claim`, `claimAll`, `applyReceipt`, `validRaceReceipt`; `ui/screens/GiftMailboxScreen.kt`: `GiftCard`, `GiftRewardAmounts`; `ui/screens/ClanRaceScreen.kt`: kết quả settlement, `RaceReward`; `protocol/.../GiftProtocol.kt`. |
| Điểm danh, nhiệm vụ | `shared/.../ui/screens/HomeScreen.kt`: `DailyCheckInCard`; `ui/screens/ProfileScreen.kt`: text `state.profileNotice`, `ProfileSection.MISSIONS`; `state/GameController.kt`: `DailyCheckInResult`, `MissionRewardResult`, `claimClanQuestReward`; `state/MissionController.kt`: `claim`; `ui/components/ServerMissionList.kt`: `ServerMissionCard`; `ui/screens/ClanScreen.kt`: `ServerMissionList`; `protocol/.../MissionProtocol.kt`; `server/.../GameEngine.kt`: `claimClanQuestReward`; `server/.../missions/PostgresMissionRepository.kt`: `claimLegacyPersonal`, `claimLegacyClan`. |
| Thành tích, mùa | `shared/.../state/AppNotification.kt`: `progressionNotifications`; `ui/screens/ProfileScreen.kt`: `StatisticsAchievementsSectionContent`; `FastToWinApp.kt`: `pendingSeasonReward`, `GlobalPrompt.SEASON_SUMMARY`; `ui/components/SeasonProgressCard.kt`: `SeasonRewardSummaryDialog`, `SeasonRewardReceiptCard`; `state/GameController.kt`: `acknowledgeSeasonReward`; `protocol/.../GameProtocol.kt`: `AchievementSnapshot`, `SeasonRewardReceiptSnapshot`. |
| Ads, mua Gem | `shared/.../ui/components/PlayQuotaDialog.kt`: `PlayQuotaExhaustedDialog`; `platform/RewardedAds.kt`: `rewardedAdFeedback`; `state/GameState.kt`: `withRewardedAdBonus`; `state/StorePurchaseFinalizer.kt`; `localization/LocalizedMessageMapper.kt`: `message(RewardedAdBonusResult)`; `protocol/.../GameProtocol.kt`: `StorePurchaseResult`, `RewardedAdBonusResult`. |
| Trận, sự kiện, giải đấu | `shared/.../ui/screens/ResultScreen.kt`: `RewardBreakdownCard`, `MatchRewardCard`, `TournamentResultCard`; `ui/components/RewardEventBanner.kt`: `RewardBreakdownCard`; `ui/screens/ProfileScreen.kt`: breakdown trong lịch sử; `ui/screens/RewardEventDetailsScreen.kt`; `ui/screens/TournamentScreen.kt`: nhánh `FINISHED`, `TournamentCancelledRefund`; `protocol/.../RewardEventProtocol.kt`; `server/.../TournamentRepository.kt`: ledger `TOURNAMENT_PRIZE`, `cancelWithRefund`. |

## Nguyên tắc khi triển khai tiếp

1. Trigger từ kết quả được cấp thưởng cho đúng tài khoản/yêu cầu; không trigger từ text notice chung, refresh profile hay chênh lệch tổng ví. Tiêu thụ sự kiện khi đóng để đổi tab, quay lại màn, recomposition hoặc retry không mở lại.
2. Receipt idempotent có thể mô tả khoản đã cấp trước đó. Đặc biệt quà và nhiệm vụ vẫn trả số thưởng gốc trên replay; chỉ nhìn số dương hoặc trạng thái `CLAIMED` là chưa đủ chứng minh lần cấp mới.
3. Theo design system Arcade hiện có: tiêu đề rõ, từng loại thưởng có nhãn và số, icon thống nhất, nút đóng dễ chạm; hỗ trợ màn nhỏ/chữ lớn và vùng nội dung cuộn. Giữ lỗi và trạng thái chờ tại vị trí thao tác. Khi thực sự triển khai phải xem ảnh điện thoại/desktop và kiểm tra accessibility.
4. Áp dụng skill `.agents/skills/ui-ux-pro-max/SKILL.md`, `references/pro-rules.md` và đối chiếu component Arcade hiện hữu; các luồng còn thiếu vẫn giữ trong danh sách để người dùng xem xét.

## Kiểm chứng đợt UI này

- Protocol: 129 bài qua. Shared host: 519 bài qua, 1 bài PostgreSQL auth recovery bỏ qua do lượt này không cấp fixture database cho kiểm thử đó; không có lỗi. Regression giữ receipt Gold chưa đọc khi rời/mở lại Shop đã được quan sát thất bại trước sửa và qua sau sửa.
- 50 ảnh Compose cho các phần chỉnh sửa và 16 ảnh bảo trì có nhúng bàn chơi đã render/validate qua; có điện thoại, desktop và chữ lớn. JS và Wasm compile qua. 24 bài UI liên quan qua trên Samsung SM-A366B, tính theo từng testcase thành công; lượt BXH dài bị ngắt kết nối ở bài cuối nên bài đó được chạy riêng và qua.
- APK Dev cuối đã được build thành công, cài lại giữ dữ liệu local và mở sẵn màn Home trên Samsung SM-A366B `RFGYC1RJC3T`. Popup mở khóa thành tích/vật phẩm dùng hàng đợi chống phát lại theo notification id. Popup IAP dùng `requestId` của receipt server. Popup quảng cáo dùng `requestId` và `grantedMatches`. Backend local cùng kết nối USB vẫn được giữ để test thủ công.
- Preview: `.artifacts/ui-preview-20261114/ui-refinements.html`. Kết quả testcase máy thật: `.artifacts/ui-preview-20261114/ui-refinement-device-passed.json`.
