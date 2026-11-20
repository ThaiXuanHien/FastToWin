plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.screenshot)
    id("com.google.gms.google-services")
}

// Public identifiers only; no signing/private configuration is consumed here.
val adMobMode = providers.gradleProperty("FASTTOWIN_ADMOB_MODE").orElse("").get().lowercase()
// Explicit local QA build only. Normal release builds do not record auth diagnostics.
val authDiagnosticsMode = providers.gradleProperty("FASTTOWIN_AUTH_DIAGNOSTICS").orElse("off").get()
require(authDiagnosticsMode in setOf("off", "qa")) { "FASTTOWIN_AUTH_DIAGNOSTICS must be off or qa" }
require(adMobMode in setOf("", "disabled", "demo", "test", "live")) { "FASTTOWIN_ADMOB_MODE must be disabled, demo, test or live" }
val adMobAppId = if (adMobMode == "demo") "ca-app-pub-3940256099942544~3347511713"
    else providers.gradleProperty("FASTTOWIN_ADMOB_APP_ID").orElse("").get()
val adMobUnitId = if (adMobMode == "demo") "ca-app-pub-3940256099942544/5224354917"
    else providers.gradleProperty("FASTTOWIN_ADMOB_AD_UNIT_ID").orElse("").get()
val adMobTestDeviceIds = providers.gradleProperty("FASTTOWIN_ADMOB_TEST_DEVICE_IDS").orElse("").get()
    .split(',').map { it.trim().uppercase() }.filter(String::isNotEmpty)
require(adMobTestDeviceIds.all { Regex("[A-F0-9]{32}").matches(it) }) { "Test-device hashes must contain 32 hex characters" }

val uploadKeystorePath = providers.environmentVariable("FASTTOWIN_UPLOAD_KEYSTORE").orNull
val uploadStorePassword = providers.environmentVariable("FASTTOWIN_UPLOAD_STORE_PASSWORD").orNull
val uploadKeyAlias = providers.environmentVariable("FASTTOWIN_UPLOAD_KEY_ALIAS").orNull
val uploadKeyPassword = providers.environmentVariable("FASTTOWIN_UPLOAD_KEY_PASSWORD").orNull
val hasProductionSigning = listOf(uploadKeystorePath, uploadStorePassword, uploadKeyAlias, uploadKeyPassword).all { !it.isNullOrBlank() }
require(listOf(uploadKeystorePath, uploadStorePassword, uploadKeyAlias, uploadKeyPassword).count { !it.isNullOrBlank() } !in 1..3) {
    "Production signing requires FASTTOWIN_UPLOAD_KEYSTORE, FASTTOWIN_UPLOAD_STORE_PASSWORD, FASTTOWIN_UPLOAD_KEY_ALIAS and FASTTOWIN_UPLOAD_KEY_PASSWORD together"
}
require(adMobAppId.isEmpty() || Regex("ca-app-pub-[0-9]{16}~[0-9]{10}").matches(adMobAppId)) { "Invalid AdMob App ID" }
require(adMobUnitId.isEmpty() || Regex("ca-app-pub-[0-9]{16}/[0-9]{10}").matches(adMobUnitId)) { "Invalid AdMob ad unit ID" }
if (adMobMode in setOf("test", "live")) {
    require(Regex("ca-app-pub-[0-9]{16}~[0-9]{10}").matches(adMobAppId) && !adMobAppId.startsWith("ca-app-pub-3940256099942544")) { "An owned AdMob App ID is required" }
    require(Regex("ca-app-pub-[0-9]{16}/[0-9]{10}").matches(adMobUnitId) && !adMobUnitId.startsWith("ca-app-pub-3940256099942544")) { "An owned rewarded ad unit is required" }
    if (adMobMode == "test") require(adMobTestDeviceIds.isNotEmpty() && adMobTestDeviceIds.all { Regex("[A-Fa-f0-9]{32}").matches(it) }) { "Explicit test-device hash allowlist is required for owned test ads" }
}

android {
    namespace = "com.hienthai.fastowin"
    compileSdk = 37
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    defaultConfig {
        applicationId = "com.hienthai.fastowin"
        minSdk = 29
        targetSdk = 37
        versionCode = 18
        versionName = "1.0.17"
        buildConfigField("boolean", "AUTH_DIAGNOSTICS_ENABLED", (authDiagnosticsMode == "qa").toString())
        buildConfigField("String", "ADMOB_MODE", "\"$adMobMode\"")
        buildConfigField("String", "ADMOB_APP_ID", "\"$adMobAppId\"")
        buildConfigField("String", "ADMOB_AD_UNIT_ID", "\"$adMobUnitId\"")
        buildConfigField("String", "ADMOB_TEST_DEVICE_IDS", "\"${adMobTestDeviceIds.joinToString(",")}\"")
        resValue("string", "admob_app_id", adMobAppId)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "APP_ENV", "\"dev\"")
            buildConfigField(
                "String",
                "GAME_SERVER_URL",
                "\"${providers.gradleProperty("FASTTOWIN_DEV_WS_URL").orElse("ws://127.0.0.1:8080/game").get()}\""
            )
            resValue("string", "app_name", "Fast To Win Dev")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "APP_ENV", "\"prod\"")
            buildConfigField(
                "String",
                "GAME_SERVER_URL",
                // APP-01: derived from FASTTOWIN_PUBLIC_HOST; FASTTOWIN_PROD_WS_URL stays as an explicit override.
                "\"${providers.gradleProperty("FASTTOWIN_PROD_WS_URL").orElse(
                    providers.gradleProperty("FASTTOWIN_PUBLIC_HOST").map { "wss://${com.hienthai.fastowin.build.PublicHost.validated(it)}/game" }
                ).get()}\""
            )
            resValue("string", "app_name", "Fast To Win")
        }
    }

    if (adMobMode.isEmpty() || adMobMode == "disabled") {
        // Build-type priority removes SDK permissions while retaining the
        // dev/prod flavor manifests and their different cleartext policies.
        for (buildType in listOf("debug", "release")) {
            check(!file("src/$buildType/AndroidManifest.xml").exists()) {
                "Merge $buildType manifest entries into the ads-disabled overlay before adding a build-type manifest"
            }
            sourceSets.getByName(buildType).manifest.srcFile("src/adsDisabled/AndroidManifest.xml")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasProductionSigning) {
                signingConfig = signingConfigs.create("productionUpload") {
                    storeFile = file(requireNotNull(uploadKeystorePath))
                    storePassword = requireNotNull(uploadStorePassword)
                    keyAlias = requireNotNull(uploadKeyAlias)
                    keyPassword = requireNotNull(uploadKeyPassword)
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

screenshotTests {
    // Permit 0.00005% differing pixels: one pixel in a 984 x 2132 phone image.
    // Linux renders have alternated by one color level at one antialiased edge;
    // two differing pixels at this size and visible UI changes still fail.
    imageDifferenceThreshold = 0.0000005f
}

dependencies {
    constraints {
        screenshotTestImplementation("androidx.transition:transition:1.6.0") {
            because("Use the same generated Transition R classes as the app's Fragment dependency")
        }
        screenshotTestImplementation("androidx.appcompat:appcompat-resources:1.4.1") {
            because("Render previews with the same AppCompat resource classes as the app")
        }
        implementation(libs.androidx.fragment) {
            because("Replace the obsolete Fragment version requested by transitive Android SDKs (Play Console warning)")
        }
    }
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    androidTestImplementation(project(":protocol"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.jetbrains.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
    screenshotTestImplementation(project(":shared"))
    screenshotTestImplementation(project(":protocol"))
    screenshotTestImplementation(libs.jetbrains.compose.foundation)
    // Compose Multiplatform 1.11.1 currently pins Material 3 to 1.9.0.
    screenshotTestImplementation("org.jetbrains.compose.material3:material3:1.9.0")
    androidTestImplementation("org.jetbrains.compose.material3:material3:1.9.0")
}
