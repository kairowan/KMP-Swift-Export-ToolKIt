package io.github.haonan.kmp.apple.packager.internal

import kotlin.test.Test
import kotlin.test.assertTrue

class ManifestRendererTest {
    @Test
    fun `renders binary target manifest`() {
        val manifest = ManifestRenderer.render(
            PackageManifestSpec(
                packageName = "Shared",
                swiftToolsVersion = "6.0",
                platforms = listOf(
                    SwiftPackagePlatformSpec(
                        swiftPlatformName = "iOS",
                        minimumVersion = "16.0",
                    )
                ),
                artifactUrl = "https://github.com/example/shared/releases/download/0.1.0/Shared-0.1.0.xcframework.zip",
                checksum = "abc123",
            )
        )

        assertTrue(manifest.contains("// swift-tools-version:6.0"))
        assertTrue(manifest.contains(".iOS(\"16.0\")"))
        assertTrue(manifest.contains("name: \"Shared\""))
        assertTrue(manifest.contains("checksum: \"abc123\""))
    }

    @Test
    fun `renders multiple apple platforms when configured`() {
        val manifest = ManifestRenderer.render(
            PackageManifestSpec(
                packageName = "Shared",
                swiftToolsVersion = "6.0",
                platforms = listOf(
                    SwiftPackagePlatformSpec(
                        swiftPlatformName = "iOS",
                        minimumVersion = "16.0",
                    ),
                    SwiftPackagePlatformSpec(
                        swiftPlatformName = "macOS",
                        minimumVersion = "13.0",
                    ),
                    SwiftPackagePlatformSpec(
                        swiftPlatformName = "tvOS",
                        minimumVersion = "16.0",
                    ),
                ),
                artifactUrl = "https://github.com/example/shared/releases/download/0.1.0/Shared-0.1.0.xcframework.zip",
                checksum = "abc123",
            )
        )

        assertTrue(manifest.contains(".iOS(\"16.0\")"))
        assertTrue(manifest.contains(".macOS(\"13.0\")"))
        assertTrue(manifest.contains(".tvOS(\"16.0\")"))
    }

    @Test
    fun `escapes swift string literals in generated manifest`() {
        val manifest = ManifestRenderer.render(
            PackageManifestSpec(
                packageName = "Shared \"Kit\"",
                swiftToolsVersion = "6.0",
                platforms = listOf(
                    SwiftPackagePlatformSpec(
                        swiftPlatformName = "iOS",
                        minimumVersion = "16.0",
                    )
                ),
                artifactUrl = "https://example.com/releases/Shared\\Kit.zip",
                checksum = "abc\"123",
            )
        )

        assertTrue(manifest.contains("name: \"Shared \\\"Kit\\\"\""))
        assertTrue(manifest.contains("url: \"https://example.com/releases/Shared\\\\Kit.zip\""))
        assertTrue(manifest.contains("checksum: \"abc\\\"123\""))
    }
}
