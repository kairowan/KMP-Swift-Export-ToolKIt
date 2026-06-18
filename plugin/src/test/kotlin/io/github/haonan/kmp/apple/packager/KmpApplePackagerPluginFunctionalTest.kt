package io.github.haonan.kmp.apple.packager

import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Exercises the plugin through Gradle TestKit so task wiring and generated outputs stay stable.
 *
 * Author: kairowan
 */
class KmpApplePackagerPluginFunctionalTest {
    @TempDir
    lateinit var testProjectDir: Path

    @Test
    fun `generateAppleLocalPackageManifest writes a path-based consumer manifest`() {
        writeFixtureProject(
            rootBuildScript = """
                plugins {
                    id("io.github.haonan.kmp.apple.packager")
                }

                kmpApplePackager {
                    packageName.set("Shared")
                    version.set("1.2.3")
                    artifactModule.set(":shared")
                    assembleTaskName.set("assembleFakeReleaseXCFramework")
                    artifactUrlOverride.set("https://example.com/releases/download/1.2.3/Shared-1.2.3.xcframework.zip")
                    publishRelease.set(false)
                    publishManifestRepository.set(false)
                    pushManifestRepository.set(false)
                    validatePackage.set(false)
                    verifyPublishedArtifact.set(false)
                    minimumIosVersion.set("16.0")
                }
            """.trimIndent()
        )

        val result = runner("generateAppleLocalPackageManifest", "--stacktrace").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateAppleLocalPackageManifest")?.outcome)

        val manifestFile = testProjectDir.resolve("build/kmpApplePackager/localPackage/Package.swift")
        assertTrue(manifestFile.exists(), "Expected local Package.swift to be generated.")

        val manifest = manifestFile.readText()
        assertTrue(manifest.contains("path: \"../xcframework/Shared.xcframework\""))
        assertTrue(manifest.contains(".iOS(\"16.0\")"))
        assertTrue(!manifest.contains("checksum:"))
        assertTrue(!manifest.contains("url:"))
    }

    @Test
    fun `publishApplePackage dry run emits local manifest and metadata`() {
        assumeTrue(isMacOs(), "The full dry-run pipeline relies on macOS tools such as ditto and swift.")

        writeFixtureProject(
            rootBuildScript = """
                plugins {
                    id("io.github.haonan.kmp.apple.packager")
                }

                kmpApplePackager {
                    packageName.set("Shared")
                    version.set("1.2.3")
                    artifactModule.set(":shared")
                    assembleTaskName.set("assembleFakeReleaseXCFramework")
                    artifactUrlOverride.set("https://example.com/releases/download/1.2.3/Shared-1.2.3.xcframework.zip")
                    publishRelease.set(false)
                    publishManifestRepository.set(false)
                    pushManifestRepository.set(false)
                    validatePackage.set(false)
                    verifyPublishedArtifact.set(false)
                    minimumIosVersion.set("16.0")
                }
            """.trimIndent()
        )

        val result = runner("publishApplePackage", "--stacktrace").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":publishApplePackage")?.outcome)
        assertTrue(result.output.contains("localManifest:"), "Expected summary output to mention the local manifest.")

        val remoteManifest = testProjectDir.resolve("build/kmpApplePackager/package/Package.swift")
        val localManifest = testProjectDir.resolve("build/kmpApplePackager/localPackage/Package.swift")
        val metadataFile = testProjectDir.resolve("build/kmpApplePackager/metadata/package-metadata.json")
        val publishReport = testProjectDir.resolve("build/kmpApplePackager/release/publish.properties")
        val supportAssetsReport = testProjectDir.resolve("build/kmpApplePackager/release/support-assets.properties")
        val supportAssetsDirectory = testProjectDir.resolve("build/kmpApplePackager/release/assets")
        val releaseBundleDirectory = testProjectDir.resolve(
            "build/kmpApplePackager/release/bundle/Shared-1.2.3-release-bundle"
        )
        val releaseBundleArchive = testProjectDir.resolve(
            "build/kmpApplePackager/release/bundle/Shared-1.2.3-release-bundle.zip"
        )
        val releaseBundleReport = testProjectDir.resolve("build/kmpApplePackager/release/bundle/report.properties")
        val verificationReport = testProjectDir.resolve("build/kmpApplePackager/artifactVerification/report.properties")

        assertTrue(remoteManifest.exists(), "Expected release Package.swift to be generated.")
        assertTrue(localManifest.exists(), "Expected local Package.swift to be generated.")
        assertTrue(metadataFile.exists(), "Expected package metadata JSON to be generated.")
        assertTrue(publishReport.exists(), "Expected release publish report to be generated.")
        assertTrue(supportAssetsReport.exists(), "Expected release support asset report to be generated.")
        assertTrue(supportAssetsDirectory.exists(), "Expected release support asset directory to be generated.")
        assertTrue(releaseBundleDirectory.exists(), "Expected release bundle directory to be generated.")
        assertTrue(releaseBundleArchive.exists(), "Expected release bundle archive to be generated.")
        assertTrue(releaseBundleReport.exists(), "Expected release bundle report to be generated.")
        assertTrue(verificationReport.exists(), "Expected artifact verification report to be generated.")
        assertTrue(
            supportAssetsDirectory.resolve("Shared-1.2.3.Package.swift").exists(),
            "Expected staged manifest support asset to be generated.",
        )
        assertTrue(
            supportAssetsDirectory.resolve("Shared-1.2.3.sha256").exists(),
            "Expected staged checksum support asset to be generated.",
        )
        assertTrue(
            supportAssetsDirectory.resolve("Shared-1.2.3.package-metadata.json").exists(),
            "Expected staged metadata support asset to be generated.",
        )
        assertTrue(
            releaseBundleDirectory.resolve("bundle-manifest.json").exists(),
            "Expected release bundle manifest to be generated.",
        )
        assertTrue(
            releaseBundleDirectory.resolve("artifact/Shared-1.2.3.xcframework.zip").exists(),
            "Expected release bundle archive copy to be generated.",
        )

        assertTrue(remoteManifest.readText().contains("url: \"https://example.com/releases/download/1.2.3/Shared-1.2.3.xcframework.zip\""))
        assertTrue(localManifest.readText().contains("path: \"../xcframework/Shared.xcframework\""))

        val metadata = metadataFile.readText()
        assertTrue(metadata.contains("\"localPath\""))
        assertTrue(metadata.contains("\"downloadUrl\" : \"https://example.com/releases/download/1.2.3/Shared-1.2.3.xcframework.zip\""))
        assertTrue(metadata.contains("\"environment\""))
        assertTrue(metadata.contains("\"xcodebuild\""))
        assertTrue(metadata.contains("\"releaseSupportAssets\""))
        assertTrue(metadata.contains("\"releaseBundle\""))
        assertTrue(metadata.contains("\"status\" : \"skipped\""))
        assertTrue(metadata.contains("\"status\" : \"assembled\""))

        assertTrue(publishReport.readText().contains("published=false"))
        assertTrue(supportAssetsReport.readText().contains("asset0LocalPath="))
        assertTrue(releaseBundleReport.readText().contains("status=assembled"))
        assertTrue(supportAssetsReport.readText().contains("reason=publishReleaseDisabled"))
        assertTrue(verificationReport.readText().contains("status=skipped"))
    }

    @Test
    fun `publishApplePackage dry run uses configured github enterprise urls`() {
        assumeTrue(isMacOs(), "The full dry-run pipeline relies on macOS tools such as ditto and swift.")

        writeFixtureProject(
            rootBuildScript = """
                plugins {
                    id("io.github.haonan.kmp.apple.packager")
                }

                kmpApplePackager {
                    packageName.set("Shared")
                    version.set("1.2.3")
                    artifactModule.set(":shared")
                    assembleTaskName.set("assembleFakeReleaseXCFramework")
                    githubServerUrl.set("https://github.example.com")
                    githubRepo.set("team/shared-package")
                    publishRelease.set(false)
                    publishManifestRepository.set(false)
                    pushManifestRepository.set(false)
                    validatePackage.set(false)
                    verifyPublishedArtifact.set(false)
                    minimumIosVersion.set("16.0")
                }
            """.trimIndent()
        )

        val result = runner("publishApplePackage", "--stacktrace").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":publishApplePackage")?.outcome)

        val remoteManifest = testProjectDir.resolve("build/kmpApplePackager/package/Package.swift")
        val metadataFile = testProjectDir.resolve("build/kmpApplePackager/metadata/package-metadata.json")

        assertTrue(
            remoteManifest.readText().contains(
                "url: \"https://github.example.com/team/shared-package/releases/download/1.2.3/Shared-1.2.3.xcframework.zip\""
            )
        )
        assertTrue(
            metadataFile.readText().contains(
                "\"downloadUrl\" : \"https://github.example.com/team/shared-package/releases/download/1.2.3/Shared-1.2.3.xcframework.zip\""
            )
        )
    }

    private fun runner(vararg arguments: String): GradleRunner {
        return GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments(*arguments)
            .withPluginClasspath()
        // Intentionally keep the runner quiet so assertions can inspect output deterministically.
    }

    private fun writeFixtureProject(rootBuildScript: String) {
        val tripleQuote = "\"\"\""

        writeFile(
            "settings.gradle.kts",
            """
                rootProject.name = "plugin-functional-test"
                include(":shared")
            """.trimIndent()
        )
        writeFile("build.gradle.kts", rootBuildScript)
        writeFile(
            "shared/build.gradle.kts",
            """
                tasks.register("assembleFakeReleaseXCFramework") {
                    val outputDirectory = layout.buildDirectory.dir("XCFrameworks/release/Shared.xcframework")
                    outputs.dir(outputDirectory)

                    doLast {
                        val xcframeworkDirectory = outputDirectory.get().asFile
                        delete(xcframeworkDirectory)

                        file("${'$'}{xcframeworkDirectory}/ios-arm64/Shared.framework").mkdirs()
                        file("${'$'}{xcframeworkDirectory}/ios-arm64-simulator/Shared.framework").mkdirs()
                        file("${'$'}{xcframeworkDirectory}/ios-arm64/Shared.framework/Shared").writeText("binary")
                        file("${'$'}{xcframeworkDirectory}/ios-arm64/Shared.framework/Info.plist").writeText("framework")
                        file("${'$'}{xcframeworkDirectory}/ios-arm64-simulator/Shared.framework/Shared").writeText("binary")
                        file("${'$'}{xcframeworkDirectory}/ios-arm64-simulator/Shared.framework/Info.plist").writeText("framework")
                        file("${'$'}{xcframeworkDirectory}/Info.plist").writeText(
                            $tripleQuote
                            <?xml version="1.0" encoding="UTF-8"?>
                            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "https://www.apple.com/DTDs/PropertyList-1.0.dtd">
                            <plist version="1.0">
                              <dict>
                                <key>CFBundlePackageType</key>
                                <string>XFWK</string>
                                <key>XCFrameworkFormatVersion</key>
                                <string>1.0</string>
                                <key>AvailableLibraries</key>
                                <array>
                                  <dict>
                                    <key>LibraryIdentifier</key>
                                    <string>ios-arm64</string>
                                    <key>LibraryPath</key>
                                    <string>Shared.framework</string>
                                    <key>BinaryPath</key>
                                    <string>Shared.framework/Shared</string>
                                    <key>SupportedPlatform</key>
                                    <string>ios</string>
                                    <key>SupportedArchitectures</key>
                                    <array>
                                      <string>arm64</string>
                                    </array>
                                  </dict>
                                  <dict>
                                    <key>LibraryIdentifier</key>
                                    <string>ios-arm64-simulator</string>
                                    <key>LibraryPath</key>
                                    <string>Shared.framework</string>
                                    <key>BinaryPath</key>
                                    <string>Shared.framework/Shared</string>
                                    <key>SupportedPlatform</key>
                                    <string>ios</string>
                                    <key>SupportedPlatformVariant</key>
                                    <string>simulator</string>
                                    <key>SupportedArchitectures</key>
                                    <array>
                                      <string>arm64</string>
                                    </array>
                                  </dict>
                                </array>
                              </dict>
                            </plist>
                            $tripleQuote.trimIndent()
                        )
                    }
                }
            """.trimIndent()
        )
    }

    private fun writeFile(relativePath: String, content: String) {
        val target = testProjectDir.resolve(relativePath)
        target.parent?.createDirectories()
        target.writeText("$content\n")
    }

    private fun isMacOs(): Boolean {
        return System.getProperty("os.name")
            .lowercase(Locale.US)
            .contains("mac")
    }
}
