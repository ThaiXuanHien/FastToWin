# Mission icon alignment amendment

Status: DONE, ready for fresh root review. User request: “tiếp tục và sử dụng icon gold và xp như trong màn nhiệm vụ nhé”.

Base: `a75a147c9da26b7b2d96a4ad4eab05514a624662`. Implementation: `1d826ffdfe9e86dd14c7ce7a5864d550d77ccfef` — `style: align reward event icons with missions`. A separate report-only commit records this actual implementation identity; its hash is returned in the handoff.

## Scope and result

`MissionArcadeDetails` in ProfileScreen calls the existing RewardAmounts component. Its GOLD icon is filled MonetizationOn tinted GoldColor (=ArcadeGold/Gold500); XP is filled Star tinted MaterialTheme.colorScheme.primary. RewardEventValues already used these glyphs but both were onSurface. This amendment adopts the same currency/experience tint definitions. The shared row covers the event banner, details factors, result base/bonus/total and persisted history values without changes to those callers.

The gold icon uses a small CircleShape Surface with the established Navy900 currency background so yellow remains legible on LIGHT surfaces. The glyph itself retains exactly GoldColor. XP retains the mission primary tint (Blue300 DARK, Blue600 LIGHT). Existing event icon glyph size remains 20dp; the gold wrapper adds 2dp padding, matching a compact currency presentation without a new artwork asset. Mission reward layout/amounts/behavior were not changed. The numeric values, zero handling, × factors, FlowRow wrapping, localized merged VI/EN descriptions, frozen source links and explanatory prose are unchanged. No new dependency, server/admin code, routing, protocol, controller, award calculation or wallet behavior change.

Owned implementation is exactly one common UI file plus 24 existing owned PNG references, listed with SHA256 below; this report is the only additional owned file. Existing Task3 logs/XML/PNGs and previous reports remain preserved. Prior shared 203/protocol 96 evidence remains valid for unchanged behavior and is not claimed as a new test execution in this amendment.

Read/applied mandatory `.agents/skills/ui-ux-pro-max/SKILL.md`, references/pro-rules.md, quick-reference.md and existing design-system/fast-to-win/MASTER.md. MASTER/tokens were not modified. Targeted search `icon non text contrast --domain ux` returned text-contrast guidance, so it was not accepted as a verified icon-specific rule. Narrow retry `meaningful icon contrast --domain icons` returned contextual icon semantics, fitting the unchanged localized combined description; non-text contrast checks use the skill's explicit pro-rules standard of 3:1, not an invented search match.

## Executed verification

Artifact root: `.artifacts/admin-reward-events-delivery/task3/icon-alignment/`.

| Gate | Evidence | Result |
|---|---|---|
| Localized UI scanner and scanner fixtures | update.log | Pass |
| Dev APK assemble | update.log, apk-hash.json | Pass |
| Kotlin JS/Wasm compilation | update.log | Pass |
| Owned screenshot regeneration | update.log, update-xml/ |1suite,24tests,0failures/errors/skips |
| Owned screenshot validation | validate.log, validation-xml/ |1suite,24tests,0failures/errors/skips |

Update/build completed successfully in 53 seconds; validation in 23 seconds. These are the same 24 previews updated and validated, not 48 unique scenarios. No unchanged backend/controller/shared unit suite rerun and no artificial visual-unit test was added. Existing experimental screenshot, SDK XML version, Wasm interop and SLF4J warnings remain outside scope. The code diff and staged-file allowlist were self-reviewed after verification; `git diff --cached --check` passed. Only the 25 explicit allowlisted files were staged for the implementation commit.

Exact PowerShell commands executed at repository root, also retained in commands.ps1:

```powershell
$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=D:/HienTX/Work/Android/FastToWin/.tmp/jvm-sockets'
.\gradlew.bat :shared:checkLocalizedUiText :shared:checkLocalizedUiTextScannerFixtures :app:assembleDevDebug :webApp:compileKotlinJs :webApp:compileKotlinWasmJs :app:updateDevDebugScreenshotTest --tests '*RewardEventScreenshotTestKt*' --no-daemon --no-configuration-cache *> .artifacts/admin-reward-events-delivery/task3/icon-alignment/update.log
.\gradlew.bat :app:validateDevDebugScreenshotTest --tests '*RewardEventScreenshotTestKt*' --no-daemon --no-configuration-cache *> .artifacts/admin-reward-events-delivery/task3/icon-alignment/validate.log
. .artifacts/admin-reward-events-delivery/task3/icon-alignment/collect.ps1
& 'C:/Users/Hien Hue/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' .artifacts/admin-reward-events-delivery/task3/icon-alignment/visual-evidence.py
```

`executed-counts.json` retains actual suite counts/XML hashes. `collect.ps1` captures source/reference/actual-render hashes and APK hash; `mission-token-source-reference-hashes.json` records the unchanged mission component/caller/token source used for the comparison. APK `app/build/outputs/apk/dev/debug/app-dev-debug.apk` SHA256: `45d68592cdb833ff68317efb966cf88287edb881bf93176b02f38b8c9e552eda`.

## Actual PNG inspection

All 24 reference files use the existing app Compose screenshot pipeline and production screens, with no preview source change for this amendment. Coverage remains compact320×568, phone430×932, enlarged fontScale1.6 and desktop1024×768; explicit VI/EN, DARK/LIGHT Home/details/result plus one landscape Home and supplemental large-text amounts card. Actual rendered PNGs copied to `icon-alignment/rendered/`; `rendered-png-hashes.json` contains every path/hash. Previous renders are preserved under `icon-alignment/before-rendered/` as well as the original Task3 evidence.

Inspected actual PNG originals `rewardEventHome_compact_616c7578_0.png`, `rewardEventLightResult_light_result_1fba07a4_0.png` and `rewardEventEnglishResult_english_large_text_7e3d1aa0_0.png`, and the 24-image `contact-sheet.png` assembled from the actual renders. Yellow coin/primary star are distinct; exact 200/60 and ×2 are intact; compact banner and playCTA remain visible, and no new reward overlap/clipping was observed. Comparison to mission identity is established against the actual RewardAmounts source; no separate composed mission screenshot or side-by-side mission rendering is claimed.

Token contrast calculation in visual-evidence.py/token-contrast.json: Gold500 on Navy900 10.995:1, LIGHT primary Blue600 on white 4.569:1, DARK primary Blue300 on Navy900 8.117:1. These compare actual existing opaque tokens at glyph/surface, not anti-aliased edge pixels. The two different glyphs and localized descriptions avoid color-only meaning.

Limits: Compose rendering is not physical-device/browser/iOS acceptance; no TalkBack runtime execution or largest possible font size test claimed. Landscape coverage is Home only. No whole-branch independent review completion is claimed. Admin Material styling is separate and untouched; if currency icons are introduced there, mission identity/tints are appropriate to consider with contrast checked against that separate surface. No provider/GitHub/push/merge/deploy actions. Preserved unrelated untracked caches/runtime/debug/E2E artifacts remain uncommitted.

## Owned source/reference SHA256

Paths relative to repository root, actual file bytes after final validation (Git may normalize source line endings).

| Owned path | SHA256 |
|---|---|
| shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/RewardEventBanner.kt | `ce0269cdb8afbb9d659b25a95d0c612895e48e0b36b4a97a914b6dce66c9a805` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventAmountsLargeText_result_large_text_8b1d90db_0.png | `d39ca6187e28214b0b56aa46c5cb2fcab32bae1179db3edf8de18f88e84b378c` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventDetails_compact_616c7578_0.png | `2580b45525a6055ae5b7b410e6c8d7092c4e983b4a9d82882150b6c9a55f78b1` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventDetails_desktop_56dec76c_0.png | `79db1f06f4e9d330c5376bd4b4622457949fc1ab38af8cf1ede4f231f40e3c25` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventDetails_large_phone_70b0462b_0.png | `08539c4331dbcfa2e43c69bde2f60ec380739af11e50e4536cba92b589763a7b` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventDetails_large_text_54fa59b4_0.png | `1c7422fae4fd35daa0688fc5aacb74c1accd4b0370ed8077059eadd97afad77b` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventEnglish_english_desktop_1b662b18_0.png | `91845fe623942b670768743e4190b0b0e00b91e0e530d311aee0ddca560df2d6` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventEnglish_english_details_7c5816a0_0.png | `0d268780f9a2b7d8212b7c48f9a68084467de9dbb9a1c60c38e4419e92317337` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventEnglishHome_english_desktop_1b662b18_0.png | `61478f5defdb50f846a7ed9bf49cd923a875c4885dbec46841ceaa15c4257737` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventEnglishHome_english_phone_1431a1ab_0.png | `3f25e4a23520da08fb89bff0e76350437da2143e59055aebd505e34160cc3f99` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventEnglishResult_english_desktop_1b662b18_0.png | `104d5f724d27f66f3dec26e56945eb3566f5dae6dcb5f3fd39f6ceffafa1c4a5` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventEnglishResult_english_large_text_7e3d1aa0_0.png | `77d8f96aaa00dfed8f1c295cb2fcd12e1fc29205c651e4df1892f07198d6e2bc` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventEnglishResult_english_phone_1431a1ab_0.png | `b797c0b4a6aa34ddc54e2c0f357fd0453cb4375bd37e59dd5fc1c9c695a83d21` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventHome_compact_616c7578_0.png | `a1d6b2c1ec5a7d346cd0e4f6a3e2c067ab4ac8be000c685d03b236b6e0dcaf13` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventHome_desktop_56dec76c_0.png | `f37bc95e964acc495a1b4b46e3f72e86fe1bc82abbb3e791549629638bf2f7a4` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventHome_large_phone_70b0462b_0.png | `1ff22a86f932b371810c57ad8cf8ffb38867365da4a65f3e6b47e23034933926` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventHome_large_text_54fa59b4_0.png | `03c5a1974c653631b86f567f82e2d4af22bb12790b998aacdaba0853ec9f3b0c` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventLandscapeHome_landscape_home_9a2d15aa_0.png | `c5e541f7e3ecabcd573ccc4386a900257cf5d5fba576dbfa38d0b7988f44e1fc` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventLightDetails_light_details_d806d0e7_0.png | `ca41e7363c49981c225fe1a2f9fd7324873026f5e4316b5c458642e2462c9ee1` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventLightHome_light_home_ea952fa8_0.png | `4d71b4c4f15f27b964356683cd32b3440529b62048db5ddaa9a448cae180025e` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventLightResult_light_result_1fba07a4_0.png | `6d06e61eb6dca15960389e1be3e76b56b8af7fd460b54a10274693cd9c83d07b` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventResult_compact_616c7578_0.png | `aeb40a2f4ef0c8b05d1b7d33d7ca45bd15f9a4cc60b6668d9ce5a07ece5585ae` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventResult_desktop_56dec76c_0.png | `494745318d3ca69765a8b6c21619abc74c937c48be1fe0cc34de2d51fd13ea94` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventResult_large_phone_70b0462b_0.png | `91026f10a4917013f03c68138829e5490d57e434372a133aaf7b0d0cd75605b8` |
| app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/RewardEventScreenshotTestKt/rewardEventResult_large_text_54fa59b4_0.png | `d1f371fa155d388b42095edd1acdf0b6c465118a2db90a3967252b56a3eb8d0a` |
