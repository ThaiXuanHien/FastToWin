# Domain docs

FastToWin sử dụng cấu trúc tài liệu miền single-context.

## Trước khi khám phá hoặc sửa code

Agent cần đọc nếu chúng tồn tại:

- `CONTEXT.md` tại thư mục gốc.
- Các ADR liên quan trong `docs/adr/`.

Nếu chưa có các file hoặc thư mục này, tiếp tục làm việc bình thường.
Không tự tạo chúng cho đến khi có quyết định miền cần ghi lại.

## Cấu trúc

```text
/
├── CONTEXT.md
├── docs/
│   ├── agents/
│   └── adr/
├── app/
├── shared/
├── protocol/
├── server/
├── webApp/
└── iosApp/
```

## Từ vựng miền

Khi đặt tên khái niệm, issue, test hoặc đề xuất refactor, ưu tiên thuật ngữ
được định nghĩa trong `CONTEXT.md`.

Nếu thiếu thuật ngữ quan trọng, ghi nhận để cập nhật bằng skill
`domain-modeling`.

## ADR

Nếu đề xuất mới mâu thuẫn với ADR hiện có, phải nêu rõ xung đột và lý do
cần xem xét lại, không được âm thầm ghi đè quyết định cũ.
