package io.github.haonan.kmp.apple.packager.tasks

import io.github.haonan.kmp.apple.packager.internal.ArtifactLocationResolver
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Prints human-readable release information to the console.")
/**
 * Prints a compact summary of the generated package metadata and publish status.
 *
 * Author: kairowan
 */
abstract class PrintReleaseSummaryTask : DefaultTask() {
    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val packageVersion: Property<String>

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
    abstract val githubServerUrl: Property<String>

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

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifestFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val localManifestFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val publishMetadataFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifestRepositoryMetadataFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val validationReportFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val artifactVerificationReportFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val artifactStructureReportFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val releaseSupportAssetsReportFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val releaseBundleReportFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val metadataFile: RegularFileProperty

    @TaskAction
    fun printSummary() {
        val checksum = checksumFile.get().asFile.readText().trim()
        val artifactUrl = ArtifactLocationResolver.resolve(
            artifactUrlOverride = artifactUrlOverride.orNull,
            githubServerUrl = githubServerUrl.get(),
            githubRepo = githubRepo.orNull,
            githubTag = githubTag.orNull,
            assetName = archiveFileName.get(),
        )
        val published = readProperty(publishMetadataFile.get().asFile, "published") ?: "unknown"
        val releaseAssetStatus = readProperty(publishMetadataFile.get().asFile, "assetStatus") ?: "unknown"
        val manifestRepositoryStatus = readProperty(manifestRepositoryMetadataFile.get().asFile, "status") ?: "unknown"
        val manifestRepository = readProperty(manifestRepositoryMetadataFile.get().asFile, "repository").orEmpty()
        val manifestRepositoryBranch = readProperty(manifestRepositoryMetadataFile.get().asFile, "branch").orEmpty()
        val manifestRepositoryRemote = readProperty(manifestRepositoryMetadataFile.get().asFile, "originRemoteUrl").orEmpty()
        val manifestRepositoryPushed = readProperty(manifestRepositoryMetadataFile.get().asFile, "pushed") ?: "unknown"
        val validationStatus = readProperty(validationReportFile.get().asFile, "status") ?: "unknown"
        val artifactVerificationStatus = readProperty(artifactVerificationReportFile.get().asFile, "status") ?: "unknown"
        val artifactStructureStatus = readProperty(artifactStructureReportFile.get().asFile, "status") ?: "unknown"
        val releaseSupportAssetsStatus = releaseSupportAssetsReportFile.orNull?.asFile?.let { file ->
            readProperty(file, "status")
        } ?: "notCaptured"
        val releaseSupportAssetsSummary = releaseSupportAssetsReportFile.orNull?.asFile?.let { file ->
            buildReleaseSupportAssetsSummary(file)
        }.orEmpty()
        val releaseBundleStatus = releaseBundleReportFile.orNull?.asFile?.let { file ->
            readProperty(file, "status")
        } ?: "notCaptured"
        val releaseBundleArchive = releaseBundleReportFile.orNull?.asFile?.let { file ->
            readProperty(file, "archive")
        }.orEmpty()
        val platforms = buildList {
            minimumIosVersion.orNull.toPlatformSummary("iOS")?.let(::add)
            minimumMacosVersion.orNull.toPlatformSummary("macOS")?.let(::add)
            minimumTvosVersion.orNull.toPlatformSummary("tvOS")?.let(::add)
            minimumWatchosVersion.orNull.toPlatformSummary("watchOS")?.let(::add)
            minimumVisionosVersion.orNull.toPlatformSummary("visionOS")?.let(::add)
            minimumMacCatalystVersion.orNull.toPlatformSummary("macCatalyst")?.let(::add)
        }.joinToString(", ")

        logger.lifecycle(
            """
            |KMP Apple Packager summary
            |package: ${packageName.get()}
            |version: ${packageVersion.get()}
            |checksum: $checksum
            |artifactUrl: $artifactUrl
            |platforms: $platforms
            |manifest: ${manifestFile.get().asFile.absolutePath}
            |localManifest: ${localManifestFile.get().asFile.absolutePath}
            |published: $published
            |releaseAssetStatus: $releaseAssetStatus
            |manifestRepositoryStatus: $manifestRepositoryStatus
            |manifestRepository: $manifestRepository
            |manifestRepositoryBranch: $manifestRepositoryBranch
            |manifestRepositoryRemote: $manifestRepositoryRemote
            |manifestRepositoryPushed: $manifestRepositoryPushed
            |validation: $validationStatus
            |artifactStructure: $artifactStructureStatus
            |artifactVerification: $artifactVerificationStatus
            |releaseSupportAssets: $releaseSupportAssetsStatus
            |releaseSupportAssetsDetail: $releaseSupportAssetsSummary
            |releaseBundle: $releaseBundleStatus
            |releaseBundleArchive: $releaseBundleArchive
            |metadata: ${metadataFile.get().asFile.absolutePath}
            """.trimMargin()
        )
    }

    private fun readProperty(file: File, key: String): String? {
        if (!file.exists()) {
            return null
        }
        return file.readLines()
            .firstOrNull { line -> line.startsWith("$key=") }
            ?.substringAfter("=")
    }

    private fun String?.toPlatformSummary(swiftPlatformName: String): String? {
        val version = this?.trim().orEmpty()
        if (version.isEmpty()) {
            return null
        }
        return "$swiftPlatformName $version"
    }

    private fun buildReleaseSupportAssetsSummary(file: File): String {
        val assetCount = readProperty(file, "assetCount")?.toIntOrNull() ?: 0
        if (assetCount <= 0) {
            return readProperty(file, "reason").orEmpty()
        }
        return (0 until assetCount).mapNotNull { index ->
            val name = readProperty(file, "asset${index}Name").orEmpty()
            val status = readProperty(file, "asset${index}Status").orEmpty()
            val type = readProperty(file, "asset${index}Type").orEmpty()
            if (name.isBlank()) {
                null
            } else {
                if (type.isBlank()) "$name:$status" else "$type=$name:$status"
            }
        }.joinToString(", ")
    }
}
