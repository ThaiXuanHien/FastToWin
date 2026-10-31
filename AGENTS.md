## Agent skills

### CI

Khi được yêu cầu chạy hoặc chạy lại CI, mặc định chạy tất cả các job, gồm máy ảo Android; chỉ giới hạn job khi người dùng chỉ định. Với workflow hiện tại, dùng `workflow_dispatch` và `run_android_ui=true` để bật đủ job.
Khởi chạy một lần, trả link rồi để CI tự chạy. Chỉ kiểm tra trạng thái hoặc đọc log khi người dùng yêu cầu; người dùng sẽ báo khi hoàn tất hoặc có lỗi. Không tự polling, watch hay tạo theo dõi định kỳ, để tiết kiệm credit.

### Issue tracker

Công việc và đặc tả được quản lý bằng GitHub Issues. Xem `docs/agents/issue-tracker.md`.

### Triage labels

Sử dụng năm nhãn triage mặc định. Xem `docs/agents/triage-labels.md`.

### Domain docs

Project sử dụng cấu trúc tài liệu miền single-context. Xem `docs/agents/domain.md`.

### UI/UX

Khi thiết kế, triển khai hoặc chỉnh sửa giao diện web/mobile, bắt buộc đọc và áp dụng skill `.agents/skills/ui-ux-pro-max/SKILL.md`.
Ưu tiên giao diện đẹp, hiện đại, tối giản; thống nhất màu sắc, typography, khoảng cách và trạng thái tương tác. Đối chiếu design system hiện có, kiểm tra khả năng truy cập và xem ảnh chụp trên điện thoại/desktop trước khi bàn giao.
