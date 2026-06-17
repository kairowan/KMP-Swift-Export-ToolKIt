package io.github.haonan.kmp.apple.packager.internal

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtifactStructureValidatorTest {
    @Test
    fun `accepts a clean xcframework archive`() {
        val workspace = Files.createTempDirectory("artifact-structure-validator")
        val xcframeworkDirectory = createXcframework(workspace.toFile(), packageName = "Shared")
        val archiveFile = createArchive(
            workspace = workspace.toFile(),
            archiveName = "Shared.zip",
            entries = listOf(
                "Shared.xcframework/",
                "Shared.xcframework/Info.plist",
                "Shared.xcframework/ios-arm64/",
                "Shared.xcframework/ios-arm64/Shared.framework/",
                "Shared.xcframework/ios-arm64/Shared.framework/Shared",
                "Shared.xcframework/ios-arm64/Shared.framework/Info.plist",
                "Shared.xcframework/ios-arm64-simulator/",
                "Shared.xcframework/ios-arm64-simulator/Shared.framework/",
                "Shared.xcframework/ios-arm64-simulator/Shared.framework/Shared",
                "Shared.xcframework/ios-arm64-simulator/Shared.framework/Info.plist",
            )
        )

        val result = ArtifactStructureValidator.validate(
            ArtifactStructureValidationSpec(
                packageName = "Shared",
                configuredPlatforms = setOf("iOS"),
                xcframeworkDirectory = xcframeworkDirectory,
                archiveFile = archiveFile,
            )
        )

        assertTrue(result.errors.isEmpty())
        assertEquals("Shared.xcframework", result.rootDirectory)
        assertEquals(2, result.libraryCount)
        assertEquals(listOf("iOS"), result.supportedPlatforms)
        assertFalse(result.hasMacosMetadataEntries)
    }

    @Test
    fun `rejects zip archives that contain macos metadata entries`() {
        val workspace = Files.createTempDirectory("artifact-structure-macos-metadata")
        val xcframeworkDirectory = createXcframework(workspace.toFile(), packageName = "Shared")
        val archiveFile = createArchive(
            workspace = workspace.toFile(),
            archiveName = "Shared.zip",
            entries = listOf(
                "Shared.xcframework/",
                "Shared.xcframework/Info.plist",
                "Shared.xcframework/ios-arm64/",
                "Shared.xcframework/ios-arm64/Shared.framework/",
                "Shared.xcframework/ios-arm64/Shared.framework/Shared",
                "__MACOSX/",
                "__MACOSX/Shared.xcframework/",
            )
        )

        val result = ArtifactStructureValidator.validate(
            ArtifactStructureValidationSpec(
                packageName = "Shared",
                configuredPlatforms = setOf("iOS"),
                xcframeworkDirectory = xcframeworkDirectory,
                archiveFile = archiveFile,
            )
        )

        assertTrue(result.errors.any { error -> error.contains("__MACOSX metadata entries") })
    }

    @Test
    fun `rejects manifest platforms that are missing from the xcframework`() {
        val workspace = Files.createTempDirectory("artifact-structure-platform-mismatch")
        val xcframeworkDirectory = createXcframework(workspace.toFile(), packageName = "Shared")
        val archiveFile = createArchive(
            workspace = workspace.toFile(),
            archiveName = "Shared.zip",
            entries = listOf(
                "Shared.xcframework/",
                "Shared.xcframework/Info.plist",
                "Shared.xcframework/ios-arm64/",
                "Shared.xcframework/ios-arm64/Shared.framework/",
                "Shared.xcframework/ios-arm64/Shared.framework/Shared",
                "Shared.xcframework/ios-arm64/Shared.framework/Info.plist",
                "Shared.xcframework/ios-arm64-simulator/",
                "Shared.xcframework/ios-arm64-simulator/Shared.framework/",
                "Shared.xcframework/ios-arm64-simulator/Shared.framework/Shared",
                "Shared.xcframework/ios-arm64-simulator/Shared.framework/Info.plist",
            )
        )

        val result = ArtifactStructureValidator.validate(
            ArtifactStructureValidationSpec(
                packageName = "Shared",
                configuredPlatforms = setOf("iOS", "macOS"),
                xcframeworkDirectory = xcframeworkDirectory,
                archiveFile = archiveFile,
            )
        )

        assertTrue(result.errors.any { error -> error.contains("declares macOS") })
    }

    private fun createXcframework(
        workspace: File,
        packageName: String,
    ): File {
        val xcframeworkDirectory = File(workspace, "$packageName.xcframework")
        File(xcframeworkDirectory, "ios-arm64/$packageName.framework").mkdirs()
        File(xcframeworkDirectory, "ios-arm64-simulator/$packageName.framework").mkdirs()
        File(xcframeworkDirectory, "ios-arm64/$packageName.framework/$packageName").writeText("binary")
        File(xcframeworkDirectory, "ios-arm64/$packageName.framework/Info.plist").writeText("framework")
        File(xcframeworkDirectory, "ios-arm64-simulator/$packageName.framework/$packageName").writeText("binary")
        File(xcframeworkDirectory, "ios-arm64-simulator/$packageName.framework/Info.plist").writeText("framework")
        File(xcframeworkDirectory, "Info.plist").writeText(
            """
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
                    <string>$packageName.framework</string>
                    <key>BinaryPath</key>
                    <string>$packageName.framework/$packageName</string>
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
                    <string>$packageName.framework</string>
                    <key>BinaryPath</key>
                    <string>$packageName.framework/$packageName</string>
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
            """.trimIndent()
        )
        return xcframeworkDirectory
    }

    private fun createArchive(
        workspace: File,
        archiveName: String,
        entries: List<String>,
    ): File {
        val archiveFile = File(workspace, archiveName)
        ZipOutputStream(archiveFile.outputStream()).use { output ->
            entries.forEach { entryName ->
                output.putNextEntry(ZipEntry(entryName))
                if (!entryName.endsWith("/")) {
                    output.write("payload".toByteArray())
                }
                output.closeEntry()
            }
        }
        return archiveFile
    }
}
