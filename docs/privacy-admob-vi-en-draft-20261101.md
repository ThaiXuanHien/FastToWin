# Privacy VI/EN — nháp cho bản Android có quảng cáo

Trạng thái: **LOCAL DRAFT để Owner duyệt nội dung; chưa thay HTML, chưa deploy, chưa Publish consent hoặc bật quảng cáo**. Ngày soạn: 01/11/2026. Liên quan [#51](https://github.com/ThaiXuanHien/FastToWin/issues/51), [rà soát đối tác/chính sách](admob-consent-policy-sources-20261101.md), [hai nháp consent](admob-consent-proposal-20261101.md).

Đây là nội dung dự kiến thay mục7 và bổ sung mục6/9 của hai trang Privacy hiện có. Những mục còn lại giữ nguyên. Ngày cập nhật trên trang chỉ đổi khi có bản nội dung cuối cùng được duyệt để triển khai. Bản production hiện ghi quảng cáo tắt vẫn phù hợp trạng thái phát hành hiện tại.

## Bản tiếng Việt dành cho người chơi

### Bổ sung cuối mục6 — Thông báo và nhà cung cấp

Trong bản Android có bật quảng cáo, Google AdMob và các đối tác quảng cáo tham gia phân phối hoặc xử lý quảng cáo có thể xử lý dữ liệu theo mục7. Bạn có thể xem thông tin về Google tại [Cách Google sử dụng thông tin từ ứng dụng và trang web](https://policies.google.com/technologies/partner-sites) và [Chính sách quyền riêng tư của Google](https://policies.google.com/privacy). Thông báo quyền riêng tư quảng cáo của Google cung cấp danh sách đối tác, thông tin về cách họ sử dụng dữ liệu và các lựa chọn áp dụng cho bạn.

### Thay mục7 — Quảng cáo

Fast To Win chỉ cung cấp quảng cáo có thưởng trong các bản Android được bật tính năng này. Web và các bản chưa bật tính năng không cung cấp quảng cáo AdMob. Bạn tự chọn có xem quảng cáo để nhận thêm lượt chơi online hay không. Một lượt xem đủ điều kiện được máy chủ xác minh thành công sẽ cấp thêm 2 lượt chơi online; không cấp Gem hay tiền mặt. Nếu không xem, bạn không nhận phần thưởng thêm từ quảng cáo đó. Không cần xem quảng cáo để yêu cầu xóa tài khoản.

Khi chức năng quảng cáo hoạt động, SDK quảng cáo của Google có thể thu thập và chia sẻ địa chỉ IP để ước tính khu vực chung, tương tác với ứng dụng/quảng cáo, dữ liệu chẩn đoán và mã nhận dạng thiết bị/tài khoản có liên quan. Dữ liệu được dùng để phân phối, đo lường quảng cáo và chống gian lận. Việc xử lý phụ thuộc cấu hình, độ tuổi, lựa chọn của bạn và yêu cầu áp dụng tại khu vực của bạn.

Google User Messaging Platform hiển thị thông báo và thu nhận lựa chọn quyền riêng tư khi cần. Nếu thông báo cung cấp lựa chọn đồng ý, từ chối hoặc quản lý mục đích/đối tác, bạn có thể sử dụng các lựa chọn đó. Khi Google yêu cầu điểm truy cập để xem lại lựa chọn, mở **Cài đặt → Quyền riêng tư quảng cáo** trong game. Bạn có thể thay đổi hoặc rút lại sự đồng ý tại đó. Việc từ chối cá nhân hóa không nhất thiết ngăn mọi quảng cáo; quảng cáo không cá nhân hóa hoặc hạn chế có thể được cung cấp khi được phép.

Game hỏi nhóm tuổi để áp dụng giới hạn quảng cáo và lưu lựa chọn nhóm tuổi trên thiết bị theo tài khoản; luồng này không yêu cầu ngày sinh đầy đủ. Không yêu cầu quảng cáo trước khi có nhóm tuổi hợp lệ hoặc khi bạn chọn dưới13tuổi. Với nhóm13–17tuổi, game tắt cá nhân hóa và áp dụng tín hiệu giới hạn độ tuổi tương ứng cho SDK. Với người lớn, việc được phép cá nhân hóa còn phụ thuộc lựa chọn quyền riêng tư và trạng thái cho phép của Google.

Để xác minh thưởng và ngăn cấp trùng, máy chủ lưu mã phiên xem quảng cáo, tài khoản liên quan, mã giao dịch xác minh, thời điểm và kết quả cấp lượt. Game gửi cho Google một mã xác minh ngẫu nhiên để đối chiếu phiên, không gắn email, mã tài khoản game hoặc token đăng nhập vào trường xác minh thưởng. Mã này vẫn có thể được máy chủ liên kết với tài khoản; đây không phải dữ liệu ẩn danh. Việc lưu giữ và xử lý dữ liệu do Fast To Win quản lý tuân theo mục8/9; dữ liệu do Google và các đối tác quản lý được giải thích trong thông tin quyền riêng tư của họ.

Bạn cũng có thể đặt lại hoặc xóa mã nhận dạng quảng cáo bằng phần quản lý quảng cáo trong cài đặt Android. Thay đổi mã này không đồng nghĩa xóa toàn bộ dữ liệu đã được các nhà cung cấp xử lý. Nếu cần hỗ trợ về lựa chọn quyền riêng tư, liên hệ [xtit6789@gmail.com](mailto:xtit6789@gmail.com); không gửi mật khẩu hoặc token.

### Bổ sung mục9 — Quyền lựa chọn và xóa dữ liệu

Đối với quảng cáo trên Android, bạn có thể xem lại các lựa chọn tại **Cài đặt → Quyền riêng tư quảng cáo** khi mục này khả dụng theo yêu cầu của Google, và quản lý mã nhận dạng quảng cáo trong cài đặt Android. Xem mục7 để biết phạm vi lựa chọn và cách liên hệ hỗ trợ. Quy trình yêu cầu xóa tài khoản hiện có vẫn áp dụng và không yêu cầu xem quảng cáo.

## English text for players

### Add at the end of section6 — Notifications and providers

In Android releases with advertising enabled, Google AdMob and advertising partners involved in serving or processing ads may process data as described in section7. Learn about Google in [How Google uses information from apps and sites](https://policies.google.com/technologies/partner-sites) and [Google's Privacy Policy](https://policies.google.com/privacy). Google's ad privacy message provides the partner list, information about their data use and the choices applicable to you.

### Replace section7 — Advertising

Fast To Win offers rewarded ads only in Android releases with this feature enabled. The Web version and releases without the feature do not offer AdMob ads. You choose whether to watch an ad for additional online matches. An eligible view successfully verified by our server grants 2 additional online matches, not Gems or cash. If you do not watch, you do not receive that ad's additional reward. Watching an ad is not required to request account deletion.

When advertising is active, Google's ad SDK may collect and share an IP address to estimate a general area, app/ad interactions, diagnostic information and relevant device/account identifiers. The data is used for ad delivery, measurement and fraud prevention. Processing depends on configuration, age, your choices and requirements applicable in your region.

Google's User Messaging Platform displays privacy messages and collects choices when required. Where the message offers consent, refusal or controls for purposes/partners, you can use those options. When Google requires an entry point to review your choices, open **Settings → Ad privacy options** in the game. You can change or withdraw consent there. Refusing personalization does not necessarily prevent every ad; non-personalized or limited ads may be offered where permitted.

The game asks for an age band to apply ad restrictions and stores that choice on the device for the account; this flow does not ask for a full date of birth. It does not request ads before a valid age band is available or when you select under13. For ages13–17, the game disables personalization and applies the corresponding age-restriction signals to the SDK. For adults, personalization also depends on privacy choices and Google's eligibility state.

To verify rewards and prevent duplicate grants, our server stores the ad-view session identifier, associated account, verification transaction identifier, timestamps and match-grant outcome. The game sends Google a random verification code to match the session; it does not attach an email, game account identifier or login token to the reward-verification field. Our server can still link this code to the account, so it is not anonymous data. Sections8/9 apply to data managed by Fast To Win; Google's and its partners' privacy information explains the data they manage.

You can also reset or delete the advertising identifier using Android's ad settings. Changing this identifier does not delete all data already processed by providers. For help with privacy choices, contact [xtit6789@gmail.com](mailto:xtit6789@gmail.com); do not send passwords or tokens.

### Add to section9 — Your choices and deletion

For Android ads, review your choices in **Settings → Ad privacy options** when this entry is available as required by Google, and manage the advertising identifier in Android settings. Section7 explains the choices and how to contact support. The existing account-deletion process still applies and does not require watching an ad.

## Ghi chú duyệt nội bộ — không đưa lên trang Privacy

### Những nội dung đã đối chiếu

| Nội dung | Bằng chứng |
| --- | --- |
| Android có thưởng, phần thưởng2lượt, xác minh phía server | [Runbook](admob-android.md); `PostgresPlayQuotaRepository.grantRewardedAd`; `V63__admob_rewarded_sessions.sql` |
| Nhóm tuổi, không hỏi ngày sinh, lưu theo tài khoản trên thiết bị | `RewardedAdPrivacyPolicy.kt` trong commonMain, `AndroidRewardedAdStorage.kt`, `AndroidRewardedAdDriver.kt` |
| Điểm vào chỉ khi UMP yêu cầu; nhãn VI/EN thực tế | `SettingsScreen.kt`, `RewardedAdCatalogTexts.kt`, `showPrivacyOptions` trong Android driver |
| Mã xác minh ngẫu nhiên, trường userID trống; dữ liệu vẫn liên kết được | `setServerSideVerificationOptions` trong Android driver; `PostgresAdMobRepository`; migrationV63 |
| Dữ liệu SDK và giới hạn AD_ID | [GMA Next-Gen Data safety](https://developers.google.com/admob/android/next-gen/privacy/play-data-disclosure), đọc01/11/2026, trang cập nhật30/10. Trang mô tả SDK mới nhất; phải xác nhận với candidate dùng1.5.0 trước phát hành. Đây không phải khai báo đã lưu trên Play. |
| Thông tin bên nhận và rút lại sự đồng ý | [Google EU consent policy](https://www.google.com/about/company/user-consent-policy/), [UMP](https://developers.google.com/admob/android/privacy) |

### Điều kiện trước khi đưa bản nháp vào HTML hoặc xuất bản

1. **Chốt đối tác và feature thực tế.** [Lần đọc sâu hơn](admob-partner-reconciliation-20261101.md) xác nhận AdMaxim(GVL9) và Index Exchange(GVL10) đang được chọn, preview công bố precise-location data và có opt-in riêng mặc địnhOFF. Như vậy đã có cơ sở vendor/feature trong editor; EU selector198so với preview210vẫn chưa đối chiếu đủ và chưa nghiệm thu form giao tới thiết bị. Đoạn dẫn tới danh sách trong form chỉ dùng được khi danh sách thực tế đầy đủ, dễ truy cập và thông tin đối tác có thể mở được. Chưa tuyên bố không thu thập/chia sẻ vị trí chính xác; cũng chưa khẳng định app đang đọc GPS. Không đưa số lượng đối tác thay đổi vào chính sách.
2. **Nghiệm thu quyền thay đổi lựa chọn.** Kiểm tra vùng/tuổi, đồng ý/từ chối, US opt-out và mở lại entry point trên đúng candidate khi được Owner yêu cầu. Không coi việc có source hoặc hai DRAFT là nghiệm thu thiết bị. Form Google hiện dùngEN; nội dung app và Privacy cóVI/EN.
3. **Đối chiếu retention với dữ liệu thưởng.** Chưa đặt một thời hạn xóa tự động mới cho phiên/biên nhận; thời điểm hết quyền nhận thưởng không phải thời điểm xóa hàng DB. Mục8 hiện chỉ mô tả lưu giữ cần thiết và backup tối đa14ngày. Trước phát hành cần xác nhận cách áp dụng xóa tài khoản cho phiên, biên nhận và bản sao lưu, cùng các nghĩa vụ lưu giữ thực tế; không hứa Google xóa theo lịch của Fast To Win.
4. **Chốt bản có quảng cáo và khai báo Play.** Candidate, quyền AD_ID/READ_BASIC_PHONE_STATE, Contains ads/Advertising ID/Data safety phải được đối chiếu riêng. Không lấy nội dung nháp làm quyền đổi Console hay bật LIVE.
5. **Đồng bộ thời điểm triển khai.** Chỉ đổi HTML/ngày cập nhật, deploy Privacy, Publish consent hoặc kích hoạt sau khi nội dung/phạm vi được duyệt riêng. Trước đó giữ chính sách production đang ghi adsOFF. BackendSSV/store link/readiness/app-ads.txt vẫn là cổng độc lập.

Không sửa runtime, HTML hoặc giao diện; không chạy CI/build/test thiết bị, đọc DB/credential, đăng issue hay thay Console trong bước soạn này. Bản nháp không phải kết luận pháp lý hoặc chứng nhận tuân thủ.
