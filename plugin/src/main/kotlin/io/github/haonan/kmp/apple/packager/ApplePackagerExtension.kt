package io.github.haonan.kmp.apple.packager
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

/**
 * Holds the user-facing DSL for configuring the Apple packaging pipeline.
 *
 * Author: kairowan
 */
abstract class ApplePackagerExtension @Inject constructor(
    providers: ProviderFactory,
) {
    abstract val packageName: Property<String>
    abstract val version: Property<String>
    abstract val artifactModule: Property<String>
    abstract val iosTargets: ListProperty<String>
    abstract val swiftToolsVersion: Property<String>
    abstract val minimumIosVersion: Property<String>
    abstract val minimumMacosVersion: Property<String>
    abstract val minimumTvosVersion: Property<String>
    abstract val minimumWatchosVersion: Property<String>
    abstract val minimumVisionosVersion: Property<String>
    abstract val minimumMacCatalystVersion: Property<String>
    abstract val swiftExecutable: Property<String>
    abstract val gitExecutable: Property<String>
    abstract val commandTimeoutSeconds: Property<Int>
    abstract val githubRequestTimeoutSeconds: Property<Int>
    abstract val githubMaxRetries: Property<Int>
    abstract val overwriteExistingReleaseAsset: Property<Boolean>
    abstract val verifyPublishedArtifact: Property<Boolean>
    abstract val artifactDownloadTimeoutSeconds: Property<Int>
    abstract val artifactDownloadMaxRetries: Property<Int>
    abstract val failOnDirtyManifestRepository: Property<Boolean>
    abstract val xcodeConfiguration: Property<String>
    abstract val assembleTaskName: Property<String>
    abstract val githubRepo: Property<String>
    abstract val githubTag: Property<String>
    abstract val githubToken: Property<String>
    abstract val releaseName: Property<String>
    abstract val releaseNotes: Property<String>
    abstract val artifactUrlOverride: Property<String>
    abstract val manifestRepository: Property<String>
    abstract val manifestRepositoryPath: Property<String>
    abstract val manifestRepositoryBranch: Property<String>
    abstract val manifestRepositorySubdirectory: Property<String>
    abstract val manifestCommitUserName: Property<String>
    abstract val manifestCommitUserEmail: Property<String>
    abstract val publishRelease: Property<Boolean>
    abstract val publishManifestRepository: Property<Boolean>
    abstract val pushManifestRepository: Property<Boolean>
    abstract val validatePackage: Property<Boolean>

    init {
        packageName.convention("Shared")
        artifactModule.convention(":shared")
        iosTargets.convention(listOf("iosArm64", "iosSimulatorArm64"))
        swiftToolsVersion.convention("6.0")
        minimumIosVersion.convention("16.0")
        minimumMacosVersion.convention("")
        minimumTvosVersion.convention("")
        minimumWatchosVersion.convention("")
        minimumVisionosVersion.convention("")
        minimumMacCatalystVersion.convention("")
        swiftExecutable.convention("swift")
        gitExecutable.convention("git")
        commandTimeoutSeconds.convention(600)
        githubRequestTimeoutSeconds.convention(120)
        githubMaxRetries.convention(2)
        overwriteExistingReleaseAsset.convention(false)
        verifyPublishedArtifact.convention(true)
        artifactDownloadTimeoutSeconds.convention(300)
        artifactDownloadMaxRetries.convention(2)
        failOnDirtyManifestRepository.convention(true)
        xcodeConfiguration.convention("release")
        githubToken.convention(providers.environmentVariable("GITHUB_TOKEN"))
        publishRelease.convention(true)
        publishManifestRepository.convention(false)
        pushManifestRepository.convention(false)
        validatePackage.convention(true)
        releaseNotes.convention("")
        githubTag.convention(version)
        releaseName.convention(githubTag)
        manifestRepositoryBranch.convention("main")
        manifestRepositorySubdirectory.convention("")
        manifestCommitUserName.convention(providers.environmentVariable("GIT_AUTHOR_NAME"))
        manifestCommitUserEmail.convention(providers.environmentVariable("GIT_AUTHOR_EMAIL"))
        // Mirror Kotlin's generated XCFramework task naming so the plugin can attach
        // itself to existing module exports without requiring extra sample-specific wiring.
        assembleTaskName.convention(
            packageName.zip(xcodeConfiguration) { name, configuration ->
                "assemble${name.toUpperCamelCase()}${configuration.toUpperCamelCase()}XCFramework"
            }
        )
    }
}

private fun String.toUpperCamelCase(): String {
    return split(Regex("[^A-Za-z0-9]+"))
        .filter(String::isNotBlank)
        .joinToString("") { token ->
            token.replaceFirstChar { char ->
                if (char.isLowerCase()) {
                    char.titlecase()
                } else {
                    char.toString()
                }
            }
        }
}
