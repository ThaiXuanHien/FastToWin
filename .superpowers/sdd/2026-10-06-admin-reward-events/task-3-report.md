# Task 3 delivery — player reward events

Status: DONE, implementation ready for the root's fresh independent review before Task4. No push, merge, deployment, production/provider changes or server/admin edits.

Implementation commit: `5d3e4e1b5a2e54fbccbc79d40125cf4fd750bbc0` — `feat: show reward events and server reward breakdown`.
Base: accepted Task2 `4ac0e6ddf2b905bf03181c515388de0766043a22`. A separate report-only commit follows this implementation commit; its identity is returned in the handoff. Owned implementation: 19 Kotlin source/test files and 24 generated screenshot references, explicitly enumerated with SHA256 below. This report is the only additional owned file. Unrelated runtime, debug, Python cache and E2E artifacts remain untouched and uncommitted.

## Delivered behavior

Public API client consumes accepted DTOs from `/api/events` and `/api/events/{id}`, converts the configured server URL using the existing helper, uses ProtocolJson and existing 10-second transport timeouts, and sends no bearer/admin cookie. It installs no HttpCache. Controller represents unknown/loading/ready/error/unavailable separately; known empty requires successful data. Pagination, published historical detail, retry and 404/503 handling use actual contracts.

Requests are fenced by generation and account/language/view context, including success, error and finally. Each HTTP operation owns a child Job so cancelling a superseded request does not cancel the caller's expiry poll. View-owned effects cancel on route-away/disposal and clear state on account/language change. Anchored server time plus monotonic elapsed time removes ACTIVE at the known end and handles start boundaries without a network reply; refresh polls every 30 seconds and at known status boundaries. Cancellation is reflected on refresh. Metadata never determines match award eligibility. Events never overlay a live match or matchmaking.

Home adds a compact static arcade banner between the player summary and existing play hero. Details use existing header/back/safe-area/responsive components, scroll on small screens, show eligible modes, independent factors, declared timezone and explicit UTC start-inclusive/end-exclusive bounds. No bottom navigation item, live popup or WS discriminator was introduced. `LobbyScreen.kt` is a necessary six-line forwarding seam: the production app renders internal `HomeDashboard` through LobbyScreen, so the app cannot bind the banner directly to HomeScreen alone. The Lobby change adds only the event state and two callbacks.

GameState copies exact authoritative event context and player reward breakdowns from GameSnapshot, clears absent legacy fields, and clears the added fields on existing room/tournament resets. Online Result renders base, event bonus, total and frozen source names supplied by the server; exact fixture totals are 200 GOLD/60 XP. No client multiplier arithmetic creates rewards. Intentional-leave zero, CASUAL base and tournament-neutral supplied values are preserved. The short neutral wallet note does not claim a computed/pending GameFinished reward has been credited. History rows and match detail use committed persisted breakdowns; missing breakdown remains explicitly unknown. Offline retains its existing reward path. Frozen source names link to the existing details route, including published ENDED/CANCELLED metadata; closing a finished result/history view clears previous selection and request state before navigation.

The resumed user request to replace displayed GOLD/XP with icons is included: banner/details factor summaries and result/history amount rows reuse existing filled MonetizationOn and Star glyphs. Numeric values (including zero) remain exact; factors retain ×. FlowRow wraps complete icon/value pairs, onSurface tint supports both themes, and clearAndSetSemantics exposes a localized complete GOLD/XP description rather than duplicate glyph announcements. Explanatory prose retains currency terms. No new imagery or dependency was added.

VI/EN copy is complete with the established English fallback policy for other languages. Existing MASTER is untouched. Applied `.agents/skills/ui-ux-pro-max/SKILL.md`, existing MASTER, references/pro-rules.md and quick-reference.md. Targeted `state loading accessibility --stack jetpack-compose` and `icon accessible label --stack jetpack-compose` returned fitting content-description/UI-state guidance. `dynamic text wrapping --domain ux`, then narrowed `dynamic type --domain ux`, did not provide a verified native wrapping-specific match; wrapping/fontscale used the skill's built-in defaults and pro-rules, checked in rendered Compose images rather than treating off-topic matches as authority.

## RED and GREEN evidence

Evidence directory: `.artifacts/admin-reward-events-delivery/task3/` (retained locally; intentionally ignored). `executed-counts.json` includes each XML suite's actual counts and SHA256. No skipped tests are hidden by Gradle task-level SKIPPED/NO-SOURCE labels.

| Evidence XML group | Suites | Executed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| red-xml |3|7|7|0|0|
| red-poller-xml |1|1|1|0|0|
| red-cors-xml |1|2|1|0|0|
| red-navigation-xml |1|1|1|0|0|
| final-shared-xml |37|203|0|0|0|
| final-protocol-xml |41|96|0|0|0|
| final-screenshot-update-xml |1|24|0|0|0|
| final-screenshot-validation-xml |1|24|0|0|0|

Initial compiling behavioral RED (`red-assertions.log`, `red-xml/`) preceded API/controller/result-state implementation: API2, controller4 and GameState1 all failed assertions, no missing types/setup compilation failure counted. `red-initial-xml/` preserves the earlier harness version, not the claimed final RED. Child-request ownership RED (`red-poller-final.log`) demonstrated that cancellation killed the caller's next suspension; an earlier test without that suspension passed and is not claimed as RED. Historical navigation RED (`red-navigation.log`) failed with the helper stub before real route mapping; GREEN also covers delayed historical success/error/finally after reset/replacement. Final shared feature suites execute API2 +controller7 +state2=11; protocol adds1localization test to accepted95, total96. Existing protocol compatibility/old-new snapshot suites remain covered without DTO edits.

Cache ruling: the initial literal outgoing Cache-Control assertion was revised after root verified accepted server CORS does not allow that custom header. `red-cors-simple.log`/XML records2APItests with1behavioral failure before removing the header. Final test verifies exact public simple GET URL, absent auth/cookie/custom Cache-Control, repeated transport invocation and fresh server timestamps. Response no-store plus existing API worker network-only policy remains authoritative; no CORS expansion or platform fetch override was made. Initial RED history remains preserved, not rewritten to imply the final assertion was original.

Other retained failures are diagnostic history, not successful gates: initial wrapper cache access failure; typed monotonic lambda compile correction (`green-focused.log`); localized scanner finding for amount literal; test placeholder-argument correction (`green-covering-2.log`); and resumed icon multiplier-literal scanner finding (`green-icons-update.log`) fixed by the catalog key. The first commit attempt was not executed because automatic approval review hit a usage limit, explicitly a review availability failure rather than an unsafe-action ruling; after user resume the normal approved commit succeeded.

Fresh final implementation checks passed: `green-final.log`41s covered final copy/navigation and protocol; `green-validate-final.log`34s covered the READY-only details guard, shared tests/scanners, APK, JS/Wasm and24references. Resumed icon-only changes then passed `green-icons-update-2.log`42s (protocol/scanners/APK/JS/Wasm +24regenerated references) and `green-icons-validate.log`24s (24validated). Shared203evidence is the preceding final run; shared controller/network/state behavior was unchanged by icon polish, so that suite was not rerun gratuitously. Protocol96 was rerun for the new localized multiplier symbol. Screenshot update and validation each execute 24; these are the same 24 previews, not 48 unique cases.

## Exact final commands

PowerShell, repository root; existing verified runtime:

```powershell
$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=D:/HienTX/Work/Android/FastToWin/.tmp/jvm-sockets'
.\gradlew.bat :shared:testAndroidHostTest :protocol:jvmTest :shared:checkLocalizedUiText :shared:checkLocalizedUiTextScannerFixtures :app:assembleDevDebug :webApp:compileKotlinJs :webApp:compileKotlinWasmJs :app:updateDevDebugScreenshotTest --tests '*RewardEventScreenshotTestKt*' --no-daemon --no-configuration-cache
.\gradlew.bat :shared:testAndroidHostTest :shared:checkLocalizedUiText :shared:checkLocalizedUiTextScannerFixtures :app:assembleDevDebug :webApp:compileKotlinJs :webApp:compileKotlinWasmJs :app:validateDevDebugScreenshotTest --tests '*RewardEventScreenshotTestKt*' --no-daemon --no-configuration-cache
.\gradlew.bat :protocol:jvmTest :shared:checkLocalizedUiText :shared:checkLocalizedUiTextScannerFixtures :app:assembleDevDebug :webApp:compileKotlinJs :webApp:compileKotlinWasmJs :app:updateDevDebugScreenshotTest --tests '*RewardEventScreenshotTestKt*' --no-daemon --no-configuration-cache
.\gradlew.bat :app:validateDevDebugScreenshotTest --tests '*RewardEventScreenshotTestKt*' --no-daemon --no-configuration-cache
```

Exact final log redirections and environment are retained in `verification-commands.ps1`; earlier RED output/XML is retained, while historical shell spelling beyond the recorded test selector was not separately logged. `collect-final-evidence.ps1` collects allowlist, hashes and per-suite counts. Git self-review checked the staged diff, new source, actual navigation bindings, localization fallback, child-job cancellation and absence of reward arithmetic; `git diff --cached --check` passed and staged 43-file list exactly matched `owned-allowlist.json` before the implementation commit.

APK: `app/build/outputs/apk/dev/debug/app-dev-debug.apk`, built successfully after icon changes; current SHA256 recorded in `apk-hash.json` and below. No server suite rerun: accepted Task2 server 227 and contract remain outside owned scope.

## Actual visual evidence and limits

24actual Compose previews from `RewardEventScreenshotTest.kt`, using the repository screenshot pipeline rather than mock drawings. Home uses production LobbyScreen/HomeDashboard; details use RewardEventDetailsScreen; results use ResultScreen with authoritative fixture values. VI DARK has320×568compact,430×932phone,430×932fontScale1.6 and1024×768desktop for all three screens. Explicit EN localization covers phone/desktop Home, phone/desktop Details and phone/large-text/desktop Result; Preview locale alone was not relied upon. LIGHT phone captures cover all three screens. One844×390landscape Home and one supplemental320×700large-text amount card complete24. Effects/sound/vibration are disabled in result fixture.

All final images are under `app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/` and copied from actual validation render output to `.artifacts/admin-reward-events-delivery/task3/final-rendered/`. Hashes for references are below; `rendered-png-hashes.json` records every actual rendered copy and relative path. `final-contact-sheet.png` assembles the actual PNGs for a24image overview. Individual inspection included compact/desktop Home, phone/large-text Details, compact/desktop Result, EN phone/details/large-text Result and independent LIGHT Home/Details/Result; the final icon contact sheet and EN large-text Result/LIGHT Details originals were inspected again. Root additionally inspected final icon compactHome, LIGHTResult and large-textResult and accepted this bounded change for independent review. No new clipping/overlap was observed in reward UI; compact Home preserves banner and playCTA, totals200/60 are legible, and LIGHT reload/source buttons use readable theme tokens. Long pages intentionally scroll.

Initial 15 renders remain in `initial-rendered/` (initial banner/result density was corrected). The previously accepted 24 pre-icon renders and their source/image hashes remain in `pre-icons-rendered/`, `pre-icons-source-reference-hashes.json` and `pre-icons-rendered-png-hashes.json`. The attempted evidence collection during screenshot output cleanup failed because the transient rendered directory was absent; collection was rerun successfully after validation and final PNGs/hashes refer to that completed render.

Limits: rendered previews are not physical device, emulator or browser E2E acceptance; no TalkBack runtime announcement or keyboard traversal execution is claimed. FontScale1.6 is enlarged text, not the largest possible Android system size. Landscape coverage is Home only. No iOS compile/device acceptance on Windows. Existing experimental screenshot/SDK XML version, Wasm interop and SLF4J warnings remain; no dependency upgrade or unrelated warning fix was attempted. Independent root review is still required before Task4.

## Owned file SHA256 manifest

Paths relative to repository root. Hashes describe actual source/reference bytes collected after final icon validation; Git may normalize line endings in its blobs.

| Owned path | SHA256 |
|---|---|
| protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/LocalizationCatalog.kt | `9c0bf2947f8cde814ef4a25ec3056d5bec6e3a74c9f067fa7c7fcb00aa49e08a` |
| protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/TextKey.kt | `b8377b769bf82ee9a1b827dac1418fdfcf6f68a858557e69646a95c766323654` |
| protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/catalogs/RewardEventCatalogTexts.kt | `481e87e483dce885d64ef3da51e3f4f7c0538337475d1cd9f7a3179baf9e7610` |
| protocol/src/commonTest/kotlin/com/hienthai/fastowin/localization/RewardEventLocalizationTest.kt | `7187ab818cd979ea62798d5c82b024ab182def015eb247a531756d84454ae1d9` |
| shared/src/commonMain/kotlin/com/hienthai/fastowin/FastToWinApp.kt | `e30cee27796e3a3c1f6d70e7696a182c0d812a8a52b66f01db5dfc67b3239640` |
| shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameController.kt | `5fb94b2e516e397857a6b4b80911fd62954657a735fe0469512e609d7d618a1f` |
| shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameState.kt | `8a246d6ce59e505004696d9b37483e9592ba1a03a9b24e32ec70262334af42cd` |
| shared/src/commonMain/kotlin/com/hienthai/fastowin/state/RewardEventController.kt | `57ea84d4464072c8ec8b636bfcd7745ea0887e03e2280b7667cec12cc7913c89` |
| shared/src/commonMain/kotlin/com/hienthai/fastowin/data/network/RewardEventApiClient.kt | `4a508b1c330fd904616cd6b5b90ff9d5a2e24e60650b40d217d0ddfa7fdf4f1c` |
| shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/RewardEventBanner.kt | `100807d0efbf9ed7ae9237db20ebda89407a2346c039f3f3c066d35f034ffe4c` |
| shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/RewardEventDetailsScreen.kt | `f14203211e869c9fb9cf186a91c0356eaef24897be753b1a506c928d4b9433ff` |
| shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/HomeScreen.kt | `4f314037a27a7077b1d9454639bd27016e9d5ca4392d07ebd79d3669ed4632dd` |
| shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/LobbyScreen.kt | `998e14e28716e9b9c474bd685c396a6b2a5085fe998e7b8179e27842e9c56d9b` |
| shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/ProfileScreen.kt | `0eede77f68cf141e55d51e61edb81185d8797100f2a9862e9688ecf83ce06e41` |
| shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/ResultScreen.kt | `c1e00e7c97ac80f4cf812c5b011fcd6932db69305c78c9ad5f0de81577cfe920` |
| shared/src/commonTest/kotlin/com/hienthai/fastowin/state/RewardEventControllerTest.kt | `457cee483da8bc39ff43bd59c9dc52b9c8d7c718bd54dd27544e7f64c89a2f3b` |
| shared/src/commonTest/kotlin/com/hienthai/fastowin/state/RewardEventGameStateTest.kt | `6f39efe57ec6b72d3d94434d097f9f1a52a469084e2d97c95df6f0e96d19d761` |
| shared/src/commonTest/kotlin/com/hienthai/fastowin/data/network/RewardEventApiClientTest.kt | `725e9b45f2e92b645a8b49080c2cbd0601570f7acc840067cdf82ad294eef8cf` |
| app/src/screenshotTest/kotlin/com/hienthai/fastowin/screenshot/RewardEventScreenshotTest.kt | `54beeb9ba8185a3e058d15fe7994494c95d26606c2127071d79d8d86f0a93d14` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventAmountsLargeText_result_large_text_8b1d90db_0.png | `546b8933c20b63e302b61c7b557df4b77818f1fec6202bc79b5aa0a75d9a3c9d` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventDetails_compact_616c7578_0.png | `9a07872c4ca8ea3b8064d2cdffa6268a4081483c962ee286632d403a1b910689` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventDetails_desktop_56dec76c_0.png | `cd681c903bd562a3a9977ce4a339f3c8bc03f07047c32394b844bcf4afd5eb97` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventDetails_large_phone_70b0462b_0.png | `8311ae4d162401417d6b2cc138638c956fd84017bc052eedd8f2761ca4108742` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventDetails_large_text_54fa59b4_0.png | `b02732aee9009633528352c6bd3a3d823e4781c0154b6f53e617b02948d3a2a9` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventEnglish_english_desktop_1b662b18_0.png | `01a68958e94fda5227eb1c06b2f07813cf2690bc766a253a2670fadf145e305b` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventEnglish_english_details_7c5816a0_0.png | `ae51880427aca275ec9c5216a413aa7d341e1928b0e420c6cbd768496e47ed12` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventEnglishHome_english_desktop_1b662b18_0.png | `ec96c7ff78957f87314325b80d65f23782f02ae1645d2e06b53d78e7d2c27a6a` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventEnglishHome_english_phone_1431a1ab_0.png | `291f988d688f554898c96be497d188c9a3dd8c4a9dc84c2f4b5838e84acf4f64` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventEnglishResult_english_desktop_1b662b18_0.png | `89a9dff8d5dabb6bc463a66b7c38caf1b741201212e1babd65eb3c673329f6f6` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventEnglishResult_english_large_text_7e3d1aa0_0.png | `f1b3e486d1e33ff4a6c012733f8df43966de86c937076d8bd92dfd83d7797fba` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventEnglishResult_english_phone_1431a1ab_0.png | `ac95716f3804ae63be290ffa022bdf88479ae017bee036a4f2e91ad577e6828a` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventHome_compact_616c7578_0.png | `74f822c548cb3bedd67e60690179c8753456c9f8e2e6bdc375734c3853dce21e` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventHome_desktop_56dec76c_0.png | `30c4e14039971bc33726010cab3ed5a8fe222f9f2f3c766e07ded81575bd9884` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventHome_large_phone_70b0462b_0.png | `68591b1d02de87802bc43e49d8355821a1743074fe61b5dd747d2a1aa7c81915` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventHome_large_text_54fa59b4_0.png | `9e04b92a58ec6d1b39f2dd88fce60016cdca3ffb7f65824e3fda854c4bc215a3` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventLandscapeHome_landscape_home_9a2d15aa_0.png | `4f4fe2d28ab26d528ad36d63dd3e2a3a7df962effe391fdc7d16c7b0096f44e6` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventLightDetails_light_details_d806d0e7_0.png | `c24572c749abf04a3d3bbd3b16619fe4f420fdd72332bd2eb59d3d906073c607` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventLightHome_light_home_ea952fa8_0.png | `3e1712a557c97e4f61892a1606fc32b569e6a20fbe7ee57fd32ebf4236d00db1` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventLightResult_light_result_1fba07a4_0.png | `a93752428e0c4673e348cfba3bfc79a5d8c0f23e26d602890ee31c76a3a57e9d` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventResult_compact_616c7578_0.png | `28827aac36bbf2c5ee8d9f6933586a0ffac13eab62189d027d24cee6cc49b3fc` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventResult_desktop_56dec76c_0.png | `f8e9f0bcc3dd9fefdba162b2da6ac5d6e13ced385669875e784c0678218ceeff` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventResult_large_phone_70b0462b_0.png | `d51095b59d4a9503399e35c9c76da113fb94d9ceac0cfa859bbaf8a9bd21e6a2` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventResult_large_text_54fa59b4_0.png | `f8d1252c3bd4cd0946ada634ae197fd9fef308f08e2432ec3555200a4272114d` |

APK SHA256: `f7f72f61469d0e8d3c265ccee58703417d5f7ed3b4d969b8679b3df01e0603a8`.
