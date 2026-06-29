import org.gradle.api.tasks.testing.Test

plugins {
    kotlin("multiplatform") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    id("com.adarshr.test-logger") version "4.0.0" apply false
    id("com.vanniktech.maven.publish") version "0.36.0" apply false
    idea
}

fun calculateVersion(baseVersion: String): String {
    return try {
        val branch = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
            .redirectErrorStream(true)
            .start()
            .inputStream
            .bufferedReader()
            .readText()
            .trim()

        if (branch == "master" || branch == "main") {
            baseVersion
        } else {
            "$baseVersion-SNAPSHOT"
        }
    } catch (_: Exception) {
        baseVersion
    }
}

group = "net.igsoft.polyglot"
version = calculateVersion("0.2.0")

println("Version: $version")

allprojects {
    group = rootProject.group
    version = rootProject.version
}

subprojects {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
