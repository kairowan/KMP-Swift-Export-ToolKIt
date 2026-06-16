pluginManagement {
    includeBuild("../..")

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "sample-kmp-library"

include(":shared")

