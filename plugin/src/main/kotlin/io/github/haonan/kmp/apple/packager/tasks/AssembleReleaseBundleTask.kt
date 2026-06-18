package io.github.haonan.kmp.apple.packager.tasks

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.haonan.kmp.apple.packager.internal.ProcessRunner
import io.github.haonan.kmp.apple.packager.internal.Sha256Hasher
import java.io.File
import java.time.Instant
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Collects release outputs into an auditable bundle directory and archive.")
/**
 * Assembles the generated release artifacts, reports, and support files into one stable audit bundle.
 *
 * Author: kairowan
 */
abstract class AssembleReleaseBundleTask : DefaultTask() {
    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val packageVersion: Property<String>

    @get:Input
    abstract val commandTimeoutSeconds: Property<Int>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archiveFile: RegularFileProperty

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
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val releaseSupportAssetsReportFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val coreMetadataFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val supportAssetsDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val bundleDirectory: DirectoryProperty

    @get:OutputFile
    abstract val bundleArchiveFile: RegularFileProperty

    @get:OutputFile
    abstract val bundleReportFile: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun assembleBundle() {
        val outputDirectory = bundleDirectory.get().asFile
        val outputArchive = bundleArchiveFile.get().asFile
        val reportFile = bundleReportFile.get().asFile
        fileSystemOperations.delete { spec ->
            spec.delete(outputDirectory, outputArchive, reportFile)
        }
        outputDirectory.mkdirs()
        reportFile.parentFile.mkdirs()

        copyFile(archiveFile.get().asFile, File(outputDirectory, "artifact/${archiveFile.get().asFile.name}"))
        copyFile(checksumFile.get().asFile, File(outputDirectory, "artifact/${checksumFile.get().asFile.name}"))
        copyFile(manifestFile.get().asFile, File(outputDirectory, "manifests/Package.swift"))
        copyFile(localManifestFile.get().asFile, File(outputDirectory, "manifests/local/Package.swift"))
        copyFile(coreMetadataFile.get().asFile, File(outputDirectory, "metadata/${coreMetadataFile.get().asFile.name}"))
        copyFile(publishMetadataFile.get().asFile, File(outputDirectory, "reports/release/publish.properties"))
        copyFile(
            manifestRepositoryMetadataFile.get().asFile,
            File(outputDirectory, "reports/manifest-repository/publish.properties"),
        )
        copyFile(validationReportFile.get().asFile, File(outputDirectory, "reports/validation/report.properties"))
        copyFile(
            artifactVerificationReportFile.get().asFile,
            File(outputDirectory, "reports/artifact-verification/report.properties"),
        )
        copyFile(
            artifactStructureReportFile.get().asFile,
            File(outputDirectory, "reports/artifact-structure/report.properties"),
        )
        copyFile(
            configurationValidationReportFile.get().asFile,
            File(outputDirectory, "reports/configuration/report.properties"),
        )
        copyFile(
            releaseSupportAssetsReportFile.get().asFile,
            File(outputDirectory, "reports/release/support-assets.properties"),
        )
        copyDirectory(supportAssetsDirectory.get().asFile, File(outputDirectory, "release-assets"))

        val manifestOutputFile = File(outputDirectory, "bundle-manifest.json")
        val entries = outputDirectory.walkTopDown()
            .filter(File::isFile)
            .sortedBy { file -> file.relativeTo(outputDirectory).invariantSeparatorsPath }
            .map { file ->
                ReleaseBundleEntry(
                    path = file.relativeTo(outputDirectory).invariantSeparatorsPath,
                    sha256 = Sha256Hasher.compute(file),
                    sizeBytes = file.length(),
                )
            }
            .toList()

        jacksonObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValue(
                manifestOutputFile,
                ReleaseBundleManifest(
                    schemaVersion = 1,
                    generatedAt = Instant.now().toString(),
                    packageName = packageName.get(),
                    packageVersion = packageVersion.get(),
                    entries = entries,
                ),
            )

        outputArchive.parentFile.mkdirs()
        outputArchive.delete()
        ProcessRunner(
            execOperations = execOperations,
            commandTimeoutSeconds = commandTimeoutSeconds.get(),
        ).run(
            commandLine = listOf(
                "ditto",
                "-c",
                "-k",
                "--norsrc",
                "--keepParent",
                outputDirectory.absolutePath,
                outputArchive.absolutePath,
            ),
        )

        reportFile.writeText(
            buildString {
                appendLine("status=assembled")
                appendLine("directory=${outputDirectory.absolutePath}")
                appendLine("archive=${outputArchive.absolutePath}")
                appendLine("manifest=${manifestOutputFile.absolutePath}")
                appendLine("entryCount=${entries.size}")
            }
        )

        logger.lifecycle("Assembled release bundle at ${outputArchive.absolutePath}")
    }

    private fun copyFile(
        source: File,
        target: File,
    ) {
        target.parentFile.mkdirs()
        source.copyTo(target = target, overwrite = true)
    }

    private fun copyDirectory(
        source: File,
        target: File,
    ) {
        target.parentFile.mkdirs()
        fileSystemOperations.copy { spec ->
            spec.from(source)
            spec.into(target)
        }
    }
}

internal data class ReleaseBundleManifest(
    val schemaVersion: Int,
    val generatedAt: String,
    val packageName: String,
    val packageVersion: String,
    val entries: List<ReleaseBundleEntry>,
)

internal data class ReleaseBundleEntry(
    val path: String,
    val sha256: String,
    val sizeBytes: Long,
)
