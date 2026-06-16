package io.github.haonan.kmp.apple.packager

import io.github.haonan.kmp.apple.packager.tasks.AssembleXCFrameworkTask
import io.github.haonan.kmp.apple.packager.tasks.ComputeChecksumTask
import io.github.haonan.kmp.apple.packager.tasks.GeneratePackageManifestTask
import io.github.haonan.kmp.apple.packager.tasks.PrintReleaseSummaryTask
import io.github.haonan.kmp.apple.packager.tasks.PublishGithubReleaseTask
import io.github.haonan.kmp.apple.packager.tasks.PublishPackageManifestRepositoryTask
import io.github.haonan.kmp.apple.packager.tasks.ValidateApplePackagerConfigurationTask
import io.github.haonan.kmp.apple.packager.tasks.ValidateSwiftPmTask
import io.github.haonan.kmp.apple.packager.tasks.WritePackageMetadataTask
import io.github.haonan.kmp.apple.packager.tasks.ZipArtifactTask
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Registers the end-to-end Gradle tasks that turn a KMP Apple framework into a SwiftPM-ready release.
 *
 * Author: kairowan
 */
class KmpApplePackagerPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        val extension = extensions.create(
            "kmpApplePackager",
            ApplePackagerExtension::class.java,
        )

        extension.version.convention(
            providers.provider {
                project.version.toString().takeUnless { it.isBlank() || it == "unspecified" } ?: "0.1.0"
            }
        )

        val taskGroup = "distribution"
        val archiveFileName = providers.provider {
            "${extension.packageName.get()}-${extension.version.get()}.xcframework.zip"
        }

        // Keep all generated assets under a single stable root so samples, docs, and CI can
        // point at predictable locations regardless of which task produced the file.
        val assembledFrameworkOutputDir = layout.buildDirectory.dir(
            providers.provider {
                "kmpApplePackager/xcframework/${extension.packageName.get()}.xcframework"
            }
        )
        val archiveOutputFile = layout.buildDirectory.file(
            archiveFileName.map { fileName ->
                "kmpApplePackager/distributions/$fileName"
            }
        )
        val checksumOutputFile = layout.buildDirectory.file(
            providers.provider {
                "kmpApplePackager/checksum/${extension.packageName.get()}.txt"
            }
        )
        val manifestOutputFile = layout.buildDirectory.file("kmpApplePackager/package/Package.swift")
        val publishMetadataOutputFile = layout.buildDirectory.file("kmpApplePackager/release/publish.properties")
        val manifestRepositoryMetadataOutputFile = layout.buildDirectory.file(
            "kmpApplePackager/packageRepository/publish.properties"
        )
        val validationReportOutputFile = layout.buildDirectory.file("kmpApplePackager/validation/report.properties")
        val configurationValidationReportOutputFile = layout.buildDirectory.file(
            "kmpApplePackager/configuration/report.properties"
        )
        val metadataOutputFile = layout.buildDirectory.file("kmpApplePackager/metadata/package-metadata.json")

        val configurationValidationTask = tasks.register(
            "validateApplePackagerConfiguration",
            ValidateApplePackagerConfigurationTask::class.java,
        )
        configurationValidationTask.configure { task ->
            task.group = taskGroup
            task.description = "Validates the KMP Apple Packager configuration before expensive release tasks run."
            task.packageName.set(extension.packageName)
            task.packageVersion.set(extension.version)
            task.artifactUrlOverride.set(extension.artifactUrlOverride)
            task.githubRepo.set(extension.githubRepo)
            task.githubTag.set(extension.githubTag)
            task.githubTokenConfigured.set(extension.githubToken.map(String::isNotBlank).orElse(false))
            task.manifestRepository.set(extension.manifestRepository)
            task.manifestRepositoryPath.set(extension.manifestRepositoryPath)
            task.manifestRepositoryBranch.set(extension.manifestRepositoryBranch)
            task.manifestRepositorySubdirectory.set(extension.manifestRepositorySubdirectory)
            task.publishRelease.set(extension.publishRelease)
            task.publishManifestRepository.set(extension.publishManifestRepository)
            task.pushManifestRepository.set(extension.pushManifestRepository)
            task.validatePackage.set(extension.validatePackage)
            task.swiftExecutable.set(extension.swiftExecutable)
            task.gitExecutable.set(extension.gitExecutable)
            task.commandTimeoutSeconds.set(extension.commandTimeoutSeconds)
            task.githubRequestTimeoutSeconds.set(extension.githubRequestTimeoutSeconds)
            task.githubMaxRetries.set(extension.githubMaxRetries)
            task.failOnDirtyManifestRepository.set(extension.failOnDirtyManifestRepository)
            task.minimumIosVersion.set(extension.minimumIosVersion)
            task.minimumMacosVersion.set(extension.minimumMacosVersion)
            task.minimumTvosVersion.set(extension.minimumTvosVersion)
            task.minimumWatchosVersion.set(extension.minimumWatchosVersion)
            task.minimumVisionosVersion.set(extension.minimumVisionosVersion)
            task.minimumMacCatalystVersion.set(extension.minimumMacCatalystVersion)
            task.validationReportFile.set(configurationValidationReportOutputFile)
        }

        val assembleTask = tasks.register("assembleAppleXCFramework", AssembleXCFrameworkTask::class.java)
        assembleTask.configure { task ->
            task.group = taskGroup
            task.description = "Copies the release XCFramework assembled by the KMP module into a stable output directory."
            task.dependsOn(configurationValidationTask)
            task.artifactModule.set(extension.artifactModule)
            task.packageName.set(extension.packageName)
            task.xcodeConfiguration.set(extension.xcodeConfiguration)
            task.outputDirectory.set(assembledFrameworkOutputDir)
        }

        val zipTask = tasks.register("zipAppleArtifact", ZipArtifactTask::class.java)
        zipTask.configure { task ->
            task.group = taskGroup
            task.description = "Zips the assembled XCFramework so it can be published as a SwiftPM binary artifact."
            task.dependsOn(assembleTask)
            task.commandTimeoutSeconds.set(extension.commandTimeoutSeconds)
            task.xcframeworkDirectory.set(assembleTask.flatMap { assembledTask -> assembledTask.outputDirectory })
            task.archiveFile.set(archiveOutputFile)
        }

        val checksumTask = tasks.register("computeApplePackageChecksum", ComputeChecksumTask::class.java)
        checksumTask.configure { task ->
            task.group = taskGroup
            task.description = "Computes the SwiftPM checksum for the generated XCFramework archive."
            task.dependsOn(zipTask)
            task.swiftExecutable.set(extension.swiftExecutable)
            task.commandTimeoutSeconds.set(extension.commandTimeoutSeconds)
            task.archiveFile.set(zipTask.flatMap { zipArtifactTask -> zipArtifactTask.archiveFile })
            task.checksumFile.set(checksumOutputFile)
        }

        val manifestTask = tasks.register("generateApplePackageManifest", GeneratePackageManifestTask::class.java)
        manifestTask.configure { task ->
            task.group = taskGroup
            task.description = "Generates Package.swift for the binary XCFramework release."
            task.dependsOn(configurationValidationTask)
            task.dependsOn(checksumTask)
            task.packageName.set(extension.packageName)
            task.swiftToolsVersion.set(extension.swiftToolsVersion)
            task.minimumIosVersion.set(extension.minimumIosVersion)
            task.minimumMacosVersion.set(extension.minimumMacosVersion)
            task.minimumTvosVersion.set(extension.minimumTvosVersion)
            task.minimumWatchosVersion.set(extension.minimumWatchosVersion)
            task.minimumVisionosVersion.set(extension.minimumVisionosVersion)
            task.minimumMacCatalystVersion.set(extension.minimumMacCatalystVersion)
            task.artifactUrlOverride.set(extension.artifactUrlOverride)
            task.githubRepo.set(extension.githubRepo)
            task.githubTag.set(extension.githubTag)
            task.archiveFileName.set(archiveFileName)
            task.checksumFile.set(checksumOutputFile)
            task.manifestFile.set(manifestOutputFile)
        }

        val publishTask = tasks.register("publishGithubRelease", PublishGithubReleaseTask::class.java)
        publishTask.configure { task ->
            task.group = taskGroup
            task.description = "Creates or reuses a GitHub release and uploads the XCFramework archive."
            task.dependsOn(configurationValidationTask)
            task.dependsOn(zipTask)
            task.githubRepo.set(extension.githubRepo)
            task.githubTag.set(extension.githubTag)
            task.githubToken.set(extension.githubToken)
            task.releaseName.set(extension.releaseName)
            task.releaseNotes.set(extension.releaseNotes)
            task.publishRelease.set(extension.publishRelease)
            task.githubRequestTimeoutSeconds.set(extension.githubRequestTimeoutSeconds)
            task.githubMaxRetries.set(extension.githubMaxRetries)
            task.archiveFile.set(archiveOutputFile)
            task.publishMetadataFile.set(publishMetadataOutputFile)
        }

        val publishManifestRepositoryTask = tasks.register(
            "publishPackageManifestRepository",
            PublishPackageManifestRepositoryTask::class.java,
        )
        publishManifestRepositoryTask.configure { task ->
            task.group = taskGroup
            task.description = "Copies Package.swift into a dedicated repository or branch and optionally pushes it."
            task.dependsOn(configurationValidationTask)
            task.dependsOn(manifestTask, publishTask)
            task.packageName.set(extension.packageName)
            task.packageVersion.set(extension.version)
            task.manifestFile.set(manifestOutputFile)
            task.manifestRepository.set(extension.manifestRepository)
            task.manifestRepositoryPath.set(extension.manifestRepositoryPath)
            task.manifestRepositoryBranch.set(extension.manifestRepositoryBranch)
            task.manifestRepositorySubdirectory.set(extension.manifestRepositorySubdirectory)
            task.manifestCommitUserName.set(extension.manifestCommitUserName)
            task.manifestCommitUserEmail.set(extension.manifestCommitUserEmail)
            task.gitExecutable.set(extension.gitExecutable)
            task.commandTimeoutSeconds.set(extension.commandTimeoutSeconds)
            task.failOnDirtyManifestRepository.set(extension.failOnDirtyManifestRepository)
            task.publishManifestRepository.set(extension.publishManifestRepository)
            task.pushManifestRepository.set(extension.pushManifestRepository)
            task.publishMetadataFile.set(manifestRepositoryMetadataOutputFile)
        }

        val validateTask = tasks.register("validateSwiftPmPackage", ValidateSwiftPmTask::class.java)
        validateTask.configure { task ->
            task.group = taskGroup
            task.description = "Validates the generated Package.swift with swift package commands."
            task.dependsOn(configurationValidationTask)
            task.dependsOn(manifestTask, publishTask)
            task.validatePackage.set(extension.validatePackage)
            task.swiftExecutable.set(extension.swiftExecutable)
            task.commandTimeoutSeconds.set(extension.commandTimeoutSeconds)
            task.manifestFile.set(manifestOutputFile)
            task.validationReportFile.set(validationReportOutputFile)
        }

        val metadataTask = tasks.register("writeApplePackageMetadata", WritePackageMetadataTask::class.java)
        metadataTask.configure { task ->
            task.group = taskGroup
            task.description = "Writes a machine-readable JSON summary of the generated package outputs."
            task.dependsOn(configurationValidationTask, manifestTask, publishTask, publishManifestRepositoryTask, validateTask)
            task.packageName.set(extension.packageName)
            task.packageVersion.set(extension.version)
            task.minimumIosVersion.set(extension.minimumIosVersion)
            task.minimumMacosVersion.set(extension.minimumMacosVersion)
            task.minimumTvosVersion.set(extension.minimumTvosVersion)
            task.minimumWatchosVersion.set(extension.minimumWatchosVersion)
            task.minimumVisionosVersion.set(extension.minimumVisionosVersion)
            task.minimumMacCatalystVersion.set(extension.minimumMacCatalystVersion)
            task.artifactUrlOverride.set(extension.artifactUrlOverride)
            task.githubRepo.set(extension.githubRepo)
            task.githubTag.set(extension.githubTag)
            task.archiveFileName.set(archiveFileName)
            task.checksumFile.set(checksumOutputFile)
            task.manifestFile.set(manifestOutputFile)
            task.publishMetadataFile.set(publishMetadataOutputFile)
            task.manifestRepositoryMetadataFile.set(manifestRepositoryMetadataOutputFile)
            task.validationReportFile.set(validationReportOutputFile)
            task.configurationValidationReportFile.set(configurationValidationReportOutputFile)
            task.metadataFile.set(metadataOutputFile)
        }

        val summaryTask = tasks.register("printApplePackageSummary", PrintReleaseSummaryTask::class.java)
        summaryTask.configure { task ->
            task.group = taskGroup
            task.description = "Prints a summary of the package version, checksum, manifest, metadata, and artifact URL."
            task.dependsOn(metadataTask)
            task.packageName.set(extension.packageName)
            task.packageVersion.set(extension.version)
            task.minimumIosVersion.set(extension.minimumIosVersion)
            task.minimumMacosVersion.set(extension.minimumMacosVersion)
            task.minimumTvosVersion.set(extension.minimumTvosVersion)
            task.minimumWatchosVersion.set(extension.minimumWatchosVersion)
            task.minimumVisionosVersion.set(extension.minimumVisionosVersion)
            task.minimumMacCatalystVersion.set(extension.minimumMacCatalystVersion)
            task.artifactUrlOverride.set(extension.artifactUrlOverride)
            task.githubRepo.set(extension.githubRepo)
            task.githubTag.set(extension.githubTag)
            task.minimumIosVersion.set(extension.minimumIosVersion)
            task.minimumMacosVersion.set(extension.minimumMacosVersion)
            task.minimumTvosVersion.set(extension.minimumTvosVersion)
            task.minimumWatchosVersion.set(extension.minimumWatchosVersion)
            task.minimumVisionosVersion.set(extension.minimumVisionosVersion)
            task.minimumMacCatalystVersion.set(extension.minimumMacCatalystVersion)
            task.archiveFileName.set(archiveFileName)
            task.checksumFile.set(checksumOutputFile)
            task.manifestFile.set(manifestOutputFile)
            task.publishMetadataFile.set(publishMetadataOutputFile)
            task.manifestRepositoryMetadataFile.set(manifestRepositoryMetadataOutputFile)
            task.validationReportFile.set(validationReportOutputFile)
            task.metadataFile.set(metadataOutputFile)
        }

        val publishApplePackageTask = tasks.register("publishApplePackage")
        publishApplePackageTask.configure { task ->
            task.group = taskGroup
            task.description = "Runs the full Apple package pipeline: validation, assemble, zip, checksum, publish, manifest generation, metadata, validation, and summary."
            task.dependsOn(summaryTask)
        }

        afterEvaluate {
            val moduleProject = project.project(extension.artifactModule.get())
            assembleTask.configure { task ->
                // Delay this lookup until the target module has finished configuring its
                // Kotlin-native tasks, otherwise Gradle may resolve the generated task too early.
                task.dependsOn(moduleProject.tasks.named(extension.assembleTaskName.get()))
            }
        }
    }
}
