import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "fast-to-win.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    port = 8082
                    open = false
                }
            }
        }
        binaries.executable()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "fast-to-win.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    port = 8081
                    open = false
                }
            }
        }
        binaries.executable()
    }
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":protocol"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
        }

        jsMain {
            kotlin.srcDir("src/wasmJsMain/kotlin")
            resources.srcDir("src/wasmJsMain/resources")
            dependencies {
                implementation(libs.ktor.client.js)
                implementation(npm("@js-joda/timezone", "2.23.0"))
            }
        }

        wasmJsMain.dependencies {
            implementation(libs.ktor.client.wasm)
            implementation(npm("@js-joda/timezone", "2.23.0"))
        }
    }
}

// Kotlin 2.4.10 copies JS glue in the optimizer, but only declares .wasm inputs.
// Track all linker outputs so a JS-only change cannot reuse stale production glue.
tasks.named("compileProductionExecutableKotlinWasmJsOptimize") {
    inputs.dir(tasks.named<KotlinJsIrLink>("compileProductionExecutableKotlinWasmJs")
        .flatMap { it.destinationDirectory })
        .withPropertyName("linkedWasmArtifacts")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
