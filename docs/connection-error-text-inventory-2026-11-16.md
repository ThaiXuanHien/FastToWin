# Các thông báo liên quan kết nối có thể hiển thị màu đỏ

Lưu ý: đây là snapshot trước thay đổi UX ngày 16/11. Bản source sau đó thay câu ServerUnavailable và cách hiển thị lỗi mạng/dịch vụ theo `connection-recovery-ux-2026-11-16.md`. Các bảng dưới giữ nguyên để đối chiếu thông báo cũ.

Rà source ngày 16/11/2026 tại checkout `codex/room-ownership-fix`, nền `4b4c2bcb`. Phạm vi: Android/Web người chơi, các API liên quan và trang admin. Danh sách lấy bản tiếng Việt; không gồm lỗi nhập liệu, số sai, thiếu tiền hay lỗi nghiệp vụ không liên quan kết nối. Đây là rà source, không phải xác nhận từng câu đã xuất hiện trên binary production 1.0.12 (13).

Màu đỏ được quyết định ở nơi render (`MaterialTheme.colorScheme.error`, Coral hoặc CSS `.alert`), không gắn cố định với TextKey. Một câu có thể đỏ ở màn này nhưng màu thường ở màn khác. Nội dung động lấy từ server/SDK không thể liệt kê thành tập câu hữu hạn.

## Android/Web: kết nối, phiên và phòng

| # | Nguyên văn hiển thị tiếng Việt | Nguồn / điều kiện |
| --- | --- | --- |
| 1 | Dịch vụ tạm thời không khả dụng. Vui lòng thử lại. | `ServerUnavailable`: lỗi mạng, socket, API hoặc dịch vụ phụ trợ. Có thể đỏ tại đăng nhập, Home, danh sách/phòng chờ, hồ sơ, bạn bè, giải đấu. |
| 2 | Chưa kết nối máy chủ | `ConnectionDisconnected`: đỏ khi ngắt kết nối làm gián đoạn lưu hồ sơ (`profileSaveError`). Nhãn trạng thái ở Home dùng màu thường. |
| 3 | Phòng đang đồng bộ. Vui lòng thử lại. | `ROOM_RECOVERING`: chuỗi fallback của ownership, chưa có ánh xạ TextKey riêng; đi vào `state.error`. |
| 4 | Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại. | `ServerSessionExpired`, hoặc `GiftErrorUnauthorized` tại hộp thư quà. Gộp nhiều mã hết hạn/không hợp lệ/thay thế phiên. |
| 5 | Hãy đăng nhập để tiếp tục. | `ServerAuthRequired`: phiên/tài khoản chưa được server chấp nhận. Không tự chứng minh có lỗi mạng. |
| 6 | Chưa có phiên khách để lưu. Hãy vào danh sách phòng rồi thử lại. | `GuestSessionMissing`: nâng cấp/lưu tiến độ khách khi không có phiên khách. |
| 7 | Phiên bản ứng dụng không còn tương thích. Vui lòng cập nhật ứng dụng. | `ServerProtocolMismatch`: phiên bản giao thức client/server không khớp. |
| 8 | Yêu cầu không hợp lệ. Vui lòng thử lại. | `ServerInvalidRequest`: gồm `PROTOCOL_DECODE_FAILED`, không chỉ lỗi dữ liệu nhập. |
| 9 | Phòng không còn khả dụng. | `ServerRoomNotFound` hoặc `RoomUnavailable`: gồm phòng không tìm thấy và client không thấy phòng trong danh sách hiện tại. |
| 10 | Phòng trong liên kết không còn tồn tại hoặc đã đủ người. | `RoomLinkUnavailable`: deep link không khớp danh sách phòng mới nhận. |
| 11 | Bạn không ở trong phòng này. | `ServerNotInRoom`: server từ chối thao tác do tư cách thành viên phòng. |
| 12 | Người chơi đã mất kết nối quá lâu. | `RoomDisconnectedTooLongNotice`: reconnect không còn trong thời gian cho phép; có thể đưa về danh sách phòng với lỗi đỏ. |

Các câu #5–#12 là thông báo liên quan phiên/phòng, không phải bằng chứng rằng mạng là nguyên nhân.

### Những mã đang bị gộp thành cùng câu #1

`DATABASE_REQUIRED`, `FRIENDS_UNAVAILABLE`, `NOTIFICATIONS_UNAVAILABLE`, `NOT_NOTIFICATIONS_UNAVAILABLE`, `LEADERBOARD_UNAVAILABLE`, `FRIEND_PROFILE_UNAVAILABLE`, `EMAIL_DELIVERY_UNAVAILABLE`, `EMAIL_DELIVERY_FAILED`, `NETWORK_ERROR`, `CONNECTION_FAILED`, `CONNECTION_NOT_READY`, `SEND_FAILED`, `IOS_KEYCHAIN_SAVE_FAILED`, `DAILY_CHECK_IN_UNAVAILABLE`, `MISSION_REWARD_UNAVAILABLE`, `PASSWORD_RESET_DELIVERY_UNAVAILABLE`, `PROFILE_UPDATE_UNAVAILABLE`, `PUSH_PREFERENCES_UNAVAILABLE`, `WALLET_HISTORY_UNAVAILABLE`, `FRIENDS_PRESENCE_UNAVAILABLE`, `CLAN_PRESENCE_UNAVAILABLE`.

`RATE_LIMIT_UNAVAILABLE` cũng chỉ định trực tiếp `messageKey=ServerUnavailable` trong phản hồi rate-limit. Vì vậy câu #1 không đủ để phân biệt socket, Redis presence, rate-limit hay API khác. `SEND_FAILED` có nhánh ánh xạ/xử lý nhưng không thấy nơi phát mã này trong client socket hiện tại.

### Các câu liên quan khác có thể vào vùng lỗi chung

- “Bạn thao tác quá nhanh. Vui lòng thử lại sau {seconds} giây.” — `RATE_LIMITED` / `TOO_MANY_REQUESTS`; có thể do yêu cầu lặp khi reconnect, nhưng không phải lỗi mất mạng.
- “Không thể hoàn tất thao tác. Vui lòng thử lại.” — `ServerActionFailed`; gồm `OPPONENT_LEFT`, `GAME_NOT_PLAYING` và nhiều lỗi nghiệp vụ khác.
- “Đã có lỗi xảy ra. Vui lòng thử lại.” — `UnknownError`, khi không có thông điệp fallback dùng được.

## Android/Web: API chức năng và nội dung cộng đồng

| # | Nguyên văn | Nơi hiển thị đỏ |
| --- | --- | --- |
| 13 | Kết nối bị gián đoạn. Đóng hộp thoại và thực hiện lại thao tác khi đã kết nối. Nội dung chưa được công bố. | Hộp thoại quy tắc nội dung (`UgcConnectionInterrupted`). |
| 14 | Không xác nhận được quy tắc. Kết nối lại và thử lại. Nội dung chưa được công bố. | Hộp thoại quy tắc nội dung (`UgcTermsFailed`). |
| 15 | Chưa xác nhận được báo cáo. Thử lại khi kết nối ổn định; nội dung đã nhập được giữ lại. | Hộp thoại báo cáo (`UgcReportFailed`). |
| 16 | Mất kết nối. Thử lại để kiểm tra quà đã được nhận hay chưa. | Hộp thư quà (`GiftErrorNetwork`). |
| 17 | Dịch vụ quà tạm thời không khả dụng. Vui lòng thử lại. | Hộp thư quà (`GiftErrorStorage`). |
| 18 | Không thể nhận quà. Vui lòng thử lại. | Fallback lỗi không được phân loại tại hộp thư quà (`GiftErrorGeneric`); không chỉ lỗi mạng. |
| 19 | Không thể cập nhật nhiệm vụ. Hãy thử lại. | Danh sách nhiệm vụ (`MissionRequestError`), cho cả lỗi tải/nhận thưởng. |
| 20 | Không thể tải lại cuộc đua. Vui lòng thử lại. | Trang cuộc đua bang (`ClanRaceError`). |

## Android: kết nối Google Play / thanh toán

Đây là kênh với Google Play, khác với WebSocket trận đấu. Các câu đi qua `billingState.error` và được tô đỏ trong Shop; không phải tất cả đều do mất mạng.

| # | Nguyên văn | Khi liên quan |
| --- | --- | --- |
| 21 | Google Play Billing chưa sẵn sàng. | Thiết lập kết nối Billing không thành công. |
| 22 | Không tải được giá từ Google Play. | Yêu cầu danh sách sản phẩm/giá thất bại. |
| 23 | Google Play Billing không khả dụng trên thiết bị. | Tính năng Billing không được hỗ trợ. |
| 24 | Thanh toán chưa hoàn tất. | Fallback nhiều mã lỗi cập nhật thanh toán, có thể gồm gián đoạn dịch vụ/mạng. |

`billingState.rawErrorFallback` cũng có thể hiển thị đỏ với nội dung động. Các lỗi cấu hình sản phẩm/App Store, gói không có sẵn và không mở được thanh toán là nhóm cấu hình/nghiệp vụ khác, không xếp thành lỗi kết nối trong bảng này.

## Trang admin

Vùng `feedback().error` là `.alert` màu đỏ. Các chuỗi cố định liên quan mạng/tải module/phiên:

1. “Không thể kết nối. Vui lòng thử lại.”
2. “Kết nối đã thay đổi. Kết quả chưa được xác nhận; vui lòng thử lại.”
3. “Phiên quản trị đã hết hạn. Vui lòng đăng nhập lại.”
4. “Yêu cầu không thành công. Vui lòng thử lại.” — fallback cho phản hồi HTTP lỗi thiếu `message`.
5. “Không thể tải thống kê. Vui lòng thử lại.”
6. “Không thể tải quản lý bảo trì. Vui lòng thử lại.”
7. “Không thể tải chiến dịch quà. Vui lòng thử lại.”
8. “Không thể tải sự kiện thưởng. Vui lòng thử lại.”
9. “Không thể tải nhiệm vụ. Vui lòng thử lại.”
10. “Không thể tải tuần đua. Vui lòng thử lại.”
11. “Không thể tải đền bù. Vui lòng thử lại.”
12. “Không thể tải thông báo. Vui lòng thử lại.”
13. “Không thể tải kiểm duyệt. Vui lòng thử lại.”

Các mẫu nối thêm vào `error.message`, hoặc vào fallback “Không thể kết nối.”:

- “ Kết quả chưa được xác nhận; thử lại cùng nội dung sẽ giữ nguyên mã yêu cầu.” — thông báo, đền bù, sự kiện.
- “ Kết quả chưa được xác nhận; thử lại cùng nội dung giữ nguyên mã yêu cầu.” — cuộc đua, nhiệm vụ.
- “ Thử lại cùng nội dung sẽ giữ nguyên mã yêu cầu.” — kiểm duyệt.

Admin giữ lại `body.message` từ server hoặc `e.message`, nên có thể có câu động khác. “Unsafe controller”, “Worker update incomplete”, “Worker update timeout”, “Controller changed” là exception nội bộ của kiểm tra service worker; đường gọi chuẩn bắt chúng rồi chuyển sang màn “Cần cập nhật trang quản trị”, không hiển thị nguyên văn thành lỗi đỏ cho người dùng.

## Có liên quan kết nối nhưng không được tô đỏ tại nơi render đã rà

- “Mất kết nối, đang thử lại…” — nhãn Home.
- “Mất kết nối. Đang thử kết nối lại…” và “Đang kết nối lại trận đấu…” — thanh trạng thái trận, màu `onSurfaceVariant`.
- “Mất kết nối. Đang khôi phục trận, phòng được giữ tối đa 30 giây…” và “Bàn số bị khóa cho đến khi máy chủ khôi phục trận.” — overlay chữ trắng.
- “TẠM MẤT\nKẾT NỐI”, mô tả offline và “Đây là lỗi kết nối, không phải máy chủ đang bảo trì.” — màn offline, không phải lỗi đỏ chung.
- “Đang chờ kết nối. Kết nối lại để tải và tham gia giải đấu.” — trình duyệt giải đấu, màu `onSurfaceVariant`.
- “Đang kết nối lại Google Play…” — `billingState.notice`, màu primary.
- “Không thể kiểm tra thông báo. Hãy thử lại khi có kết nối.” và “Chưa xác nhận được kết quả lưu. Thử lại để xác nhận cùng lựa chọn.” — khu vực thông báo/cài đặt, không chỉ định màu error tại nơi render đã rà.
- “Không thể kiểm tra sự kiện. Hãy thử lại khi có kết nối.” và “Dịch vụ sự kiện tạm thời không khả dụng.” — trang/banner sự kiện, màu thường tại các nơi render đã rà.
- Lỗi tải thông tin bang (`ClanLoadError`) nhận nội dung lỗi nhưng render `onSurface`, không phải màu đỏ.
- “Người chơi đã mất kết nối quá lâu.” khi được chuyển vào `rematchNotice` trên màn kết quả dùng màu `onSurfaceVariant`; cùng câu có thể đỏ khi hiển thị ở danh sách phòng.

## Nguồn chính để đối chiếu

- `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/ProtocolMessageKeys.kt`: mã lỗi → TextKey.
- `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/catalogs/`: các câu tiếng Việt.
- `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/MissionPlayerTexts.kt`: nhiệm vụ.
- `shared/src/commonMain/kotlin/com/hienthai/fastowin/localization/LocalizedMessageMapper.kt`: ưu tiên messageKey, rồi code, rồi fallback.
- `shared/src/commonMain/kotlin/com/hienthai/fastowin/data/network/GameSocketClient.kt`: phát lỗi socket.
- `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameController.kt`: lỗi chung, mất phiên/phòng và profileSaveError.
- `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/`: AuthScreen, HomeScreen, LobbyScreen, ProfileScreen, FriendsScreen, ShopScreen, GiftMailboxScreen, ClanRaceScreen, TournamentScreen.
- `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/UgcDialogs.kt`, `ServerMissionList.kt`: lỗi nội dung/nhiệm vụ.
- `server/src/main/kotlin/com/hienthai/fastowin/server/ownership/`: fallback ROOM_RECOVERING.
- `server/src/main/resources/admin/admin.js`, `admin.css` và các module: thông báo admin.

Không sửa hành vi app hoặc bản vá reconnect trong lần rà này; không chạy CI, không triển khai.
