package io.github.haonan.kmp.apple.packager.tasks

import io.github.haonan.kmp.apple.packager.internal.ArtifactStructureValidationSpec
import io.github.haonan.kmp.apple.packager.internal.ArtifactStructureValidator
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Validates XCFramework and zip artifact structure before release metadata is published.")
/**
 * Ensures the generated XCFramework and distribution archive are structurally safe for SwiftPM distribution.
 *
 * Author: kairowan
 */
abstract class ValidateAppleArtifactStructureTask : DefaultTask() {
    @get:Input
    abstract val packageName: Property<String>

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

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val xcframeworkDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archiveFile: RegularFileProperty

    @get:OutputFile
    abstract val validationReportFile: RegularFileProperty

    @TaskAction
    fun validateArtifactStructure() {
        val result = ArtifactStructureValidator.validate(
            ArtifactStructureValidationSpec(
                packageName = packageName.get(),
                configuredPlatforms = configuredPlatforms(),
                xcframeworkDirectory = xcframeworkDirectory.get().asFile,
                archiveFile = archiveFile.get().asFile,
            )
        )

        val reportFile = validationReportFile.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText(
            buildString {
                appendLine("status=${if (result.errors.isEmpty()) "valid" else "invalid"}")
                appendLine("rootDirectory=${result.rootDirectory}")
                appendLine("formatVersion=${result.formatVersion.orEmpty()}")
                appendLine("libraryCount=${result.libraryCount}")
                appendLine("supportedPlatforms=${result.supportedPlatforms.joinToString(",")}")
                appendLine("archiveTopLevelEntries=${result.archiveTopLevelEntries.joinToString(",")}")
                appendLine("hasMacosMetadataEntries=${result.hasMacosMetadataEntries}")
                appendLine("errorCount=${result.errors.size}")
                appendLine("warningCount=${result.warnings.size}")
                result.errors.forEachIndexed { index, error ->
                    appendLine("error$index=$error")
                }
                result.warnings.forEachIndexed { index, warning ->
                    appendLine("warning$index=$warning")
                }
            }
        )

        result.warnings.forEach { warning ->
            logger.warn("KMP Apple Packager artifact structure warning: $warning")
        }

        if (result.errors.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("KMP Apple Packager artifact structure is invalid:")
                    result.errors.forEach { error ->
                        appendLine("- $error")
                    }
                }.trimEnd()
            )
        }

        logger.lifecycle("Validated XCFramework and archive structure for ${packageName.get()}.")
    }

    private fun configuredPlatforms(): Set<String> {
        return buildSet {
            minimumIosVersion.orNull.takeIf { !it.isNullOrBlank() }?.let { add("iOS") }
            minimumMacosVersion.orNull.takeIf { !it.isNullOrBlank() }?.let { add("macOS") }
            minimumTvosVersion.orNull.takeIf { !it.isNullOrBlank() }?.let { add("tvOS") }
            minimumWatchosVersion.orNull.takeIf { !it.isNullOrBlank() }?.let { add("watchOS") }
            minimumVisionosVersion.orNull.takeIf { !it.isNullOrBlank() }?.let { add("visionOS") }
            minimumMacCatalystVersion.orNull.takeIf { !it.isNullOrBlank() }?.let { add("macCatalyst") }
        }
    }
}
