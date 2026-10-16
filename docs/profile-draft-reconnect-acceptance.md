# Giữ profile draft khi offline/reconnect — #39

Cập nhật: #41 được triển khai cùng PR #42. Kết quả bàn phím ở các mục dưới là lịch sử vòng #39; xem [nghiệm thu bàn phím](profile-keyboard-acceptance.md) cho bản sửa và giới hạn nghiệm thu hiện tại.

Nghiệm thu mạng thật ngày14/10/2026; hoàn tất regression/cleanup ngày15/10/2026. Issue: https://github.com/ThaiXuanHien/FastToWin/issues/39 .
Bản sửa dựa trên nhánh PNG alpha của PR #40, commit nguồn `3afd7ea`.

## Nguyên nhân và phạm vi sửa

GameContent thay ProfileScreen bằng OfflineScreen khi service health không tới được. Các biến `remember` và AvatarEditSelection vốn thuộc ProfileScreen bị hủy khi form rời composition.

ProfileEditorState giờ thuộc phiên GameContent, keyed theo controller (tài khoản/server), nằm ngoài nhánh offline. State chỉ ở bộ nhớ; không đưa bitmap/bytes vào Bundle hay lưu draft lên backend. Rời trang hồ sơ, Hủy, logout hoặc thay owner loại draft. Observer xác nhận Lưu cũng nằm ở owner, tránh phát lại thông báo thành công cũ khi form trở lại.

Khi picker rời composition, bỏ trạng thái đang xử lý nhưng giữ lựa chọn tốt trước đó. Mỗi phiên edit có AvatarEditSelection mới để callback muộn của phiên đã hủy không đi vào phiên tiếp theo. Không đổi bố cục/theme.

## Kiểm thử tự động

- Red trên Samsung SM-A366B Android16/API36:3/5 test UI ban đầu thất bại vì không còn `profile_display_name` sau offline;2 case account/logout đã pass.
- Green nhóm liên quan:17/17 UI pass (5 regression mới +10 profile/practice +2 xử lý ảnh PNG/JPEG); shared host287/287 pass.
- Bổ sung navigation-away:6/6 regression pass; localization scanner/check pass.
- Siết callback muộn đến sau khi mở editor mới: focused case pass.
- Final trên Samsung:7/7 regression pass, gồm viewport1200×800; ảnh chụp đã xem. Log `final-instrumentation.log`, ảnh `profile-draft-desktop.png` ở artifact local. Bài desktop đóng bàn phím ảo để mô phỏng nhập bằng bàn phím ngoài; không dùng nó để xác nhận IME phone đã sửa.
- Rà soát mã độc lập: không có finding correctness cần sửa; góp ý coverage navigation/callback muộn đã bổ sung.

UI test host dùng state owner thật ở trên nhánh conditional, giống GameContent. Nó chứng minh contract UI/lifecycle; lượt mạng thật bên dưới đối chiếu wiring app đầy đủ. Viewport rộng trên Android là kiểm tra bố cục Compose, không thay thế nghiệm thu browser thật.

## Nghiệm thu mạng thật trên Samsung

Dev riêng kết nối staging WSS/R2 private, synthetic account Device QA và ảnh PNG tổng hợp. Không dùng dữ liệu cá nhân.

1. Nhập tên Draft QA, chọn PNG bằng system picker, chưa bấm Lưu.
2. Backend vẫn tên Device QA, avatar revision0, không object R2.
3. Tắt Wi-Fi/dữ liệu, quan sát màn Tạm mất kết nối, rồi bật lại trạng thái gốc.
4. Sau tự reconnect, editor còn mở, tên Draft QA và ảnh nháp xanh còn nguyên. Backend vẫn tên Device QA/revision0: không autosave.
5. Bấm Lưu: form đóng, báo đã lưu, profile đổi tên Draft QA và ảnh xanh; backend revision1, JPEG512×512/12808bytes, SHA khớp HTTP, corner RGB(255,255,255).

SHA ảnh: `6ab12b75f9af179017649610557c6214d9ee909d9c8370103ebe3186754dcf36`.
Bằng chứng local ignored `.artifacts/profile-draft-20261014/device/`: `draft-before-offline.png`, `offline-stable-layout.json`, `draft-after-reconnect.png`, `saved-after-reconnect.png`, các metadata quan sát trước/sau. Credential/object key không commit.

## Diagnostic viewport rộng và giới hạn

Bài viewport rộng ban đầu fail. Screenshot trắng và semantics chứng minh picker callback chạy1lần, tên Draft name/preview vẫn tồn tại, nhưng scroll viewport có height0 khi IME native còn mở. Semantics root1080×720px và scroll height0 phù hợp với việc viewport desktop được thu trong cửa sổ phone nhưng vẫn nhận IME native; đây là suy luận từ diagnostic, chưa quy kết nguyên nhân của #41 trên app thật. Đóng software keyboard trong riêng bài desktop làm toàn bộ assertion hiển thị/draft/save pass; không bỏ/đổi assertion thành chỉ kiểm tra tồn tại. Giữ ảnh/semantics lỗi local để đối chiếu. Bài phone với IME vẫn thuộc #41, chưa xác định root cause hoàn chỉnh trên app thật.

## Giới hạn và phát hiện thêm

- Lúc mở bàn phím Samsung, vùng form trở thành nền trống; ẩn IME thì form/focus trở lại. Đã ghi riêng [#41](https://github.com/ThaiXuanHien/FastToWin/issues/41). Vòng này nhập tên qua ADB khi IME đã ẩn, không coi kiểm thử bàn phím hiển thị là đạt và chưa quy kết commit gây lỗi khi chưa đối chiếu APK baseline.
- Tài khoản synthetic xác minh email trong staging DB, không chứng minh SMTP đăng ký.
- Chưa nghiệm thu browser/iOS thật cho39; không giữ draft qua process death/force-stop trong phạm vi này.
- CI của commit source `3afd7ea` pass toàn bộ5 jobs: https://github.com/ThaiXuanHien/FastToWin/actions/runs/38592681419 . CI PR #40 cũng pass. Commit bổ sung test viewport/báo cáo sau đó cần kiểm tra CI riêng. Lỗi intermittent giải đấu #38 chưa được chẩn đoán/sửa chỉ bằng việc run này pass.

## Dọn dẹp

Dữ liệu thử/tài khoản/object R2, SSH tạm và staging compute được dọn/thu hồi/tắt sau test; chỉ đẩy due time của key synthetic đã nghỉ để worker xử lý. Wi-Fi/dữ liệu khôi phục, ảnh QA và app Dev/test do vòng này cài được dọn. Production Android/backend không được cập nhật. Các file UI/IDE đang sửa ở checkout gốc được để nguyên; nhánh này không stage hoặc ghi đè chúng. Không dùng hashbaseline ngày14 để khẳng định chúng không đổi qua ngày15. Các record cleanup nằm `.artifacts/profile-draft-20261014/`.

## Nghiệm thu bổ sung Samsung bản cuối — 16/10/2026

Source `fd8219975b71047ab67a02c69f14b2cf8be68c26`, Samsung A36 SM-A366B, Android 16/API36, Dev riêng trỏ staging WSS/R2 private. Đây là cập nhật cho các giới hạn Samsung còn chờ ở báo cáo lịch sử phía trên.

- Log thiết bị ngày 16/10 xác nhận 9/9 regression draft/PNG đạt. Bốn cấu hình bàn phím Samsung đạt: portrait chữ thường và font 1.4 (2/2), landscape chữ thường (1/1) và font 1.4 chạy riêng (1/1). Lượt gộp landscape trước đó có một timeout tại bước chờ orientation, trước khi dựng editor; không tính lượt lỗi đó là pass.
- Ảnh đã xem xác nhận trường nhập và Lưu/Hủy nằm trên IME, safe area còn giữ và navigation trở lại khi đóng bàn phím. Bằng chứng local ignored: `.artifacts/profile-keyboard-20261016/` tại checkout gốc.
- Lượt mạng thật cuối: nhập `QADraftResume16`, chọn ảnh QA xanh tổng hợp, chưa Lưu; tắt Wi-Fi/data 40 giây đến màn Offline. Sau bật lại mạng, app tự reconnect, editor/tên/preview còn nguyên; header vẫn tên cũ `QADraft16`.
- HTTP avatar không đổi trước/sau reconnect: SHA-256 `6ab12b75f9af179017649610557c6214d9ee909d9c8370103ebe3186754dcf36`. Chỉ sau bấm Lưu, editor đóng, hiện “Đã lưu hồ sơ”, tên đổi `QADraftResume16` và HTTP trả đúng ảnh QA xanh mới, SHA-256 `fbb1563496e7c8fe21355933734b0358ecb2d68d84684cc3fa1c0ed3fda0a541`.
- Lượt thử trước bị ngắt bởi “phiên hết hạn” do quy trình QA đăng nhập observer cùng tài khoản. `Authentication.login`/`PostgresAuthRepository.replaceSessions` chủ động thu hồi các phiên cũ. Lượt cuối chỉ dùng HTTP avatar công khai để đối chiếu khi Samsung đang chạy; không sửa mã xác thực và không coi lỗi do observer là regression của PR.
- Rà soát độc lập PR #40/#42 đã hoàn tất, không có finding cần sửa trong phạm vi PNG/draft/keyboard. CI head `fd82199` đạt toàn bộ năm nhóm; sau tích hợp master phải kiểm tra CI của head mới riêng.

Samsung cuối đã nghiệm thu; browser/iOS runtime và CDN vẫn chưa được chứng minh bằng lượt này. Không coi Android Dev là bản đã phát hành Google Play. Production chưa được thay đổi bởi lượt kiểm thử này.
