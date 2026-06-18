import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugin.compatibility.compatibility

plugins {
    kotlin("jvm") version "2.0.21"
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "2.1.1"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
}

val repositoryUrl = "https://github.com/kairowan/KMP-Swift-Export-ToolKIt"
val githubPackagesOwner = providers.gradleProperty("githubPackagesOwner")
    .orElse(providers.environmentVariable("GITHUB_REPOSITORY_OWNER"))
    .orElse("kairowan")
val githubPackagesRepository = providers.gradleProperty("githubPackagesRepository")
    .orElse(
        providers.environmentVariable("GITHUB_REPOSITORY").map { repositoryReference ->
            repositoryReference.substringAfter('/')
        }
    )
    .orElse("KMP-Swift-Export-ToolKIt")

gradlePlugin {
    website.set(repositoryUrl)
    vcsUrl.set("$repositoryUrl.git")
    plugins {
        create("kmpApplePackager") {
            id = "io.github.haonan.kmp.apple.packager"
            implementationClass = "io.github.haonan.kmp.apple.packager.KmpApplePackagerPlugin"
            displayName = "KMP Apple Packager"
            description = "Build, archive, checksum, publish, and validate XCFramework releases for SwiftPM."
            tags.set(
                listOf(
                    "kotlin-multiplatform",
                    "swiftpm",
                    "xcframework",
                    "apple",
                    "release-automation",
                )
            )
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

publishing {
    publications {
        withType<MavenPublication>().configureEach {
            if (name == "pluginMaven") {
                artifactId = "kmp-apple-packager-gradle-plugin"
            }
            pom {
                name.set("KMP Apple Packager")
                description.set("Build, archive, checksum, publish, and validate XCFramework releases for SwiftPM.")
                url.set(repositoryUrl)
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("kairowan")
                        name.set("kairowan")
                        url.set("https://github.com/kairowan")
                    }
                }
                scm {
                    url.set(repositoryUrl)
                    connection.set("scm:git:$repositoryUrl.git")
                    developerConnection.set("scm:git:ssh://git@github.com/kairowan/KMP-Swift-Export-ToolKIt.git")
                }
            }
        }
    }
    repositories {
        maven {
            name = "pluginStaging"
            url = layout.buildDirectory.dir("staging-repo").get().asFile.toURI()
        }
        maven {
            name = "GitHubPackages"
            url = uri(
                githubPackagesOwner.zip(githubPackagesRepository) { owner, repository ->
                    "https://maven.pkg.github.com/$owner/$repository"
                }
            )
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .orNull
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .orNull
            }
        }
    }
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named("publishPlugins") {
    doFirst {
        check(!project.version.toString().endsWith("SNAPSHOT")) {
            "publishPlugins requires a final version. Set -PreleaseVersion=<final-version> before publishing."
        }
    }
}
