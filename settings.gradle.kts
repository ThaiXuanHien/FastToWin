pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    // Kotlin/Wasm registers the official Node.js distribution as an Ivy
    // repository while configuring its toolchain. PREFER_PROJECT allows that
    // plugin-owned repository instead of failing the whole build.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

// This value also becomes the root npm package name for Kotlin/Wasm, so it
// must follow npm naming rules (lowercase and no spaces).
rootProject.name = "fast-to-win"
include(":app")
include(":shared")
include(":protocol")
include(":server")
include(":webApp")
