plugins {
    kotlin("jvm") version "2.0.21"
    `java-gradle-plugin`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

gradlePlugin {
    plugins {
        create("kmpApplePackager") {
            id = "io.github.haonan.kmp.apple.packager"
            implementationClass = "io.github.haonan.kmp.apple.packager.KmpApplePackagerPlugin"
            displayName = "KMP Apple Packager"
            description = "Build, archive, checksum, publish, and validate XCFramework releases for SwiftPM."
        }
    }
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

