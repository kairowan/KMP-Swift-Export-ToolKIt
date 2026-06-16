package io.github.haonan.kmp.apple.packager.tasks

import io.github.haonan.kmp.apple.packager.internal.ArtifactLocationResolver
import io.github.haonan.kmp.apple.packager.internal.ManifestRenderer
import io.github.haonan.kmp.apple.packager.internal.PackageManifestSpec
import io.github.haonan.kmp.apple.packager.internal.SwiftPackagePlatformSpec
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Generates Package.swift based on release metadata and checksum outputs.")
/**
 * Generates `Package.swift` for the zipped XCFramework artifact.
 *
 * Author: kairowan
 */
abstract class GeneratePackageManifestTask : DefaultTask() {
    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val swiftToolsVersion: Property<String>

    @get:Input
    abstract val minimumIosVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val minimumMacosVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val minimumTvosVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val minimumWatchosVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val minimumVisionosVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val minimumMacCatalystVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val artifactUrlOverride: Property<String>

    @get:Input
    @get:Optional
    abstract val githubRepo: Property<String>

    @get:Input
    @get:Optional
    abstract val githubTag: Property<String>

    @get:Input
    abstract val archiveFileName: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val checksumFile: RegularFileProperty

    @get:OutputFile
    abstract val manifestFile: RegularFileProperty

    @TaskAction
    fun generateManifest() {
        val checksum = checksumFile.get().asFile.readText().trim()
        val platforms = buildList {
            minimumIosVersion.orNull.toPlatformSpec("iOS")?.let(::add)
            minimumMacosVersion.orNull.toPlatformSpec("macOS")?.let(::add)
            minimumTvosVersion.orNull.toPlatformSpec("tvOS")?.let(::add)
            minimumWatchosVersion.orNull.toPlatformSpec("watchOS")?.let(::add)
            minimumVisionosVersion.orNull.toPlatformSpec("visionOS")?.let(::add)
            minimumMacCatalystVersion.orNull.toPlatformSpec("macCatalyst")?.let(::add)
        }

        if (platforms.isEmpty()) {
            throw GradleException(
                "Configure at least one SwiftPM deployment target. " +
                    "For example: kmpApplePackager.minimumIosVersion.set(\"16.0\")"
            )
        }

        // Prefer an explicit override for local experiments, then fall back to the canonical
        // GitHub release download pattern used by the default publishing flow.
        val artifactUrl = ArtifactLocationResolver.resolve(
            artifactUrlOverride = artifactUrlOverride.orNull,
            githubRepo = githubRepo.orNull,
            githubTag = githubTag.orNull,
            assetName = archiveFileName.get(),
        )

        val manifest = ManifestRenderer.render(
            PackageManifestSpec(
                packageName = packageName.get(),
                swiftToolsVersion = swiftToolsVersion.get(),
                platforms = platforms,
                artifactUrl = artifactUrl,
                checksum = checksum,
            )
        )

        val output = manifestFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(manifest)

        logger.lifecycle("Generated Package.swift at ${output.absolutePath}")
    }

    private fun String?.toPlatformSpec(swiftPlatformName: String): SwiftPackagePlatformSpec? {
        val version = this?.trim().orEmpty()
        if (version.isEmpty()) {
            return null
        }
        return SwiftPackagePlatformSpec(
            swiftPlatformName = swiftPlatformName,
            minimumVersion = version,
        )
    }
}
