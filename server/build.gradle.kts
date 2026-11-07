import java.security.MessageDigest

plugins {
    alias(libs.plugins.kover)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.hienthai.fastowin.server.MainKt")
}

dependencies {
    implementation(project(":protocol"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.aws.s3)
    implementation(libs.aws.url.connection)
    implementation(libs.lettuce)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.angus.mail)
    implementation(libs.logback.classic)
    implementation("com.google.firebase:firebase-admin:9.2.0")
    implementation(libs.postgresql)
    implementation(libs.ktor.client.okhttp)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

kover {
    reports {
        filters {
            excludes {
                classes("*\$\$serializer")
            }
        }
        total {
            verify {
                rule {
                    minBound(providers.gradleProperty("serverCoverageMinLine").map(String::toInt).getOrElse(86))
                }
            }
        }
    }
}

tasks.register<JavaExec>("seedDevFullAccount") {
    group = "application"
    description = "Creates or refreshes the full-featured local development account."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.hienthai.fastowin.server.DevFullAccountSeedKt")
}

tasks.register<JavaExec>("bootstrapAdminOwner") {
    group = "application"
    description = "Bootstraps the first dedicated admin Owner using a masked console or password file."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.hienthai.fastowin.server.admin.AdminBootstrapKt")
    standardInput = System.`in`
}

// Export only the test launcher classpath; production distributions contain main sources only.
tasks.register("roomOwnershipAcceptanceClasspath") {
    group = "verification"
    dependsOn(tasks.testClasses)
    val acceptanceClasspath = sourceSets.test.get().runtimeClasspath
    inputs.property("acceptanceClasspath", acceptanceClasspath.asPath)
    val output = layout.buildDirectory.file("room-ownership-acceptance-classpath.txt")
    outputs.file(output)
    doLast { output.get().asFile.writeText(acceptanceClasspath.asPath) }
}

// Export only the test launcher classpath; production distributions contain main sources only.
tasks.register("missionAcceptanceClasspath") {
    group = "verification"
    dependsOn(tasks.testClasses)
    val acceptanceClasspath = sourceSets.test.get().runtimeClasspath
    inputs.property("acceptanceClasspath", acceptanceClasspath.asPath)
    val output = layout.buildDirectory.file("mission-acceptance-classpath.txt")
    outputs.file(output)
    doLast { output.get().asFile.writeText(acceptanceClasspath.asPath) }
}

// Export only the test launcher classpath; production distributions contain main sources only.
tasks.register("clanRaceAcceptanceClasspath") {
    group = "verification"
    dependsOn(tasks.testClasses)
    val acceptanceClasspath = sourceSets.test.get().runtimeClasspath
    inputs.property("acceptanceClasspath", acceptanceClasspath.asPath)
    val output = layout.buildDirectory.file("clan-race-acceptance-classpath.txt")
    outputs.file(output)
    doLast { output.get().asFile.writeText(acceptanceClasspath.asPath) }
}

// Isolated, test-only acceptance distribution. Runtime never invokes Gradle.
val redisAcceptanceTestsJar by tasks.registering(Jar::class) {
    archiveFileName.set("redis-acceptance-tests.jar")
    from(sourceSets.test.get().output)
}

tasks.register("redisAcceptanceBundle") {
    group = "verification"
    dependsOn(redisAcceptanceTestsJar, tasks.named("jar"), tasks.testClasses)
    doLast {
        val target = rootProject.layout.projectDirectory.dir(".artifacts/redis-cloud-acceptance-20261102/runner").asFile
        val libraries = target.resolve("lib")
        target.deleteRecursively()
        libraries.mkdirs()
        val jars = sourceSets.test.get().runtimeClasspath.files.filter { it.isFile && it.extension == "jar" } +
            listOf(tasks.named<Jar>("jar").get().archiveFile.get().asFile, redisAcceptanceTestsJar.get().archiveFile.get().asFile)
        require(jars.map { it.name }.distinct().size == jars.size) { "Duplicate runtime jar names" }
        jars.forEach { it.copyTo(libraries.resolve(it.name)) }
        val process = ProcessBuilder("git", "rev-parse", "HEAD").directory(rootProject.projectDir).start()
        val revision = process.inputStream.bufferedReader().readText().trim()
        check(process.waitFor() == 0 && revision.matches(Regex("[0-9a-f]{40}")))
        target.resolve("REVISION").writeText("$revision\n")
        val files = target.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(target).invariantSeparatorsPath }.toList()
        fun digest(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        target.resolve("SHA256SUMS").writeText(files.joinToString("\n", postfix = "\n") { "${digest(it)}  ${it.relativeTo(target).invariantSeparatorsPath}" })
        println("Redis acceptance bundle: ${target.absolutePath}")
    }
}
