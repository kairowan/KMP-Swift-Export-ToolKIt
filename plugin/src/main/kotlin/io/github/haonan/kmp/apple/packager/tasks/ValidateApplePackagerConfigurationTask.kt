package io.github.haonan.kmp.apple.packager.tasks

import io.github.haonan.kmp.apple.packager.internal.ApplePackagerConfigurationSpec
import io.github.haonan.kmp.apple.packager.internal.ConfigurationValidator
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Validates user configuration and writes a report consumed by downstream release tasks.")
/**
 * Fails fast when the release configuration is incomplete or internally inconsistent.
 *
 * Author: kairowan
 */
abstract class ValidateApplePackagerConfigurationTask : DefaultTask() {
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
    abstract val githubTokenConfigured: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val manifestRepository: Property<String>

    @get:Input
    @get:Optional
    abstract val manifestRepositoryPath: Property<String>

    @get:Input
    abstract val manifestRepositoryBranch: Property<String>

    @get:Input
    abstract val manifestRepositorySubdirectory: Property<String>

    @get:Input
    abstract val publishRelease: Property<Boolean>

    @get:Input
    abstract val publishManifestRepository: Property<Boolean>

    @get:Input
    abstract val pushManifestRepository: Property<Boolean>

    @get:Input
    abstract val validatePackage: Property<Boolean>

    @get:Input
    abstract val swiftExecutable: Property<String>

    @get:Input
    abstract val gitExecutable: Property<String>

    @get:Input
    abstract val commandTimeoutSeconds: Property<Int>

    @get:Input
    abstract val githubRequestTimeoutSeconds: Property<Int>

    @get:Input
    abstract val githubMaxRetries: Property<Int>

    @get:Input
    abstract val failOnDirtyManifestRepository: Property<Boolean>

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

    @get:OutputFile
    abstract val validationReportFile: RegularFileProperty

    @TaskAction
    fun validateConfiguration() {
        val reportFile = validationReportFile.get().asFile
        reportFile.parentFile.mkdirs()

        val result = ConfigurationValidator.validate(
            ApplePackagerConfigurationSpec(
                packageName = packageName.get(),
                packageVersion = packageVersion.get(),
                artifactUrlOverride = artifactUrlOverride.orNull,
                githubRepo = githubRepo.orNull,
                githubTag = githubTag.orNull,
                githubTokenConfigured = githubTokenConfigured.get(),
                manifestRepository = manifestRepository.orNull,
                manifestRepositoryPath = manifestRepositoryPath.orNull,
                manifestRepositoryBranch = manifestRepositoryBranch.get(),
                manifestRepositorySubdirectory = manifestRepositorySubdirectory.get(),
                publishRelease = publishRelease.get(),
                publishManifestRepository = publishManifestRepository.get(),
                pushManifestRepository = pushManifestRepository.get(),
                validatePackage = validatePackage.get(),
                swiftExecutable = swiftExecutable.get(),
                gitExecutable = gitExecutable.get(),
                commandTimeoutSeconds = commandTimeoutSeconds.get(),
                githubRequestTimeoutSeconds = githubRequestTimeoutSeconds.get(),
                githubMaxRetries = githubMaxRetries.get(),
                failOnDirtyManifestRepository = failOnDirtyManifestRepository.get(),
                minimumIosVersion = minimumIosVersion.orNull,
                minimumMacosVersion = minimumMacosVersion.orNull,
                minimumTvosVersion = minimumTvosVersion.orNull,
                minimumWatchosVersion = minimumWatchosVersion.orNull,
                minimumVisionosVersion = minimumVisionosVersion.orNull,
                minimumMacCatalystVersion = minimumMacCatalystVersion.orNull,
            )
        )

        reportFile.writeText(
            buildString {
                appendLine("status=${if (result.errors.isEmpty()) "valid" else "invalid"}")
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
            logger.warn("KMP Apple Packager configuration warning: $warning")
        }

        if (result.errors.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("KMP Apple Packager configuration is invalid:")
                    result.errors.forEach { error ->
                        appendLine("- $error")
                    }
                }.trimEnd()
            )
        }

        logger.lifecycle("Validated KMP Apple Packager configuration.")
    }
}
