import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.multiplatform.library) apply false
    alias(libs.plugins.test.logger) apply false
    alias(libs.plugins.maven.publish) apply false
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
version = calculateVersion("0.4.0")

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
