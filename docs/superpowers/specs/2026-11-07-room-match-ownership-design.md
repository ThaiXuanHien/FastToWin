# Phân quyền quản lý phòng và trận trên nhiều backend

Ngày 07/11/2026. Thiết kế cho [#15](https://github.com/ThaiXuanHien/FastToWin/issues/15), tiếp nối phần Redis của [#12](https://github.com/ThaiXuanHien/FastToWin/issues/12).

## Mục tiêu của bản thử

Hai người dùng tài khoản thử có thể tạo, tham gia và chơi cùng một trận thường 1v1 trong phòng riêng chế độ Cổ điển (`CASUAL`, `ORDER`), dù WebSocket của họ vào hai backend khác nhau. Khi backend giữ phòng dừng giữa trận, backend còn lại tiếp quản, phục hồi trạng thái đã lưu và cho cả hai người vào lại đúng trận. Lượt chơi đã xác nhận được giữ nguyên. Kết quả và thưởng được ghi một lần theo mã trận.

Kiểm thử đầu tiên chạy với hai tiến trình độc lập và dữ liệu thử ở local, sau đó nghiệm thu trên staging. Cấu hình thử chỉ nhận nhóm tài khoản và loại phòng nói trên. Ghép trận, xếp hạng, giải đấu, khán giả, đấu lại và các chế độ có đồng hồ riêng thuộc các lát cắt sau. Việc tăng replica production cần kết quả nghiệm thu đủ phạm vi của #15 và quyết định triển khai riêng.

## Cơ sở trong mã hiện tại

`GameEngine` giữ phòng, phiên và hàng đợi chốt trận theo từng tiến trình. Khi khởi động, mỗi engine hiện có thể đọc toàn bộ snapshot đang hoạt động. `PostgresActiveRoomRepository` lưu snapshot theo mã phòng bằng upsert. Hai tiến trình dùng luồng này cùng lúc có thể khôi phục cùng phòng và ghi đè dữ liệu của nhau. `PostgresMatchResultRepository` đã dùng một giao dịch và khóa duy nhất `matches.id` để ngăn áp dụng lại thống kê và phần thưởng. Cơ chế đó cần đi cùng kiểm tra quyền chốt trận để ngăn một backend đã mất quyền ghi đưa kết quả lên trước. [Engine](../../../server/src/main/kotlin/com/hienthai/fastowin/server/GameEngine.kt) · [Kho snapshot](../../../server/src/main/kotlin/com/hienthai/fastowin/server/ActiveRoomRepository.kt) · [Kho kết quả](../../../server/src/main/kotlin/com/hienthai/fastowin/server/PostgresMatchResultRepository.kt)

## PostgreSQL giữ quyền ghi và trạng thái trận

- Mỗi phòng có một bản ghi quyền quản lý gồm `roomId`, mã lần khởi động của backend, thế hệ quyền ghi tăng dần (`ownerEpoch`), thời điểm hết hạn theo đồng hồ PostgreSQL và phiên bản snapshot (`revision`). Chỉ tiến trình đang giữ quyền còn hạn được xử lý thay đổi phòng. Mã lần khởi động đổi khi tiến trình chạy lại, kể cả khi dùng cùng địa chỉ mạng.
- Tạo phòng và ghi tư cách thành viên cùng giao dịch với snapshot đầu. Ràng buộc duy nhất theo người chơi ngăn hai lệnh đồng thời đưa một tài khoản vào hai phòng thuộc bản thử. Khi tiếp quản, một giao dịch khóa bản ghi quyền, kiểm tra hết hạn hoặc đã bàn giao, tăng `ownerEpoch` và trả snapshot đã lưu. Engine khôi phục theo phòng đã nhận quyền. Snapshot cũ đi qua luồng hiện tại ở chế độ một backend. Bản thử dùng cơ sở dữ liệu staging riêng cho hai tiến trình cùng phiên bản và cấu hình. Kiểm tra schema và chế độ ghi trước khi báo sẵn sàng.
- Mọi thay đổi bền vững kiểm tra mã tiến trình, `ownerEpoch`, hạn quyền ghi và `revision` dự kiến dưới khóa hàng trong cùng giao dịch. Điều kiện áp dụng cả khi ghi snapshot, cập nhật thành viên, chốt khởi trận, đóng phòng và ghi kết quả. Giao dịch giữ ngắn, có giới hạn chờ khóa, và trả lỗi tạm thời nếu không xác định được trạng thái. Khi ghi bị từ chối hoặc có phản hồi commit không rõ, engine bỏ trạng thái tạm và đọc lại bản đã lưu trước khi xử lý tiếp.
- Gia hạn thử mỗi 3 giây, thời hạn 12 giây theo giờ PostgreSQL. Khi không xác minh được quyền ghi, backend dừng xác nhận thay đổi cho phòng. Backend còn lại chỉ tiếp quản sau khi PostgreSQL xác nhận quyền cũ hết hạn hoặc đã được trả. Mục tiêu staging ban đầu là khôi phục trong 25 giây tính từ lúc dừng backend khi PostgreSQL và backend còn lại hoạt động bình thường. Các con số này là ngưỡng thử nghiệm cần đo.

## Định tuyến và kết nối lại

- Bộ cân bằng tải gửi WebSocket `/game` tới backend sẵn sàng. Backend tiếp nhận xác thực bằng luồng hiện có, tìm phòng từ mã phòng hoặc tư cách thành viên đã lưu, rồi chuyển lệnh qua kênh nội bộ có xác thực tới backend đang giữ quyền. Kênh dùng danh sách đích riêng của staging, giới hạn kích thước yêu cầu và thời gian chờ. Backend giữ phòng kiểm tra lại phiên, quyền tham gia, mật khẩu phòng và thế hệ quyền ghi. Trình duyệt chỉ dùng địa chỉ công khai hiện có.
- Mỗi kết nối đã xác thực nhận một thế hệ phiên. Thay kết nối cập nhật thế hệ trong PostgreSQL. Lệnh hoặc sự kiện ngắt từ socket cũ bị bỏ qua khi thế hệ đã thay đổi. Kiểm tra thế hệ phiên và quyền phòng tại điểm ghi để tránh trường hợp yêu cầu đã chuyển tiếp đến muộn. Giới hạn tần suất công khai được áp dụng khi tiếp nhận lệnh, rồi gắn vào yêu cầu nội bộ đã xác thực để một lần chuyển tiếp không tính thành hai lượt.
- Snapshot, bản ghi mã yêu cầu và các sự kiện cần gửi được commit trước khi trả xác nhận. Dùng `requestId` hiện có cho lượt chọn số. Yêu cầu gửi lại với cùng mã trong cùng trận trả kết quả đã ghi và giữ nguyên điểm. Khi phản hồi tạo phòng hoặc tham gia bị mất, lần kết nối lại đọc tư cách thành viên hiện tại. Các lệnh thiếu mã ổn định dùng lại trạng thái đã ghi hoặc yêu cầu client đọc lại trước khi thử tiếp.
- Backend nhận WebSocket lấy sự kiện đã commit theo thứ tự `revision` và chỉ gửi cho socket có quyền nhận. Thông báo PostgreSQL đánh thức bên nhận, còn bản ghi sự kiện đã lưu cho phép đọc bù khi thông báo bị mất. Bản thử giữ tối đa 256 phiên bản sự kiện mỗi phòng trong 2 phút. Nếu khoảng cần đọc bù nằm ngoài giới hạn này, gửi snapshot mới nhất qua luồng đồng bộ lại. Khi đổi quyền, bỏ dữ liệu đến muộn từ thế hệ cũ và đồng bộ snapshot trước khi nhận lệnh mới. Dữ liệu gửi dùng phép chiếu công khai hiện có. Log và nhãn metrics chỉ dùng nhóm cố định.
- Khi backend giữ phòng mất kết nối, phòng báo đang khôi phục và tạm chờ xác nhận. Backend mới dùng snapshot đã commit gồm mã trận, bàn chơi, lượt đã xác nhận, điểm, số thứ tự và trạng thái chờ chốt. Cả hai tài khoản có cửa sổ kết nối lại 30 giây kể từ lúc phòng phục hồi. Kết nối vẫn hoạt động có thể gắn lại với phòng qua backend nhận WebSocket. Thời hạn này được ghi cùng snapshot để lượt tiếp quản kế tiếp có thể tiếp tục một cửa sổ đang mở. Trong cửa sổ này, việc mất backend không kích hoạt xử thua vì mất kết nối. Sau đó áp dụng quy tắc mất kết nối hiện tại dựa trên các phiên thật sự đã nối lại.

## Khởi trận, ghi kết quả và sự cố

- Khi cả hai sẵn sàng, tái sử dụng quy tắc xác nhận khởi trận và hạn mức chơi hiện có. Mã trận, trạng thái khởi trận và biên nhận hạn mức phải được lưu với kiểm tra quyền ghi. Nếu commit thành công nhưng phản hồi bị mất, backend tiếp quản tìm đúng mã trận và biên nhận để tiếp tục xử lý. Mỗi yêu cầu thử lại dùng dữ liệu đã lưu thay vì sinh trận hoặc trừ hạn mức lần nữa.
- Khi trận kết thúc, lưu kết quả dự kiến cùng snapshot đã commit. Giao dịch chốt khóa bản ghi quyền phòng, xác minh mã trận và payload đã lưu rồi ghi kết quả, thống kê và thưởng qua luồng PostgreSQL hiện có. Ràng buộc theo `matchId` bảo vệ lần thử lại sau mất phản hồi. Nếu payload đã lưu khác kết quả hiện có cho cùng mã trận, báo lỗi toàn vẹn để kiểm tra. Sau khi kết quả đã commit, lần kết nối lại có thể đọc kết quả dù backend trước đó chưa gửi thông báo.
- Redis tiếp tục phục vụ presence và các giới hạn tần suất dùng chung. Khi Redis mất kết nối, các thao tác phụ thuộc giới hạn dùng chung tuân theo chính sách lỗi tạm thời hiện có. Khi PostgreSQL không xác nhận được quyền ghi, phòng chờ phục hồi và chỉ trả dữ liệu đã xác minh. Readiness của bản thử dùng cho cân bằng tải và drain. Liveness phản ánh tiến trình còn sống.
- Khi triển khai có kế hoạch trên staging, backend đang drain ngừng nhận phòng mới, hoàn tất giao dịch đang chạy, trả quyền phòng sau khi lưu trạng thái và để kết nối vào backend còn sẵn sàng. Khi bị dừng đột ngột, quá trình tiếp quản đi qua hạn quyền ghi và kiểm tra snapshot như trên.

## Tiêu chí nghiệm thu

| Ca kiểm thử | Kết quả cần quan sát |
| --- | --- |
| Tạo và tham gia từ hai backend khác nhau | Cùng một `roomId` và `matchId`, đúng hai thành viên, mỗi thay đổi có một phiên bản đã commit. Hai lệnh tham gia đồng thời từ cùng tài khoản chỉ tạo một tư cách thành viên. |
| Dừng backend giữa trận sau khi đã xác nhận một lượt | Hai người vào lại đúng trận, bàn và điểm giữ nguyên, chơi tiếp được trong mục tiêu 25 giây khi hạ tầng còn lại hoạt động. |
| Dừng trước commit và dừng sau commit nhưng trước phản hồi | Thao tác chưa commit không xuất hiện khi phục hồi. Thao tác đã commit xuất hiện một lần. Gửi lại `requestId` trả cùng kết quả. |
| Backend cũ mất mạng, backend mới tiếp quản, rồi backend cũ quay lại | Thế hệ cũ bị từ chối ở thao tác ghi snapshot, đóng phòng và chốt trận. Phiên mới vẫn hoạt động sau khi đóng socket cũ. |
| Mất thông báo, sự kiện đến muộn hoặc đến sai thứ tự | Bên nhận đọc bù hoặc đồng bộ snapshot, bỏ sự kiện đã nhận và giữ nguyên thứ tự trạng thái. |
| Dừng sau khi ghi khởi trận hoặc kết quả nhưng trước khi phản hồi | Mỗi mã trận có một biên nhận hạn mức, một kết quả và một bộ thay đổi thống kê/phần thưởng tương ứng. |
| PostgreSQL hoặc Redis mất kết nối, rồi phục hồi | Trả trạng thái tạm thời theo chính sách của kho dữ liệu bị lỗi. Sau phục hồi, snapshot và số lần ghi kết quả đúng với các giao dịch đã commit. |
| Drain và tải đồng thời | Ghi số phòng, tốc độ lệnh, p95 độ trễ lệnh và phục hồi, độ dài hàng đợi, độ bão hòa pool PostgreSQL. Chạy cùng kịch bản trên một và hai backend để có số đo đối chiếu. |

Metrics tối thiểu gồm lần tiếp quản, từ chối quyền ghi cũ, lỗi chuyển tiếp, số lần đồng bộ lại, tuổi sự kiện chờ gửi, lỗi chốt trận và độ trễ khôi phục. Sau khi đo local, dùng kết quả đó để chọn ngưỡng tải và cảnh báo cho staging. Báo cáo ghi rõ phạm vi thử và các ca cần làm trước khi tăng replica production.
