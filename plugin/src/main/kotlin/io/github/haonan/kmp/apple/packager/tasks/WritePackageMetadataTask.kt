package io.github.haonan.kmp.apple.packager.tasks

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.haonan.kmp.apple.packager.internal.ArtifactLocationResolver
import io.github.haonan.kmp.apple.packager.internal.CommandAvailabilityProbe
import java.io.File
import java.time.Instant
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
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

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
    abstract val githubServerUrl: Property<String>

    @get:Input
    @get:Optional
    abstract val githubRepo: Property<String>

    @get:Input
    @get:Optional
    abstract val githubTag: Property<String>

    @get:Input
    abstract val archiveFileName: Property<String>

    @get:Input
    abstract val swiftExecutable: Property<String>

    @get:Input
    abstract val gitExecutable: Property<String>

    @get:Input
    abstract val commandTimeoutSeconds: Property<Int>

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
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val configurationValidationReportFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val releaseSupportAssetsReportFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val releaseBundleReportFile: RegularFileProperty

    @get:OutputFile
    abstract val metadataFile: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun writeMetadata() {
        val checksum = checksumFile.get().asFile.readText().trim()
        val artifactUrl = ArtifactLocationResolver.resolve(
            artifactUrlOverride = artifactUrlOverride.orNull,
            githubServerUrl = githubServerUrl.get(),
            githubRepo = githubRepo.orNull,
            githubTag = githubTag.orNull,
            assetName = archiveFileName.get(),
        )
        val releaseMetadata = readProperties(publishMetadataFile.get().asFile)
        val manifestRepositoryMetadata = readProperties(manifestRepositoryMetadataFile.get().asFile)
        val validationMetadata = readProperties(validationReportFile.get().asFile)
        val artifactVerificationMetadata = readProperties(artifactVerificationReportFile.get().asFile)
        val artifactStructureMetadata = readProperties(artifactStructureReportFile.get().asFile)
        val configurationMetadata = readProperties(configurationValidationReportFile.get().asFile)
        val releaseSupportAssetsMetadata = releaseSupportAssetsReportFile.orNull?.asFile?.let(::readProperties).orEmpty()
        val releaseBundleMetadata = releaseBundleReportFile.orNull?.asFile?.let(::readProperties).orEmpty()

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
                localPath = localManifestFile.get().asFile.absolutePath,
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
            artifactStructure = ArtifactStructureMetadata(
                status = artifactStructureMetadata["status"] ?: "unknown",
                rootDirectory = artifactStructureMetadata["rootDirectory"],
                formatVersion = artifactStructureMetadata["formatVersion"],
                libraryCount = artifactStructureMetadata["libraryCount"]?.toIntOrNull(),
                supportedPlatforms = readCommaSeparatedValues(artifactStructureMetadata["supportedPlatforms"]),
                archiveTopLevelEntries = readCommaSeparatedValues(artifactStructureMetadata["archiveTopLevelEntries"]),
                hasMacosMetadataEntries = parseBoolean(artifactStructureMetadata["hasMacosMetadataEntries"]),
            ),
            configuration = ConfigurationMetadata(
                status = configurationMetadata["status"] ?: "unknown",
                warnings = readIndexedValues(configurationMetadata, "warning"),
            ),
            releaseSupportAssets = ReleaseSupportAssetsMetadata(
                status = releaseSupportAssetsMetadata["status"] ?: "notCaptured",
                reason = releaseSupportAssetsMetadata["reason"],
                assets = readReleaseSupportAssets(releaseSupportAssetsMetadata),
            ),
            releaseBundle = ReleaseBundleMetadata(
                status = releaseBundleMetadata["status"] ?: "notCaptured",
                directory = releaseBundleMetadata["directory"],
                archive = releaseBundleMetadata["archive"],
                manifest = releaseBundleMetadata["manifest"],
                entryCount = releaseBundleMetadata["entryCount"]?.toIntOrNull(),
            ),
            environment = buildEnvironmentMetadata(),
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

    private fun buildEnvironmentMetadata(): EnvironmentMetadata {
        return EnvironmentMetadata(
            generatedAt = Instant.now().toString(),
            operatingSystem = OperatingSystemMetadata(
                name = System.getProperty("os.name").orEmpty(),
                version = System.getProperty("os.version").orEmpty(),
                architecture = System.getProperty("os.arch").orEmpty(),
            ),
            java = JavaEnvironmentMetadata(
                version = System.getProperty("java.version").orEmpty(),
                vendor = System.getProperty("java.vendor").orEmpty(),
                runtimeVersion = System.getProperty("java.runtime.version").orEmpty(),
                vmName = System.getProperty("java.vm.name").orEmpty(),
                home = System.getProperty("java.home").orEmpty(),
            ),
            gradle = GradleEnvironmentMetadata(
                version = project.gradle.gradleVersion,
            ),
            tools = ToolchainMetadata(
                swift = captureToolVersion(swiftExecutable.get(), listOf("--version")),
                git = captureToolVersion(gitExecutable.get(), listOf("--version")),
                xcodebuild = captureToolVersion("xcodebuild", listOf("-version")),
            ),
        )
    }

    private fun captureToolVersion(
        executable: String,
        arguments: List<String>,
    ): ToolVersionMetadata {
        return try {
            val result = CommandAvailabilityProbe.probe(
                execOperations = execOperations,
                executable = executable,
                arguments = arguments,
                commandTimeoutSeconds = commandTimeoutSeconds.get(),
            )
            ToolVersionMetadata(
                executable = executable,
                status = if (result.available) "available" else "unavailable",
                version = result.output,
                error = result.failureMessage,
            )
        } catch (exception: Exception) {
            ToolVersionMetadata(
                executable = executable,
                status = "unavailable",
                version = null,
                error = exception.message?.lineSequence()?.firstOrNull(),
            )
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

    private fun readCommaSeparatedValues(value: String?): List<String> {
        return value.orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
    }

    private fun readReleaseSupportAssets(
        properties: Map<String, String>,
    ): List<ReleaseSupportAssetMetadata> {
        val assetCount = properties["assetCount"]?.toIntOrNull() ?: 0
        return (0 until assetCount).map { index ->
            ReleaseSupportAssetMetadata(
                name = properties["asset${index}Name"].orEmpty(),
                type = properties["asset${index}Type"],
                status = properties["asset${index}Status"].orEmpty(),
                localPath = properties["asset${index}LocalPath"],
                checksum = properties["asset${index}Checksum"],
                downloadUrl = properties["asset${index}DownloadUrl"],
            )
        }.filter { asset -> asset.name.isNotBlank() }
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
    val artifactStructure: ArtifactStructureMetadata,
    val configuration: ConfigurationMetadata,
    val releaseSupportAssets: ReleaseSupportAssetsMetadata,
    val releaseBundle: ReleaseBundleMetadata,
    val environment: EnvironmentMetadata,
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
    val localPath: String,
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

internal data class ArtifactStructureMetadata(
    val status: String,
    val rootDirectory: String?,
    val formatVersion: String?,
    val libraryCount: Int?,
    val supportedPlatforms: List<String>,
    val archiveTopLevelEntries: List<String>,
    val hasMacosMetadataEntries: Boolean?,
)

internal data class ConfigurationMetadata(
    val status: String,
    val warnings: List<String>,
)

internal data class ReleaseSupportAssetsMetadata(
    val status: String,
    val reason: String?,
    val assets: List<ReleaseSupportAssetMetadata>,
)

internal data class ReleaseSupportAssetMetadata(
    val name: String,
    val type: String?,
    val status: String,
    val localPath: String?,
    val checksum: String?,
    val downloadUrl: String?,
)

internal data class ReleaseBundleMetadata(
    val status: String,
    val directory: String?,
    val archive: String?,
    val manifest: String?,
    val entryCount: Int?,
)

internal data class EnvironmentMetadata(
    val generatedAt: String,
    val operatingSystem: OperatingSystemMetadata,
    val java: JavaEnvironmentMetadata,
    val gradle: GradleEnvironmentMetadata,
    val tools: ToolchainMetadata,
)

internal data class OperatingSystemMetadata(
    val name: String,
    val version: String,
    val architecture: String,
)

internal data class JavaEnvironmentMetadata(
    val version: String,
    val vendor: String,
    val runtimeVersion: String,
    val vmName: String,
    val home: String,
)

internal data class GradleEnvironmentMetadata(
    val version: String,
)

internal data class ToolchainMetadata(
    val swift: ToolVersionMetadata,
    val git: ToolVersionMetadata,
    val xcodebuild: ToolVersionMetadata,
)

internal data class ToolVersionMetadata(
    val executable: String,
    val status: String,
    val version: String?,
    val error: String?,
)
