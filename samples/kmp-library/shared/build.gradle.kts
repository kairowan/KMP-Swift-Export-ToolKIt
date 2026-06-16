import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    kotlin("multiplatform") version "2.0.21"
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    val frameworkName = "Shared"
    val xcf = XCFramework(frameworkName)

    targets.withType(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget::class.java).configureEach {
        binaries.framework {
            baseName = frameworkName
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain {
            dependencies {
            }
        }
    }
}

