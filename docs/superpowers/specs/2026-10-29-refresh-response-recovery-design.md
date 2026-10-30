# Khôi phục phản hồi refresh bị mất — đề xuất để duyệt

Ngày 29/10/2026. Phạm vi giai đoạn đầu: backend chung và Android. Tài liệu thiết kế, chưa có triển khai hoặc quyền deploy/migrate/phát hành mới.

## Vấn đề và bằng chứng

Server hiện commit xoay access/refresh token trước khi gửi HTTP response. Token refresh cũ mất hiệu lực ngay. Nếu client không nhận được response thì vẫn giữ token cũ; lần refresh tiếp theo nhận 401 và chuyển về đăng nhập. Ca local đã tái hiện 3/3; route backend local xác nhận bộ token mới vẫn hợp lệ trong khi token cũ bị từ chối.

Internal QA 1.0.7(8), source 1445232, đã nghiệm thu trên Samsung 29/10: 960.125 giây chạy nền, 65 checks, cùng controller/process; 6 lỗi TRANSPORT vẫn giữ phiên; khi mở lại nhận 200 và lưu thành công. Hai request được đối chiếu chính xác với Web proxy bằng request-id. Ca này chưa tái hiện lỗi hết phiên trước đó và không chứng minh nguyên nhân của mọi lần đăng xuất.

Mục tiêu mới là sửa đúng lỗ hổng phục hồi đã tái hiện; không gắn kết quả này với nguyên nhân của sự cố thiết bị chưa được chứng minh.

## Lựa chọn

| Hướng | Khả năng phục hồi | Đánh đổi |
| --- | --- | --- |
| **Thử lại có định danh bí mật, trả lại đúng kết quả đã cấp** | Có, kể cả server restart; không xoay thêm trong cùng lần thử | Cần trạng thái bền vững và transaction ở backend, lưu pending trước HTTP ở Android. Đề xuất chọn. |
| Cho token cũ tự hiệu lực thêm một khoảng | Có thể giảm đăng xuất | Token cũ đơn lẻ có thể đổi thêm credential trong khoảng nới; không phân biệt rõ request phát lại và lần refresh mới. Không chọn. |
| Chỉ retry/đổi thông báo ở client | Không lấy lại được bộ token đã commit mà client chưa nhận | Không giải quyết hợp đồng một lần ở server. Không chọn. |

Android triển khai trước vì là nền tảng đã có ca tái hiện/nghiệm thu. Web/PWA dùng cookie HttpOnly và cơ chế phối hợp nhiều tab; native iOS có Keychain riêng. Giai đoạn này kiểm tra tương thích các nền tảng đó nhưng không bật cơ chế mới cho chúng.

## Luồng đề xuất

1. Trước HTTP đầu tiên, Android tạo `refreshAttemptSecret` ngẫu nhiên mật mã 32 bytes, base64url không padding, và lưu nó cùng credential cũ trong chính bản ghi phiên được mã hóa. Không dùng `kotlin.random.Random` cho secret này.
2. Client gửi token refresh hiện tại và attempt secret qua HTTPS trong body `/auth/refresh`. Secret được coi như credential phụ, không phải request-id để ghi log. Nó không được xuất hiện trong trace QA, URL, telemetry hoặc audit.
3. Backend xoay token và ghi biên nhận khôi phục trong **cùng transaction**. Chỉ trả response sau commit.
4. Nếu client mất response, nó giữ nguyên cặp token cũ/attempt secret, dùng lại khi reconnect theo backoff hiện có. Không tạo secret mới cho cùng kết quả chưa rõ.
5. Trong tối đa **5 phút tính từ commit đầu tiên**, đúng cặp cũ có thể nhận lại **chính bộ token đã cấp**, với thời hạn ban đầu. Không phát sinh vòng xoay mới hoặc gia hạn cửa sổ theo retry.
6. Khi client lưu được bộ token mới thì pending attempt bị xóa cùng lần ghi bản ghi phiên. Lần refresh kế tiếp dùng một secret mới.
7. Chỉ trả biên nhận nếu phiên nguồn vẫn hợp lệ, tài khoản hoạt động và các token kế tiếp trong biên nhận vẫn là generation hiện tại. Đăng xuất/khóa/đổi mật khẩu/thu hồi/đăng nhập nơi khác luôn có hiệu lực.

Sau 5 phút, sai token/secret, receipt hỏng hoặc token kế tiếp không còn là generation hiện tại: từ chối bằng lỗi auth chung, không tiết lộ receipt có tồn tại. Không coi mọi401 là lỗi mạng. Một request lặp không được làm mới TTL hoặc tự sửa lại phiên đã bị thu hồi.

## Contract và tương thích

- Bổ sung trường tùy chọn `refreshAttemptSecret: String? = null` vào `RefreshTokenRequest`; chỉ bật cho client Android mới sau backend rollout đầy đủ. Token response vẫn dùng `AuthSessionResponse` hiện tại.
- `LogoutRequest` cũng nhận attempt secret tùy chọn để thu hồi generation đã commit trong lúc client còn pending. Thiếu trường mới giữ hành vi logout legacy.
- Thiếu secret giữ nguyên đường refresh legacy cho Internal8/Web/iOS cũ. Secret có nhưng sai format bị 400 trước khi xoay token. Không nhận secret trong header/path/query.
- Request được xác định là Web session tiếp tục dùng cookie HttpOnly, Origin/CSRF hiện tại; không đưa refresh token hoặc attempt secret vào localStorage. Không bật replay receipt cho cookie flow trong giai đoạn này.
- Android mới hoạt động sau backend mới; không phát hành client trước khi backend/schema mới có trên mọi instance đang phục vụ. Không dùng `ignoreUnknownKeys` của server cũ làm bằng chứng server hỗ trợ khôi phục.
- Bản Android cũ không được hưởng phục hồi response loss; vẫn hoạt động như trước. Có thể rollback client trong khi backend tiếp tục nhận cả legacy/new.

## Lưu trạng thái backend

Đề xuất migration kế tiếp sau V68 (dự kiến V69, kiểm tra số còn trống khi triển khai), bảng `auth_refresh_recoveries`:

- `session_id` liên kết đúng session tồn tại, tối đa 1 receipt hiện tại/session;
- hash token refresh đầu vào, hash attempt secret;
- hash token refresh/access kế tiếp để fence generation;
- payload response được mã hóa xác thực, phiên bản codec, nonce mã hóa;
- issued-at, recovery-expires-at cố định 5 phút từ commit, thời hạn metadata thu hồi gắn với hạn session kế tiếp, ràng buộc/index cho tra cứu và dọn hạn.

Không lưu plaintext token, attempt secret hoặc JSON response rõ trong DB. Không cần Redis, không dùng cache RAM làm nguồn chuẩn cho receipt. InMemoryAuthRepository mô phỏng cùng contract với giới hạn trạng thái tương ứng; PostgreSQL là bằng chứng bền vững.

### Mã hóa biên nhận

Đề xuất dùng primitive chuẩn, không tự tạo thuật toán mật mã:

- HKDF-SHA256 theoRFC 5869: IKM là bytes UTF-8 chính xác của token refresh cũ có entropy256 bit; salt là32 bytes attempt secret sau giải mã base64url; info tách miền cố định `fasttowin/auth-refresh-recovery/v1`.
- Dẫn xuất key AES-256 và mã hóa AES-GCM bằng implementation JCA; nonce GCM 12 bytes mới từSecureRandom cho mỗi receipt, authentication tag 128 bit. Không dùng lại nonce sau re-encryption.
- AAD gắn phiên bản codec, sessionID, hash đầu vào/attempt, hash successor và các mốc issued/expiry để chống hoán đổi payload/metadata.
- Key dẫn xuất chỉ trong bộ nhớ xử lý request rồi bỏ; DB chỉ chứa hash/ciphertext/metadata. Người chỉ có DB dump không có đủ token cũ/attempt secret để giải mã. Không tạo thêm provider hoặc secret server dài hạn trong thiết kế này.
- Mất tag/codec sai/giải mã lỗi thì fail closed, không trả dữ liệu cũ hoặc sinh cặp token mới từ receipt hỏng. Kiểm tra độ dài/format trước cấp phát/crypto; không log giá trị đầu vào hoặc ciphertext.

Đây là thiết kế bổ sung của FastToWin, không phải khẳng định RFC 9700 định nghĩa idempotent refresh. Cửa sổ 5 phút làm tăng khoảng thời gian dùng lại **đúng cặp** đầu vào+secret để lấy lại kết quả cũ; không có bảo vệ nếu cả hai credential của lần thử bị đánh cắp. Nó không thay cho device binding/DPoP và không mở rộng phạm vi truy cập của phiên.

## Transaction, concurrency và thu hồi

- Trong một transaction, khóa đúng session và kiểm tra account/restriction, xác định fresh rotation hoặc exact replay, rồi cập nhật token và receipt cùng lúc. Không có trạng thái commit token nhưng thiếu receipt. Nếu encode/crypto/DB lỗi trước commit thì rollback toàn bộ.
- Hai request cùng cặp gửi đồng thời phải nhận cùng response; chỉ 1 rotation. Tra cứu/khóa phải xử lý được trường hợp request thứ hai tới sau khi hash cũ đã được thay, không chỉ `SELECT ... WHERE current_refresh_hash=old FOR UPDATE` như hiện tại.
- Cùng token cũ nhưng secret khác bị từ chối, không tạo generation mới và không làm thay đổi chính sách thu hồi hiện có. Không tự bổ sung chính sách revoke-family trong task này.
- Quy tắc khóa/linearization phải thống nhất với login replace, logout, revoke-one/all, password reset/change, delete và moderation block. Receipt không cấp kết quả khi revocation đã commit trước request recovery. Nếu recovery hoàn tất trước revocation thì revocation phải làm mất hiệu lực bộ token vừa trả như thường lệ.
- Khi next rotation hợp lệ xảy ra, receipt cũ không còn dùng được; xóa/thay receipt khi cập nhật session. Những request legacy cũng phải vô hiệu receipt cũ nếu chúng advance generation.
- Logout bằng credential cũ đang pending phải tìm đúng session qua cặp pending cũ/secret và thu hồi cả generation kế tiếp còn hiện hành; không để orphan session tiếp tục sống chỉ vì token cũ đã bị xoay. Không dùng receipt của session A để thu hồi session B hoặc phiên đăng nhập mới thay thế.
- Cửa sổ lấy lại response và thời hạn tra cứu thu hồi là hai việc khác nhau: sau 5 phút xóa ciphertext nhưng giữ các hash ánh xạ đúng predecessor/attempt→session/successor hiện hành để phục vụ **chỉ logout**. Metadata này không cấp token, tối đa 1 bản ghi/session; hết hạn cùng session kế tiếp, bị thay khi rotation mới hoặc bị xóa cùng thu hồi/sessiondelete. Nhờ vậy logout sau cửa sổ 5 phút vẫn thu hồi được orphan generation hiện hành; không biến bản ghi hết hạn thành một đường recovery không giới hạn.
- Mọi đường thu hồi còn lại làm receipt vô hiệu qua trạng thái session/generation và dọn receipt cùng transaction nơi phù hợp. Điều kiện xác thực luôn kiểm tra trạng thái mới, không chỉ dựa vào tồn tại ciphertext chưa hết 5 phút.
- Payload recovery hết hạn bị từ chối ngay, kể cả ciphertext chưa được dọn. Dọn ciphertext 5 phút và metadata hết hạn session có giới hạn theo index, chạy trong lifecycle worker hiện có; không tạo scheduler/provider riêng. Xóa account/session phải cascade, không giữ orphan receipt.

## Android và độ bền

- `StoredAuthSession` thêm pending attempt tùy chọn có giá trị mặc định để đọc được dữ liệu cũ. Chỉ store Android chuẩn bị/sử dụng trường mới; đường Web/iOS không tạo secret.
- Bản ghi mã hóa của Android chứa cả credential cũ và pending, không tạo file plaintext riêng. Lưu pending phải bền vững thành công **trước** khi gửi HTTP; nếu lưu thất bại thì không gửi request có thể xoay token.
- Sử dụng thao tác update có kiểm tra phiên/generation hiện tại dưới cùng khóa store theo serverUrl trong process. Controller được tạo lại phải đọc pending đang có, không tạo secret khác. Hoàn tất chỉ được lưu nếu session/attempt còn khớp.
- Sau response, lưu credential mới + pending null như một bản ghi nguyên tử. Process chết trước bước này thì lần mở sau dùng pending cũ khôi phục; chết sau bước này thì dùng credential mới. Nếu storage bị lỗi thật, giữ xử lý fail-closed rõ ràng; không hứa phục hồi khi dữ liệu local đã mất hoặc hỏng.
- `AuthController` tiếp tục sở hữu single-flight refresh; hủy màn hình không hủy rotation, close/logout không cho response trễ khôi phục đăng nhập. Không tạo một retry owner song song với socket/backoff hiện có.
- Khi người dùng logout trong lúc refresh, xóa phiên local và hủy pending; request logout của controller sở hữu snapshot cần gửi đủ cặp token/attempt để thu hồi generation đã commit nếu chưa nhận được response. Không đợi response refresh để phục hồi UI đăng nhập đã thoát.
- Trace QA hiện tại tiếp tục chỉ scalar/enum; không đưa attempt secret hoặc token/hash của chúng vào log. Có thể thêm enum nhận receipt replay nếu thật sự cần trong kế hoạch test, không phải một ID từ secret.
- Không tự gửi lại lệnh mua/nhận thưởng/quyên góp/trận; phục hồi chỉ áp dụng giao dịch cấp credential.

## Kiểm thử phải đạt trước phân phối

1. Backend commit xong nhưng transport cố ý làm mất response: retry đúng cặp nhận đúng bộ token và client giữ PLAYING; không có rotation thứ hai. Test phải dùng route/service thật cùng PostgreSQL, không chỉ MockEngine.
2. Lỗi mạng trước commit vẫn retry được; timeout/408/429/5xx không xóa phiên hợp lệ, không vô hạn tight-loop.
3. Restart backend sau commit, restart/controller recreation phíaAndroid trước lưu: cùng pending phục hồi được trong cửa sổ.
4. Request đồng thời: cùng cặp, khác attempt, fresh so với replay, receipt cũ sau rotation tiếp; hash successor và số rotation đúng.
5. Race với logout, revoke-one/all, login thay phiên, moderation block, password change/reset và delete; không khôi phục phiên đã bị thu hồi. Bao gồm logout dùng cặp pending cũ sau server commit, trước/sau 5 phút và sau khi ciphertext đã dọn, chỉ thu hồi đúng successor hiện hành.
6. Expiry5 phút (ngay trước/đúng/ngay sau), sai token/secret, malformed/oversize, ciphertext/tag/AAD bị sửa, receipt bị gắn session khác: không nhận credential mới; replay không kéo dàiTTL.
7. Save pending thất bại trước request không được gửiHTTP; crash trước/sau atomic save; response cũ saulogout hoặc đăngnhập khác không overwrite phiên hiện tại.
8. DB dump/log/audit không có token/attempt secret/plainresponse; HKDF official test vector và cipher roundtrip/tamper tests; review bảo mật độc lập trước merge.
9. Client legacy Android 8, Web cookie/CSRF và iOS compile tương thích; không đưa bearer credential về localStorage hoặc thay đổi semantics cookie.
10. CI đầy đủ, migration PostgreSQL thật và binary Android ký: ca response loss có kiểm soát cùng ca background/reopen. Ghi rõ phần nào là local, staging hoặc thiết bị thật; không dùng một lần 16 phút xanh làm bằng chứng mọi timing.

## Rollout và giới hạn quyền

- Thiết kế được duyệt → kế hoạch triển khai/test → thay đổi local và review. Bước này chưa có quyền migrate/deploy server, bật môi trường, merge hoặc phát hành Internal mới.
- Thử trên môi trường cô lập trước; backup/restore và migration gate cần duyệt cụ thể trước production. Không tái dùng approval bảo trì cũ cho schema mới.
- Backend/schema mới tương thích legacy trước, Android bật sau khi server hoàn tất. Internal QA 8 đang chạy tiếp tục dùng luồng cũ; không thay khi chưa có candidate được review.
- Sau khi client mới đã tạo receipt, không tự rollback server về binary không hiểu recovery rồi tuyên bố an toàn. Giữ service mới hoặc duy trì bảo trì và xử lý theo kế hoạch được duyệt. Giới hạn5 phút không tự chứng minh rollback mọi request an toàn.
- Không liên quan testerclosedtesting/Alpha/AdMob/UGC/gemrevenue. Không cần Redis hoặc multi-instance rollout để hoàn tất hợp đồng này, nhưng transaction phải chịu được hai request cạnh tranh.

## Phần triển khai dự kiến

`AuthProtocol.kt`, `AuthApiClient.kt`, `AuthController.kt`, `SessionStores.kt`, `AndroidAuthSessionStore.kt`, nền tảng tạo secretAndroid, `Authentication.kt`, `AuthRepository`/Postgres/InMemory, authroutes/logout, migration mới, tài liệu API và kiểm thử tương ứng. Không sửa UI.

## Tài liệu đối chiếu

- RFC 9700 [§4.14.2 Refresh Token Protection](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14.2): xoay token, bảo vệ replay và thu hồi. Dùng để đánh giá rủi ro; không gọi contract mới là OAuth-compliant chỉ từ tài liệu này.
- RFC 5869 [HKDF](https://www.rfc-editor.org/rfc/rfc5869.html): primitive dẫn xuất key và test vector. Đây không phải hướng dẫn tự chế token deterministic.
- Tài liệu repo `docs/account-api.md`; source và bằng chứng `.artifacts/auth-recurrence-20261028/`, `.artifacts/internal-auth-qa-v8-20261028/physical-acceptance.json`.

## Tự rà soát

Đã chọn rõ Android trước/backend hỗ trợ legacy, cửa sổ 5 phút từcommit, nguồn chuẩnPostgreSQL, khôngplaintext credential, cùngtransaction/khôngextendTTL, logoutpending và các revocation race, testresponse loss thật và rollout riêng. Không còn TODO/TBD hoặc hành vi chưa chốt trong phạm vi đề xuất. Đây là đề xuất để người dùng duyệt trước bước lập kế hoạch, chưa phải xác nhận crypto/transaction đã được kiểm thử hoặc triển khai.
