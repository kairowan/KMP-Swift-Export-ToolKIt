package io.github.haonan.kmp.apple.packager.internal

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.gradle.api.GradleException

class XcframeworkInfoPlistParserTest {
    @Test
    fun `parses available libraries from xcframework info plist`() {
        val plistFile = Files.createTempFile("xcframework-info", ".plist")
        plistFile.writeText(
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
            """.trimIndent()
        )

        val result = XcframeworkInfoPlistParser.parse(plistFile.toFile())

        assertEquals("XFWK", result.bundlePackageType)
        assertEquals("1.0", result.formatVersion)
        assertEquals(2, result.availableLibraries.size)
        assertEquals("ios-arm64", result.availableLibraries[0].libraryIdentifier)
        assertEquals("simulator", result.availableLibraries[1].supportedPlatformVariant)
    }

    @Test
    fun `rejects blank required plist strings`() {
        val plistFile = Files.createTempFile("xcframework-info-blank", ".plist")
        plistFile.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <plist version="1.0">
              <dict>
                <key>AvailableLibraries</key>
                <array>
                  <dict>
                    <key>LibraryIdentifier</key>
                    <string>   </string>
                    <key>LibraryPath</key>
                    <string>Shared.framework</string>
                    <key>BinaryPath</key>
                    <string>Shared.framework/Shared</string>
                    <key>SupportedPlatform</key>
                    <string>ios</string>
                  </dict>
                </array>
              </dict>
            </plist>
            """.trimIndent()
        )

        val exception = assertFailsWith<GradleException> {
            XcframeworkInfoPlistParser.parse(plistFile.toFile())
        }

        assertEquals(
            "Required plist string value for key LibraryIdentifier must not be blank.",
            exception.message,
        )
    }

    @Test
    fun `does not resolve external entities from plist content`() {
        val secretFile = Files.createTempFile("xcframework-secret", ".txt")
        secretFile.writeText("secret-value")
        val plistFile = Files.createTempFile("xcframework-info-xxe", ".plist")
        plistFile.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist [
              <!ENTITY xxe SYSTEM "${secretFile.toUri()}">
            ]>
            <plist version="1.0">
              <dict>
                <key>AvailableLibraries</key>
                <array>
                  <dict>
                    <key>LibraryIdentifier</key>
                    <string>&xxe;</string>
                    <key>LibraryPath</key>
                    <string>Shared.framework</string>
                    <key>BinaryPath</key>
                    <string>Shared.framework/Shared</string>
                    <key>SupportedPlatform</key>
                    <string>ios</string>
                  </dict>
                </array>
              </dict>
            </plist>
            """.trimIndent()
        )

        val exception = assertFailsWith<GradleException> {
            XcframeworkInfoPlistParser.parse(plistFile.toFile())
        }

        assertTrue(exception.message.orEmpty().contains("LibraryIdentifier"))
        assertTrue(!exception.message.orEmpty().contains("secret-value"))
    }
}
