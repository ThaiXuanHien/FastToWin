# Hồ sơ và bàn phím Android — #41

Bản sửa nằm cùng PR #42 với #39; dựa trên commit draft `8215c614`. Báo cáo ngày 15/10/2026.

## Nguyên nhân và cách sửa

Trong tab Tài khoản, `TopLevelTabIfNeeded` đặt form weighted và bottom navigation ở hai nhánh con. Bottom navigation dùng `safeDrawing` gồm IME; editor dùng `imePadding`. Hai nhánh đều trừ chiều cao bàn phím, khiến vùng cuộn form có chiều cao 0 mặc dù trường tên vẫn còn focus.

Shell giờ consume IME một lần ở tổ tiên chung. Mỗi màn vẫn giữ xử lý inset của nó, nhận phần inset còn lại sau khi cha đã consume. Nhánh không có tab navigation không đổi.

Trên landscape Samsung, sau khi sửa phần trừ lặp, header và navigation vẫn chỉ để lại 44 px cho form. Khi IME mở và chiều cao còn lại dưới 320 dp, shell tạm ẩn hai thanh; giữ safe area phía trên và hai bên cho form. Đóng IME thì hai thanh trở lại. Nội dung vẫn ở một call site composition, giữ draft/focus khi chuyển bố cục.

## Bằng chứng trước và sau

Samsung SM-A366B, Android 16/API36, Samsung Keyboard English (US):

- Ca đỏ dùng shell thật và cửa sổ edge-to-edge/adjustResize như launcher: ô `profile_display_name` không hiển thị; semantics vùng cuộn `top=bottom=258px`. Ảnh xác nhận header/navigation còn nhưng phần form trống.
- Bản consume IME chung: ca portrait pass, trường tên có focus/hiển thị; Lưu và Hủy cuộn tới được khi IME còn mở. Nhóm draft/profile/ảnh/shell đạt 31/31, bao gồm font 1.4.
- Landscape: siết assertion phải hiển thị toàn bộ trường/nút đã phát hiện vùng cuộn còn 44 px so với trường cao 245 px. Bản compact sau đó đạt ca landscape chữ thường, gồm Lưu; ảnh đã xem.
- Bản cuối bổ sung safe area cho compact và kiểm tra bars trở lại khi đóng IME. Chưa nghiệm thu lại bản cuối trên Samsung vì người dùng hiện không còn máy. Không coi kết quả các bản trung gian là nghiệm thu máy thật cho toàn bộ bản cuối.

Bằng chứng local ignored: `.artifacts/profile-keyboard-20261015/` — `red.png`, `red.txt`, `portrait-regressions.txt`, `landscape-full-visibility-red.txt`, `landscape-compact-standard.txt`, ảnh portrait/landscape và semantics tương ứng.

## Kiểm thử bản cuối

`ProfileKeyboardUiTest` dùng shell thật, cấu hình softInputMode từ manifest launcher, Surface/theme app và IME thực. Hai ca chữ thường/font 1.4 kiểm tra focus, toàn bộ bounds trong viewport, vị trí ở trên cửa sổ IME, Lưu/Hủy cuộn tới được, không autosave, payload khi Lưu và bars/name sau đóng IME. Có thể truyền `qaOrientation` để yêu cầu portrait/landscape; chạy riêng từng method khi đổi orientation ngoài app.

- Android Dev và APK instrumentation: build thành công.
- Shared host: 287/287 pass, không failure/error/skip.
- Shared JS/Wasm: compile thành công; localization pass. Có warning deprecation đã tồn tại.
- Review độc lập bản cuối: không finding correctness cần sửa; bổ sung so sánh bounds với mép IME theo góp ý coverage.
- Emulator Pixel 10 Pro XL, API37, Gboard: bản cuối đạt 31/31 regression (2 IME, 7 draft, 10 profile/practice, 2 ảnh, 10 shell). Hai lượt landscape riêng cho chữ thường/font 1.4 đều đạt 1/1. Ảnh portrait/landscape, form, Lưu/Hủy, bars trở lại và viewport 1200×800 đã xem.
- Log: `emulator-final-regressions.txt`, `emulator-landscape-standard.txt`, `emulator-landscape-large-text.txt`; ảnh/semantics trong thư mục `emulator/`. Cửa sổ portrait 1344×2992 và landscape 2992×1344; IME thực mở, không dùng đóng bàn phím để vượt assertion.
- Emulator ban đầu khôi phục snapshot bị mất focus/màn hình đen; cold boot giải quyết được. Những lượt lỗi môi trường được giữ trong artifact, không tính là pass.
- CI của head #39 `8215c614` đã xanh cả 5 jobs ở push và PR. Bản bổ sung #41 cần CI của commit mới; không dùng kết quả head cũ thay cho head mới.

## Phần còn chờ

Samsung bản cuối: portrait/landscape với chữ thường/font 1.4, safe area, đóng IME khôi phục header/nav và giữ tên. iOS/PWA runtime chưa nghiệm thu trong vòng này. Giữ #41 mở cho đến khi hoàn tất phần nghiệm thu và tích hợp PR.

Không cần khởi động staging hoặc ghi dữ liệu backend cho kiểm thử UI này. #39 có báo cáo mạng thật riêng tại [profile-draft-reconnect-acceptance.md](profile-draft-reconnect-acceptance.md). PR #42 vẫn xếp trên PR #40.
## Dọn dẹp và giới hạn thiết bị

Emulator được khôi phục cấu hình IME/rotation ban đầu, gỡ riêng Dev/test của vòng này và tắt sau nghiệm thu; giữ app production có sẵn. Không thay đổi network/font scale toàn hệ thống. Samsung không còn kết nối nên chưa gỡ lại Dev/test được cài trong lượt #41; cần dọn khi có máy. Vòng này chỉ dùng fixture UI trong bộ nhớ, không tạo tài khoản/token hoặc ảnh cá nhân trên máy.
