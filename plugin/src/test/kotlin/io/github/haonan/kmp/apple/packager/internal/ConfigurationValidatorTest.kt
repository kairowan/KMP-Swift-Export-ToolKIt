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
        publishRelease: Boolean = false,
        publishManifestRepository: Boolean = false,
        pushManifestRepository: Boolean = false,
        validatePackage: Boolean = true,
        swiftExecutable: String = "swift",
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
            publishRelease = publishRelease,
            publishManifestRepository = publishManifestRepository,
            pushManifestRepository = pushManifestRepository,
            validatePackage = validatePackage,
            swiftExecutable = swiftExecutable,
            minimumIosVersion = minimumIosVersion,
            minimumMacosVersion = minimumMacosVersion,
            minimumTvosVersion = minimumTvosVersion,
            minimumWatchosVersion = minimumWatchosVersion,
            minimumVisionosVersion = minimumVisionosVersion,
            minimumMacCatalystVersion = minimumMacCatalystVersion,
        )
    }
}
