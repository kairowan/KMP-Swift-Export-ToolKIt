plugins {
    base
}

group = "io.github.haonan"
version = "0.1.0-SNAPSHOT"

allprojects {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

tasks.named<Delete>("clean") {
    delete(layout.buildDirectory)
}
