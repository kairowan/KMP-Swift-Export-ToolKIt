plugins {
    id("io.github.haonan.kmp.apple.packager")
}

group = "com.example"

val samplePackageVersion = providers.gradleProperty("kmp.apple.packager.sampleVersion")
    .orElse("1.0.0")

version = samplePackageVersion.get()

kmpApplePackager {
    packageName.set("Shared")
    version.set(project.version.toString())
    artifactModule.set(":shared")
    githubServerUrl.set(
        providers.gradleProperty("kmp.apple.packager.githubServerUrl")
            .orElse("https://github.com")
    )
    githubApiUrl.set(
        providers.gradleProperty("kmp.apple.packager.githubApiUrl")
            .orElse(
                githubServerUrl.map { serverUrl ->
                    val normalizedServerUrl = serverUrl.trim().trimEnd('/')
                    if (normalizedServerUrl.equals("https://github.com", ignoreCase = true)) {
                        "https://api.github.com"
                    } else {
                        "$normalizedServerUrl/api/v3"
                    }
                }
            )
    )
    githubRepo.set(
        providers.environmentVariable("GITHUB_REPOSITORY").orElse("yourname/shared-package")
    )
    githubTag.set(
        providers.gradleProperty("kmp.apple.packager.githubTag")
            .orElse(project.version.toString())
    )
    releaseName.set(
        providers.gradleProperty("kmp.apple.packager.releaseName")
            .orElse(githubTag)
    )
    releaseNotes.set(
        providers.environmentVariable("KMP_APPLE_PACKAGER_RELEASE_NOTES")
            .orElse(
                providers.gradleProperty("kmp.apple.packager.releaseNotes")
            )
            .orElse("")
    )
    artifactUrlOverride.set(
        providers.gradleProperty("kmp.apple.packager.artifactUrlOverride")
            .orElse("")
    )
    minimumIosVersion.set(
        providers.gradleProperty("kmp.apple.packager.minimumIosVersion")
            .orElse("16.0")
    )
    minimumMacosVersion.set(
        providers.gradleProperty("kmp.apple.packager.minimumMacosVersion")
            .orElse("")
    )
    minimumTvosVersion.set(
        providers.gradleProperty("kmp.apple.packager.minimumTvosVersion")
            .orElse("")
    )
    minimumWatchosVersion.set(
        providers.gradleProperty("kmp.apple.packager.minimumWatchosVersion")
            .orElse("")
    )
    minimumVisionosVersion.set(
        providers.gradleProperty("kmp.apple.packager.minimumVisionosVersion")
            .orElse("")
    )
    minimumMacCatalystVersion.set(
        providers.gradleProperty("kmp.apple.packager.minimumMacCatalystVersion")
            .orElse("")
    )
    swiftExecutable.set(
        providers.gradleProperty("kmp.apple.packager.swiftExecutable")
            .orElse("swift")
    )
    gitExecutable.set(
        providers.gradleProperty("kmp.apple.packager.gitExecutable")
            .orElse("git")
    )
    commandTimeoutSeconds.set(
        providers.gradleProperty("kmp.apple.packager.commandTimeoutSeconds")
            .map(String::toInt)
            .orElse(600)
    )
    githubRequestTimeoutSeconds.set(
        providers.gradleProperty("kmp.apple.packager.githubRequestTimeoutSeconds")
            .map(String::toInt)
            .orElse(120)
    )
    githubMaxRetries.set(
        providers.gradleProperty("kmp.apple.packager.githubMaxRetries")
            .map(String::toInt)
            .orElse(2)
    )
    overwriteExistingReleaseAsset.set(
        providers.gradleProperty("kmp.apple.packager.overwriteExistingReleaseAsset")
            .map(String::toBoolean)
            .orElse(false)
    )
    verifyPublishedArtifact.set(
        providers.gradleProperty("kmp.apple.packager.verifyPublishedArtifact")
            .map(String::toBoolean)
            .orElse(true)
    )
    artifactDownloadTimeoutSeconds.set(
        providers.gradleProperty("kmp.apple.packager.artifactDownloadTimeoutSeconds")
            .map(String::toInt)
            .orElse(300)
    )
    artifactDownloadMaxRetries.set(
        providers.gradleProperty("kmp.apple.packager.artifactDownloadMaxRetries")
            .map(String::toInt)
            .orElse(2)
    )
    failOnDirtyManifestRepository.set(
        providers.gradleProperty("kmp.apple.packager.failOnDirtyManifestRepository")
            .map(String::toBoolean)
            .orElse(true)
    )
    manifestRepository.set(
        providers.gradleProperty("kmp.apple.packager.manifestRepository")
            .orElse("")
    )
    manifestRepositoryPath.set(
        providers.gradleProperty("kmp.apple.packager.manifestRepositoryPath")
            .orElse("")
    )
    manifestRepositoryBranch.set(
        providers.gradleProperty("kmp.apple.packager.manifestRepositoryBranch")
            .orElse("main")
    )
    manifestRepositorySubdirectory.set(
        providers.gradleProperty("kmp.apple.packager.manifestRepositorySubdirectory")
            .orElse("")
    )
    manifestCommitUserName.set(
        providers.gradleProperty("kmp.apple.packager.manifestCommitUserName")
            .orElse("")
    )
    manifestCommitUserEmail.set(
        providers.gradleProperty("kmp.apple.packager.manifestCommitUserEmail")
            .orElse("")
    )
    publishRelease.set(
        providers.gradleProperty("kmp.apple.packager.publishRelease")
            .map(String::toBoolean)
            .orElse(false)
    )
    publishManifestRepository.set(
        providers.gradleProperty("kmp.apple.packager.publishManifestRepository")
            .map(String::toBoolean)
            .orElse(false)
    )
    pushManifestRepository.set(
        providers.gradleProperty("kmp.apple.packager.pushManifestRepository")
            .map(String::toBoolean)
            .orElse(false)
    )
    validatePackage.set(
        providers.gradleProperty("kmp.apple.packager.validatePackage")
            .map(String::toBoolean)
            .orElse(false)
    )
}

val iosConsumerProjectDir = projectDir.resolve("../ios-consumer").canonicalFile
val localPackageDirectory = layout.buildDirectory.dir("kmpApplePackager/localPackage")
val derivedDataDirectory = layout.buildDirectory.dir("kmpApplePackager/smoke/DerivedData")

tasks.register<Exec>("smokeTestIosConsumer") {
    group = "verification"
    description = "Builds the sample Swift consumer against the generated local package with xcodebuild."
    dependsOn("generateAppleLocalPackageManifest")
    onlyIf {
        val isMacOs = System.getProperty("os.name").contains("mac", ignoreCase = true)
        if (!isMacOs) {
            logger.lifecycle("Skipping smokeTestIosConsumer because xcodebuild requires macOS.")
        }
        isMacOs
    }
    workingDir = iosConsumerProjectDir
    environment("SHARED_PACKAGE_PATH", localPackageDirectory.get().asFile.absolutePath)
    commandLine(
        "xcodebuild",
        "-scheme",
        "SmokeConsumer",
        "-destination",
        "generic/platform=iOS Simulator",
        "-derivedDataPath",
        derivedDataDirectory.get().asFile.absolutePath,
        "build",
    )
    doFirst {
        delete(
            iosConsumerProjectDir.resolve(".build"),
            iosConsumerProjectDir.resolve(".swiftpm"),
            iosConsumerProjectDir.resolve("Package.resolved"),
            derivedDataDirectory.get().asFile,
        )
    }
}
