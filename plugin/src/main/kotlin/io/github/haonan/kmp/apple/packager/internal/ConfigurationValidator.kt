package io.github.haonan.kmp.apple.packager.internal

import java.net.URI

/**
 * Captures the configuration surface that should be validated before running expensive release work.
 *
 * Author: kairowan
 */
internal data class ApplePackagerConfigurationSpec(
    val packageName: String,
    val packageVersion: String,
    val artifactUrlOverride: String?,
    val githubRepo: String?,
    val githubTag: String?,
    val githubTokenConfigured: Boolean,
    val manifestRepository: String?,
    val manifestRepositoryPath: String?,
    val manifestRepositoryBranch: String,
    val manifestRepositorySubdirectory: String,
    val manifestCommitUserName: String?,
    val manifestCommitUserEmail: String?,
    val publishRelease: Boolean,
    val publishManifestRepository: Boolean,
    val pushManifestRepository: Boolean,
    val validatePackage: Boolean,
    val swiftExecutable: String,
    val gitExecutable: String,
    val commandTimeoutSeconds: Int,
    val githubRequestTimeoutSeconds: Int,
    val githubMaxRetries: Int,
    val verifyPublishedArtifact: Boolean,
    val artifactDownloadTimeoutSeconds: Int,
    val artifactDownloadMaxRetries: Int,
    val failOnDirtyManifestRepository: Boolean,
    val minimumIosVersion: String?,
    val minimumMacosVersion: String?,
    val minimumTvosVersion: String?,
    val minimumWatchosVersion: String?,
    val minimumVisionosVersion: String?,
    val minimumMacCatalystVersion: String?,
)

/**
 * Represents the outcome of validating a package configuration for release.
 *
 * Author: kairowan
 */
internal data class ConfigurationValidationResult(
    val errors: List<String>,
    val warnings: List<String>,
)

/**
 * Performs fail-fast validation for the user-facing DSL so CI fails early and predictably.
 *
 * Author: kairowan
 */
internal object ConfigurationValidator {
    private val githubRepoPattern = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

    fun validate(spec: ApplePackagerConfigurationSpec): ConfigurationValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val packageName = spec.packageName.trim()
        if (packageName.isEmpty()) {
            errors += "packageName must not be blank."
        }
        if (packageName.contains('\n') || packageName.contains('\r')) {
            errors += "packageName must not contain line breaks."
        }

        if (spec.packageVersion.trim().isEmpty()) {
            errors += "version must not be blank."
        }

        if (!hasAtLeastOnePlatform(spec)) {
            errors += "Configure at least one platform deployment target, such as minimumIosVersion."
        }

        val artifactUrlOverride = spec.artifactUrlOverride.normalized()
        val githubRepo = spec.githubRepo.normalized()
        val githubTag = spec.githubTag.normalized()
        val manifestRepository = spec.manifestRepository.normalized()
        val manifestRepositoryPath = spec.manifestRepositoryPath.normalized()
        val manifestRepositoryBranch = spec.manifestRepositoryBranch.trim()
        val manifestRepositorySubdirectory = spec.manifestRepositorySubdirectory.trim()
        val manifestCommitUserName = spec.manifestCommitUserName.normalized()
        val manifestCommitUserEmail = spec.manifestCommitUserEmail.normalized()
        val swiftExecutable = spec.swiftExecutable.trim()
        val gitExecutable = spec.gitExecutable.trim()

        if (artifactUrlOverride.isEmpty() && (githubRepo.isEmpty() || githubTag.isEmpty())) {
            errors += "Configure artifactUrlOverride or provide both githubRepo and githubTag."
        }

        if (artifactUrlOverride.isNotEmpty() && !isSupportedArtifactUrl(artifactUrlOverride)) {
            errors += "artifactUrlOverride must be an absolute http or https URL."
        }

        if (githubRepo.isNotEmpty() && !githubRepoPattern.matches(githubRepo)) {
            errors += "githubRepo must use the owner/repository format expected by GitHub."
        }

        if (githubTag.contains('\n') || githubTag.contains('\r')) {
            errors += "githubTag must not contain line breaks."
        }

        if (spec.publishRelease) {
            if (githubRepo.isEmpty()) {
                errors += "githubRepo must be configured when publishRelease=true."
            }
            if (githubTag.isEmpty()) {
                errors += "githubTag must be configured when publishRelease=true."
            }
            if (!spec.githubTokenConfigured) {
                errors += "GITHUB_TOKEN must be available when publishRelease=true."
            }
            if (spec.githubRequestTimeoutSeconds <= 0) {
                errors += "githubRequestTimeoutSeconds must be greater than 0."
            }
            if (spec.githubMaxRetries < 0) {
                errors += "githubMaxRetries must be 0 or greater."
            }
            if (!spec.verifyPublishedArtifact) {
                warnings += "verifyPublishedArtifact=false disables the post-publish download and checksum verification step."
            }
            if (artifactUrlOverride.isNotEmpty()) {
                warnings += "artifactUrlOverride is set, so the generated Package.swift will not point at the uploaded GitHub release asset."
            }
        }

        if (spec.pushManifestRepository && !spec.publishManifestRepository) {
            errors += "pushManifestRepository=true requires publishManifestRepository=true."
        }

        if (spec.publishManifestRepository) {
            if (manifestRepository.isEmpty() == manifestRepositoryPath.isEmpty()) {
                errors += "Set exactly one of manifestRepository or manifestRepositoryPath when publishManifestRepository=true."
            }
            if (manifestRepositoryBranch.isEmpty()) {
                errors += "manifestRepositoryBranch must not be blank when publishManifestRepository=true."
            }
            if (gitExecutable.isEmpty()) {
                errors += "gitExecutable must not be blank when publishManifestRepository=true."
            }
            if (spec.pushManifestRepository && !spec.failOnDirtyManifestRepository) {
                warnings += "failOnDirtyManifestRepository=false allows pushing Package.swift from a checkout that may already contain unrelated local changes."
            }
            if (manifestCommitUserName.isEmpty() || manifestCommitUserEmail.isEmpty()) {
                warnings += "manifestCommitUserName/manifestCommitUserEmail are not fully configured; the task will fall back to git user.name/user.email from the selected checkout."
            }
        }

        if (containsPathTraversal(manifestRepositorySubdirectory)) {
            errors += "manifestRepositorySubdirectory must not contain '..' path traversal segments."
        }

        if (spec.validatePackage && swiftExecutable.isEmpty()) {
            errors += "swiftExecutable must not be blank when validatePackage=true."
        }

        if (spec.commandTimeoutSeconds <= 0) {
            errors += "commandTimeoutSeconds must be greater than 0."
        }
        if (spec.verifyPublishedArtifact && spec.artifactDownloadTimeoutSeconds <= 0) {
            errors += "artifactDownloadTimeoutSeconds must be greater than 0 when verifyPublishedArtifact=true."
        }
        if (spec.verifyPublishedArtifact && spec.artifactDownloadMaxRetries < 0) {
            errors += "artifactDownloadMaxRetries must be 0 or greater when verifyPublishedArtifact=true."
        }

        return ConfigurationValidationResult(
            errors = errors,
            warnings = warnings,
        )
    }

    private fun hasAtLeastOnePlatform(spec: ApplePackagerConfigurationSpec): Boolean {
        return listOf(
            spec.minimumIosVersion,
            spec.minimumMacosVersion,
            spec.minimumTvosVersion,
            spec.minimumWatchosVersion,
            spec.minimumVisionosVersion,
            spec.minimumMacCatalystVersion,
        ).any { value -> value.normalized().isNotEmpty() }
    }

    private fun String?.normalized(): String = this?.trim().orEmpty()

    private fun isSupportedArtifactUrl(url: String): Boolean {
        return runCatching {
            val uri = URI(url)
            uri.isAbsolute && (uri.scheme == "http" || uri.scheme == "https")
        }.getOrDefault(false)
    }

    private fun containsPathTraversal(path: String): Boolean {
        return path.split('/', '\\').any { segment -> segment == ".." }
    }
}
