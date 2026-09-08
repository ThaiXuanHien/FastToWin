package com.hienthai.fastowin.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

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
                source[offset] == '"' -> offset = scanStringLiteral(offset)
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
            if (!tripleQuoted && source[offset] == '"') {
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

object LocalizedUiTextScanner {
    private val localizedUiSourceSegments = listOf(
        "/ui/screens/",
        "/ui/components/",
        "/state/",
        "/navigation/",
        "/data/network/",
        "/platform/",
    )
    private val localizationCatalogFiles = setOf(
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
        "RussianCatalog.kt",
    )
    private val vietnameseLetter = Regex("[À-ỹ]")

    fun isLocalizedUiSource(source: File): Boolean {
        val path = source.invariantSeparatorsPath
        return source.isFile && source.name !in localizationCatalogFiles &&
            (path.endsWith("/FastToWinApp.kt") || localizedUiSourceSegments.any(path::contains))
    }

    fun findViolations(source: String, allowLegacyFallback: Boolean): List<KotlinStringLiteral> {
        val lexicalScan = KotlinStringLiteralScanner(source).scan()
        val allowedFallbackRanges = if (allowLegacyFallback) {
            findLegacyFallbackLiteralRanges(source, lexicalScan)
        } else {
            emptyList()
        }
        return lexicalScan.stringLiterals.filter { literal ->
            val isLegacyFallback = allowedFallbackRanges.any { allowed ->
                literal.startOffset >= allowed.startOffset &&
                    literal.endOffsetExclusive <= allowed.endOffsetExclusive
            }
            !isLegacyFallback && vietnameseLetter.containsMatchIn(literal.literalText)
        }
    }

    private fun findLegacyFallbackLiteralRanges(
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
}

@DisableCachingByDefault(because = "The gate validates sources and has no outputs to cache.")
abstract class CheckLocalizedUiTextTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sharedSourceRoot: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val backendSourceRoot: DirectoryProperty

    @TaskAction
    fun checkLocalizedUiText() {
        val sharedSourceRoot = sharedSourceRoot.get().asFile
        val backendSourceRoot = backendSourceRoot.get().asFile
        val sharedFiles = sharedSourceRoot.walkTopDown()
            .filter { source -> isSharedMainKotlinSource(source, sharedSourceRoot) }
            .toSet()
        val backendFiles = backendSourceRoot.walkTopDown()
            .filter { source -> source.isFile && source.extension == "kt" }
            .toSet()
        val violations = (sharedFiles + backendFiles)
            .sortedBy { it.invariantSeparatorsPath }
            .flatMap { source ->
                val content = source.readText()
                LocalizedUiTextScanner.findViolations(source = content, allowLegacyFallback = source in backendFiles)
                    .map { literal ->
                        val lineNumber = content.take(literal.startOffset).count { it == '\n' } + 1
                        val preview = content.substring(literal.startOffset, literal.endOffsetExclusive)
                            .lineSequence().first().trim()
                        "${source.invariantSeparatorsPath}:$lineNumber: $preview"
                    }
            }
        check(violations.isEmpty()) {
            "Vietnamese string literals must be moved to the 12 localization catalogs. " +
                "Backend compatibility text must be wrapped by legacyFallback(...); " +
                "operator-authored maintenance text must remain runtime data.\n" +
                violations.joinToString(separator = "\n")
        }
    }

    private fun isSharedMainKotlinSource(source: File, sourceRoot: File): Boolean {
        if (!source.isFile || source.extension != "kt") return false
        val relativePath = source.relativeTo(sourceRoot).invariantSeparatorsPath
        return Regex("^[^/]+Main/kotlin/.+\\.kt$").matches(relativePath) &&
            !relativePath.contains("/localization/catalogs/") &&
            LocalizedUiTextScanner.isLocalizedUiSource(source)
    }
}

@DisableCachingByDefault(because = "The fixture gate validates scanner behavior and has no outputs to cache.")
abstract class CheckLocalizedUiTextFixturesTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val fixtureDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val reconnectOverlaySource: RegularFileProperty

    @TaskAction
    fun checkFixtures() {
        val fixtureRoot = fixtureDirectory.get().asFile
        val sourceSelectionFailures = listOf(reconnectOverlaySource.get().asFile)
            .filterNot(LocalizedUiTextScanner::isLocalizedUiSource)
            .map { source ->
                "${source.invariantSeparatorsPath}: reconnect overlay source is excluded from the localized UI scan"
            }
        val positiveFailures = fixtureFailures(fixtureRoot, "positive", expectedViolationCount = 1)
        val negativeFailures = fixtureFailures(fixtureRoot, "negative", expectedViolationCount = 0)
        val rejectedUiTextFailures = fixtureFailures(fixtureRoot, "reject", expectedViolationCount = 1)
        val acceptedUiTextFailures = fixtureFailures(fixtureRoot, "accept", expectedViolationCount = 0)
        check(
            positiveFailures.isEmpty() &&
                negativeFailures.isEmpty() &&
                rejectedUiTextFailures.isEmpty() &&
                acceptedUiTextFailures.isEmpty() &&
                sourceSelectionFailures.isEmpty(),
        ) {
            "Localization scanner fixture failures:\n" +
                (positiveFailures + negativeFailures + rejectedUiTextFailures + acceptedUiTextFailures + sourceSelectionFailures)
                    .joinToString("\n")
        }
    }

    private fun fixtureFailures(
        fixtureRoot: File,
        group: String,
        expectedViolationCount: Int,
    ): List<String> = File(fixtureRoot, group)
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .sortedBy(File::getName)
        .mapNotNull { fixture ->
            val allowsLegacyFallback = group == "accept" &&
                fixture.invariantSeparatorsPath.contains("/server/")
            val violationCount = LocalizedUiTextScanner.findViolations(
                source = fixture.readText(),
                allowLegacyFallback = allowsLegacyFallback,
            ).size
            fixture.takeIf { violationCount != expectedViolationCount }?.let {
                "${it.name}: expected $expectedViolationCount violation(s), found $violationCount"
            }
        }
        .toList()
}
