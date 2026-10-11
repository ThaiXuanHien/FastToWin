# Lịch giải đấu (#13)

Đặc tả gốc: https://github.com/ThaiXuanHien/FastToWin/issues/13. Người dùng đã duyệt tự bắt đầu đúng giờ, mở check-in trước 15 phút, loại người vắng trước khi chia nhánh, hủy và hoàn phí nếu thiếu người.

## Hành vi

- Giữ giải thủ công hiện có; lịch là tùy chọn khi tạo giải, dùng được cho giải công khai và riêng tư.
- Người tạo chọn giờ địa phương và múi giờ IANA. Server lưu thời điểm UTC và múi giờ; check-in mở và đăng ký khóa cùng lúc, đúng 15 phút trước giờ bắt đầu.
- Giờ bắt đầu phải cách thời điểm server ít nhất 15 phút và không quá 365 ngày. Múi giờ không hợp lệ bị từ chối. UI phải hiển thị thời điểm đăng ký đóng, check-in và bắt đầu cùng múi giờ trước khi xác nhận.
- Check-in chỉ do chính tài khoản tham gia thực hiện, trong khoảng [mở check-in, bắt đầu). Check-in lặp lại không làm thay đổi phí. Không cho chủ giải bắt đầu sớm một giải có lịch.
- Đến giờ, chỉ người đã check-in, đang kết nối và không bận trong phòng/tìm trận mới đủ điều kiện. Giữ nguyên thể thức đã chọn 4/8/16 người: không đổi thể thức, không chèn bot/byes. Thiếu một người đủ điều kiện cũng hủy, hoàn toàn bộ phí thực trả của mọi người đã đóng phí, kể cả người rời lobby trước đó.
- Chủ giải hủy giải có lịch trước giờ cũng hoàn phí theo cùng cơ chế. Người tự rời lobby chưa nhận hoàn ngay; nếu giải bị hủy thì nhận hoàn một lần. Không cho rời/đăng ký lại sau khi đăng ký đã khóa. UI phải trình bày quy tắc này trước khi người chơi trả phí.
- Lịch, check-in và kết quả nằm trong snapshot bền vững. Khóa đăng ký dựa trên thời gian server ngay tại admission, không phụ thuộc tick. Tick khoảng một giây khởi động/hủy những giải đến hạn; restart phải tiếp tục xử lý các lịch đã quá hạn.
- Việc tạo nhánh, phòng và cập nhật RUNNING chỉ được công bố sau khi transaction bền vững thành công. Hủy và hoàn phí dùng một transaction và khóa chống hoàn trùng chung với bảo trì. Lỗi lưu không được tạo phòng ma, mất phí hoặc phát thông báo thành công.
- Bảo trì vẫn được ưu tiên: không khởi động giải khi chặn ý định chơi mới; giữ lịch quá hạn để xử lý khi mở lại, trừ chế độ bảo trì đã hủy giải bằng cơ chế hiện có.
- Protocol mới giữ trường tùy chọn tương thích snapshot cũ. Client cũ không hỗ trợ check-in phải được từ chối tạo/tham gia giải có lịch với thông báo yêu cầu cập nhật, nhưng vẫn chơi giải thủ công.
- UI Android/iOS/Web chung: dark Material, modal tạo giải gọn, nhãn dễ hiểu, countdown giây/phút dựa vào thời gian server, nút check-in có trạng thái đã check-in và vô hiệu hóa đúng hạn. Icon có nhãn accessibility, vùng chạm ít nhất 48dp. Shared dùng kotlinx-datetime 0.8.0 để chuyển giờ địa phương/IANA và từ chối giờ không tồn tại do DST; server không thêm dependency.
- Modal tạo giải có nút đóng 48dp với nhãn bản địa hóa, có thể đóng bản nháp và mở lại từ nút tạo giải. Sau khi đóng modal, nút Quay lại tiếp tục điều hướng từ trang giải đấu. Các fixture Web nhập tên/lệ phí phải mở modal trước; kiểm tra bàn phím iOS đóng bằng nút thực trên modal trước khi kiểm tra điều hướng và canvas nền.
- Lời mời hiển thị lệ phí thực tế và lịch/múi giờ trước khi chấp nhận. Client khóa nút tham gia khi đăng ký đóng; server vẫn kiểm tra lại hạn tại admission. Lỗi tạo giải hiện trong modal và được cuộn tới; giải đang RUNNING không còn hiện countdown chờ bắt đầu.

## Giao diện protocol chung

```kotlin
@Serializable data class TournamentScheduleRequest(
    val startsAtEpochMillis: Long, val timeZoneId: String
)
@Serializable data class TournamentScheduleSnapshot(
    val startsAtEpochMillis: Long, val timeZoneId: String,
    val registrationClosesAtEpochMillis: Long,
    val checkInOpensAtEpochMillis: Long
)
```

`CreateTournament.schedule: TournamentScheduleRequest? = null`; `TournamentSnapshot.schedule` và `PublicTournamentSummary.schedule` kiểu `TournamentScheduleSnapshot? = null`; `TournamentPlayerSnapshot.isCheckedIn: Boolean = false`; `ClientMessage.CheckInTournament(tournamentId: String)`. Snapshot cập nhật gửi thêm `serverTimeEpochMillis: Long? = null` để UI căn chỉnh đồng hồ. Protocol tăng từ 43 lên 44, sàn tương thích 38 giữ nguyên.

## Nghiệm thu

Web Wasm và JavaScript fallback nạp `@js-joda/timezone` 2.23.0 trước khi khởi tạo giao diện để hỗ trợ múi giờ IANA. Phiên bản này tương thích `@js-joda/core` 3.2.0 của kotlinx-datetime 0.8.0; lockfile của cả hai target được lưu trong Git. Thiết lập theo [hướng dẫn của kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime/blob/v0.8.0/README.md#note-about-time-zones-in-wasmjs). Nghiệm thu Web phải dùng múi giờ có tên như `Asia/Bangkok`, không chỉ `UTC`.

Kiểm tra biên thời gian trước/đúng/sau hạn, check-in giả mạo/lặp, đăng ký qua invitation và public, rời sau hạn, start thủ công, phiên bản client cũ; đủ người tự bắt đầu đúng một lần; thiếu người hủy/hoàn một lần; PostgreSQL rollback và restart trước/sau hạn; maintenance không khởi động giải. Kiểm thử controller và UI thực Web; ảnh chụp điện thoại và desktop. Các kiểm tra iOS trên thiết bị thật cần máy Mac/thiết bị của người dùng.

Giới hạn Compose Web: sau khi hủy dialog, các node accessibility của Compose nền có thể vẫn vắng dù canvas nền đã hiện lại trong các ca native Chromium/WebKit. Fixture bàn phím iOS ghi nhận vị trí nút Quay lại thực trước khi mở modal, đóng bằng nút Đóng, rồi nhấp lại vị trí đã đo sau khi khôi phục kích thước viewport và giữ kiểm tra Home/viewport. Cách kiểm tra này không chứng minh accessibility DOM nền được khôi phục; khả năng đóng/mở lại modal bằng accessibility trên Web vẫn cần kiểm tra riêng. Trong lượt mô phỏng bàn phím WebKit hiện tại, ảnh đã chỉ còn nền tối sau resize khi modal còn mở dù CSS kích thước đạt; kiểm tra Home sau đóng không đạt, nên không coi các ca này là đã nghiệm thu. Test Android bao phủ luồng đóng/mở lại; bản build xác nhận biên dịch test, còn chạy instrumentation cần thiết bị.
