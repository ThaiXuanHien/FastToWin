# Thiết kế kinh tế, cấp bang, lượt chơi và hoàn thiện đa nền tảng

**Ngày:** 08/09/2026

**Trạng thái:** Đã thống nhất trong trao đổi, chờ duyệt lại bản đặc tả tiếng Việt

## 1. Mục tiêu

Phát triển hệ thống kinh tế và tiến trình dài hạn của Fast To Win nhưng không
biến bảng xếp hạng thành cơ chế trả tiền để thắng. Đợt phát triển này sẽ đơn
giản hóa cửa hàng, bổ sung quyên góp và cấp bang, xếp hạng tài sản kiếm được,
mở rộng nhiệm vụ và vật phẩm thành tích, đồng thời thêm giới hạn lượt chơi
online do server quản lý và cơ chế nhận thêm lượt qua quảng cáo có thưởng.

Đợt này cũng loại bỏ xem lại diễn biến trận, sửa kết nối lại, hoàn thiện nền tối
trên Web và xử lý các phần đa ngôn ngữ còn thiếu. Phần production xác định kiến
trúc AWS Singapore và mô hình chi phí cho 10.000, 50.000, 100.000 kết nối
WebSocket đồng thời, cũng như tải độc lập 10.000, 50.000, 100.000 thao tác mỗi
giây.

## 2. Phạm vi và các quyết định đã chốt

- Cửa hàng chỉ bán gói Gem và gói Vàng.
- Mặt số và Bàn số đã sở hữu vẫn được giữ và sử dụng nhưng không còn được bán.
- Vàng được đổi bằng Gem; Gem được mua qua Store của nền tảng.
- Xếp hạng bang dùng tổng tài sản đã quyên góp, không dùng số dư ví hiện tại của
  thành viên.
- Xếp hạng Vàng và Gem của người chơi dùng tổng tài sản hợp lệ đã kiếm được.
  Gem mua từ Store và Vàng đổi từ Gem không được tính.
- Mỗi tài khoản có 10 lượt đấu online mỗi ngày. Đấu thường và đấu hạng dùng
  chung hạn mức.
- Một quảng cáo được xác minh hợp lệ cộng 2 lượt. Không giới hạn số quảng cáo
  trong ngày.
- Luyện tập offline, đấu giải và hoạt động bang không tiêu thụ lượt.
- Bỏ xem lại diễn biến trận. Vẫn giữ Mời đấu lại, lịch sử trận, chi tiết trận,
  chia sẻ kết quả và dữ liệu sự kiện trận.
- Khung và Danh hiệu chỉ nhận được qua hoạt động, mùa giải hoặc bang hội; không
  bán trong cửa hàng.
- Dự toán production dùng AWS khu vực Châu Á Thái Bình Dương (Singapore) và
  tính cả số kết nối đồng thời lẫn số thao tác mỗi giây.

Đăng nhập Google/Apple và danh sách người vừa thi đấu cùng tiếp tục nằm ngoài
phạm vi theo quyết định sản phẩm trước đó. Quảng cáo có thưởng trên Web cũng
chưa triển khai production cho đến khi chọn được nhà cung cấp hỗ trợ xác minh
phía server.

## 3. Kiến trúc

Server là nguồn quyết định duy nhất cho số dư ví, tổng tài sản kiếm được, tiến
trình bang, lượt chơi, lượt thưởng từ quảng cáo và thứ tự xếp hạng. Client không
được gửi số tiền thưởng, XP, tỷ lệ quy đổi hoặc điểm xếp hạng cần áp dụng.

Hệ thống được chia thành các module có interface nhỏ và che giấu phần xử lý
phức tạp bên trong.

### 3.1. Ví tài sản (`Wallet`)

`Wallet` cộng hoặc trừ Vàng/Gem và ghi lịch sử ví trong cùng một transaction.
Mỗi thay đổi có khóa chống xử lý lặp và mã nguồn phát sinh. Module cũng xác định
khoản cộng nào được tính vào tổng tài sản kiếm được hợp lệ.

Các nguồn được tính gồm thưởng trận, điểm danh, nhiệm vụ, thành tích, mùa giải
và nhiệm vụ bang. Gem mua từ Store, Vàng đổi từ Gem, dữ liệu seed môi trường
dev, điều chỉnh của quản trị viên và hoàn tiền đều không được tính. Quyên góp
bang là khoản trừ nên không làm tăng tổng tài sản kiếm được.

### 3.2. Luật kinh tế (`RewardEconomy`)

`RewardEconomy` quản lý toàn bộ luật thưởng trận, điểm danh, nhiệm vụ, thành
tích, quy đổi tài sản và XP bang. Nơi gọi chỉ truyền mã luật cùng dữ kiện; module
trả về quyết định cần áp dụng. Cách này giữ giá trị nhất quán giữa lưu trữ phần
thưởng, protocol và nội dung xem trước trên UI.

### 3.3. Tiến trình bang (`ClanProgression`)

`ClanProgression` nhận bang, thành viên, loại tài sản và số lượng quyên góp. Một
transaction duy nhất phải trừ ví, ghi lịch sử quyên góp, tăng tổng đóng góp của
thành viên và bang, cộng XP bang rồi áp dụng mọi mốc cấp vừa vượt qua. Không
được lưu trạng thái dở dang nếu một bước thất bại.

### 3.4. Hạn mức lượt chơi (`PlayQuota`)

`PlayQuota` trả về số lượt còn lại, kiểm tra quyền vào trận online và trừ lượt
khi phòng chuyển sang trạng thái đang chơi. Module chỉ cộng 2 lượt thưởng sau
khi nhận kết quả hợp lệ từ `RewardedAdVerifier`. Mã giao dịch quảng cáo và mã
trận đều phải chống xử lý lặp.

### 3.5. Xác minh quảng cáo (`RewardedAdVerifier`)

`RewardedAdVerifier` là seam giữa luật game và nền tảng quảng cáo. Adapter dev
xác minh biên nhận thử nghiệm có đánh dấu rõ. Adapter production trên mobile
xác minh bằng chứng phía server của nhà cung cấp. Adapter Web không cộng lượt
cho đến khi nhà cung cấp phù hợp được chọn và cấu hình.

### 3.6. Bảng xếp hạng (`Leaderboard`)

`Leaderboard` đọc các giá trị tổng hợp đã được đánh index, không cộng lại toàn
bộ lịch sử ví hoặc quyên góp mỗi khi có yêu cầu. Module hỗ trợ phân trang bằng
cursor và thứ tự phụ ổn định khi nhiều người có cùng giá trị.

## 4. Dữ liệu và migration

Một migration Flyway tiến một chiều sẽ bổ sung:

- `player_stats.lifetime_earned_gold` và
  `player_stats.lifetime_earned_gems`, không âm và mặc định bằng 0.
- `player_stats.lifetime_earned_gold_reached_at` và
  `player_stats.lifetime_earned_gems_reached_at`, được cập nhật khi tổng tương
  ứng tăng và dùng để phân định thứ hạng bằng nhau.
- `clans.experience_points`, `clans.level`, `clans.donated_gold`,
  `clans.donated_gems` và `clans.level_reached_at`.
- `clan_members.donated_gold` và `clan_members.donated_gems`.
- Bảng `clan_donations`: `id`, `clan_id`, `user_id`, `currency`, `amount`,
  `experience_awarded`, `request_id`, `created_at`.
- Bảng `daily_play_quotas`: `user_id`, `quota_date`, `matches_consumed`,
  `bonus_matches_granted`, `updated_at`.
- Bảng `rewarded_ad_grants`: `id`, `user_id`, `provider`,
  `provider_transaction_id`, `matches_granted`, `verified_at`.

Ràng buộc duy nhất bảo vệ `(user_id, quota_date)`, mã yêu cầu quyên góp và mã
giao dịch quảng cáo. Các index giảm dần phục vụ xếp hạng Vàng/Gem người chơi,
cấp/XP bang và tổng Vàng/Gem bang đã nhận.

Số dư ví và vật phẩm hiện tại được giữ nguyên. Các giao dịch cộng trong lịch sử
cũ chỉ được cộng vào tổng tài sản kiếm được khi nguồn của chúng chắc chắn hợp
lệ. Nguồn cũ không rõ ràng sẽ bị loại khỏi xếp hạng thay vì làm tăng thành tích
sai lệch.

## 5. Cửa hàng và quy đổi tài sản

Cửa hàng có hai tab cùng chiều rộng:

| Tab | Nội dung |
| --- | --- |
| Gem | Các gói Store do catalog server cung cấp |
| Vàng | Các gói đổi Gem thành Vàng |

Catalog quy đổi do server quản lý:

| Gói | Chi phí | Nhận được |
| --- | ---: | ---: |
| Túi Vàng | 10 Gem | 1.000 Vàng |
| Rương Vàng | 45 Gem | 5.000 Vàng |
| Kho Vàng | 80 Gem | 10.000 Vàng |

Hai gói lớn nhận nhiều hơn khoảng 11% và 25% so với tỷ lệ cơ bản. Việc quy đổi
phải nguyên tử và chống xử lý lặp. Vàng quy đổi không được cộng vào
`lifetime_earned_gold`.

`CARD_BACK` và `BOARD_SKIN` vẫn tồn tại trong protocol vì tài khoản cũ có thể đã
sở hữu và đang trang bị chúng. Chỉ xóa chúng khỏi catalog bán hàng và tab cửa
hàng, không xóa dữ liệu sở hữu.

## 6. Cân bằng phần thưởng

### 6.1. Thưởng trận

| Kết quả | Đấu thường | Đấu hạng |
| --- | --- | --- |
| Thắng | 80 Vàng, 24 XP | 100 Vàng, 30 XP |
| Hòa | 40 Vàng, 16 XP | 50 Vàng, 20 XP |
| Thua | 20 Vàng, 8 XP | 25 Vàng, 10 XP |
| Chủ động rời sau khi bắt đầu | 0 Vàng, 0 XP | 0 Vàng, 0 XP và xử thua Elo |

Phòng bị hủy trước khi chơi không phát thưởng. Thưởng trận phải chống xử lý lặp
theo mã trận và người chơi.

### 6.2. Điểm danh hằng ngày

Giữ nguyên chu kỳ bảy ngày hiện tại:

- XP: `10, 10, 15, 15, 20, 25, 40`.
- Vàng: `50, 60, 70, 80, 100, 120, 200`.
- Gem: `0, 0, 0, 0, 0, 0, 1`.

Giá trị này nhỏ hơn thu nhập từ mười trận mỗi ngày, trong khi ngày thứ bảy vẫn
có phần thưởng cao cấp đáng chú ý.

### 6.3. Phần thưởng mùa

Giữ nguyên thưởng theo bậc hiện tại: 300–6.000 Vàng và 0–12 Gem từ Đồng đến
Thách Đấu, kèm vật phẩm mùa hiện có.

### 6.4. Xoay vòng nhiệm vụ

Mỗi ngày server chọn 4 trong 10 nhiệm vụ: 1 Dễ, 2 Thường và 1 Khó hoặc Tinh
anh. Mỗi tuần chọn 4 trong 8 nhiệm vụ: 1 Thường, 2 Khó và 1 Tinh anh. Kết quả
xoay vòng được xác định theo chu kỳ để người dùng kết nối lại hoặc đổi thiết bị
vẫn nhìn thấy cùng một bộ nhiệm vụ.

Nhiệm vụ ngày:

| Mã | Yêu cầu | Độ khó | Phần thưởng |
| --- | --- | --- | --- |
| `DAILY_PLAY_1` | Hoàn thành 1 trận online | Dễ | 10 XP, 50 Vàng |
| `DAILY_CASUAL_2` | Hoàn thành 2 trận đấu thường | Dễ | 15 XP, 80 Vàng |
| `DAILY_PLAY_3` | Hoàn thành 3 trận online | Thường | 20 XP, 100 Vàng |
| `DAILY_WIN_1` | Thắng 1 trận online | Thường | 25 XP, 150 Vàng |
| `DAILY_RANKED_2` | Hoàn thành 2 trận đấu hạng | Thường | 25 XP, 120 Vàng |
| `DAILY_CORRECT_100` | Chọn đúng 100 số | Thường | 25 XP, 150 Vàng |
| `DAILY_ACCURACY_90` | Hoàn thành trận với độ chính xác từ 90% | Khó | 35 XP, 200 Vàng |
| `DAILY_PERFECT_WIN_1` | Thắng mà không chọn sai | Tinh anh | 40 XP, 250 Vàng, 1 Gem |
| `DAILY_CHECK_IN` | Nhận điểm danh trong ngày | Dễ | 10 XP, 50 Vàng |
| `DAILY_DONATE_GOLD_500` | Quyên góp 500 Vàng cho bang | Khó | 30 XP, 180 Vàng |

Nhiệm vụ tuần:

| Mã | Yêu cầu | Độ khó | Phần thưởng |
| --- | --- | --- | --- |
| `WEEKLY_PLAY_15` | Hoàn thành 15 trận online | Thường | 75 XP, 400 Vàng |
| `WEEKLY_WIN_5` | Thắng 5 trận online | Khó | 100 XP, 600 Vàng |
| `WEEKLY_RANKED_WIN_3` | Thắng 3 trận đấu hạng | Khó | 120 XP, 700 Vàng, 1 Gem |
| `WEEKLY_CORRECT_500` | Chọn đúng 500 số | Khó | 90 XP, 500 Vàng |
| `WEEKLY_STREAK_3` | Đạt chuỗi thắng 3 | Tinh anh | 140 XP, 800 Vàng, 1 Gem |
| `WEEKLY_PERFECT_3` | Thắng 3 trận không chọn sai | Tinh anh | 180 XP, 1.000 Vàng, 3 Gem |
| `WEEKLY_DONATE_GOLD_2000` | Quyên góp 2.000 Vàng cho bang | Khó | 100 XP, 600 Vàng |
| `WEEKLY_DONATE_GEMS_5` | Quyên góp 5 Gem cho bang | Tinh anh | 120 XP, 700 Vàng, 2 Gem |

Người chơi vẫn phải chủ động nhận thưởng nhiệm vụ. Mỗi phần thưởng chỉ được
nhận một lần. Nhiệm vụ quyên góp không được hoàn lại nhiều tài sản cùng loại
hơn số đã quyên góp.

## 7. Thành tích, Khung và Danh hiệu

Hai mươi thành tích được chia thành bốn nhóm, mỗi nhóm năm mốc. Thưởng tài sản
được cố định theo độ khó:

- Dễ: 100 Vàng và 20 XP.
- Thường: 250 Vàng và 50 XP.
- Khó: 500 Vàng, 100 XP và 1 Gem.
- Tinh anh: 1.000 Vàng, 200 XP và 3 Gem.

| Mã | Điều kiện | Độ khó | Vật phẩm mở khóa |
| --- | --- | --- | --- |
| `FIRST_WIN` | Thắng trận online đầu tiên | Dễ | Danh hiệu `Khai Chiến` |
| `WINS_10` | Thắng 10 trận online | Thường | Danh hiệu `Cao Thủ`, Khung `Chiến Binh` |
| `WINS_50` | Thắng 50 trận online | Khó | Danh hiệu `Bách Chiến`, Khung `Bách Chiến` |
| `RANKED_WINS_100` | Thắng 100 trận đấu hạng | Tinh anh | Danh hiệu `Kẻ Chinh Phục`, Khung `Kim Cương` |
| `WIN_STREAK_10` | Đạt chuỗi thắng 10 | Tinh anh | Danh hiệu `Bất Bại`, Khung `Bất Khuất` |
| `PERFECT_MATCH_1` | Thắng một trận không chọn sai | Dễ | Danh hiệu `Nhất Kích` |
| `PERFECT_MATCHES_10` | Thắng 10 trận không chọn sai | Khó | Khung `Quán Quân` |
| `ACCURACY_90_TEN` | Đạt ít nhất 90% chính xác trong 10 trận | Thường | Danh hiệu `Mắt Thần` |
| `RESPONSE_2500_TEN` | Phản ứng trung bình dưới 2,5 giây trong 10 trận | Khó | Danh hiệu `Phản Xạ Vàng`, Khung `Tốc Ảnh` |
| `RESPONSE_1500_TEN` | Phản ứng trung bình dưới 1,5 giây trong 10 trận | Tinh anh | Danh hiệu `Thần Tốc`, Khung `Tia Chớp` |
| `CHECKIN_STREAK_7` | Điểm danh liên tiếp 7 ngày | Dễ | Khung `Liệt Hỏa` |
| `CHECKIN_STREAK_30` | Điểm danh liên tiếp 30 ngày | Khó | Khung `Bất Diệt` |
| `CHECKINS_50` | Điểm danh tổng cộng 50 lần | Thường | Không có vật phẩm |
| `CHECKINS_100` | Điểm danh tổng cộng 100 lần | Tinh anh | Khung `Chí Tôn` |
| `PLAYER_LEVEL_30` | Đạt cấp người chơi 30 | Khó | Danh hiệu `Chiến Thần`, Khung `Huyền Thoại` |
| `CLAN_JOINED` | Tham gia một bang | Dễ | Khung `Vinh Quang` |
| `CLAN_GOLD_10000` | Quyên góp tổng cộng 10.000 Vàng | Khó | Khung `Long Uy` |
| `CLAN_GEMS_50` | Quyên góp tổng cộng 50 Gem | Tinh anh | Khung `Đế Vương` |
| `CLAN_QUESTS_10` | Nhận thưởng 10 nhiệm vụ bang | Khó | Danh hiệu `Trụ Cột` |
| `CLAN_LEVEL_10` | Thuộc bang khi bang đạt cấp 10 | Tinh anh | Khung `Vô Song` |

Thành tích được tính từ dữ liệu đã lưu và chỉ mở khóa một lần. Tài sản cùng vật
phẩm được phát trong một transaction chống xử lý lặp.

Mười sáu Khung mới:

`Tia Chớp`, `Liệt Hỏa`, `Chiến Binh`, `Bách Chiến`, `Kim Cương`, `Thách Đấu`,
`Vinh Quang`, `Bất Khuất`, `Huyền Thoại`, `Đế Vương`, `Tốc Ảnh`, `Quán Quân`,
`Bất Diệt`, `Long Uy`, `Chí Tôn` và `Vô Song`.

Mười hai Danh hiệu mới:

`Khai Chiến`, `Thần Tốc`, `Bất Bại`, `Bách Chiến`, `Mắt Thần`, `Trụ Cột`,
`Nhất Kích`, `Phản Xạ Vàng`, `Cao Thủ`, `Kẻ Chinh Phục`, `Chiến Thần` và
`Vua Tốc Độ`.

Khung `Thách Đấu` và Danh hiệu `Vua Tốc Độ` là phần thưởng mùa. Các vật phẩm
còn lại được gắn vào bảng thành tích phía trên. Thành tích không có vật phẩm
vẫn nhận phần thưởng tài sản theo độ khó.

Tên hiển thị có bản dịch cho đủ 12 ngôn ngữ. Database và protocol dùng ID ổn
định bằng tiếng Anh để không phụ thuộc ngôn ngữ giao diện. Vật phẩm điểm danh
và mùa giải đã mở khóa trước đây vẫn được giữ.

## 8. Quyên góp, cấp và xếp hạng bang

### 8.1. Quy tắc quyên góp

- 100 Vàng tạo 10 XP bang.
- 1 Gem tạo 10 XP bang.
- Vàng quyên góp phải là số dương và chia hết cho 100.
- Gem quyên góp phải là số nguyên dương.
- Mốc Vàng gợi ý: 100, 500, 1.000 và 5.000.
- Mốc Gem gợi ý: 1, 5, 10 và 50.
- UI hiển thị số dư và XP bang sẽ nhận trước khi xác nhận.
- Dialog xác nhận phải nói rõ quyên góp không thể hoàn tác.

### 8.2. Cấp bang

XP cần để tăng từ cấp hiện tại lên cấp tiếp theo:

`1.000 + (cấp hiện tại - 1) × 500`.

Cấp bang tối đa là 50. Một lần quyên góp vượt nhiều ngưỡng phải áp dụng đủ các
cấp trong cùng transaction. Ở cấp 50, UI hiển thị `MAX`; tổng quyên góp vẫn tiếp
tục tăng để phục vụ bảng xếp hạng.

### 8.3. Bảng xếp hạng bang

Bảng xếp hạng bang có ba tab:

- Cấp bang: cấp giảm dần, XP giảm dần, thời điểm đạt cấp tăng dần, ID bang tăng
  dần.
- Vàng quyên góp: tổng Vàng giảm dần, cấp bang giảm dần, ID bang tăng dần.
- Gem quyên góp: tổng Gem giảm dần, cấp bang giảm dần, ID bang tăng dần.

Thành viên xem được tổng đóng góp của chính mình. Bang chủ xem được tổng Vàng
và Gem của từng thành viên. Hồ sơ bang công khai chỉ hiện tổng của bang, không
được lộ số dư ví của thành viên.

## 9. Xếp hạng tài sản người chơi

Bảng xếp hạng người chơi bổ sung hai tab `Vàng kiếm được` và `Gem kiếm được`
bên cạnh Elo. Giá trị là tổng thu nhập hợp lệ và không giảm khi người chơi tiêu
tài sản. Khoản mua, quy đổi, seed, điều chỉnh quản trị và hoàn tiền bị loại.

Thứ tự gồm tổng tài sản giảm dần, thời điểm đạt tổng hiện tại tăng dần, ID người
dùng tăng dần. Danh sách dùng phân trang bằng cursor, giữ vị trí cuộn khi mở hồ
sơ rồi quay lại và không hiển thị số dư Gem hiện tại của người khác.

## 10. Hạn mức trận online và quảng cáo có thưởng

### 10.1. Quy tắc lượt chơi

- Mỗi tài khoản có 10 lượt cơ bản trong một ngày hạn mức.
- Đấu thường và đấu hạng dùng chung hạn mức.
- Một quảng cáo được xác minh cộng 2 lượt.
- Không giới hạn số quảng cáo mỗi ngày.
- Hạn mức reset lúc 00:00 theo `Asia/Bangkok`, đồng nhất với nhiệm vụ hiện tại.
- Luyện tập offline, đấu giải và hoạt động bang không sử dụng lượt.
- Server kiểm tra lượt trước khi vào hàng chờ hoặc phòng.
- Chỉ trừ lượt khi phòng chuyển sang trạng thái đang chơi.
- Hủy phòng trước khi chơi không mất lượt.
- Chủ động rời sau khi bắt đầu vẫn mất lượt đã giữ.
- Mọi thao tác trừ và cộng lượt phải chống xử lý lặp.

Client hiển thị số lượt còn lại tại bước chọn loại trận online và dialog hết
lượt. Không đưa giá trị này vào header tài sản chung.

### 10.2. Quảng cáo theo môi trường

- Dev dùng biên nhận quảng cáo mô phỏng có nhãn rõ ràng.
- Android và iOS production phải xác minh phía server trước khi cộng lượt.
- Web production hiển thị trạng thái hết lượt và hướng dẫn dùng Android/iOS để
  xem quảng cáo cho đến khi có nhà cung cấp Web phù hợp.
- Quảng cáo lỗi, bị hủy, trùng hoặc không xác minh được sẽ không phát lượt.
- Thông tin bí mật và ID thử nghiệm của nhà cung cấp nằm trong cấu hình môi
  trường, không nằm trong hằng số protocol.

## 11. Sửa cơ chế kết nối lại

Kết nối client trở thành state machine rõ ràng gồm: `Disconnected`,
`Connecting`, `Authenticating`, `Connected`, `Reconnecting` và `Terminal`.
Mỗi lần kết nối lại phải tạo WebSocket transport mới, không tái sử dụng session
đã đóng.

Sau khi mất kết nối ngoài ý muốn, client thử lại sau 1, 2, 4 và 8 giây, sau đó
mỗi 5 giây trong thời hạn server giữ phòng 30 giây. Client xác thực session tài
khoản trước, rồi gửi resume token. Khi phục hồi thành công, trạng thái phòng,
bàn số, target, điểm, số lần sai và phase cục bộ được thay bằng snapshot chính
thức từ server.

Màn hình hiện tại vẫn nằm dưới lớp phủ đang kết nối lại. Nút `Thử lại` tạo một
chu kỳ mới. Session tài khoản hết hạn phải về thẳng Đăng nhập. Nếu tài khoản còn
hợp lệ nhưng thời gian giữ phòng đã hết, client nhận kết quả thua chính thức
trước khi quay về sảnh.

Kiểm thử gồm test state machine, integration test với Ktor WebSocket thật và
kiểm thử thủ công bằng cách bật/tắt mạng trên hai thiết bị.

## 12. UI và giao diện Web

- Cửa hàng có đúng hai tab cùng chiều rộng và chiều cao cố định.
- Dialog giữ chiều rộng tối đa giống mobile trên màn hình Web lớn.
- HTML `body`, Compose root và vùng ngoài nội dung responsive đều dùng nền tối
  2D Arcade; không còn dải trắng trên trình duyệt.
- Dòng `Đấu sĩ • Mùa khởi đầu` được tăng khoảng cách phía trên nhưng không thay
  đổi cấu trúc xung quanh.
- UI hạn mức, quyên góp, cấp bang và bảng xếp hạng mới dùng component 2D Arcade
  hiện tại cùng giới hạn chiều rộng thích ứng.
- Pull-to-refresh tiếp tục dùng cử chỉ cảm ứng trên mobile. Web dùng action làm
  mới phù hợp chuột, không giả lập thao tác kéo.

## 13. Loại bỏ xem lại trận

Xóa UI, route điều hướng, state controller và message protocol chỉ dùng để dựng
lại và phát diễn biến trận. Giữ nguyên:

- Dữ liệu `match_events` phục vụ thống kê và chống gian lận.
- Lịch sử trận và chi tiết trận.
- Chia sẻ kết quả.
- Luồng gửi và phản hồi Mời đấu lại sau trận.

Không migration hoặc xóa các bản ghi sự kiện trận hiện có.

## 14. Hoàn thiện đa ngôn ngữ

Ứng dụng tiếp tục hỗ trợ lựa chọn Hệ thống và 12 ngôn ngữ cụ thể. Mọi chuỗi hiển
thị được thêm hoặc sửa trong đợt này phải dùng `TextKey` cùng tham số. Thông báo
từ backend mang key và tham số; client hiển thị theo ngôn ngữ đang chọn.

Kiểm thử tự động phải xác nhận mọi catalog ngôn ngữ đều có đủ key bắt buộc. Bộ
quét source chặn chuỗi hiển thị hard-code mới trong UI dùng chung, adapter
billing/quảng cáo và thông báo server. Chỉ cho phép ngoại lệ đối với log kỹ
thuật, mã lỗi, ID ổn định, product ID, nhãn dev và fixture kiểm thử.

Test phải bao phủ số lượng và số nhiều động, tên nhiệm vụ/thành tích, quy đổi
tài sản, thông báo hạn mức, cấp bang, nhãn xếp hạng và kết quả quảng cáo. Ngôn
ngữ khác không được âm thầm rơi về tiếng Việt hoặc tiếng Anh.

## 15. Production và mô hình chi phí

### 15.1. Kiến trúc AWS Singapore

```text
Android / iOS / Web
        |
CloudFront + WAF
        |
Application Load Balancer (HTTPS/WSS)
        |
ECS Ktor WebSocket -------- ElastiCache Valkey
        |                         |
        +---- RDS PostgreSQL Multi-AZ
        +---- S3 + CloudFront cho avatar
        +---- SQS cho tác vụ nền
```

Valkey lưu session realtime, presence, matchmaking, trạng thái phòng đang hoạt
động, bộ đếm rate limit dùng chung và Pub/Sub giữa các backend instance.
PostgreSQL tiếp tục là nguồn bền vững cho danh tính, ví, lịch sử trận, thành
tích, bang và sổ giao dịch kinh tế. SQS tách thông báo đẩy cùng các tác vụ nền
có thể thử lại.

Game engine lưu trong bộ nhớ của một instance hiện tại chưa phù hợp production
ở các quy mô trên. Phải đưa trạng thái realtime ra kho dùng chung trước khi mở
rộng ngang.

### 15.2. Giả định dự toán

- AWS Châu Á Thái Bình Dương (Singapore), 730 giờ mỗi tháng.
- Dịch vụ production Multi-AZ và giá on-demand.
- Trung bình một thao tác mỗi 5 giây trên mỗi người đang hoạt động.
- Khoảng 2,5 KB dữ liệu được xử lý qua hệ thống cho mỗi thao tác.
- Dự phòng 30% CPU và bộ nhớ.
- Chưa áp dụng Savings Plans hoặc reserved instance.
- Không gồm VAT, phí Store, phí quảng cáo, email/SMS, nhân sự vận hành và xử lý
  sự cố.

AWS tính Application Load Balancer theo giờ hoạt động và LCU đã dùng. LCU lấy
giá trị cao nhất giữa kết nối mới, kết nối đang mở, dữ liệu xử lý và số rule.
Một ALB LCU gồm 3.000 kết nối đang hoạt động mỗi phút. Tham khảo:
<https://aws.amazon.com/elasticloadbalancing/pricing/>.

ElastiCache Serverless tính theo dữ liệu lưu trữ và ECPU; thao tác đọc/ghi bắt
đầu từ một ECPU cho mỗi KB truyền. Tham khảo:
<https://aws.amazon.com/elasticache/pricing/>.

Fargate tính theo vCPU, bộ nhớ được yêu cầu và thời gian task chạy. Tham khảo:
<https://aws.amazon.com/fargate/pricing/>.

### 15.3. Khoảng ngân sách

| Kết nối ổn định | Thao tác trung bình/giây | Chi phí ước tính/tháng |
| ---: | ---: | ---: |
| 10.000 CCU | 2.000 | 2.000–5.000 USD |
| 50.000 CCU | 10.000 | 8.000–20.000 USD |
| 100.000 CCU | 20.000 | 15.000–40.000 USD |

| Tải độc lập duy trì liên tục | Chi phí ước tính/tháng |
| ---: | ---: |
| 10.000 thao tác/giây | 8.000–20.000 USD |
| 50.000 thao tác/giây | 35.000–90.000 USD |
| 100.000 thao tác/giây | 70.000–180.000 USD |

Đây là khoảng ngân sách phục vụ lập kế hoạch, không phải báo giá. Hai biến số
lớn nhất là số byte thực tế được broadcast cho mỗi lượt và số kết nối WebSocket
mỗi Ktor task chịu được. Cần load test cả hai trước khi chấp nhận danh mục chi
phí chính thức từ AWS Calculator.

## 16. Thứ tự triển khai

Chia thành năm đợt có thể kiểm thử độc lập:

1. Sửa nền tảng: reconnect, nền Web tối, khoảng cách UI, bỏ xem lại trận và quét
   đa ngôn ngữ.
2. Cửa hàng và phần thưởng: chỉ Gem/Vàng, quy đổi, cân bằng thưởng, mở rộng nhiệm
   vụ, thành tích, Khung và Danh hiệu.
3. Bang và xếp hạng: quyên góp, cấp bang, xếp hạng bang và xếp hạng tài sản hợp
   lệ của người chơi.
4. Lượt chơi và quảng cáo: hạn mức ngày, adapter dev, interface xác minh mobile
   và UI hết lượt.
5. Production: tách trạng thái realtime, load test, tính tài nguyên và cập nhật
   báo cáo chi phí AWS.

Mỗi đợt gồm xử lý tương thích protocol, migration cần thiết, test server, test
client dùng chung, Android UI test và kiểm tra responsive Web. Khi triển khai
production phải hỗ trợ rolling upgrade mà không ép client vẫn tương thích phải
mất kết nối.

## 17. Tiêu chí nghiệm thu

- Cửa hàng chỉ có tab Gem/Vàng và vẫn giữ vật phẩm người chơi đã sở hữu.
- Quy đổi tài sản và quyên góp bang phải nguyên tử, chống xử lý lặp.
- Cấp bang và năm bảng xếp hạng mới có thứ tự ổn định, phân trang bằng cursor.
- Tài sản mua hoặc quy đổi không làm tăng hạng tài sản kiếm được.
- Đấu thường và đấu hạng dùng chung lượt ngày, chỉ trừ đúng một lần khi trận bắt
  đầu.
- Một bằng chứng quảng cáo hợp lệ duy nhất cộng đúng 2 lượt; bằng chứng trùng
  không được cộng.
- Phần thưởng và nhiệm vụ khớp toàn bộ giá trị trong đặc tả này.
- Có 20 thành tích, 16 Khung và 12 Danh hiệu, mở khóa từ dữ liệu đã lưu.
- Không còn xem lại trận nhưng dữ liệu sự kiện, chi tiết trận và Mời đấu lại vẫn
  hoạt động.
- Reconnect phục hồi được trận trong thời hạn 30 giây và cho kết quả đúng khi
  hết thời hạn.
- Web không còn nền trắng ở các kích thước responsive hỗ trợ.
- Mọi UI và thông báo server bắt buộc đều hiển thị đủ 12 ngôn ngữ, không fallback
  ngoài ý muốn.
- Báo cáo production có số đo CCU, số thao tác, CPU, bộ nhớ và băng thông để thu
  hẹp khoảng chi phí AWS.
