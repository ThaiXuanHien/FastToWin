# app-ads.txt — chuẩn bị local ngày01/11/2026

Trạng thái: **đã thêm source, kiểm tra HTTP local và xác minh gói Web; chưa triển khai, chưa khai báo website trên Store, chưa xác minh AdMob**. Phạm vi tiếp tục trong lúc Owner tuyển Closed testers. Không mua domain, tạo hạ tầng, thêm network, sửa Store, yêu cầu crawl, deploy hoặc bật quảng cáo.

## Quan sát và file đã chuẩn bị

Trang Store đúng package `com.hienthai.fastowin`, mục App support đang hiện email hỗ trợ và Privacy, **không thấy liên kết Developer website**. Chưa đọc trường website trong Console nên không khẳng định giá trị trường đó là rỗng. Không lưu địa chỉ cá nhân hiển thị trong panel.

AdMob → Apps → How to set up app-ads.txt cung cấp đúng dòng:

```text
google.com, pub-1914037185148012, DIRECT, f08c47fec0942fa0
```

File ASCII/UTF-8 không BOM, một dòng kết thúc LF: `.artifacts/app-ads-preparation-20261101/app-ads.txt`. Bằng chứng snippet và bản tóm tắt Store nằm cùng thư mục. Đây là seller authorization của publisher sở hữu tài khoản; không phải danh sách consent198/210. Không tự thêm198dòng hoặc mã reseller từ các nhà cung cấp thấy trong form consent.

## Host đề xuất

Đề xuất dùng website hiện có `https://fasttowin.up.railway.app/`, với file tại `https://fasttowin.up.railway.app/app-ads.txt`; đây **chưa phải lựa chọn domain đã lưu hoặc file đã public**. Phương án tận dụng Web hiện tại, không thêm chi phí mua domain/hạ tầng. Domain riêng sau này là lựa chọn khác cần Owner chốt, không phải điều kiện bắt buộc của bản chuẩn bị này.

[Public Suffix List](https://publicsuffix.org/list/public_suffix_list.dat), bản31/10/2026đọc ngày01/11, liệt kê `up.railway.app`. Điều này hỗ trợ việc coi hostname tenant FastToWin là một miền tách biệt theo PSL; **chưa chứng minh AdMob đã crawl/chấp nhận hostname này**. Phải đối chiếu URL crawler thực tế sau khi Store/AdMob nhận cấu hình, không tự trỏ vào miền của tenant khác hoặc root do Railway quản lý.

[Hướng dẫn Google](https://support.google.com/admob/answer/9363762?hl=en) yêu cầu Developer website được liên kết trong listing và file được công bố tại root hostname mà crawler xác định. Đặt URL website ở trường liên hệ website; không thay URL Privacy hoặc nhập URL app-ads.txt vào trường Privacy. File đúng không đồng nghĩa appReady; [xác minh app](https://support.google.com/admob/answer/14538460?hl=en) và app readiness vẫn là bước riêng.

## Thiết kế local đã được Owner duyệt

Đây là bổ sung gọn vào Web đang có, không thay game/SDK:

1. Thêm đúng file vào `webApp/src/wasmJsMain/resources/app-ads.txt`, kiểm tra file được đưa vào site của gói Web thực tế.
2. Thêm route chính xác `/app-ads.txt` trước SPA fallback trong `deploy/railway/web/Caddyfile` và `deploy/Caddyfile`. Trả `text/plain; charset=utf-8`, HTTP200với đúng nội dung; thiếu file phải404, không trả `index.html`.
3. Kiểm tra local bằng harness Caddy routing hiện có: nội dung/type/status, nhánh thiếu file, cùng smoke các route legal/backend/SPA bị ảnh hưởng. Chỉ build/test cần thiết; không khởi chạy CI, không mở máy ảo/điện thoại.

Source Caddy trước thay đổi dùng `try_files {path} /index.html` ở catch-all, chưa có route riêng hay resource app-ads.txt. Kiểm thử local mới đã tái hiện thiếu file vẫn trả200. Đây **không phải kết quả HTTP production**. Công cụ Web không truy cập được URL production app-ads/robots trong lần đọc này, nên chưa kết luận200/404hoặc nội dung đang được phục vụ ngoài production.

## Kết quả triển khai local

- Thêm resource đúng snippet, UTF-8 không BOM, LF, 59 byte; `.gitattributes` giữ LF qua checkout Windows.
- Cả hai Caddyfile có handler riêng `/app-ads.txt`, không dùng SPA fallback. Chạy `scripts/tests/CaddyRouting.Tests.ps1` bằng Caddy 2.11.4 thật: ban đầu thất bại đúng vì thiếu file trả 200; sau sửa, cả hai cấu hình đạt 200/đúng nội dung/`text/plain; charset=utf-8`, query string không đổi nội dung, thiếu file trả 404.
- Các kiểm thử routing hiện có cùng đạt: SPA, 12 URL legal khi chưa đăng nhập, admin bật/tắt, hai mailbox API, auth/body/query/CSP/cache và chặn internal.
- Review phần thay đổi không phát hiện lỗi cụ thể. Kiểm thử routing đã chạy lại trên cây source chuẩn bị tích hợp và cùng đạt. Chưa push, chạy CI, thay cấu hình Store hoặc triển khai.
- Build offline `:webApp:composeCompatibilityBrowserDistribution` thành công trong 14 phút 54 giây: 91 task, 18 thực thi. Đối chiếu source và ba đầu ra JS/Wasm/compatibility: cùng 59 byte và SHA-256 `4ac7f90a0961b65faa5f6b0dd926e970920e7f032708780d95467014581304c0`. Bằng chứng tổng hợp: `.artifacts/app-ads-preparation-20261101/result.json`.
- Build ban đầu lỗi Java Unix-domain loopback trước khi xử lý project. Sau khi đặt riêng `jdk.net.unixdomain.tmpdir` vào `.artifacts/jtmp` cho tiến trình build, build chạy được; không sửa JDK, firewall hay cấu hình máy. Cảnh báo Compose plugin/runtime không đồng bộ phiên bản và kích thước bundle/dynamic dependency vẫn còn, nằm ngoài thay đổi file tĩnh này.

## Gói Web đã chuẩn bị để duyệt triển khai

- File: `.artifacts/app-ads-release-20261101-230154/FastToWin-Web-app-ads-20261101.zip`.
- SHA-256: `a433135d0a1b9f6237b542e786ec4e06f6c761a11d667f6860c4f67180bd7ef5`.
- Kích thước: 68.081.677 byte; 68 file. `manifest.json` cùng thư mục lưu checksum từng file; đã đọc lại toàn bộ entry ZIP và đối chiếu đủ nội dung/kích thước/checksum.
- Phạm vi: gói compatibility đã build, `config.production.js` same-origin và ba template Web Railway (`Caddyfile`, `Dockerfile`, `railway.json`). Không có backend/DB, tài liệu Privacy dự thảo hoặc dữ liệu tài khoản QA trong gói.
- Đây là gói local, chưa upload. Trước triển khai cần duyệt đúng gói/phạm vi; sau triển khai mới kiểm tra public `/app-ads.txt`, robots/redirect và URL crawler. Không suy ra AdMob Verified từ kiểm thử local.

## Các bước ngoài local — chưa thực hiện

- Sau khi code/gói được duyệt: xin phạm vi deploy Web chính xác, kiểm tra public file và khả năng crawler truy cập/robots/redirect. Không tự sửa CSP/quyền hay mở endpoint backend.
- Đọc lại rồi xin lưu Developer website của đúng FastToWin trên Store, giữ PrivacyURL và các khai báo khác. Chờ website xuất hiện trên listing thực tế.
- Khi app Store được AdMob tìm thấy và Owner cho phép liên kết/xác minh, đọc lại hostname/URL crawl và kết quả Verified. Google lưu ý cần thời gian đồng bộ/crawl; không tự polling hoặc tạo lịch theo dõi.
- Giữ AdMobOFF, consentDRAFT, chưa có callback production. app-ads.txt không cấp lượt chơi, không phát hành Play và không thay nhóm Closed tester.

Bước kế tiếp: tích hợp thay đổi và chuẩn bị gói/phạm vi triển khai Web cụ thể để Owner duyệt, rồi mới kiểm tra URL public và xử lý Developer website/AdMob ở các bước riêng. Không tự khởi chạy CI hoặc triển khai từ kết quả local này.
