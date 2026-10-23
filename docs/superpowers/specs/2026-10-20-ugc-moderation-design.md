## Thiết kế #53 — đã duyệt

Đã chốt với chủ dự án: báo cáo giữ trong thời gian xử lý; xóa nội dung và định danh báo cáo sau 90 ngày kể từ lúc đóng. Khi xóa tài khoản, gỡ định danh người báo cáo ngay. Backup tiếp tục áp dụng 14 ngày.

### Phương án

Đề xuất luồng báo cáo + kiểm duyệt thủ công ngay trong hệ thống: không thêm dịch vụ trả phí. Email hỗ trợ vẫn là kênh bổ sung. Chỉ nhận báo cáo qua email sẽ khó thao tác trong app; tự động phân loại bằng AI tăng chi phí và cần thêm đánh giá sai lệch nên chưa dùng.

### Phạm vi nội dung và giao diện

Rà mã thấy UGC gồm biệt danh/avatar, tên/mô tả bang hội, tên phòng (`CreateRoom.roomName`) và tên giải đấu (`CreateTournament.name`). Nút cờ có nhãn trợ năng/tooltip mở modal hiện có: chọn loại nội dung/lý do, ghi chú tối đa 500 ký tự, gửi, hiển thị thành công/lỗi/thử lại. Có thao tác chặn người chơi riêng, không tự chặn hoặc xử phạt chỉ vì nhận báo cáo. Truy cập từ các nơi hiển thị hồ sơ/đối thủ, bang hội, phòng và giải đấu; tái sử dụng style game và admin, ui-ux-pro-max, kiểm tra phone/desktop.

### Điều khoản

Lưu phiên bản quy tắc và thời điểm đồng ý phía server. Tài khoản mới và khách phải đồng ý trước khi công bố biệt danh; tài khoản cũ được yêu cầu đồng ý trước lần tạo/sửa/upload UGC tiếp theo. Server kiểm tra ở mọi đường ghi, gồm đăng ký/nâng cấp khách, hồ sơ/avatar, phòng, giải và bang hội. Không dùng checkbox đã tích sẵn hoặc chỉ lưu local. Đăng nhập, xem thông tin điều khoản, hỗ trợ/xóa tài khoản không bị khóa vì chưa đồng ý. Phát hành client hỗ trợ trước, rồi mới bật bắt buộc sau khi kiểm tra; client cũ cần thông báo cập nhật rõ ràng, không âm thầm bỏ qua.

### Tiếp nhận và chống spam

Server lấy người báo cáo từ phiên đã xác thực (kể cả khách có phiên hợp lệ), không tin reporter ID do client gửi. Kiểm tra đối tượng còn hợp lệ; request ID giúp retry không tạo bản ghi mới; hạn mức đề xuất 5 báo cáo/10 phút và 20/ngày cho mỗi danh tính, kèm giới hạn IP tại gateway. Một báo cáo đang mở cho cùng người báo cáo/đối tượng/loại nội dung được trả lại trạng thái đã tiếp nhận. Không công khai danh tính người báo cáo cho người bị báo cáo. Chỉ giữ văn bản hiển thị tại thời điểm báo cáo và tham chiếu/revision ảnh hiện tại, không sao chép ảnh người chơi sang thư mục bằng chứng hay public logs. Nếu nội dung thay đổi/mất, admin thấy rõ trạng thái đó.

### Kiểm duyệt

Hàng đợi Mới → Đang xem xét → Đã xử lý/Bỏ qua. SUPPORT xem và phân loại; OPERATOR/OWNER được áp dụng biện pháp, bắt buộc ghi lý do. Mọi thay đổi có kiểm tra quyền phía server, CSRF/origin, request ID, phiên bản để tránh hai admin xử lý đè nhau và audit không chứa ghi chú/PII thô.

Biện pháp giai đoạn đầu: thay biệt danh/tên phòng/tên giải/tên bang bằng nhãn trung tính, ẩn mô tả hoặc gỡ avatar; cảnh báo người tạo. Nội dung đang hiển thị được làm mới ở các luồng liên quan, gồm đọc avatar và lời mời/lịch sử; không hủy trận/giải hay sửa ví khi chỉ gỡ nội dung. Nội dung đã thay đổi phải được đối chiếu revision trước khi gỡ. Thêm khóa/mở khóa tài khoản có lý do cho OPERATOR/OWNER khi cần xử lý vi phạm lặp lại; hiện schema có BLOCKED nhưng chưa có luồng admin hoàn chỉnh, nên không coi đây là tính năng đã sẵn có. Khóa phải chặn login/refresh/kết nối mới và thu hồi phiên đang hoạt động; không xóa tài khoản hay tự động tịch thu tiền game.

### Dữ liệu và nghiệm thu

Worker hằng ngày dọn báo cáo đã đóng >90 ngày. Gỡ định danh người báo cáo khi xóa tài khoản; rà cả target ID, trích nội dung và audit để không giữ hồ sơ cá nhân ngầm. Audit dài hạn chỉ giữ số liệu/tác vụ đã loại dữ liệu cá nhân. Cập nhật Privacy/Data safety/reviewer instructions theo thay đổi thực tế.

Kiểm tra hợp đồng protocol và migration PostgreSQL; bypass điều khoản/giả danh/CSRF/sai quyền; hạn mức/trùng/retry; xóa tài khoản và hết hạn 90 ngày; xử lý nội dung đã đổi; thu hồi session khóa tài khoản; không tác động số dư/giải đấu; render văn bản an toàn, phone/desktop. Không chạy emulator; nghiệm thu Samsung khi có máy. Hoàn tất trên staging trước khi đề xuất AAB và bật production.

Chủ dự án đã duyệt thiết kế và cho phép triển khai tại [bình luận thiết kế của #53](https://github.com/ThaiXuanHien/FastToWin/issues/53#issuecomment-6291437295). Bằng chứng kiểm tra và giới hạn phát hành được ghi trong `docs/ugc-moderation-rollout.md`; phê duyệt thiết kế không đồng nghĩa đã bật production.
