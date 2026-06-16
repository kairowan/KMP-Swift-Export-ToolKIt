package io.github.haonan.kmp.apple.packager

import io.github.haonan.kmp.apple.packager.internal.toUpperCamelCase
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
