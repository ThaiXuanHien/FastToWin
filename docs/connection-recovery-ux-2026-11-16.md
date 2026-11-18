# Phản hồi kết nối và thử lại

Theo yêu cầu Owner, Android/Web không tiếp tục dùng câu chung “Dịch vụ tạm thời không khả dụng. Vui lòng thử lại.” cho các lỗi mạng và dịch vụ.

## Hành vi

- Kết nối chưa sẵn sàng trong hơn hai giây: thanh trạng thái màu xanh/nền theme, chữ “Đang kết nối lại…” và nút “Thử lại”. Không hiện lỗi đỏ chung do một yêu cầu nền. Thanh tự ẩn khi phiên phục hồi.
- Lỗi dịch vụ khi vẫn có kết nối: thanh phản hồi “Chưa thể hoàn tất lúc này”, nút “Tải lại” và nút đóng. Action chỉ đọc lại dữ liệu đang xem. Không tự gửi lại mua hàng, nhận thưởng, tham gia phòng/bang hay chỉnh sửa hồ sơ.
- Lưu hồ sơ thất bại vẫn giữ phản hồi riêng: “Thay đổi chưa được lưu. Hãy lưu lại khi đã kết nối.” Lỗi này không thay thế lỗi thao tác khác sau khi đóng hồ sơ.
- Các nơi còn dùng `ServerUnavailable`, như form đăng nhập, nhận bản dịch thân thiện hơn: “Chưa thể hoàn tất lúc này. Bạn thử lại nhé.” Form giữ nút thao tác hiện có.
- Đo độ trễ tự động chỉ gửi khi socket CONNECTED và phiên đã phục hồi. Bỏ nhãn kết nối trùng lặp ở Home.
- Retry dữ liệu bang dùng ID đang yêu cầu, kể cả chưa nhận được snapshot đầu tiên. Lỗi bảng xếp hạng dừng spinner; tải lại bắt đầu loading mới.

Thanh phản hồi có live region polite, nút ít nhất 48dp, xuống dòng action trên màn hẹp và dùng token màu của design system. Trong trận đang chạy, overlay reconnect hiện có tiếp tục giữ quyền khóa bàn số; không chồng thêm thanh thứ hai. Màn offline dựa trên `/api/status` vẫn giữ cơ chế riêng; thay đổi này không điều chỉnh timeout, chính sách chạy nền hay gate bảo trì.

## Kiểm chứng

- Hai regression đầu tiên đã thất bại trước sửa: transport error không được đi vào lỗi đỏ chung; lỗi nền không làm mất trận/tiến độ. Regression spinner cũng thất bại trước khi sửa cờ loading.
- 131 unit test thuộc state, socket, localization và clan đạt, không skip/failure/error. `:shared:checkLocalizedUiText` đạt.
- Runner unit local thêm `-Xfriend-paths` tới JAR server cho fixture PostgreSQL có sẵn vốn gọi `internal gameModule` từ module shared. Đây là cấu hình init trong ignored artifacts, không thay đổi public API hoặc build config của repo. Không thực hiện acceptance PostgreSQL trong đợt unit này.
- Build Web Wasm development, Dev APK và AndroidTest APK đạt.
- Ba Playwright case đạt trên desktop 1280×900, điện thoại 375×812 và ngang 932×430, với reduced motion. Test mô phỏng lỗi đọc leaderboard và chặn thông điệp xác thực khi reconnect; khôi phục dùng phản hồi backend thật. Tài khoản synthetic được xóa bởi teardown. Đã xem ảnh reload/reconnect và chỉnh tương phản nút đóng.
- Review độc lập phát hiện hai thiếu sót (retry lần tải bang đầu tiên và phạm vi lỗi lưu hồ sơ); đã sửa và thêm regression. Lượt review lại không còn finding hành động.

Native instrumentation kiểm tra nút tải lại/đóng/thử lại, tự ẩn khi kết nối lại và chụp Home với fontScale 1.3 trên Samsung. Kết quả lượt binary cuối cùng được lưu trong `.artifacts/connection-ux-*`.

Không chạy CI và không triển khai production. Bản vá ownership/reconnect đang có trong cùng checkout được giữ nguyên. Nghiệm thu production của #15 vẫn mở.
