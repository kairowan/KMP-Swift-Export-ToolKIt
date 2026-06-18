package io.github.haonan.kmp.apple.packager.internal

import kotlin.test.Test
import kotlin.test.assertEquals

class EnvironmentValidationRequirementsResolverTest {
    @Test
    fun `generateAppleLocalPackageManifest keeps host requirements disabled`() {
        val result = EnvironmentValidationRequirementsResolver.resolve(
            requestedTaskPaths = listOf(":generateAppleLocalPackageManifest"),
            scheduledTaskPaths = listOf(
                ":validateApplePackagerConfiguration",
                ":assembleAppleXCFramework",
                ":generateAppleLocalPackageManifest",
            ),
            publishManifestRepository = false,
        )

        assertEquals(EnvironmentValidationRequirements.None, result)
    }

    @Test
    fun `publishApplePackage enables release and git requirements when manifest publishing is enabled`() {
        val result = EnvironmentValidationRequirementsResolver.resolve(
            requestedTaskPaths = listOf(":publishApplePackage"),
            scheduledTaskPaths = listOf(
                ":validateApplePackagerConfiguration",
                ":zipAppleArtifact",
                ":computeApplePackageChecksum",
                ":publishPackageManifestRepository",
                ":publishApplePackage",
            ),
            publishManifestRepository = true,
        )

        assertEquals(
            EnvironmentValidationRequirements(
                requireMacOs = true,
                requireSwift = true,
                requireGit = true,
                requireXcodebuild = true,
                requireDitto = true,
            ),
            result,
        )
    }

    @Test
    fun `explicit validation task keeps full release diagnostics`() {
        val result = EnvironmentValidationRequirementsResolver.resolve(
            requestedTaskPaths = listOf(":validateApplePackagerConfiguration"),
            scheduledTaskPaths = listOf(":validateApplePackagerConfiguration"),
            publishManifestRepository = false,
        )

        assertEquals(
            EnvironmentValidationRequirements(
                requireMacOs = true,
                requireSwift = true,
                requireGit = false,
                requireXcodebuild = true,
                requireDitto = true,
            ),
            result,
        )
    }
}
