package io.github.haonan.kmp.apple.packager.internal

/**
 * Captures the values required to render a binary-target Swift package manifest.
 *
 * Author: kairowan
 */
internal data class PackageManifestSpec(
    val packageName: String,
    val swiftToolsVersion: String,
    val minimumIosVersion: String,
    val artifactUrl: String,
    val checksum: String,
)

/**
 * Renders the minimal `Package.swift` needed for SwiftPM binary distribution.
 *
 * Author: kairowan
 */
internal object ManifestRenderer {
    fun render(spec: PackageManifestSpec): String {
        return """
            // swift-tools-version:${spec.swiftToolsVersion}
            import PackageDescription

            let package = Package(
                name: "${spec.packageName}",
                platforms: [
                    .iOS("${spec.minimumIosVersion}")
                ],
                products: [
                    .library(
                        name: "${spec.packageName}",
                        targets: ["${spec.packageName}"]
                    )
                ],
                targets: [
                    .binaryTarget(
                        name: "${spec.packageName}",
                        url: "${spec.artifactUrl}",
                        checksum: "${spec.checksum}"
                    )
                ]
            )
        """.trimIndent() + "\n"
    }
}
