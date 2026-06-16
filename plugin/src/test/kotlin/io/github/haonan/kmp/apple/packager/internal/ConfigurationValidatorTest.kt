package io.github.haonan.kmp.apple.packager.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigurationValidatorTest {
    @Test
    fun `accepts a valid release configuration`() {
        val result = ConfigurationValidator.validate(validSpec())

        assertTrue(result.errors.isEmpty())
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `requires one platform and one artifact url source`() {
        val result = ConfigurationValidator.validate(
            validSpec(
                minimumIosVersion = "",
                githubRepo = "",
                githubTag = "",
            )
        )

        assertEquals(
            listOf(
                "Configure at least one platform deployment target, such as minimumIosVersion.",
                "Configure artifactUrlOverride or provide both githubRepo and githubTag.",
            ),
            result.errors,
        )
    }

    @Test
    fun `rejects ambiguous manifest repository configuration`() {
        val result = ConfigurationValidator.validate(
            validSpec(
                publishManifestRepository = true,
                manifestRepository = "kairowan/shared-spm",
                manifestRepositoryPath = "/tmp/shared-spm",
            )
        )

        assertTrue(
            result.errors.contains(
                "Set exactly one of manifestRepository or manifestRepositoryPath when publishManifestRepository=true."
            )
        )
    }

    @Test
    fun `warns when release upload is enabled but manifest uses an override url`() {
        val result = ConfigurationValidator.validate(
            validSpec(
                publishRelease = true,
                artifactUrlOverride = "https://downloads.example.com/Shared.xcframework.zip",
            )
        )

        assertTrue(result.errors.isEmpty())
        assertEquals(
            listOf(
                "artifactUrlOverride is set, so the generated Package.swift will not point at the uploaded GitHub release asset."
            ),
            result.warnings,
        )
    }

    @Test
    fun `rejects path traversal in manifest subdirectory`() {
        val result = ConfigurationValidator.validate(
            validSpec(
                manifestRepositorySubdirectory = "../release",
            )
        )

        assertTrue(
            result.errors.contains(
                "manifestRepositorySubdirectory must not contain '..' path traversal segments."
            )
        )
    }

    @Test
    fun `rejects non-positive timeouts and negative retry counts`() {
        val result = ConfigurationValidator.validate(
            validSpec(
                commandTimeoutSeconds = 0,
                publishRelease = true,
                githubRequestTimeoutSeconds = 0,
                githubMaxRetries = -1,
            )
        )

        assertTrue(result.errors.contains("commandTimeoutSeconds must be greater than 0."))
        assertTrue(result.errors.contains("githubRequestTimeoutSeconds must be greater than 0."))
        assertTrue(result.errors.contains("githubMaxRetries must be 0 or greater."))
    }

    @Test
    fun `warns when dirty manifest repositories are allowed during push`() {
        val result = ConfigurationValidator.validate(
            validSpec(
                publishManifestRepository = true,
                pushManifestRepository = true,
                manifestRepositoryPath = "/tmp/shared-spm",
                failOnDirtyManifestRepository = false,
            )
        )

        assertTrue(result.errors.isEmpty())
        assertTrue(
            result.warnings.contains(
                "failOnDirtyManifestRepository=false allows pushing Package.swift from a checkout that may already contain unrelated local changes."
            )
        )
    }

    @Test
    fun `warns when manifest commit identity falls back to git config`() {
        val result = ConfigurationValidator.validate(
            validSpec(
                publishManifestRepository = true,
                manifestRepositoryPath = "/tmp/shared-spm",
                manifestCommitUserName = "",
                manifestCommitUserEmail = "",
            )
        )

        assertTrue(result.errors.isEmpty())
        assertTrue(
            result.warnings.contains(
                "manifestCommitUserName/manifestCommitUserEmail are not fully configured; the task will fall back to git user.name/user.email from the selected checkout."
            )
        )
    }

    @Test
    fun `rejects invalid artifact verification timeout and retry values`() {
        val result = ConfigurationValidator.validate(
            validSpec(
                verifyPublishedArtifact = true,
                artifactDownloadTimeoutSeconds = 0,
                artifactDownloadMaxRetries = -1,
            )
        )

        assertTrue(
            result.errors.contains(
                "artifactDownloadTimeoutSeconds must be greater than 0 when verifyPublishedArtifact=true."
            )
        )
        assertTrue(
            result.errors.contains(
                "artifactDownloadMaxRetries must be 0 or greater when verifyPublishedArtifact=true."
            )
        )
    }

    @Test
    fun `warns when post publish artifact verification is disabled`() {
        val result = ConfigurationValidator.validate(
            validSpec(
                publishRelease = true,
                verifyPublishedArtifact = false,
            )
        )

        assertTrue(result.errors.isEmpty())
        assertTrue(
            result.warnings.contains(
                "verifyPublishedArtifact=false disables the post-publish download and checksum verification step."
            )
        )
    }

    private fun validSpec(
        packageName: String = "Shared",
        packageVersion: String = "0.1.0",
        artifactUrlOverride: String? = null,
        githubRepo: String = "kairowan/shared-package",
        githubTag: String = "0.1.0",
        githubTokenConfigured: Boolean = true,
        manifestRepository: String? = null,
        manifestRepositoryPath: String? = null,
        manifestRepositoryBranch: String = "main",
        manifestRepositorySubdirectory: String = "swiftpm",
        manifestCommitUserName: String? = "CI Bot",
        manifestCommitUserEmail: String? = "ci@example.com",
        publishRelease: Boolean = false,
        publishManifestRepository: Boolean = false,
        pushManifestRepository: Boolean = false,
        validatePackage: Boolean = true,
        swiftExecutable: String = "swift",
        gitExecutable: String = "git",
        commandTimeoutSeconds: Int = 600,
        githubRequestTimeoutSeconds: Int = 120,
        githubMaxRetries: Int = 2,
        verifyPublishedArtifact: Boolean = true,
        artifactDownloadTimeoutSeconds: Int = 300,
        artifactDownloadMaxRetries: Int = 2,
        failOnDirtyManifestRepository: Boolean = true,
        minimumIosVersion: String = "16.0",
        minimumMacosVersion: String? = "",
        minimumTvosVersion: String? = "",
        minimumWatchosVersion: String? = "",
        minimumVisionosVersion: String? = "",
        minimumMacCatalystVersion: String? = "",
    ): ApplePackagerConfigurationSpec {
        return ApplePackagerConfigurationSpec(
            packageName = packageName,
            packageVersion = packageVersion,
            artifactUrlOverride = artifactUrlOverride,
            githubRepo = githubRepo,
            githubTag = githubTag,
            githubTokenConfigured = githubTokenConfigured,
            manifestRepository = manifestRepository,
            manifestRepositoryPath = manifestRepositoryPath,
            manifestRepositoryBranch = manifestRepositoryBranch,
            manifestRepositorySubdirectory = manifestRepositorySubdirectory,
            manifestCommitUserName = manifestCommitUserName,
            manifestCommitUserEmail = manifestCommitUserEmail,
            publishRelease = publishRelease,
            publishManifestRepository = publishManifestRepository,
            pushManifestRepository = pushManifestRepository,
            validatePackage = validatePackage,
            swiftExecutable = swiftExecutable,
            gitExecutable = gitExecutable,
            commandTimeoutSeconds = commandTimeoutSeconds,
            githubRequestTimeoutSeconds = githubRequestTimeoutSeconds,
            githubMaxRetries = githubMaxRetries,
            verifyPublishedArtifact = verifyPublishedArtifact,
            artifactDownloadTimeoutSeconds = artifactDownloadTimeoutSeconds,
            artifactDownloadMaxRetries = artifactDownloadMaxRetries,
            failOnDirtyManifestRepository = failOnDirtyManifestRepository,
            minimumIosVersion = minimumIosVersion,
            minimumMacosVersion = minimumMacosVersion,
            minimumTvosVersion = minimumTvosVersion,
            minimumWatchosVersion = minimumWatchosVersion,
            minimumVisionosVersion = minimumVisionosVersion,
            minimumMacCatalystVersion = minimumMacCatalystVersion,
        )
    }
}
