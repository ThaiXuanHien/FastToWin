import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
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
        }
    }
}

compose.resources {
    packageOfResClass = "com.hienthai.fastowin.resources"
    publicResClass = true
}

val localizedUiSourceSegments = listOf(
    "/ui/screens/",
    "/ui/components/",
    "/state/",
    "/navigation/",
    "/data/network/",
    "/platform/"
)
val localizationCatalogFiles = setOf(
    "EnglishCatalog.kt",
    "VietnameseCatalog.kt",
    "SimplifiedChineseCatalog.kt",
    "JapaneseCatalog.kt",
    "KoreanCatalog.kt",
    "SpanishCatalog.kt",
    "BrazilianPortugueseCatalog.kt",
    "FrenchCatalog.kt",
    "GermanCatalog.kt",
    "IndonesianCatalog.kt",
    "ThaiCatalog.kt",
    "RussianCatalog.kt"
)
val kotlinStringLiteral = Regex("\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"")
val vietnameseLetter = Regex("[À-ỹ]")
val legacyFallbackCall = Regex(
    "legacyFallback\\(\\s*(?:\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\")\\s*\\)"
)

val checkLocalizedUiText by tasks.registering {
    group = "verification"
    description = "Rejects Vietnamese string literals outside localization catalogs and compatibility boundaries."
    notCompatibleWithConfigurationCache("The scanner intentionally reads source contents during execution.")

    val sharedMainSources = fileTree(layout.projectDirectory.dir("src")) {
        include("*Main/kotlin/**/*.kt")
        exclude("**/localization/catalogs/**")
    }.filter { source ->
        val path = source.invariantSeparatorsPath
        localizedUiSourceSegments.any(path::contains) && source.name !in localizationCatalogFiles
    }
    val backendSources = fileTree(rootProject.layout.projectDirectory.dir("server/src/main/kotlin")) {
        include("**/*.kt")
    }
    inputs.files(sharedMainSources, backendSources)

    doLast {
        val violations = buildList {
            (sharedMainSources + backendSources).sortedBy { it.invariantSeparatorsPath }.forEach { source ->
                val content = source.readText()
                val scannedContent = if (source in backendSources) {
                    legacyFallbackCall.replace(content) { call ->
                        call.value.map { character -> if (character == '\n') '\n' else ' ' }.joinToString("")
                    }
                } else content
                kotlinStringLiteral.findAll(scannedContent).forEach { match ->
                    if (vietnameseLetter.containsMatchIn(match.value)) {
                        val lineNumber = scannedContent.take(match.range.first).count { it == '\n' } + 1
                        val preview = match.value.lineSequence().first().trim()
                        add("${source.relativeTo(rootProject.projectDir).invariantSeparatorsPath}:$lineNumber: $preview")
                    }
                }
            }
        }
        check(violations.isEmpty()) {
            "Vietnamese string literals must be moved to the 12 localization catalogs. " +
                "Backend compatibility text must be wrapped by legacyFallback(...); " +
                "operator-authored maintenance text must remain runtime data.\n" +
                violations.joinToString(separator = "\n")
        }
    }
}

tasks.named("check") {
    dependsOn(checkLocalizedUiText)
}
