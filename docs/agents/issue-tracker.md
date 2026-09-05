# Issue tracker: GitHub

Issues và đặc tả của FastToWin được quản lý tại GitHub Issues.
Dùng GitHub CLI (`gh`) để thao tác.

Repository: `ThaiXuanHien/FastToWin`

## Quy ước

- Tạo issue: `gh issue create --title "..." --body "..."`
- Đọc issue: `gh issue view <number> --comments`
- Liệt kê issue: `gh issue list --state open`
- Bình luận: `gh issue comment <number> --body "..."`
- Thêm hoặc xóa nhãn: `gh issue edit <number> --add-label "..."` hoặc `--remove-label "..."`
- Đóng issue: `gh issue close <number> --comment "..."`

GitHub CLI tự xác định repository từ Git remote khi chạy bên trong project.

## Pull request

PR không được xem là yêu cầu tính năng trong hàng đợi triage.

## Quy tắc dành cho skill

- Khi skill yêu cầu “publish to the issue tracker”, hãy tạo GitHub Issue.
- Khi skill yêu cầu lấy ticket, dùng `gh issue view <number> --comments`.
- Issue và PR dùng chung không gian số; nếu `#42` không rõ loại, kiểm tra PR trước rồi kiểm tra Issue.
