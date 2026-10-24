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
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.angus.mail)
    implementation(libs.logback.classic)
    implementation("com.google.firebase:firebase-admin:9.2.0")
    runtimeOnly(libs.postgresql)

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
tasks.register("missionAcceptanceClasspath") {
    group = "verification"
    dependsOn(tasks.testClasses)
    val acceptanceClasspath = sourceSets.test.get().runtimeClasspath
    val output = layout.buildDirectory.file("mission-acceptance-classpath.txt")
    outputs.file(output)
    doLast { output.get().asFile.writeText(acceptanceClasspath.asPath) }
}
