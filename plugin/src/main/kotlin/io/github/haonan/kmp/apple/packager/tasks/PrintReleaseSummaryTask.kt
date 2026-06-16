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

    @TaskAction
    fun printSummary() {
        val checksum = checksumFile.get().asFile.readText().trim()
        val artifactUrl = ArtifactLocationResolver.resolve(
            artifactUrlOverride = artifactUrlOverride.orNull,
            githubRepo = githubRepo.orNull,
            githubTag = githubTag.orNull,
            assetName = archiveFileName.get(),
        )
        val published = readProperty(publishMetadataFile.get().asFile, "published") ?: "unknown"
        val manifestRepositoryStatus = readProperty(manifestRepositoryMetadataFile.get().asFile, "status") ?: "unknown"
        val manifestRepository = readProperty(manifestRepositoryMetadataFile.get().asFile, "repository").orEmpty()
        val manifestRepositoryBranch = readProperty(manifestRepositoryMetadataFile.get().asFile, "branch").orEmpty()
        val manifestRepositoryPushed = readProperty(manifestRepositoryMetadataFile.get().asFile, "pushed") ?: "unknown"
        val validationStatus = readProperty(validationReportFile.get().asFile, "status") ?: "unknown"

        logger.lifecycle(
            """
            |KMP Apple Packager summary
            |package: ${packageName.get()}
            |version: ${packageVersion.get()}
            |checksum: $checksum
            |artifactUrl: $artifactUrl
            |manifest: ${manifestFile.get().asFile.absolutePath}
            |published: $published
            |manifestRepositoryStatus: $manifestRepositoryStatus
            |manifestRepository: $manifestRepository
            |manifestRepositoryBranch: $manifestRepositoryBranch
            |manifestRepositoryPushed: $manifestRepositoryPushed
            |validation: $validationStatus
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
}
