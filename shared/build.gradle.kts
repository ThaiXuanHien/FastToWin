import com.hienthai.fastowin.build.CheckLocalizedUiTextFixturesTask
import com.hienthai.fastowin.build.CheckLocalizedUiTextTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kover)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

kotlin {
    android {
        namespace = "com.hienthai.fastowin.shared"
        compileSdk = 37
        minSdk = 29
        withHostTestBuilder {}.configure {}

        androidResources {
            enable = true
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosArm64()
    iosSimulatorArm64()
    js {
        browser()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    applyDefaultHierarchyTemplate()

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":protocol"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.play.billing)
            implementation(libs.gma.nextgen)
            implementation(libs.ump)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        jsMain {
            kotlin.srcDir("src/wasmJsMain/kotlin")
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }

        wasmJsMain.dependencies {
            implementation(libs.ktor.client.wasm)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

        getByName("androidHostTest").dependencies {
            implementation(project(":server"))
            implementation(libs.ktor.server.test.host)
            implementation(libs.hikari)
            implementation(libs.flyway.core)
            implementation(libs.flyway.database.postgresql)
            implementation(libs.postgresql)
        }
    }
}

compose.resources {
    packageOfResClass = "com.hienthai.fastowin.resources"
    publicResClass = true
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "com.hienthai.fastowin.resources.*",
                    "com.hienthai.fastowin.ui.*",
                    "com.hienthai.fastowin.FastToWinAppKt*",
                    "com.hienthai.fastowin.ComposableSingletons*",
                    "*\$\$serializer",
                )
            }
        }
        total {
            verify {
                rule {
                    minBound(providers.gradleProperty("sharedCoverageMinLine").map(String::toInt).getOrElse(52))
                }
            }
        }
    }
}

val localizationScannerFixtures = layout.projectDirectory.dir("src/localizationScannerFixtures")
val checkLocalizedUiTextScannerFixtures by tasks.registering(CheckLocalizedUiTextFixturesTask::class) {
    group = "verification"
    description = "Verifies Kotlin lexical coverage used by the localized UI source scanner."
    fixtureDirectory.set(localizationScannerFixtures)
    reconnectOverlaySource.set(
        layout.projectDirectory.file("src/commonMain/kotlin/com/hienthai/fastowin/FastToWinApp.kt")
    )
}

val checkLocalizedUiText by tasks.registering(CheckLocalizedUiTextTask::class) {
    group = "verification"
    description = "Rejects Vietnamese string literals outside localization catalogs and compatibility boundaries."
    sharedSourceRoot.set(layout.projectDirectory.dir("src"))
    backendSourceRoot.set(rootProject.layout.projectDirectory.dir("server/src/main/kotlin"))
    dependsOn(checkLocalizedUiTextScannerFixtures)
}

tasks.named("check") {
    dependsOn(checkLocalizedUiText)
}
