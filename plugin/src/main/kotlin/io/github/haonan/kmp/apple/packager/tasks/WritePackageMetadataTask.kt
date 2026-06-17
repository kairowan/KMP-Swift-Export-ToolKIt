package io.github.haonan.kmp.apple.packager.tasks

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.haonan.kmp.apple.packager.internal.ArtifactLocationResolver
import java.io.File
import org.gradle.api.DefaultTask
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

@DisableCachingByDefault(because = "Aggregates release outputs into a machine-readable metadata file for CI and downstream tooling.")
/**
 * Writes a JSON summary of the generated package so CI and release automation can consume it directly.
 *
 * Author: kairowan
 */
abstract class WritePackageMetadataTask : DefaultTask() {
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
    abstract val configurationValidationReportFile: RegularFileProperty

    @get:OutputFile
    abstract val metadataFile: RegularFileProperty

    @TaskAction
    fun writeMetadata() {
        val checksum = checksumFile.get().asFile.readText().trim()
        val artifactUrl = ArtifactLocationResolver.resolve(
            artifactUrlOverride = artifactUrlOverride.orNull,
            githubRepo = githubRepo.orNull,
            githubTag = githubTag.orNull,
            assetName = archiveFileName.get(),
        )
        val releaseMetadata = readProperties(publishMetadataFile.get().asFile)
        val manifestRepositoryMetadata = readProperties(manifestRepositoryMetadataFile.get().asFile)
        val validationMetadata = readProperties(validationReportFile.get().asFile)
        val artifactVerificationMetadata = readProperties(artifactVerificationReportFile.get().asFile)
        val configurationMetadata = readProperties(configurationValidationReportFile.get().asFile)

        val metadata = PackageMetadata(
            packageName = packageName.get(),
            version = packageVersion.get(),
            platforms = buildPlatforms(),
            artifact = ArtifactMetadata(
                archiveFileName = archiveFileName.get(),
                downloadUrl = artifactUrl,
                checksum = checksum,
            ),
            manifest = ManifestMetadata(
                path = manifestFile.get().asFile.absolutePath,
            ),
            release = ReleaseMetadata(
                published = parseBoolean(releaseMetadata["published"]),
                assetStatus = releaseMetadata["assetStatus"],
                releaseUrl = releaseMetadata["releaseUrl"],
                downloadUrl = releaseMetadata["downloadUrl"],
            ),
            manifestRepository = ManifestRepositoryMetadata(
                status = manifestRepositoryMetadata["status"] ?: "unknown",
                repository = manifestRepositoryMetadata["repository"],
                branch = manifestRepositoryMetadata["branch"],
                path = manifestRepositoryMetadata["path"],
                commit = manifestRepositoryMetadata["commit"],
                commitAuthorName = manifestRepositoryMetadata["commitAuthorName"],
                commitAuthorEmail = manifestRepositoryMetadata["commitAuthorEmail"],
                pushed = parseBoolean(manifestRepositoryMetadata["pushed"]),
                originRemoteUrl = manifestRepositoryMetadata["originRemoteUrl"],
                usesLocalCheckout = parseBoolean(manifestRepositoryMetadata["usesLocalCheckout"]),
            ),
            validation = ValidationMetadata(
                status = validationMetadata["status"] ?: "unknown",
                path = validationMetadata["path"],
            ),
            artifactVerification = ArtifactVerificationMetadata(
                status = artifactVerificationMetadata["status"] ?: "unknown",
                url = artifactVerificationMetadata["url"],
                downloadedFile = artifactVerificationMetadata["downloadedFile"],
                checksum = artifactVerificationMetadata["checksum"],
                expectedChecksum = artifactVerificationMetadata["expectedChecksum"],
                actualChecksum = artifactVerificationMetadata["actualChecksum"],
                reason = artifactVerificationMetadata["reason"],
            ),
            configuration = ConfigurationMetadata(
                status = configurationMetadata["status"] ?: "unknown",
                warnings = readIndexedValues(configurationMetadata, "warning"),
            ),
        )

        val output = metadataFile.get().asFile
        output.parentFile.mkdirs()
        jacksonObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValue(output, metadata)

        logger.lifecycle("Wrote package metadata to ${output.absolutePath}")
    }

    private fun buildPlatforms(): List<PlatformMetadata> {
        return buildList {
            minimumIosVersion.orNull.toPlatformMetadata("iOS")?.let(::add)
            minimumMacosVersion.orNull.toPlatformMetadata("macOS")?.let(::add)
            minimumTvosVersion.orNull.toPlatformMetadata("tvOS")?.let(::add)
            minimumWatchosVersion.orNull.toPlatformMetadata("watchOS")?.let(::add)
            minimumVisionosVersion.orNull.toPlatformMetadata("visionOS")?.let(::add)
            minimumMacCatalystVersion.orNull.toPlatformMetadata("macCatalyst")?.let(::add)
        }
    }

    private fun String?.toPlatformMetadata(name: String): PlatformMetadata? {
        val version = this?.trim().orEmpty()
        if (version.isEmpty()) {
            return null
        }
        return PlatformMetadata(
            name = name,
            minimumVersion = version,
        )
    }

    private fun parseBoolean(value: String?): Boolean? {
        return when (value?.trim()?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

    private fun readIndexedValues(
        properties: Map<String, String>,
        keyPrefix: String,
    ): List<String> {
        return properties.entries
            .filter { (key, _) -> key.removePrefix(keyPrefix).toIntOrNull() != null }
            .sortedBy { (key, _) -> key.removePrefix(keyPrefix).toIntOrNull() ?: Int.MAX_VALUE }
            .map { (_, value) -> value }
    }

    private fun readProperties(file: File): Map<String, String> {
        if (!file.exists()) {
            return emptyMap()
        }
        return file.readLines()
            .mapNotNull { line ->
                val separatorIndex = line.indexOf('=')
                if (separatorIndex <= 0) {
                    null
                } else {
                    line.substring(0, separatorIndex) to line.substring(separatorIndex + 1)
                }
            }
            .toMap()
    }
}

internal data class PackageMetadata(
    val packageName: String,
    val version: String,
    val platforms: List<PlatformMetadata>,
    val artifact: ArtifactMetadata,
    val manifest: ManifestMetadata,
    val release: ReleaseMetadata,
    val manifestRepository: ManifestRepositoryMetadata,
    val validation: ValidationMetadata,
    val artifactVerification: ArtifactVerificationMetadata,
    val configuration: ConfigurationMetadata,
)

internal data class PlatformMetadata(
    val name: String,
    val minimumVersion: String,
)

internal data class ArtifactMetadata(
    val archiveFileName: String,
    val downloadUrl: String,
    val checksum: String,
)

internal data class ManifestMetadata(
    val path: String,
)

internal data class ReleaseMetadata(
    val published: Boolean?,
    val assetStatus: String?,
    val releaseUrl: String?,
    val downloadUrl: String?,
)

internal data class ManifestRepositoryMetadata(
    val status: String,
    val repository: String?,
    val branch: String?,
    val path: String?,
    val commit: String?,
    val commitAuthorName: String?,
    val commitAuthorEmail: String?,
    val pushed: Boolean?,
    val originRemoteUrl: String?,
    val usesLocalCheckout: Boolean?,
)

internal data class ValidationMetadata(
    val status: String,
    val path: String?,
)

internal data class ArtifactVerificationMetadata(
    val status: String,
    val url: String?,
    val downloadedFile: String?,
    val checksum: String?,
    val expectedChecksum: String?,
    val actualChecksum: String?,
    val reason: String?,
)

internal data class ConfigurationMetadata(
    val status: String,
    val warnings: List<String>,
)
