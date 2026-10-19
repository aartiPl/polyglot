plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.test.logger)
    alias(libs.plugins.maven.publish)
}

kotlin {
    jvm()
    android {
        namespace = "net.igsoft.polyglot.core"
        compileSdk = 37
        minSdk = 23
        withHostTest {}
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    iosArm64()
    iosSimulatorArm64()
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.atomicfu)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.test.assertk)
        }

        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
            runtimeOnly(libs.test.junit.platform.launcher)
        }
    }
}

testlogger {
    showStandardStreams = true
    showFullStackTraces = false
}

val licensesSpec = Action<MavenPomLicenseSpec> {
    license {
        name.set("MIT License")
        url.set("https://opensource.org/licenses/MIT")
    }
}

val developersSpec = Action<MavenPomDeveloperSpec> {
    developer {
        id.set("aartiPl")
        name.set("Marcin Kuszczak")
        email.set("aarti@interia.pl")
    }
}

val scmSpec = Action<MavenPomScm> {
    connection.set("scm:git:git://https://github.com/aartiPl/polyglot.git")
    developerConnection.set("scm:git:ssh:https://github.com/aartiPl/polyglot.git")
    url.set("https://github.com/aartiPl/polyglot/tree/main")
}

mavenPublishing {
    coordinates(group.toString(), project.name, version.toString())
    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set(project.name)
        description.set("Polyglot core translation registry and resolver")
        url.set("https://github.com/aartiPl/polyglot")

        licenses(licensesSpec)
        developers(developersSpec)
        scm(scmSpec)
    }
}
