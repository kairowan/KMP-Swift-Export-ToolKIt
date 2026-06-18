package io.github.haonan.kmp.apple.packager.tasks

import io.github.haonan.kmp.apple.packager.internal.ApplePackagerConfigurationSpec
import io.github.haonan.kmp.apple.packager.internal.ApplePackagerHostEnvironment
import io.github.haonan.kmp.apple.packager.internal.CommandProbeResult
import io.github.haonan.kmp.apple.packager.internal.CommandAvailabilityProbe
import io.github.haonan.kmp.apple.packager.internal.ConfigurationValidator
import io.github.haonan.kmp.apple.packager.internal.EnvironmentValidator
import io.github.haonan.kmp.apple.packager.internal.merge
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

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
    abstract val githubServerUrl: Property<String>

    @get:Input
    abstract val githubApiUrl: Property<String>

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
    @get:Optional
    abstract val manifestCommitUserName: Property<String>

    @get:Input
    @get:Optional
    abstract val manifestCommitUserEmail: Property<String>

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
    abstract val overwriteExistingReleaseAsset: Property<Boolean>

    @get:Input
    abstract val verifyPublishedArtifact: Property<Boolean>

    @get:Input
    abstract val publishReleaseSupportAssets: Property<Boolean>

    @get:Input
    abstract val artifactDownloadTimeoutSeconds: Property<Int>

    @get:Input
    abstract val artifactDownloadMaxRetries: Property<Int>

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

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun validateConfiguration() {
        val reportFile = validationReportFile.get().asFile
        reportFile.parentFile.mkdirs()

        val configurationSpec = ApplePackagerConfigurationSpec(
            packageName = packageName.get(),
            packageVersion = packageVersion.get(),
            artifactUrlOverride = artifactUrlOverride.orNull,
            githubServerUrl = githubServerUrl.get(),
            githubApiUrl = githubApiUrl.get(),
            githubRepo = githubRepo.orNull,
            githubTag = githubTag.orNull,
            githubTokenConfigured = githubTokenConfigured.get(),
            manifestRepository = manifestRepository.orNull,
            manifestRepositoryPath = manifestRepositoryPath.orNull,
            manifestRepositoryBranch = manifestRepositoryBranch.get(),
            manifestRepositorySubdirectory = manifestRepositorySubdirectory.get(),
            manifestCommitUserName = manifestCommitUserName.orNull,
            manifestCommitUserEmail = manifestCommitUserEmail.orNull,
            publishRelease = publishRelease.get(),
            publishManifestRepository = publishManifestRepository.get(),
            pushManifestRepository = pushManifestRepository.get(),
            validatePackage = validatePackage.get(),
            swiftExecutable = swiftExecutable.get(),
            gitExecutable = gitExecutable.get(),
            commandTimeoutSeconds = commandTimeoutSeconds.get(),
            githubRequestTimeoutSeconds = githubRequestTimeoutSeconds.get(),
            githubMaxRetries = githubMaxRetries.get(),
            overwriteExistingReleaseAsset = overwriteExistingReleaseAsset.get(),
            verifyPublishedArtifact = verifyPublishedArtifact.get(),
            publishReleaseSupportAssets = publishReleaseSupportAssets.get(),
            artifactDownloadTimeoutSeconds = artifactDownloadTimeoutSeconds.get(),
            artifactDownloadMaxRetries = artifactDownloadMaxRetries.get(),
            failOnDirtyManifestRepository = failOnDirtyManifestRepository.get(),
            minimumIosVersion = minimumIosVersion.orNull,
            minimumMacosVersion = minimumMacosVersion.orNull,
            minimumTvosVersion = minimumTvosVersion.orNull,
            minimumWatchosVersion = minimumWatchosVersion.orNull,
            minimumVisionosVersion = minimumVisionosVersion.orNull,
            minimumMacCatalystVersion = minimumMacCatalystVersion.orNull,
        )
        val validatedTimeout = maxOf(commandTimeoutSeconds.get(), 1)
        val environment = ApplePackagerHostEnvironment(
            operatingSystemName = System.getProperty("os.name").orEmpty(),
            swift = CommandAvailabilityProbe.probe(
                execOperations = execOperations,
                executable = swiftExecutable.get(),
                arguments = listOf("--version"),
                commandTimeoutSeconds = validatedTimeout,
            ),
            git = CommandAvailabilityProbe.probe(
                execOperations = execOperations,
                executable = gitExecutable.get(),
                arguments = listOf("--version"),
                commandTimeoutSeconds = validatedTimeout,
            ),
            xcodebuild = CommandAvailabilityProbe.probe(
                execOperations = execOperations,
                executable = "xcodebuild",
                arguments = listOf("-version"),
                commandTimeoutSeconds = validatedTimeout,
            ),
            ditto = CommandAvailabilityProbe.probe(
                execOperations = execOperations,
                executable = "xcrun",
                arguments = listOf("--find", "ditto"),
                commandTimeoutSeconds = validatedTimeout,
            ).asExecutable("ditto"),
        )

        val configurationResult = ConfigurationValidator.validate(configurationSpec)
        val environmentResult = EnvironmentValidator.validate(configurationSpec, environment)
        val result = configurationResult.merge(environmentResult)

        reportFile.writeText(
            buildString {
                appendLine("status=${if (result.errors.isEmpty()) "valid" else "invalid"}")
                appendLine("hostOperatingSystem=${environment.operatingSystemName.toPropertyValue()}")
                appendLine("errorCount=${result.errors.size}")
                appendLine("warningCount=${result.warnings.size}")
                appendLine("swiftStatus=${environment.swift.toStatusValue()}")
                appendLine("swiftDetail=${environment.swift.toDetailValue()}")
                appendLine("gitStatus=${environment.git.toStatusValue()}")
                appendLine("gitDetail=${environment.git.toDetailValue()}")
                appendLine("xcodebuildStatus=${environment.xcodebuild.toStatusValue()}")
                appendLine("xcodebuildDetail=${environment.xcodebuild.toDetailValue()}")
                appendLine("dittoStatus=${environment.ditto.toStatusValue()}")
                appendLine("dittoDetail=${environment.ditto.toDetailValue()}")
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

    private fun String.toPropertyValue(): String {
        return replace("\r", " ").replace("\n", " ").trim()
    }

    private fun CommandProbeResult.toStatusValue(): String {
        return if (available) "available" else "unavailable"
    }

    private fun CommandProbeResult.toDetailValue(): String {
        return (output ?: failureMessage).orEmpty().toPropertyValue()
    }

    private fun CommandProbeResult.asExecutable(executable: String): CommandProbeResult {
        return copy(executable = executable)
    }
}
