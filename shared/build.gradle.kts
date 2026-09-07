import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

data class KotlinStringLiteral(
    val startOffset: Int,
    val endOffsetExclusive: Int,
    val literalText: String,
)

data class KotlinLexicalRange(
    val startOffset: Int,
    val endOffsetExclusive: Int,
)

data class KotlinLexicalScan(
    val stringLiterals: List<KotlinStringLiteral>,
    val maskedRanges: List<KotlinLexicalRange>,
)

class KotlinStringLiteralScanner(private val source: String) {
    private val stringLiterals = mutableListOf<KotlinStringLiteral>()
    private val maskedRanges = mutableListOf<KotlinLexicalRange>()

    fun scan(): KotlinLexicalScan {
        scanCode(startOffset = 0, interpolationDepth = 0)
        return KotlinLexicalScan(
            stringLiterals = stringLiterals.sortedBy(KotlinStringLiteral::startOffset),
            maskedRanges = maskedRanges.sortedBy(KotlinLexicalRange::startOffset),
        )
    }

    private fun scanCode(startOffset: Int, interpolationDepth: Int): Int {
        var offset = startOffset
        var braceDepth = interpolationDepth
        while (offset < source.length) {
            when {
                source.startsWith("//", offset) -> offset = skipLineComment(offset)
                source.startsWith("/*", offset) -> offset = skipBlockComment(offset)
                source[offset] == '\"' -> offset = scanStringLiteral(offset)
                source[offset] == '\'' -> offset = skipCharacterLiteral(offset)
                braceDepth > 0 && source[offset] == '{' -> {
                    braceDepth += 1
                    offset += 1
                }
                braceDepth > 0 && source[offset] == '}' -> {
                    braceDepth -= 1
                    offset += 1
                    if (braceDepth == 0) return offset
                }
                else -> offset += 1
            }
        }
        return offset
    }

    private fun scanStringLiteral(startOffset: Int): Int {
        val tripleQuoted = source.startsWith("\"\"\"", startOffset)
        val delimiterLength = if (tripleQuoted) 3 else 1
        val literalText = StringBuilder()
        var offset = startOffset + delimiterLength

        while (offset < source.length) {
            if (tripleQuoted && source.startsWith("\"\"\"", offset)) {
                return finishStringLiteral(startOffset, offset + delimiterLength, literalText)
            }
            if (!tripleQuoted && source[offset] == '\"') {
                return finishStringLiteral(startOffset, offset + delimiterLength, literalText)
            }
            if (!tripleQuoted && source[offset] == '\\') {
                literalText.append(source[offset])
                if (offset + 1 < source.length) {
                    literalText.append(source[offset + 1])
                    offset += 2
                } else {
                    offset += 1
                }
                continue
            }
            if (source[offset] == '$' && offset + 1 < source.length) {
                if (source[offset + 1] == '{') {
                    offset = scanCode(startOffset = offset + 2, interpolationDepth = 1)
                    continue
                }
                if (isKotlinIdentifierStart(source[offset + 1])) {
                    offset += 2
                    while (offset < source.length && isKotlinIdentifierPart(source[offset])) {
                        offset += 1
                    }
                    continue
                }
            }
            literalText.append(source[offset])
            offset += 1
        }

        return finishStringLiteral(startOffset, source.length, literalText)
    }

    private fun finishStringLiteral(
        startOffset: Int,
        endOffsetExclusive: Int,
        literalText: StringBuilder,
    ): Int {
        stringLiterals += KotlinStringLiteral(
            startOffset = startOffset,
            endOffsetExclusive = endOffsetExclusive,
            literalText = literalText.toString(),
        )
        maskedRanges += KotlinLexicalRange(startOffset, endOffsetExclusive)
        return endOffsetExclusive
    }

    private fun skipLineComment(startOffset: Int): Int {
        var offset = startOffset + 2
        while (offset < source.length && source[offset] != '\n') offset += 1
        maskedRanges += KotlinLexicalRange(startOffset, offset)
        return offset
    }

    private fun skipBlockComment(startOffset: Int): Int {
        var offset = startOffset + 2
        var depth = 1
        while (offset < source.length && depth > 0) {
            when {
                source.startsWith("/*", offset) -> {
                    depth += 1
                    offset += 2
                }
                source.startsWith("*/", offset) -> {
                    depth -= 1
                    offset += 2
                }
                else -> offset += 1
            }
        }
        maskedRanges += KotlinLexicalRange(startOffset, offset)
        return offset
    }

    private fun skipCharacterLiteral(startOffset: Int): Int {
        var offset = startOffset + 1
        while (offset < source.length) {
            when {
                source[offset] == '\\' && offset + 1 < source.length -> offset += 2
                source[offset] == '\'' -> {
                    offset += 1
                    maskedRanges += KotlinLexicalRange(startOffset, offset)
                    return offset
                }
                else -> offset += 1
            }
        }
        maskedRanges += KotlinLexicalRange(startOffset, offset)
        return offset
    }

    private fun isKotlinIdentifierStart(character: Char): Boolean =
        character == '_' || Character.isJavaIdentifierStart(character)

    private fun isKotlinIdentifierPart(character: Char): Boolean =
        Character.isJavaIdentifierPart(character)
}

fun findLegacyFallbackLiteralRanges(
    source: String,
    lexicalScan: KotlinLexicalScan,
): List<KotlinLexicalRange> {
    val structuralSource = StringBuilder(source)
    lexicalScan.maskedRanges.forEach { range ->
        for (offset in range.startOffset until range.endOffsetExclusive) {
            structuralSource[offset] = ' '
        }
    }
    val structure = structuralSource.toString()
    val outermostStrings = lexicalScan.stringLiterals.filter { candidate ->
        lexicalScan.stringLiterals.none { other ->
            other.startOffset < candidate.startOffset &&
                other.endOffsetExclusive >= candidate.endOffsetExclusive
        }
    }

    return Regex("\\blegacyFallback\\s*\\(").findAll(structure).mapNotNull { call ->
        val openParenthesis = structure.indexOf('(', call.range.first)
        var closeParenthesis = -1
        var depth = 0
        for (offset in openParenthesis until structure.length) {
            when (structure[offset]) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) {
                        closeParenthesis = offset
                        break
                    }
                }
            }
        }
        if (closeParenthesis < 0) return@mapNotNull null

        val candidates = outermostStrings.filter { literal ->
            literal.startOffset > openParenthesis &&
                literal.endOffsetExclusive <= closeParenthesis
        }
        val argument = candidates.singleOrNull() ?: return@mapNotNull null
        val prefixIsOnlyTrivia = structure.substring(openParenthesis + 1, argument.startOffset).isBlank()
        val suffixIsOnlyTrivia = structure.substring(argument.endOffsetExclusive, closeParenthesis).isBlank()
        argument.takeIf { prefixIsOnlyTrivia && suffixIsOnlyTrivia }?.let {
            KotlinLexicalRange(it.startOffset, it.endOffsetExclusive)
        }
    }.toList()
}

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
val vietnameseLetter = Regex("[À-ỹ]")
val localizationScannerFixtures = layout.projectDirectory.dir("src/localizationScannerFixtures")

val checkLocalizedUiTextScannerFixtures by tasks.registering {
    group = "verification"
    description = "Verifies Kotlin lexical coverage used by the localized UI source scanner."
    notCompatibleWithConfigurationCache("The fixture check exercises build-script lexical scanner code.")
    inputs.dir(localizationScannerFixtures)

    doLast {
        val positiveFailures = fileTree(localizationScannerFixtures.dir("positive")) {
            include("**/*.kt")
        }.sortedBy { it.name }.mapNotNull { fixture ->
            val violationCount = KotlinStringLiteralScanner(fixture.readText()).scan().stringLiterals
                .count { vietnameseLetter.containsMatchIn(it.literalText) }
            fixture.takeIf { violationCount != 1 }?.let {
                "${it.name}: expected 1 violation, found $violationCount"
            }
        }
        val negativeFailures = fileTree(localizationScannerFixtures.dir("negative")) {
            include("**/*.kt")
        }.sortedBy { it.name }.mapNotNull { fixture ->
            val violationCount = KotlinStringLiteralScanner(fixture.readText()).scan().stringLiterals
                .count { vietnameseLetter.containsMatchIn(it.literalText) }
            fixture.takeIf { violationCount != 0 }?.let {
                "${it.name}: expected 0 violations, found $violationCount"
            }
        }
        check(positiveFailures.isEmpty() && negativeFailures.isEmpty()) {
            "Localization scanner fixture failures:\n" +
                (positiveFailures + negativeFailures).joinToString("\n")
        }
    }
}

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
                val lexicalScan = KotlinStringLiteralScanner(content).scan()
                val allowedFallbackRanges = if (source in backendSources) {
                    findLegacyFallbackLiteralRanges(content, lexicalScan)
                } else {
                    emptyList()
                }
                lexicalScan.stringLiterals.forEach { literal ->
                    val isLegacyFallback = allowedFallbackRanges.any { allowed ->
                        literal.startOffset >= allowed.startOffset &&
                            literal.endOffsetExclusive <= allowed.endOffsetExclusive
                    }
                    if (!isLegacyFallback && vietnameseLetter.containsMatchIn(literal.literalText)) {
                        val lineNumber = content.take(literal.startOffset).count { it == '\n' } + 1
                        val preview = content.substring(
                            literal.startOffset,
                            literal.endOffsetExclusive,
                        ).lineSequence().first().trim()
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

checkLocalizedUiText.configure {
    dependsOn(checkLocalizedUiTextScannerFixtures)
}

tasks.named("check") {
    dependsOn(checkLocalizedUiText)
}
