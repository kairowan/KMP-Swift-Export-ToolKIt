package io.github.haonan.kmp.apple.packager.internal

/**
 * Describes which parts of the host toolchain must be available for the current task graph.
 *
 * Author: kairowan
 */
internal data class EnvironmentValidationRequirements(
    val requireMacOs: Boolean,
    val requireSwift: Boolean,
    val requireGit: Boolean,
    val requireXcodebuild: Boolean,
    val requireDitto: Boolean,
) {
    companion object {
        val None = EnvironmentValidationRequirements(
            requireMacOs = false,
            requireSwift = false,
            requireGit = false,
            requireXcodebuild = false,
            requireDitto = false,
        )
    }
}

/**
 * Resolves host-environment requirements from the current task selection so local-only tasks
 * can stay cross-platform while release tasks still fail fast with actionable diagnostics.
 *
 * Author: kairowan
 */
internal object EnvironmentValidationRequirementsResolver {
    private const val VALIDATION_TASK_NAME = "validateApplePackagerConfiguration"
    private const val ZIP_TASK_NAME = "zipAppleArtifact"
    private const val CHECKSUM_TASK_NAME = "computeApplePackageChecksum"
    private const val VALIDATE_SWIFTPM_TASK_NAME = "validateSwiftPmPackage"
    private const val VERIFY_PUBLISHED_ARTIFACT_TASK_NAME = "verifyPublishedArtifact"
    private const val PUBLISH_MANIFEST_REPOSITORY_TASK_NAME = "publishPackageManifestRepository"

    fun resolve(
        requestedTaskPaths: List<String>,
        scheduledTaskPaths: Collection<String>,
        publishManifestRepository: Boolean,
    ): EnvironmentValidationRequirements {
        val requestedTaskNames = requestedTaskPaths.map(::taskName).toSet()
        val scheduledTaskNames = scheduledTaskPaths.map(::taskName).toSet()

        val explicitValidationRequested = VALIDATION_TASK_NAME in requestedTaskNames
        val requireReleaseHost = explicitValidationRequested || ZIP_TASK_NAME in scheduledTaskNames
        val requireSwift = explicitValidationRequested ||
            CHECKSUM_TASK_NAME in scheduledTaskNames ||
            VALIDATE_SWIFTPM_TASK_NAME in scheduledTaskNames ||
            VERIFY_PUBLISHED_ARTIFACT_TASK_NAME in scheduledTaskNames
        val requireGit = publishManifestRepository &&
            PUBLISH_MANIFEST_REPOSITORY_TASK_NAME in scheduledTaskNames

        return EnvironmentValidationRequirements(
            requireMacOs = requireReleaseHost,
            requireSwift = requireSwift,
            requireGit = requireGit,
            requireXcodebuild = requireReleaseHost,
            requireDitto = requireReleaseHost,
        )
    }

    private fun taskName(taskPath: String): String {
        return taskPath.substringAfterLast(':').ifBlank { taskPath }
    }
}
