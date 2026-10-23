# Vietnamese Play Store assets

- App icon: existing `webApp/src/wasmJsMain/resources/icons/icon-512.png`, copied without visual changes.
- Feature graphic: authored SVG (`feature-graphic.svg`), rasterized to 1024×500 RGB PNG. No download/install CTA, price or ranking claim.
- Four phone images: actual Android Compose screenshot-preview renders already tracked on source master `bf71524`, small-phone 840×1491, VI, fixture data only. These are not physical-device acceptance evidence. They reflect the current native screen components and contain no production user email or secrets. Export removes only the fully opaque alpha channel; pixel RGB values remain unchanged. No UI reconstruction or emulator run.
- Source fixtures: `app/src/screenshotTest/kotlin/com/hienthai/fastowin/screenshot/ArcadeCoreScreenshotTest.kt` (home, activeGame) and `ArcadeFeatureScreenshotTest.kt` (runningTournament, clanDetail).
- Re-capture if the displayed features change. 840×1491 satisfies basic store dimension/aspect requirements; these images do not satisfy the 1080×1920 recommendation placement threshold. Do not claim recommendation eligibility.
- Alt text: icon “Biểu tượng Fast To Win với ô số 1, 50 và tia chớp”; feature “Fast To Win — tìm số 1–50 trên nền navy với các ô số xanh, tím, vàng”; phone 01 “Trang chủ với lựa chọn trận đấu và phần thưởng điểm danh”; 02 “Trận tìm số 1–50 với bảng số, điểm và thời gian”; 03 “Giải đấu loại trực tiếp và danh sách người tham gia”; 04 “Thông tin bang hội và thành viên”.

Metadata is in `docs/play-store-release-checklist.md`. Uploads are drafts until Console confirms they are saved; this asset pack alone does not complete app review or device testing.
