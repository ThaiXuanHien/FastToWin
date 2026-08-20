# Implementation Plan - Sửa lỗi encoding tiếng Việt

Sửa các lỗi hiển thị ký tự lạ trong tiếng Việt (encoding issues) trên toàn bộ project, bao gồm cả backend và frontend.

## Proposed Changes

### [Protocol]

#### [MODIFY] [GameProtocol.kt](file:///D:/HienTX/Work/Android/FastToWin/protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GameProtocol.kt)
- Sửa tên các hạng xếp hạng (`RankedTier`): "Đồng", "Bạc", "Vàng", "Bạch kim", "Kim cương", "Cao thủ".
- Sửa tên các vật phẩm trong shop (`SHOP_ITEMS`): "Bài Lưng Vàng", "Bài Lưng Kim Cương", "Bàn Cờ Tối", "Khu Rừng".

---

### [Server]

#### [MODIFY] [GameEngine.kt](file:///D:/HienTX/Work/Android/FastToWin/server/src/main/kotlin/com/hienthai/fastowin/server/GameEngine.kt)
- Sửa rất nhiều chuỗi thông báo lỗi bị lỗi encoding (đặc biệt là các lỗi double-encoded như "TÃƒÂªn ngÃ†Â°Ã¡Â»Âi chÃ†Â¡i").
- Các chuỗi như "Tên người chơi không được để trống", "Phiên chơi không còn hợp lệ", "Đã tham gia giải",...

#### [MODIFY] [PostgresPlayerProfileRepository.kt](file:///D:/HienTX/Work/Android/FastToWin/server/src/main/kotlin/com/hienthai/fastowin/server/PostgresPlayerProfileRepository.kt)
- Sửa "TÃ¢n binh" thành "Tân binh".
- Sửa các tên khung và danh hiệu khác: "Khung cơ bản", "Khung Đồng", "Khung Vàng", "Khung Hoàn hảo", "Khung Bền bỉ", "Nhà vô địch", "Tia chớp", "Chuyên cần", "Ảnh đại diện Điểm danh".
- Sửa "Đang phân hạng" và "Đối thủ".

#### [MODIFY] [Application.kt](file:///D:/HienTX/Work/Android/FastToWin/server/src/main/kotlin/com/hienthai/fastowin/server/Application.kt)
- Sửa các chuỗi thông báo lỗi HTTP và WebSocket.

#### [MODIFY] [Authentication.kt](file:///D:/HienTX/Work/Android/FastToWin/server/src/main/kotlin/com/hienthai/fastowin/server/Authentication.kt)
- Sửa các thông báo đăng ký, đăng nhập, đổi mật khẩu.

---

### [Shared UI State]

#### [MODIFY] [GameState.kt](file:///D:/HienTX/Work/Android/FastToWin/shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameState.kt)
- Sửa `DEFAULT_OPPONENT_NAME` thành "Đối thủ".
- Sửa chuỗi "Bạn" trong `PlayerState`.

## Verification Plan

### Manual Verification
- Kiểm tra lại các file đã sửa đảm bảo không còn ký tự lạ (Ã, áº, á»,...).
- Build project để đảm bảo không có lỗi cú pháp sau khi sửa.
