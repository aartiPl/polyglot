plugins {
    kotlin("multiplatform")
    id("com.adarshr.test-logger")
    id("com.vanniktech.maven.publish")
}

group = "net.igsoft.polyglot.core"

kotlin {
    jvm()
    jvmToolchain(17)

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("com.willowtreeapps.assertk:assertk:0.28.1")
        }

        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
            runtimeOnly("org.junit.platform:junit-platform-launcher")
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
