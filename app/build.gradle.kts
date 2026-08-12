plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.hienthai.fastowin"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hienthai.fastowin"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
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
                "\"${providers.gradleProperty("FASTTOWIN_PROD_WS_URL").orElse("wss://configure-production-server.invalid/game").get()}\""
            )
            resValue("string", "app_name", "Fast To Win")
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

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
}
