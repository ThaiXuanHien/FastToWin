# Đồng bộ trạng thái online qua Redis cho #12

Phạm vi là trạng thái hiển thị trong Bạn bè và Thành viên bang, với hai backend local dùng chung Redis. Trạng thái phòng, trận, quyền tham gia và quyết toán thuộc luồng hiện tại của game. Các giới hạn tần suất còn chạy trong RAM và quyền điều phối phòng/trận trên nhiều backend thuộc các bước riêng của #12 và #15.

## Trạng thái và vòng đời kết nối

Giữ bốn giá trị hiện có `OFFLINE`, `ONLINE`, `IN_ROOM` và `PLAYING`. Mỗi WebSocket tài khoản đã xác thực nhận một ID kết nối riêng do server sinh. Kết nối khách không tạo bản ghi. Engine lấy trạng thái từ kết nối và phòng tại backend sở hữu chúng. Nếu một người có nhiều kết nối hợp lệ, ưu tiên `PLAYING` rồi `IN_ROOM` rồi `ONLINE`. Chỉ trả `OFFLINE` từ Redis khi đọc thành công và không còn kết nối hợp lệ.

Ghi ngay khi xác thực hoặc đổi trạng thái. Gia hạn mỗi 20 giây khi WebSocket còn sống, thời hạn 60 giây tính theo giờ Redis. Khi ngắt, thu hồi đúng ID kết nối. Xử lý ghi và xóa tuần tự cho cùng một kết nối. Khi socket bị thay thế, ngừng phát cập nhật từ socket cũ. Mỗi lượt gia hạn chỉ thử một lần trong ngân sách thời gian của thao tác. Lượt kế tiếp lấy trạng thái mới nhất của socket đang sống, không phát lại tác vụ cũ đã timeout. Khi backend chết, các bản ghi của nó tự hết hạn kể cả khi backend khác vẫn hoạt động.

## Lưu và đọc theo lô

Dùng namespace `ftw:<namespace>:presence:v1:*` và tài khoản Redis riêng có ACL giới hạn đúng nhóm khóa và lệnh cần thiết. Cờ Redis presence bật riêng. Chế độ mặc định đọc trạng thái từ engine như hiện tại.

Mỗi người chơi có tối đa ba sorted set, tương ứng ba trạng thái online. Thành viên của set là ID kết nối và score là thời điểm hết hạn. Dùng một thao tác nguyên tử khi đổi trạng thái để chuyển đúng ID sang set mới. Khi gia hạn, dọn phần tử hết hạn và đặt TTL cho khóa. Khi đọc, chỉ tính phần tử có score lớn hơn thời gian hiện tại, kể cả khi khóa vẫn còn vì một kết nối khác hoạt động. Ưu tiên trạng thái cao nhất có kết nối hợp lệ. Gom ID đã loại trùng theo từng lô tối đa 50 và giới hạn thời gian xử lý, không quét toàn bộ keyspace. Redis I/O diễn ra ngoài mutex của `GameEngine`.

Redis lỗi hoặc hết thời gian thì lần làm mới bỏ qua kết quả và giữ ảnh chụp gần nhất trên client. Khi tải lần đầu mà Redis lỗi, trả trạng thái tốt nhất từ backend đang phục vụ với bốn giá trị giao thức hiện có. Kết quả cục bộ chỉ phản ánh các kết nối mà backend đó biết. Khôi phục bằng nhịp gia hạn và làm mới kế tiếp. Ghi đếm lỗi theo nhóm cố định và tránh ghi token hoặc ID kết nối vào log.

## Làm mới Bạn bè và Thành viên bang

Các yêu cầu đọc danh sách đang có lấy trạng thái theo lô cho đúng những người mà server đã cho phép trả về. Với client hỗ trợ, gửi một yêu cầu làm mới nhẹ mỗi 15 giây khi màn Bạn bè hoặc danh sách thành viên đang hiện và kết nối đã xác thực. Yêu cầu chỉ chọn phạm vi danh sách, không nhận danh sách ID người dùng từ client. Server tự xác định quan hệ bạn bè hoặc quyền xem bang theo luồng hiện tại, kiểm tra lại quyền mỗi lần và trả riêng trạng thái. Khi đổi màn, đổi bang hoặc mất phiên, dừng vòng làm mới và bỏ phản hồi đến muộn của phạm vi cũ. Giới hạn tần suất yêu cầu tại server và trả trạng thái chỉ cho socket đã yêu cầu.

Dùng cơ chế tương thích phiên bản WebSocket hiện có để client mới chỉ gửi yêu cầu làm mới khi backend hỗ trợ. Client cũ tiếp tục nhận trạng thái qua các phản hồi Bạn bè và Bang đang có. Tín hiệu cập nhật trực tiếp trong cùng backend vẫn dùng được để hiển thị sớm hơn nhịp 15 giây. Với hai backend, lần làm mới thành công tiếp theo sẽ thấy thay đổi bên kia. Nếu backend chết, thời gian hiển thị offline tối đa khoảng 75 giây tính từ lần gia hạn cuối trong điều kiện Redis và nhịp làm mới hoạt động bình thường.

## Nghiệm thu local

- Hai backend dùng chung một Redis thật. Bạn bè và thành viên bang thấy cùng trạng thái khi hai người ở hai backend, từ online đến vào phòng, bắt đầu trận và rời phòng.
- Đóng một trong hai socket hợp lệ, thay socket và nhận sự kiện đóng muộn, và ngắt socket cuối. Xác nhận độ ưu tiên giữa các trạng thái và kết nối mới không bị socket cũ xóa.
- Dừng cứng một backend, đo thời hạn từ lần gia hạn cuối và xác nhận kết nối ở backend còn lại tiếp tục được nhìn thấy. Khi Redis gián đoạn, xác nhận lần làm mới bỏ qua kết quả lỗi và việc làm mới tiếp tục khi Redis hồi phục.
- Kiểm tra tài khoản khách, người ngoài danh sách, người bị chặn theo quyền hiện có, chuyển màn và đổi bang khi phản hồi cũ đang về. Kiểm tra client cũ với backend mới và client mới với backend cũ.
- Thử tải với hai backend, Redis `64 MB`, `noeviction` và AOF bật như production. Ghi số lần đọc/ghi mỗi giây, độ trễ p95 của lần đọc theo lô, đỉnh bộ nhớ, kích thước AOF theo thời gian và tình trạng từ chối lệnh. Chạy rate limit đồng thời để phát hiện tranh tài nguyên. So sánh với mẫu rate limit riêng trước khi đánh giá dùng chung Redis.

Bản thử dùng cấu hình và dữ liệu QA local. Sau nghiệm thu, dùng số đo về Redis, AOF và độ trễ để chọn cấu hình phù hợp và lập kế hoạch triển khai riêng.
