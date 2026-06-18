package io.github.haonan.kmp.apple.packager.internal

import kotlin.test.Test
import kotlin.test.assertTrue

class EnvironmentValidatorTest {
    @Test
    fun `accepts a healthy macOS toolchain`() {
        val result = EnvironmentValidator.validate(
            spec = validSpec(),
            environment = validEnvironment(),
        )

        assertTrue(result.errors.isEmpty())
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `rejects non macos hosts`() {
        val result = EnvironmentValidator.validate(
            spec = validSpec(),
            environment = validEnvironment(
                operatingSystemName = "Linux",
            ),
        )

        assertTrue(
            result.errors.contains(
                "KMP Apple Packager release tasks currently require macOS because XCFramework assembly, xcodebuild, and ditto-based archiving depend on Apple tooling."
            )
        )
    }

    @Test
    fun `rejects missing swift toolchain`() {
        val result = EnvironmentValidator.validate(
            spec = validSpec(swiftExecutable = "/missing/swift"),
            environment = validEnvironment(
                swift = unavailableProbe("/missing/swift"),
            ),
        )

        assertTrue(
            result.errors.contains(
                "swiftExecutable='/missing/swift' is not available. Install Swift or configure kmpApplePackager.swiftExecutable to point at a working toolchain."
            )
        )
    }

    @Test
    fun `requires git only when manifest publishing is enabled`() {
        val result = EnvironmentValidator.validate(
            spec = validSpec(
                publishManifestRepository = true,
                gitExecutable = "/missing/git",
            ),
            environment = validEnvironment(
                git = unavailableProbe("/missing/git"),
            ),
        )

        assertTrue(
            result.errors.contains(
                "gitExecutable='/missing/git' is not available. Install git or configure kmpApplePackager.gitExecutable when publishManifestRepository=true."
            )
        )
    }

    @Test
    fun `rejects missing xcodebuild and ditto on macos`() {
        val result = EnvironmentValidator.validate(
            spec = validSpec(),
            environment = validEnvironment(
                xcodebuild = unavailableProbe("xcodebuild"),
                ditto = unavailableProbe("ditto"),
            ),
        )

        assertTrue(
            result.errors.contains(
                "xcodebuild is not available. Install Xcode or Xcode command line tools before packaging Apple frameworks."
            )
        )
        assertTrue(
            result.errors.contains(
                "ditto is not available. Install Xcode command line tools on macOS to create SwiftPM-compatible XCFramework archives."
            )
        )
    }

    private fun validSpec(
        swiftExecutable: String = "swift",
        gitExecutable: String = "git",
        publishManifestRepository: Boolean = false,
    ): ApplePackagerConfigurationSpec {
        return ApplePackagerConfigurationSpec(
            packageName = "Shared",
            packageVersion = "1.0.0",
            artifactUrlOverride = "https://example.com/releases/Shared.zip",
            githubServerUrl = "https://github.com",
            githubApiUrl = "https://api.github.com",
            githubRepo = "kairowan/shared-package",
            githubTag = "1.0.0",
            githubTokenConfigured = true,
            manifestRepository = null,
            manifestRepositoryPath = null,
            manifestRepositoryBranch = "main",
            manifestRepositorySubdirectory = "",
            manifestCommitUserName = "CI Bot",
            manifestCommitUserEmail = "ci@example.com",
            publishRelease = false,
            publishManifestRepository = publishManifestRepository,
            pushManifestRepository = false,
            validatePackage = true,
            swiftExecutable = swiftExecutable,
            gitExecutable = gitExecutable,
            commandTimeoutSeconds = 600,
            githubRequestTimeoutSeconds = 120,
            githubMaxRetries = 2,
            overwriteExistingReleaseAsset = false,
            verifyPublishedArtifact = true,
            publishReleaseSupportAssets = true,
            artifactDownloadTimeoutSeconds = 300,
            artifactDownloadMaxRetries = 2,
            failOnDirtyManifestRepository = true,
            minimumIosVersion = "16.0",
            minimumMacosVersion = "",
            minimumTvosVersion = "",
            minimumWatchosVersion = "",
            minimumVisionosVersion = "",
            minimumMacCatalystVersion = "",
        )
    }

    private fun validEnvironment(
        operatingSystemName: String = "Mac OS X",
        swift: CommandProbeResult = availableProbe("swift", "Apple Swift version 6.2.3"),
        git: CommandProbeResult = availableProbe("git", "git version 2.50.1"),
        xcodebuild: CommandProbeResult = availableProbe("xcodebuild", "Xcode 26.2"),
        ditto: CommandProbeResult = availableProbe("ditto", "usage: ditto"),
    ): ApplePackagerHostEnvironment {
        return ApplePackagerHostEnvironment(
            operatingSystemName = operatingSystemName,
            swift = swift,
            git = git,
            xcodebuild = xcodebuild,
            ditto = ditto,
        )
    }

    private fun availableProbe(
        executable: String,
        output: String,
    ): CommandProbeResult {
        return CommandProbeResult(
            executable = executable,
            available = true,
            output = output,
            failureMessage = null,
        )
    }

    private fun unavailableProbe(executable: String): CommandProbeResult {
        return CommandProbeResult(
            executable = executable,
            available = false,
            output = null,
            failureMessage = "Failed to start command: $executable --version",
        )
    }
}
