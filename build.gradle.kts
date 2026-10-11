import org.jetbrains.compose.web.tasks.UnpackSkikoWasmRuntimeTask

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization) apply false
    alias(libs.plugins.compose.screenshot) apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
}

val webComposeVersion = libs.versions.composeWebRuntime.get()
val webSkikoVersion = libs.versions.skikoWebRuntime.get()

subprojects {
    if (name == "shared" || name == "webApp") {
        // Keep Android/iOS on the stable runtime; only Web needs these upstream fixes.
        configurations.configureEach {
            if (name.lowercase().contains("wasmjs") || name.startsWith("js")) {
                resolutionStrategy.eachDependency {
                    when (requested.group) {
                        "org.jetbrains.compose.ui", "org.jetbrains.compose.foundation",
                        "org.jetbrains.compose.runtime", "org.jetbrains.compose.animation" ->
                            useVersion(webComposeVersion)
                        "org.jetbrains.skiko" -> {
                            if (requested.name == "skiko-js-wasm-runtime") {
                                useTarget("org.jetbrains.skiko:skiko-wasm-js:$webSkikoVersion")
                            } else {
                                useVersion(webSkikoVersion)
                            }
                        }
                    }
                }
            }
        }
        plugins.withId("org.jetbrains.compose") {
            // The stable Gradle plugin still unpacks the legacy runtime artifact.
            // Skiko 0.153 moved its matching JS/Wasm runtime to this classifier.
            val webSkikoRuntime = configurations.create("fastToWinWebSkikoRuntime") {
                isCanBeConsumed = false
                isTransitive = false
            }
            dependencies.add(
                webSkikoRuntime.name,
                "org.jetbrains.skiko:skiko-wasm-js:$webSkikoVersion:skiko-runtime@jar",
            )
            // Compose registers/configures this task after evaluating its Web targets.
            afterEvaluate {
                tasks.withType<UnpackSkikoWasmRuntimeTask>().configureEach {
                    skikoRuntimeFiles = webSkikoRuntime
                }
            }
        }
    }
}
