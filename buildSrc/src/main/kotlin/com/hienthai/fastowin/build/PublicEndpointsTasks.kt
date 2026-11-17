package com.hienthai.fastowin.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * APP-01: the production host lives only in gradle.properties (FASTTOWIN_PUBLIC_HOST).
 * Moving to a custom domain means changing that one value and shipping an app update.
 */
object PublicHost {
    const val PROPERTY = "FASTTOWIN_PUBLIC_HOST"
    const val IOS_SETTING = "FASTTOWIN_PUBLIC_HOST"
    private val pattern = Regex("^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)(\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$")

    fun validated(host: String): String {
        if (!pattern.matches(host)) throw GradleException("$PROPERTY must be a lowercase DNS host name without scheme or path, got '$host'.")
        return host
    }
}

@CacheableTask
abstract class GeneratePublicEndpointsTask : DefaultTask() {
    @get:Input
    abstract val host: Property<String>

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val host = PublicHost.validated(host.get())
        val pkg = packageName.get()
        val directory = outputDirectory.get().asFile.resolve(pkg.replace('.', '/'))
        directory.mkdirs()
        directory.resolve("GeneratedPublicHost.kt").writeText(
            """
            |package $pkg
            |
            |// Generated from ${PublicHost.PROPERTY} in gradle.properties. Do not edit.
            |internal const val GENERATED_PUBLIC_HOST: String = "$host"
            |""".trimMargin()
        )
    }
}

/** Fails the build when the iOS release setting drifts from gradle.properties. */
@DisableCachingByDefault(because = "Cheap consistency check")
abstract class CheckIosPublicHostTask : DefaultTask() {
    @get:Input
    abstract val host: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectFile: RegularFileProperty

    @TaskAction
    fun check() {
        val expected = PublicHost.validated(host.get())
        val values = Regex("""${PublicHost.IOS_SETTING} = "?([^";]+)"?;""").findAll(projectFile.get().asFile.readText())
            .map { it.groupValues[1] }.toSet()
        if (values != setOf(expected)) {
            throw GradleException(
                "iosApp build setting ${PublicHost.IOS_SETTING} is $values but ${PublicHost.PROPERTY} is '$expected'. " +
                    "Update iosApp/iosApp.xcodeproj/project.pbxproj to match."
            )
        }
    }
}
