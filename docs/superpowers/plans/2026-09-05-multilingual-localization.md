# Multilingual Localization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add immediate, persisted language switching for System plus 12 languages across Android, iOS, Web, backend notifications and account email, while renaming the visible product term “Mặt bài” to “Mặt số”.

**Architecture:** Put the pure Kotlin language model, text keys, plural rules and all catalogs in `protocol/commonMain` so both clients and the JVM backend use one source of truth. Add a Compose adapter and platform locale bridge in `shared`; render structured server messages by key with backward-compatible raw text fallbacks. Migrate UI by feature slice, adding all 12 translations and a hard-coded-text scan in every slice.

**Tech Stack:** Kotlin 2.4.10, Compose Multiplatform 1.11.1, kotlinx.serialization, Ktor, PostgreSQL/Flyway, Android Compose UI tests, Playwright Web E2E.

**Spec:** `docs/superpowers/specs/2026-09-05-multilingual-localization-design.md`

## Global Constraints

- Supported catalogs are exactly `vi`, `en`, `zh-Hans`, `ja`, `ko`, `es`, `pt-BR`, `fr`, `de`, `id`, `th`, and `ru`; `system` resolves a catalog but is not a thirteenth catalog.
- The first launch follows the device language; unsupported locales fall back to English.
- Language changes apply immediately without replacing navigation, controller, form, or lazy-list state.
- User-created display names, room names/passwords, clan names/descriptions and other user-entered text are never translated.
- New protocol fields are optional or have defaults; existing IDs, `CARD_BACK`, `card_back_gold`, and `card_back_diamond` never change.
- The visible Vietnamese term is `Mặt số`; `Mặt bài` must not remain in shipped UI or current product documentation.
- English is the runtime fallback and the canonical placeholder contract for every translated template.
- Preserve unrelated working-tree edits in `.idea/deploymentTargetSelector.xml` and `TournamentScreen.kt`; stage only localization hunks when that screen is migrated.
- Never send tokens, passwords, email addresses, secrets, or arbitrary translation keys as localization arguments or log them.

---

## File Structure

New localization domain files:

```text
protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/
  AppLanguage.kt
  TextKey.kt
  QuantityKey.kt
  LocalizationCatalog.kt
  LocalizationService.kt
  catalogs/
    EnglishCatalog.kt
    VietnameseCatalog.kt
    SimplifiedChineseCatalog.kt
    JapaneseCatalog.kt
    KoreanCatalog.kt
    SpanishCatalog.kt
    BrazilianPortugueseCatalog.kt
    FrenchCatalog.kt
    GermanCatalog.kt
    IndonesianCatalog.kt
    ThaiCatalog.kt
    RussianCatalog.kt
shared/src/commonMain/kotlin/com/hienthai/fastowin/localization/
  LocalLocalization.kt
  PlatformLocale.kt
shared/src/androidMain/kotlin/com/hienthai/fastowin/localization/PlatformLocale.android.kt
shared/src/iosMain/kotlin/com/hienthai/fastowin/localization/PlatformLocale.ios.kt
shared/src/wasmJsMain/kotlin/com/hienthai/fastowin/localization/PlatformLocale.wasm.kt
```

Catalog files contain only language data. `LocalizationService` owns lookup,
fallback, placeholder replacement and plural selection. `LocalLocalization` is
the only Compose dependency in the subsystem. Screens consume `localized(...)`
and do not know how catalogs are stored.

---

### Task 1: Pure Kotlin localization domain and 12 catalog skeletons

**Progress (2026-09-05):** Implemented on `codex/multilingual-localization`.
Added `commonTest` dependency in `protocol/build.gradle.kts` because the module
previously had no test library. The first test compilation failed on the missing
localization API as expected. All 9 catalog tests then passed; JVM, JS and Wasm
compilation succeeded. This task supplies foundation strings only; Settings and
the remaining feature strings are still pending in Tasks 2–11.

Local verification uses Corretto 17 and the process-only option
`-Djdk.net.unixdomain.tmpdir=D:/HienTX/Work/Android/FastToWin/.artifacts/localization-tmp`
to avoid this machine's Java loopback/socket temporary-path error. No machine-wide
JDK or startup-script setting was changed.

**Files:**
- Create: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/AppLanguage.kt`
- Create: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/TextKey.kt`
- Create: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/QuantityKey.kt`
- Create: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/LocalizationCatalog.kt`
- Create: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/LocalizationService.kt`
- Create: the 12 files under `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/catalogs/` listed in File Structure
- Create: `protocol/src/commonTest/kotlin/com/hienthai/fastowin/localization/LocalizationCatalogTest.kt`

**Interfaces:**
- Produces: `AppLanguage`, `TextKey`, `QuantityKey`, `LocalizationCatalog`, `LocalizationService`, `allLocalizationCatalogs`.
- Consumes: no UI or platform API.

- [x] **Step 1: Write failing language-resolution and catalog-contract tests**

```kotlin
class LocalizationCatalogTest {
    @Test
    fun supportedCatalogsAreComplete() {
        assertEquals(AppLanguage.entries.toSet(), allLocalizationCatalogs.keys)
        val englishKeys = allLocalizationCatalogs.getValue(AppLanguage.ENGLISH).texts.keys
        allLocalizationCatalogs.forEach { (_, catalog) ->
            assertEquals(englishKeys, catalog.texts.keys)
            assertTrue(catalog.texts.values.none(String::isBlank))
        }
    }

    @Test
    fun placeholdersMatchEnglish() {
        val english = allLocalizationCatalogs.getValue(AppLanguage.ENGLISH)
        allLocalizationCatalogs.values.forEach { catalog ->
            english.texts.keys.forEach { key ->
                assertEquals(
                    placeholders(english.texts.getValue(key)),
                    placeholders(catalog.texts.getValue(key)),
                    "Placeholder mismatch for ${catalog.language}:$key"
                )
            }
        }
    }

    @Test fun unsupportedLanguageFallsBackToEnglish() =
        assertEquals(AppLanguage.ENGLISH, resolveAppLanguage("system", listOf("ar-EG")))

    @Test fun simplifiedChineseAliasesResolve() {
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, resolveAppLanguage("system", listOf("zh-CN")))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, resolveAppLanguage("system", listOf("zh-SG")))
    }

    @Test fun europeanPortugueseDoesNotSelectBrazilianPortuguese() =
        assertEquals(AppLanguage.ENGLISH, resolveAppLanguage("system", listOf("pt-PT")))
}
```

- [x] **Step 2: Run the protocol tests and confirm RED**

Run:

```powershell
./gradlew.bat :protocol:jvmTest --tests "*LocalizationCatalogTest" --no-daemon
```

Expected: compilation fails because the localization types do not exist.

- [x] **Step 3: Implement the stable language and catalog APIs**

```kotlin
enum class AppLanguage(val code: String, val languageTag: String, val nativeName: String) {
    VIETNAMESE("vi", "vi", "Tiếng Việt"),
    ENGLISH("en", "en", "English"),
    SIMPLIFIED_CHINESE("zh-Hans", "zh-Hans", "中文简体"),
    JAPANESE("ja", "ja", "日本語"),
    KOREAN("ko", "ko", "한국어"),
    SPANISH("es", "es", "Español"),
    BRAZILIAN_PORTUGUESE("pt-BR", "pt-BR", "Português (Brasil)"),
    FRENCH("fr", "fr", "Français"),
    GERMAN("de", "de", "Deutsch"),
    INDONESIAN("id", "id", "Bahasa Indonesia"),
    THAI("th", "th", "ไทย"),
    RUSSIAN("ru", "ru", "Русский")
}

data class LocalizationCatalog(
    val language: AppLanguage,
    val texts: Map<TextKey, String>,
    val quantities: Map<QuantityKey, Map<PluralCategory, String>>
)

class LocalizationService(val language: AppLanguage) {
    fun text(key: TextKey, arguments: Map<String, Any?> = emptyMap()): String
    fun quantity(key: QuantityKey, count: Int, arguments: Map<String, Any?> = emptyMap()): String
}
```

Implement exact-tag resolution first, explicit `zh-CN`/`zh-SG` aliases second,
language-only matching third, with `pt-BR` excluded from language-only matching.
Add foundation keys for generic actions (`Back`, `Cancel`, `Confirm`, `Retry`,
`Close`, `Save`, `Delete`, `Loading`, `UnknownError`) to all 12 catalogs.

- [x] **Step 4: Implement named placeholder and plural validation**

Use `{name}` tokens only. Reject missing arguments in tests, leave no raw token in
rendered output, and implement Russian `one/few/many/other` rules plus `one/other`
for English-like languages. Languages without count inflection use `other`.

```kotlin
internal fun pluralCategory(language: AppLanguage, count: Int): PluralCategory = when (language) {
    AppLanguage.RUSSIAN -> when {
        count % 10 == 1 && count % 100 != 11 -> PluralCategory.ONE
        count % 10 in 2..4 && count % 100 !in 12..14 -> PluralCategory.FEW
        count % 10 == 0 || count % 10 in 5..9 || count % 100 in 11..14 -> PluralCategory.MANY
        else -> PluralCategory.OTHER
    }
    AppLanguage.ENGLISH,
    AppLanguage.SPANISH,
    AppLanguage.GERMAN -> if (count == 1) PluralCategory.ONE else PluralCategory.OTHER
    AppLanguage.BRAZILIAN_PORTUGUESE,
    AppLanguage.FRENCH -> if (count == 0 || count == 1) PluralCategory.ONE else PluralCategory.OTHER
    else -> PluralCategory.OTHER
}
```

- [x] **Step 5: Run tests and confirm GREEN**

```powershell
./gradlew.bat :protocol:jvmTest --tests "*LocalizationCatalogTest" --no-daemon
```

Expected: all localization domain tests pass.

- [x] **Step 6: Commit**

```powershell
git add protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization protocol/src/commonTest/kotlin/com/hienthai/fastowin/localization
git commit -m "feat: add shared localization catalog"
```

---

### Task 2: Device locale bridge and backward-compatible preferences

**Progress (2026-09-05):** Implemented. Existing Android/iOS/Web JSON stores
automatically persist `languageCode`; older JSON defaults to `system`, while
unknown codes retain other settings and resolve using device preferences.
`resolveSavedLanguage` delegates to the protocol resolver to avoid duplicating
normalization rules. Added Android LocaleList, iOS NSLocale and shared JS/Wasm
navigator bridges plus document-language support for the upcoming root provider.

Verification: RED compilation confirmed missing preference/resolver APIs before
implementation. GREEN: 66 shared host tests (including 6 new tests) passed with
no failures/skips; Android app, Web JS and Web Wasm compilation passed in 56s.
iOS compilation and device checks remain pending macOS. No Settings UI wiring
is included yet; that is Task 3.

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/data/preferences/AppPreferences.kt`
- Create: `shared/src/commonMain/kotlin/com/hienthai/fastowin/localization/PlatformLocale.kt`
- Create: `shared/src/androidMain/kotlin/com/hienthai/fastowin/localization/PlatformLocale.android.kt`
- Create: `shared/src/iosMain/kotlin/com/hienthai/fastowin/localization/PlatformLocale.ios.kt`
- Create: `shared/src/wasmJsMain/kotlin/com/hienthai/fastowin/localization/PlatformLocale.wasm.kt`
- Modify: `shared/src/commonTest/kotlin/com/hienthai/fastowin/data/preferences/AppPreferencesTest.kt`
- Create: `shared/src/commonTest/kotlin/com/hienthai/fastowin/localization/LanguagePreferenceTest.kt`

**Interfaces:**
- Consumes: `resolveAppLanguage` from Task 1.
- Produces: `platformLanguageTags()`, `applyPlatformLanguageTag(tag)`,
  `resolveSavedLanguage(code, systemTags)`, `AppPreferences.languageCode`.

- [x] **Step 1: Write failing preference compatibility tests**

```kotlin
@Test
fun legacyPreferencesDefaultToSystemWithoutLosingOtherValues() {
    val legacy = """{"soundEnabled":false,"themeMode":"LIGHT"}"""
    val decoded = ProtocolJson.decodeFromString<AppPreferences>(legacy)
    assertEquals("system", decoded.languageCode)
    assertFalse(decoded.soundEnabled)
    assertEquals(AppThemeMode.LIGHT, decoded.themeMode)
}

@Test
fun unknownLanguageCodeResolvesThroughSystemInsteadOfResettingPreferences() {
    assertEquals(AppLanguage.JAPANESE, resolveSavedLanguage("future-code", listOf("ja-JP")))
}
```

- [ ] **Step 2: Confirm RED**

```powershell
./gradlew.bat :shared:testAndroidHostTest --tests "*AppPreferencesTest" --tests "*LanguagePreferenceTest" --no-daemon
```

- [x] **Step 3: Add the serialized preference and platform contract**

```kotlin
@Serializable
data class AppPreferences(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val visualEffectsEnabled: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val boardStyle: BoardStyle = BoardStyle.CLASSIC,
    val fontScale: AppFontScale = AppFontScale.STANDARD,
    val hasCompletedTutorial: Boolean = false,
    val languageCode: String = "system"
)

expect fun platformLanguageTags(): List<String>
expect fun applyPlatformLanguageTag(languageTag: String)

fun resolveSavedLanguage(code: String, systemTags: List<String>): AppLanguage =
    if (code == "system") {
        resolveAppLanguage("system", systemTags)
    } else {
        AppLanguage.entries.firstOrNull { it.code == code }
            ?: resolveAppLanguage("system", systemTags)
    }
```

Android reads `LocaleList.getDefault()`, iOS reads
`NSLocale.preferredLanguages`, and Web reads `navigator.languages` then
`navigator.language`. Android/iOS `applyPlatformLanguageTag` is a no-op because
Compose text comes from the app catalog; Web sets `document.documentElement.lang`.

- [x] **Step 4: Verify Windows-supported platform compilers (iOS pending macOS)**

```powershell
./gradlew.bat :shared:testAndroidHostTest :app:compileDevDebugKotlin :webApp:compileKotlinWasmJs :webApp:compileKotlinJs --no-daemon
```

Expected: Android host tests and both Web compilers pass. iOS compilation remains
covered by the macOS CI job.

- [x] **Step 5: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/hienthai/fastowin/data/preferences/AppPreferences.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/localization shared/src/androidMain/kotlin/com/hienthai/fastowin/localization shared/src/iosMain/kotlin/com/hienthai/fastowin/localization shared/src/wasmJsMain/kotlin/com/hienthai/fastowin/localization shared/src/commonTest/kotlin/com/hienthai/fastowin/data/preferences/AppPreferencesTest.kt shared/src/commonTest/kotlin/com/hienthai/fastowin/localization/LanguagePreferenceTest.kt
git commit -m "feat: resolve and persist app language"
```

---

### Task 3: Compose provider and Settings language selector

**Progress (2026-09-06):** Implemented on `codex/multilingual-localization`.
The language provider now wraps the existing app state without recreating navigation
or controllers. Settings offers System plus all 12 languages in a scrollable,
accessible selector and applies changes immediately. Android/shared/Web compilation
passed, and all 3 focused selector UI tests passed on `emulator-5554`.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/hienthai/fastowin/localization/LocalLocalization.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/FastToWinApp.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/SettingsScreen.kt`
- Create: `app/src/androidTest/java/com/hienthai/fastowin/LocalizationSettingsUiTest.kt`

**Interfaces:**
- Consumes: Tasks 1–2.
- Produces: `LocalLocalization`, `ProvideLocalization`, `localized`, test tags `language_setting`, `language_dialog`, and `language_option_{languageCode}`.

- [x] **Step 1: Write failing Compose UI tests**

```kotlin
@Test
fun choosingEnglishUpdatesPreferencesAndVisibleTitleImmediately() {
    val preferences = mutableStateOf(AppPreferences(languageCode = "vi"))
    composeRule.setContent {
        val language = resolveSavedLanguage(preferences.value.languageCode, listOf("vi-VN"))
        ProvideLocalization(language) {
            FastToWinTheme(preferences.value) {
                SettingsScreen(
                    preferences = preferences.value,
                    onPreferencesChange = { preferences.value = it },
                    onPreviewSound = {},
                    onOpenTutorial = {},
                    onBack = {}
                )
            }
        }
    }
    composeRule.onNodeWithTag("language_setting").performScrollTo().performClick()
    composeRule.onNodeWithTag("language_option_en").performClick()
    composeRule.onNodeWithText("Settings").assertIsDisplayed()
    composeRule.runOnIdle { assertEquals("en", preferences.value.languageCode) }
}

@Test
fun languageDialogContainsSystemAndTwelveLanguages() {
    composeRule.onNodeWithTag("language_setting").performClick()
    composeRule.onNodeWithTag("language_dialog").assertIsDisplayed()
    val expected = listOf("system", "vi", "en", "zh-Hans", "ja", "ko", "es", "pt-BR", "fr", "de", "id", "th", "ru")
    expected.forEach { composeRule.onNodeWithTag("language_option_$it").assertExists() }
}
```

- [x] **Step 2: Confirm RED**

```powershell
./gradlew.bat :app:compileDevDebugAndroidTestKotlin --no-daemon
```

- [x] **Step 3: Implement the Compose adapter and root provider**

```kotlin
val LocalLocalization = staticCompositionLocalOf {
    LocalizationService(AppLanguage.VIETNAMESE)
}

@Composable
fun localized(key: TextKey, vararg arguments: Pair<String, Any?>): String =
    LocalLocalization.current.text(key, arguments.toMap())

@Composable
fun ProvideLocalization(language: AppLanguage, content: @Composable () -> Unit) {
    val service = remember(language) { LocalizationService(language) }
    CompositionLocalProvider(LocalLocalization provides service, content = content)
}
```

In `FastToWinApp`, resolve language from `appPreferences.languageCode` and
`platformLanguageTags()`, call `applyPlatformLanguageTag` in `LaunchedEffect`, and
wrap `FastToWinTheme` with `ProvideLocalization`. Do not key or recreate
`AuthController`, `GameController`, or navigation state by language.

- [x] **Step 4: Implement the responsive selector**

Add a Language section to Settings. Use `ArcadeDialog`, a vertically scrollable
list, native name plus English name, selected semantics and the exact test tags
defined above. Selection calls:

```kotlin
onPreferencesChange(preferences.copy(languageCode = selectedCode))
showLanguageDialog = false
```

Add every Settings selector string to all 12 catalogs in the same commit.

- [x] **Step 5: Run the focused UI test**

```powershell
./gradlew.bat :app:connectedDevDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.hienthai.fastowin.LocalizationSettingsUiTest" --no-daemon
```

- [x] **Step 6: Commit**

```powershell
git add protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/catalogs shared/src/commonMain/kotlin/com/hienthai/fastowin/FastToWinApp.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/localization shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/SettingsScreen.kt app/src/androidTest/java/com/hienthai/fastowin/LocalizationSettingsUiTest.kt
git commit -m "feat: add in-app language selector"
```

---

### Task 4: Localize the app shell, maintenance, offline and authentication

**Progress (2026-09-06):** Implemented on `codex/multilingual-localization`.
Localized the shared header, update dialog, load-more action, maintenance/offline
states, tutorial, authentication flows, top-level titles and challenge shell copy
for all 12 catalogs. Password validation now returns stable `LocalizedText` keys.
Focused tests cover Vietnamese/English/Japanese rendering, localized accessibility
labels, recovery actions and preservation of typed email across a language change.
Verification passed for catalog/shared tests, Android test compilation, Wasm/JS
compilation, all 12 focused shell/auth UI tests and the focused Shop terminology test.

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/ArcadeComponents.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/FastToWinHeader.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/AppUpdateDialog.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/MaintenanceScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/OfflineScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/TutorialScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/AuthScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/FastToWinApp.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/AccountValidation.kt`
- Modify: all 12 catalog files
- Modify: `app/src/androidTest/java/com/hienthai/fastowin/ArcadeShellUiTest.kt`
- Modify: `app/src/androidTest/java/com/hienthai/fastowin/MaintenanceScreenUiTest.kt`
- Modify: `app/src/androidTest/java/com/hienthai/fastowin/OfflineScreenUiTest.kt`
- Create: `app/src/androidTest/java/com/hienthai/fastowin/LocalizedAuthUiTest.kt`
- Modify: `shared/src/commonTest/kotlin/com/hienthai/fastowin/state/AccountValidationTest.kt`

**Interfaces:**
- Consumes: `localized` and `LocalLocalization`.
- Produces: localized shell/auth screens and reusable common-action keys.

- [x] **Step 1: Add failing Vietnamese/English/Japanese shell tests**

Wrap direct screen tests in `ProvideLocalization`. Assert the same screen renders
`Bảo trì`, `Maintenance`, and `メンテナンス` when only the provider changes. Add an
Auth test that types an email, switches provider language and asserts the email
field still contains its value.

- [x] **Step 2: Confirm RED with the four focused classes**

```powershell
./gradlew.bat :app:connectedDevDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.hienthai.fastowin.ArcadeShellUiTest,com.hienthai.fastowin.MaintenanceScreenUiTest,com.hienthai.fastowin.OfflineScreenUiTest,com.hienthai.fastowin.LocalizedAuthUiTest" --no-daemon
```

- [x] **Step 3: Replace every user-facing literal in the listed files**

Use this pattern for static and parameterized content:

```kotlin
Text(localized(TextKey.AuthLoginTitle))
Text(localized(TextKey.AuthWelcomePlayer, "player" to displayName))
```

Change account validators to return stable error keys plus named arguments; render
those keys in Auth/Profile rather than returning Vietnamese strings from state.
Keep test tags, route names, URLs, protocol codes, icon descriptions used only as
stable automation IDs, operator-supplied maintenance messages, and user input
unchanged. Add a `TextKey` and all 12 translations for every replaced literal; do
not concatenate translated fragments.

- [x] **Step 4: Prove the feature slice contains no Vietnamese UI literals**

```powershell
rg -n '"[^"\r\n]*[À-ỹ][^"\r\n]*"' shared/src/commonMain/kotlin/com/hienthai/fastowin/FastToWinApp.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/state/AccountValidation.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/ArcadeComponents.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/FastToWinHeader.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/AppUpdateDialog.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/MaintenanceScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/OfflineScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/TutorialScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/AuthScreen.kt
```

Expected: no user-facing match; document any technical/test-only allowlist entry
beside the scanner introduced in Task 11.

- [x] **Step 5: Run catalog and UI tests, then commit**

```powershell
./gradlew.bat :protocol:jvmTest :app:connectedDevDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.hienthai.fastowin.ArcadeShellUiTest,com.hienthai.fastowin.MaintenanceScreenUiTest,com.hienthai.fastowin.OfflineScreenUiTest,com.hienthai.fastowin.LocalizedAuthUiTest" --no-daemon
git add protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization shared/src/commonMain/kotlin/com/hienthai/fastowin/FastToWinApp.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/state/AccountValidation.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/ArcadeComponents.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/FastToWinHeader.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/AppUpdateDialog.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/MaintenanceScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/OfflineScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/TutorialScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/AuthScreen.kt shared/src/commonTest/kotlin/com/hienthai/fastowin/state/AccountValidationTest.kt app/src/androidTest/java/com/hienthai/fastowin/ArcadeShellUiTest.kt app/src/androidTest/java/com/hienthai/fastowin/MaintenanceScreenUiTest.kt app/src/androidTest/java/com/hienthai/fastowin/OfflineScreenUiTest.kt app/src/androidTest/java/com/hienthai/fastowin/LocalizedAuthUiTest.kt
git commit -m "feat: localize app shell and authentication"
```

---

### Task 5: Localize home, rooms, gameplay, result and practice

**Progress (2026-09-06):** Implemented. Home, room browser/waiting,
match gameplay, result, practice, deep-link sharing and generated result images
now render through the shared localization catalog. Gameplay state survives a
runtime language change. Validation failures use stable technical codes instead
of Vietnamese exception strings. Focused Android UI coverage exercises English,
German, Japanese, Russian and Thai; catalog validation covers all 12 languages.

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/FastToWinApp.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/HomeScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/LobbyScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/GameScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/ResultScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/PracticeScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/GameIcons.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/navigation/GameMode.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/platform/DeepLinks.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/PracticeGame.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameState.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/platform/ResultImageShare.kt`
- Modify: `shared/src/androidMain/kotlin/com/hienthai/fastowin/platform/ResultImageShare.android.kt`
- Modify: `shared/src/iosMain/kotlin/com/hienthai/fastowin/platform/ResultImageShare.ios.kt`
- Modify: `shared/src/wasmJsMain/kotlin/com/hienthai/fastowin/platform/ResultImageShare.wasm.kt`
- Modify: all 12 catalog files
- Modify: `app/src/androidTest/java/com/hienthai/fastowin/ArcadeGameUiTest.kt`
- Modify: `app/src/androidTest/java/com/hienthai/fastowin/CriticalFlowsUiTest.kt`
- Modify: `app/src/androidTest/java/com/hienthai/fastowin/PracticeStateRestorationUiTest.kt`
- Modify: `app/src/androidTest/java/com/hienthai/fastowin/ResultNavigationUiTest.kt`
- Create: `app/src/androidTest/java/com/hienthai/fastowin/LocalizedGameplayUiTest.kt`

**Interfaces:**
- Consumes: Tasks 1–4.
- Produces: localized primary gameplay loop.

- [x] **Step 1: Write failing gameplay localization/state-retention tests**

Test English and German on Home/Lobby, Japanese on Game, Russian on Result, and
Thai on Practice. Start a practice board, select at least one number, switch the
provider language, then assert the score/target state is unchanged while labels
change.

- [x] **Step 2: Confirm RED**

```powershell
./gradlew.bat :app:compileDevDebugAndroidTestKotlin --no-daemon
```

- [x] **Step 3: Migrate all literals in the five screens and GameIcons**

Replace `GameMode.title/description` strings with `titleKey/descriptionKey` and
render them at the UI boundary. Replace the localized default player/opponent
names in `GameState` with keys rendered at the UI boundary. Make deep-link parsers
return stable validation codes and build room/challenge share text in the caller
from localization keys. Map those codes in `FastToWinApp` instead of displaying
exception text.

Build every result-image label in `ResultScreen` with localization keys, including
caption, share-sheet title, time, accuracy and slogan, then pass those already
localized values through `ResultShareContent`; Android, iOS and Web renderers must
not construct Vietnamese text or choose a language. Create
whole-sentence keys for countdown, score, wrong taps, combo, target,
spectator state, room visibility, match type, game mode, result analysis and
rematch state. Use `quantity(...)` for player/round/match counts. Keep room name,
player name and scores as named arguments.

- [x] **Step 4: Scan only this slice for remaining Vietnamese UI literals**

```powershell
rg -n '"[^"\r\n]*[À-ỹ][^"\r\n]*"' shared/src/commonMain/kotlin/com/hienthai/fastowin/FastToWinApp.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/navigation/GameMode.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/platform/DeepLinks.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/state/PracticeGame.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameState.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/platform/ResultImageShare.kt shared/src/androidMain/kotlin/com/hienthai/fastowin/platform/ResultImageShare.android.kt shared/src/iosMain/kotlin/com/hienthai/fastowin/platform/ResultImageShare.ios.kt shared/src/wasmJsMain/kotlin/com/hienthai/fastowin/platform/ResultImageShare.wasm.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/HomeScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/LobbyScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/GameScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/ResultScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/PracticeScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/GameIcons.kt
```

- [x] **Step 5: Run focused tests and commit**

```powershell
./gradlew.bat :protocol:jvmTest :app:connectedDevDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.hienthai.fastowin.LocalizedGameplayUiTest,com.hienthai.fastowin.ArcadeGameUiTest,com.hienthai.fastowin.PracticeStateRestorationUiTest,com.hienthai.fastowin.ResultNavigationUiTest" --no-daemon
git add protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization shared/src/commonMain/kotlin/com/hienthai/fastowin/FastToWinApp.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/navigation/GameMode.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/platform/DeepLinks.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/state/PracticeGame.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameState.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/platform/ResultImageShare.kt shared/src/androidMain/kotlin/com/hienthai/fastowin/platform/ResultImageShare.android.kt shared/src/iosMain/kotlin/com/hienthai/fastowin/platform/ResultImageShare.ios.kt shared/src/wasmJsMain/kotlin/com/hienthai/fastowin/platform/ResultImageShare.wasm.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/HomeScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/LobbyScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/GameScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/ResultScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/PracticeScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/GameIcons.kt app/src/androidTest/java/com/hienthai/fastowin/ArcadeGameUiTest.kt app/src/androidTest/java/com/hienthai/fastowin/CriticalFlowsUiTest.kt app/src/androidTest/java/com/hienthai/fastowin/PracticeStateRestorationUiTest.kt app/src/androidTest/java/com/hienthai/fastowin/ResultNavigationUiTest.kt app/src/androidTest/java/com/hienthai/fastowin/LocalizedGameplayUiTest.kt
git commit -m "feat: localize gameplay flows"
```

---

### Task 6: Localize profile, progression and activity history

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/ProfileScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/SeasonHistoryScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/RewardComponents.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/SeasonProgressCard.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/PlayerAvatar.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/PresenceIndicator.kt`
- Modify: all 12 catalog files
- Modify: `app/src/androidTest/java/com/hienthai/fastowin/ProfileSectionsUiTest.kt`
- Modify: `app/src/androidTest/java/com/hienthai/fastowin/SeasonHistoryUiTest.kt`
- Modify: `app/src/androidTest/java/com/hienthai/fastowin/SeasonProgressUiTest.kt`
- Create: `app/src/androidTest/java/com/hienthai/fastowin/LocalizedProfileUiTest.kt`

**Interfaces:**
- Produces: localized profile, check-in, missions, collection, wallet, match history, account/security and season history.

- [ ] **Step 1: Add failing localized profile tests**

Verify Spanish Profile tabs, Brazilian Portuguese wallet-history filters, French
daily check-in, German account confirmation dialogs and Russian season history.
Scroll Profile before switching language and assert a tagged item remains visible
after recomposition.

- [ ] **Step 2: Migrate the exact files above**

Add keys for ranks, achievements, titles, cosmetic descriptions, wallet sources,
daily rewards, mission progress, session device labels, confirmation dialogs and
match statistics. Render counts through `quantity`; pass player/rank/item values
as named arguments.

- [ ] **Step 3: Scan, run focused tests and commit**

```powershell
rg -n '"[^"\r\n]*[À-ỹ][^"\r\n]*"' shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/ProfileScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/SeasonHistoryScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/RewardComponents.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/SeasonProgressCard.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/PlayerAvatar.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/PresenceIndicator.kt
./gradlew.bat :protocol:jvmTest :app:connectedDevDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.hienthai.fastowin.LocalizedProfileUiTest,com.hienthai.fastowin.ProfileSectionsUiTest,com.hienthai.fastowin.SeasonHistoryUiTest,com.hienthai.fastowin.SeasonProgressUiTest" --no-daemon
git add protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/ProfileScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/SeasonHistoryScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/RewardComponents.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/SeasonProgressCard.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/PlayerAvatar.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/PresenceIndicator.kt app/src/androidTest/java/com/hienthai/fastowin/ProfileSectionsUiTest.kt app/src/androidTest/java/com/hienthai/fastowin/SeasonHistoryUiTest.kt app/src/androidTest/java/com/hienthai/fastowin/SeasonProgressUiTest.kt app/src/androidTest/java/com/hienthai/fastowin/LocalizedProfileUiTest.kt
git commit -m "feat: localize profile and progression"
```

---

### Task 7: Localize social, leaderboard, tournaments, notifications and shop

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/FriendsScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/ClanScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/LeaderboardScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/TournamentScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/NotificationsScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/ShopScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/platform/StoreBilling.kt`
- Modify: `shared/src/androidMain/kotlin/com/hienthai/fastowin/platform/StoreBilling.android.kt`
- Modify: `shared/src/iosMain/kotlin/com/hienthai/fastowin/platform/StoreBilling.ios.kt`
- Modify: `shared/src/wasmJsMain/kotlin/com/hienthai/fastowin/platform/StoreBilling.wasm.kt`
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GameProtocol.kt`
- Modify: `docs/roadmap.md`
- Modify: all 12 catalog files
- Modify: `app/src/androidTest/java/com/hienthai/fastowin/FriendsScreenTest.kt`
- Modify: `app/src/androidTest/java/com/hienthai/fastowin/ClanScreenTest.kt`
- Modify: `app/src/androidTest/java/com/hienthai/fastowin/NavigationHeaderUiTest.kt`
- Modify: `app/src/androidTest/java/com/hienthai/fastowin/TournamentScreenTest.kt`
- Create: `app/src/androidTest/java/com/hienthai/fastowin/LocalizedSocialShopUiTest.kt`

**Interfaces:**
- Produces: localized secondary feature screens and the visible `Mặt số` rename.

- [x] **Step 1: Write failing shop terminology and multilingual screen tests**

```kotlin
@Test
fun vietnameseShopUsesNumberSkinTerminology() {
    renderShop(language = AppLanguage.VIETNAMESE)
    composeRule.onNodeWithText("Mặt số").assertIsDisplayed()
    composeRule.onAllNodesWithText("Mặt bài").assertCountEquals(0)
}

@Test
fun englishShopUsesNumberSkinsWithoutChangingIds() {
    assertEquals("card_back_gold", defaultShopItems.first { it.type == CosmeticType.CARD_BACK }.id)
    renderShop(language = AppLanguage.ENGLISH)
    composeRule.onNodeWithText("Number Skins").assertIsDisplayed()
}
```

Add screen assertions covering Chinese Friends, Japanese Clan, Korean
Leaderboard, Indonesian Tournament, Thai Notifications and French Shop.

- [x] **Step 2: Confirm RED**

```powershell
./gradlew.bat :app:compileDevDebugAndroidTestKotlin --no-daemon
```

- [x] **Step 3: Migrate every listed screen and product display name**

Change `StoreBillingState.notice/error` into a stable `TextKey`, named arguments
and optional platform raw fallback so Android/iOS/Web gateways do not emit
Vietnamese strings. `ShopScreen` renders these values with the active catalog.
Change visible Vietnamese data names in `defaultShopItems` to `Mặt số Hoàng Kim`
and `Mặt số Kim Cương` only as raw fallback. Render current clients by item ID to
localized keys. Do not rename IDs or `CARD_BACK`.

When editing `TournamentScreen.kt`, preserve the pre-existing 4 dp Spacer working
tree change. Stage localization hunks interactively so that unrelated line is not
claimed by this task unless the owner commits it separately.

- [x] **Step 4: Verify terminology and remaining literals**

```powershell
rg -n -i "Mặt bài" shared protocol README.md BACKEND_SETUP.md docs --glob '!docs/superpowers/specs/**' --glob '!docs/superpowers/plans/**'
rg -n '"[^"\r\n]*[À-ỹ][^"\r\n]*"' shared/src/commonMain/kotlin/com/hienthai/fastowin/platform/StoreBilling.kt shared/src/androidMain/kotlin/com/hienthai/fastowin/platform/StoreBilling.android.kt shared/src/iosMain/kotlin/com/hienthai/fastowin/platform/StoreBilling.ios.kt shared/src/wasmJsMain/kotlin/com/hienthai/fastowin/platform/StoreBilling.wasm.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/FriendsScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/ClanScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/LeaderboardScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/TournamentScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/NotificationsScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/ShopScreen.kt
```

Expected: the first command has no matches; the second has no user-facing match.

- [x] **Step 5: Run focused tests and commit only scoped hunks**

```powershell
./gradlew.bat :protocol:jvmTest :app:connectedDevDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.hienthai.fastowin.LocalizedSocialShopUiTest,com.hienthai.fastowin.FriendsScreenTest,com.hienthai.fastowin.ClanScreenTest,com.hienthai.fastowin.NavigationHeaderUiTest,com.hienthai.fastowin.TournamentScreenTest" --no-daemon
git add -p shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/TournamentScreen.kt
git add protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GameProtocol.kt protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization shared/src/commonMain/kotlin/com/hienthai/fastowin/platform/StoreBilling.kt shared/src/androidMain/kotlin/com/hienthai/fastowin/platform/StoreBilling.android.kt shared/src/iosMain/kotlin/com/hienthai/fastowin/platform/StoreBilling.ios.kt shared/src/wasmJsMain/kotlin/com/hienthai/fastowin/platform/StoreBilling.wasm.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/FriendsScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/ClanScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/LeaderboardScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/NotificationsScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/ShopScreen.kt app/src/androidTest/java/com/hienthai/fastowin/FriendsScreenTest.kt app/src/androidTest/java/com/hienthai/fastowin/ClanScreenTest.kt app/src/androidTest/java/com/hienthai/fastowin/NavigationHeaderUiTest.kt app/src/androidTest/java/com/hienthai/fastowin/TournamentScreenTest.kt app/src/androidTest/java/com/hienthai/fastowin/LocalizedSocialShopUiTest.kt docs/roadmap.md
git commit -m "feat: localize social and shop screens"
```

---

### Task 8: Structured client-visible server messages

**Files:**
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GameProtocol.kt`
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/AuthProtocol.kt`
- Create: `shared/src/commonMain/kotlin/com/hienthai/fastowin/localization/LocalizedMessageMapper.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameController.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/AuthController.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/AppNotification.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/data/network/GameSocketClient.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/data/network/AuthApiClient.kt`
- Modify: `shared/src/iosMain/kotlin/com/hienthai/fastowin/data/network/IosAuthSessionStore.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/Application.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/Authentication.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/GameEngine.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/StorePurchases.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/MissionRules.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/SeasonLifecycleRepository.kt`
- Modify: `server/src/test/kotlin/com/hienthai/fastowin/server/AuthenticationTest.kt`
- Modify: `server/src/test/kotlin/com/hienthai/fastowin/server/GameEngineTest.kt`
- Modify: `shared/src/commonTest/kotlin/com/hienthai/fastowin/state/AppNotificationTest.kt`
- Modify: `shared/src/commonTest/kotlin/com/hienthai/fastowin/state/AuthControllerTest.kt`
- Modify: `shared/src/commonTest/kotlin/com/hienthai/fastowin/data/network/GameSocketClientTest.kt`
- Create: `shared/src/commonTest/kotlin/com/hienthai/fastowin/localization/LocalizedMessageMapperTest.kt`

**Interfaces:**
- Produces: optional `messageKey/messageArgs` wire fields and localized code mapping with raw fallback.

- [ ] **Step 1: Write failing compatibility and mapper tests**

```kotlin
@Test
fun knownServerErrorUsesCurrentCatalog() {
    val error = ServerMessage.Error("AUTH_REQUIRED", "Hãy đăng nhập.")
    assertEquals("Sign in to continue.", englishMapper.message(error))
}

@Test
fun unknownServerErrorKeepsRawFallback() {
    val error = ServerMessage.Error("FUTURE_CODE", "Legacy fallback")
    assertEquals("Legacy fallback", englishMapper.message(error))
}

@Test
fun oldJsonWithoutMessageKeyStillDecodes() {
    val decoded = ProtocolJson.decodeFromString<ServerMessage>(legacyErrorJson)
    assertNull((decoded as ServerMessage.Error).messageKey)
}
```

- [ ] **Step 2: Add optional structured fields and bump protocol version once**

```kotlin
data class LocalizedMessage(
    val key: String? = null,
    val arguments: Map<String, String> = emptyMap(),
    val fallback: String = ""
)

data class Error(
    val code: String,
    val message: String,
    val requestId: String? = null,
    val messageKey: String? = null,
    val messageArgs: Map<String, String> = emptyMap()
) : ServerMessage()
```

Add equivalent optional fields to message/reason-only result types. Increase
`PROTOCOL_VERSION` from 38 to 39 in this task only. Map known HTTP/WS codes to
`TextKey`; never use a network key that is absent from the enum.

Update server error/action producers to send stable keys and named arguments in
addition to their current Vietnamese fallback. `AccountActionResponse` receives
optional `messageKey/messageArgs`; operator-authored maintenance messages remain
raw. Convert rule and purchase result objects to carry keys rather than composing
Vietnamese fragments that the client cannot translate. Platform-only storage and
network exceptions, such as iOS Keychain failures, expose stable error codes to
the controller; the active client catalog owns the displayed sentence.

- [ ] **Step 3: Inject the current localization service without recreating controllers**

Add `updateLanguage(language: AppLanguage)` to `AuthController` and
`GameController`, backed by a mutable `LocalizationService`. Call both from a
`LaunchedEffect(resolvedLanguage)` in `FastToWinApp`. Replace raw server message
assignment with `LocalizedMessageMapper` output.

- [ ] **Step 4: Run shared/protocol tests and commit**

```powershell
./gradlew.bat :protocol:jvmTest :shared:testAndroidHostTest :server:test --tests "*AuthenticationTest" --tests "*GameEngineTest" :app:compileDevDebugKotlin :webApp:compileKotlinWasmJs --no-daemon
git add protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GameProtocol.kt protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/AuthProtocol.kt protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization shared/src/commonMain/kotlin/com/hienthai/fastowin/localization/LocalizedMessageMapper.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameController.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/state/AuthController.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/state/AppNotification.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/data/network/GameSocketClient.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/data/network/AuthApiClient.kt shared/src/iosMain/kotlin/com/hienthai/fastowin/data/network/IosAuthSessionStore.kt shared/src/commonTest/kotlin/com/hienthai/fastowin/state/AppNotificationTest.kt shared/src/commonTest/kotlin/com/hienthai/fastowin/state/AuthControllerTest.kt shared/src/commonTest/kotlin/com/hienthai/fastowin/data/network/GameSocketClientTest.kt shared/src/commonTest/kotlin/com/hienthai/fastowin/localization/LocalizedMessageMapperTest.kt server/src/main/kotlin/com/hienthai/fastowin/server/Application.kt server/src/main/kotlin/com/hienthai/fastowin/server/Authentication.kt server/src/main/kotlin/com/hienthai/fastowin/server/GameEngine.kt server/src/main/kotlin/com/hienthai/fastowin/server/StorePurchases.kt server/src/main/kotlin/com/hienthai/fastowin/server/MissionRules.kt server/src/main/kotlin/com/hienthai/fastowin/server/SeasonLifecycleRepository.kt server/src/test/kotlin/com/hienthai/fastowin/server/AuthenticationTest.kt server/src/test/kotlin/com/hienthai/fastowin/server/GameEngineTest.kt
git commit -m "feat: localize protocol and client messages"
```

---

### Task 9: Persist localizable notification templates

**Files:**
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GameProtocol.kt`
- Create: `server/src/main/resources/db/migration/V41__localize_notifications.sql`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/NotificationRepository.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/GameEngine.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/DevFullAccountSeed.kt`
- Modify: `server/src/test/kotlin/com/hienthai/fastowin/server/NotificationRepositoryTest.kt`
- Modify: `server/src/test/kotlin/com/hienthai/fastowin/server/GameEngineTest.kt`
- Modify: `server/src/test/kotlin/com/hienthai/fastowin/server/PostgresAuthenticationTest.kt`

**Interfaces:**
- Produces: `NotificationSnapshot.titleKey/titleArgs/messageKey/messageArgs` and nullable PostgreSQL columns.

- [ ] **Step 1: Write failing legacy/new notification persistence tests**

Test a new keyed notification round-trip with Unicode arguments, and insert a
legacy row containing only title/message to prove it still loads with null keys.
Assert changing `LocalizationService` language rerenders a keyed snapshot without
rewriting the database row.

- [ ] **Step 2: Add the additive migration**

```sql
ALTER TABLE user_notifications
    ADD COLUMN title_key VARCHAR(120),
    ADD COLUMN title_args JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN message_key VARCHAR(120),
    ADD COLUMN message_args JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD CONSTRAINT user_notifications_title_key_nonblank
        CHECK (title_key IS NULL OR BTRIM(title_key) <> ''),
    ADD CONSTRAINT user_notifications_message_key_nonblank
        CHECK (message_key IS NULL OR BTRIM(message_key) <> ''),
    ADD CONSTRAINT user_notifications_title_args_object
        CHECK (jsonb_typeof(title_args) = 'object'),
    ADD CONSTRAINT user_notifications_message_args_object
        CHECK (jsonb_typeof(message_args) = 'object');
```

Do not update existing rows.

- [ ] **Step 3: Persist and create structured notifications**

Update repository SELECT/INSERT mappings and every `NotificationSnapshot(...)`
created by `GameEngine` to include allowlisted keys and named arguments while
retaining Vietnamese raw fallback for old clients.

- [ ] **Step 4: Run in-memory and PostgreSQL tests**

```powershell
./gradlew.bat :server:test --tests "*NotificationRepositoryTest" --tests "*GameEngineTest" --tests "*PostgresAuthenticationTest" --no-daemon
```

- [ ] **Step 5: Commit**

```powershell
git add protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GameProtocol.kt server/src/main/resources/db/migration/V41__localize_notifications.sql server/src/main/kotlin/com/hienthai/fastowin/server/NotificationRepository.kt server/src/main/kotlin/com/hienthai/fastowin/server/GameEngine.kt server/src/main/kotlin/com/hienthai/fastowin/server/DevFullAccountSeed.kt server/src/test/kotlin/com/hienthai/fastowin/server/NotificationRepositoryTest.kt server/src/test/kotlin/com/hienthai/fastowin/server/GameEngineTest.kt server/src/test/kotlin/com/hienthai/fastowin/server/PostgresAuthenticationTest.kt
git commit -m "feat: persist localizable notifications"
```

---

### Task 10: Localize push notifications and account emails

**Files:**
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GameProtocol.kt`
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/AuthProtocol.kt`
- Create: `server/src/main/resources/db/migration/V42__store_notification_locale.sql`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/PlayerProfileRepository.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/PostgresPlayerProfileRepository.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/PushNotificationService.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/AuthEmailSender.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/Application.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameController.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/AuthController.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/data/network/AuthApiClient.kt`
- Modify: `server/src/test/kotlin/com/hienthai/fastowin/server/PushNotificationServiceTest.kt`
- Modify: `server/src/test/kotlin/com/hienthai/fastowin/server/PushReminderServiceTest.kt`
- Modify: `server/src/test/kotlin/com/hienthai/fastowin/server/AuthEmailSenderTest.kt`
- Modify: `server/src/test/kotlin/com/hienthai/fastowin/server/AuthenticationTest.kt`

**Interfaces:**
- Produces: locale-aware `UpdateFcmToken`, push target and auth email request contracts.

- [ ] **Step 1: Write failing locale normalization, push and email tests**

Assert `ja-JP` sends Japanese templates, `zh-CN` sends Simplified Chinese,
`ar-EG` falls back to English, Web Push `language` equals the resolved tag, and
legacy requests without `languageTag` decode and send English.

- [ ] **Step 2: Add locale fields compatibly**

```kotlin
data class UpdateFcmToken(
    val token: String,
    val languageTag: String? = null
) : ClientMessage()

data class PasswordResetRequest(
    val email: String,
    val languageTag: String? = null
)

data class EmailVerificationRequest(
    val accessToken: String,
    val languageTag: String? = null
)
```

Registration and guest upgrade do not send email in the current route flow, so do
not add a locale field to those requests. Server normalization accepts only
supported tags and stores `en` for missing/unsupported values.

- [ ] **Step 3: Add the locale migration and repository contract**

```sql
ALTER TABLE users
    ADD COLUMN notification_language VARCHAR(16) NOT NULL DEFAULT 'en';
```

Change `updateFcmToken(playerId, token, languageTag)` and return language from push
target queries. On language change, resend the current token/tag without changing
push opt-in preferences.

- [ ] **Step 4: Render push and email from the shared pure Kotlin catalog**

Change `PushNotificationService` call sites to pass a `TextKey` plus arguments,
then render title/body using `LocalizationService`. Change `AuthEmailSender` to
accept `AppLanguage`, localize subject/body, and keep tokens as arguments. Replace
hard-coded `.setLanguage("vi")` with the resolved tag.

- [ ] **Step 5: Run backend and compatibility tests, then commit**

```powershell
./gradlew.bat :protocol:jvmTest :server:test --tests "*PushNotificationServiceTest" --tests "*PushReminderServiceTest" --tests "*AuthEmailSenderTest" --tests "*AuthenticationTest" :shared:testAndroidHostTest --no-daemon
git add protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GameProtocol.kt protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/AuthProtocol.kt protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization server/src/main/resources/db/migration/V42__store_notification_locale.sql server/src/main/kotlin/com/hienthai/fastowin/server/PlayerProfileRepository.kt server/src/main/kotlin/com/hienthai/fastowin/server/PostgresPlayerProfileRepository.kt server/src/main/kotlin/com/hienthai/fastowin/server/PushNotificationService.kt server/src/main/kotlin/com/hienthai/fastowin/server/AuthEmailSender.kt server/src/main/kotlin/com/hienthai/fastowin/server/Application.kt server/src/test/kotlin/com/hienthai/fastowin/server/PushNotificationServiceTest.kt server/src/test/kotlin/com/hienthai/fastowin/server/PushReminderServiceTest.kt server/src/test/kotlin/com/hienthai/fastowin/server/AuthEmailSenderTest.kt server/src/test/kotlin/com/hienthai/fastowin/server/AuthenticationTest.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameController.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/state/AuthController.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/data/network/AuthApiClient.kt
git commit -m "feat: localize push and account email"
```

---

### Task 11: Add hard-coded UI scanner, update docs and Web persistence E2E

**Files:**
- Modify: `shared/build.gradle.kts`
- Modify: `e2e/tests/browser-smoke.spec.mjs`
- Modify: `e2e/tests/responsive.spec.mjs`
- Modify: `README.md`
- Modify: `BACKEND_SETUP.md`
- Modify: `docs/account-api.md`
- Modify: `docs/roadmap.md`
- Create: `docs/localization.md`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Produces: `checkLocalizedUiText` Gradle task and developer localization guide.

- [ ] **Step 1: Add a failing source scanner task**

Register `checkLocalizedUiText` in `shared/build.gradle.kts`. Scan Kotlin files
under `ui/screens`, `ui/components`, `state`, `navigation`, `data/network` and
`platform` for Vietnamese letters inside string literals. Exclude the 12 catalog
files. Allow only explicit stable test tags, IDs, routes and debug logs; every
allowlist entry includes a reason and exact file/line pattern. Also scan backend
message producers, allowing Vietnamese only inside the named `legacyFallback(...)`
compatibility helper and operator-authored maintenance data. Wire the task into
`check`; its first run must fail until all migrated slices are clean.

- [ ] **Step 2: Add failing Web E2E cases**

```javascript
test('language persists and updates document language', async ({ page }) => {
  await selectLanguage(page, 'ja')
  await expect(page.locator('html')).toHaveAttribute('lang', 'ja')
  await page.reload()
  await expect(page.getByText('設定')).toBeVisible()
})

test('language change keeps current route', async ({ page }) => {
  await page.goto('/rooms')
  await selectLanguage(page, 'de')
  await expect(page).toHaveURL(/\/rooms$/)
})
```

- [ ] **Step 3: Document contributor and API behavior**

`docs/localization.md` must list all codes, fallback rules, key naming,
placeholder/plural rules, how to add a language, how server fallbacks work and the
exact validation commands. Update account API requests with optional
`languageTag`; update backend setup for notification locale migration; mark the
roadmap item complete and use `Mặt số` consistently.

- [ ] **Step 4: Add CI checks**

Add `:protocol:jvmTest` and `:shared:checkLocalizedUiText` to the build-and-test
Gradle invocation. Add the two Web tests to the existing Playwright suite without
creating another browser job.

- [ ] **Step 5: Run docs, scanner and Web tests**

```powershell
./gradlew.bat :protocol:jvmTest :shared:checkLocalizedUiText :shared:testAndroidHostTest :webApp:compileKotlinWasmJs :webApp:compileKotlinJs --no-daemon
Set-Location e2e
pnpm test --grep "language"
Set-Location ..
git diff --check
```

- [ ] **Step 6: Commit**

```powershell
git add shared/build.gradle.kts e2e/tests/browser-smoke.spec.mjs e2e/tests/responsive.spec.mjs .github/workflows/ci.yml README.md BACKEND_SETUP.md docs/account-api.md docs/roadmap.md docs/localization.md
git commit -m "test: enforce multilingual UI coverage"
```

---

### Task 12: Full cross-platform verification and final audit

**Files:**
- Modify only files required to fix failures found by this verification.

**Interfaces:**
- Consumes every previous task.
- Produces a release-candidate localization change with evidence.

- [ ] **Step 1: Verify catalog, backend, shared and client compilation**

```powershell
./gradlew.bat :protocol:jvmTest :server:test :shared:testAndroidHostTest :shared:checkLocalizedUiText :app:compileDevDebugAndroidTestKotlin :app:assembleDevDebug :webApp:compileKotlinWasmJs :webApp:compileKotlinJs --no-daemon
```

- [ ] **Step 2: Run the full Android Compose UI suite**

```powershell
./gradlew.bat :app:connectedDevDebugAndroidTest --no-daemon
```

- [ ] **Step 3: Run the complete Web E2E suite**

```powershell
Set-Location e2e
pnpm test
pnpm test:js-fallback
Set-Location ..
```

- [ ] **Step 4: Audit translations and compatibility**

```powershell
rg -n -i "Mặt bài" shared protocol README.md BACKEND_SETUP.md docs --glob '!docs/superpowers/specs/**' --glob '!docs/superpowers/plans/**'
rg -n '"[^"\r\n]*[À-ỹ][^"\r\n]*"' shared/src/commonMain shared/src/androidMain shared/src/iosMain shared/src/wasmJsMain server/src/main --glob '!**/localization/catalogs/**'
git diff --check
git status --short
```

Expected: no current product-text match for `Mặt bài`, no whitespace errors, and
every match from the second command is either a documented debug-only string, an
operator-authored maintenance value or an explicit old-client fallback. No match
may flow directly to a current client UI. Only known unrelated user changes remain
unstaged. Verify a dev account retains owned `card_back_gold`/`card_back_diamond`
items after server restart.

- [ ] **Step 5: Verify CI including macOS iOS build**

Push only after local evidence is green, then confirm GitHub Actions jobs `Build
and test`, `Web E2E`, `Android UI test`, and `iOS simulator build` all succeed.
Do not claim real-device iOS validation; record it as pending until hardware is
available.

- [ ] **Step 6: Stop on any verification failure**

If a command fails, return to the task that owns the failing file, add a focused
regression test, fix it, rerun that task's verification and commit those exact
files there. When every command is green and no file changed during this task,
do not create an empty verification commit.
