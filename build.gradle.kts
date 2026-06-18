val releaseVersion = providers.gradleProperty("releaseVersion")
    .orElse("1.0.0-SNAPSHOT")

plugins {
    base
}

group = "io.github.haonan"
version = releaseVersion.get()

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

tasks.named<Delete>("clean") {
    delete(layout.buildDirectory)
}
