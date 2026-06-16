package io.github.haonan.kmp.apple.packager.internal

/**
 * Captures the values required to render a binary-target Swift package manifest.
 *
 * Author: kairowan
 */
internal data class PackageManifestSpec(
    val packageName: String,
    val swiftToolsVersion: String,
    val platforms: List<SwiftPackagePlatformSpec>,
    val artifactUrl: String,
    val checksum: String,
)

/**
 * Represents a single platform declaration inside the generated Swift package manifest.
 *
 * Author: kairowan
 */
internal data class SwiftPackagePlatformSpec(
    val swiftPlatformName: String,
    val minimumVersion: String,
)

/**
 * Renders the minimal `Package.swift` needed for SwiftPM binary distribution.
 *
 * Author: kairowan
 */
internal object ManifestRenderer {
    fun render(spec: PackageManifestSpec): String {
        return buildString {
            appendLine("// swift-tools-version:${spec.swiftToolsVersion}")
            appendLine("import PackageDescription")
            appendLine()
            appendLine("let package = Package(")
            appendLine("    name: \"${spec.packageName.toSwiftStringLiteral()}\",")
            appendLine("    platforms: [")
            spec.platforms.forEachIndexed { index, platform ->
                val suffix = if (index == spec.platforms.lastIndex) "" else ","
                appendLine("        .${platform.swiftPlatformName}(\"${platform.minimumVersion.toSwiftStringLiteral()}\")$suffix")
            }
            appendLine("    ],")
            appendLine("    products: [")
            appendLine("        .library(")
            appendLine("            name: \"${spec.packageName.toSwiftStringLiteral()}\",")
            appendLine("            targets: [\"${spec.packageName.toSwiftStringLiteral()}\"]")
            appendLine("        )")
            appendLine("    ],")
            appendLine("    targets: [")
            appendLine("        .binaryTarget(")
            appendLine("            name: \"${spec.packageName.toSwiftStringLiteral()}\",")
            appendLine("            url: \"${spec.artifactUrl.toSwiftStringLiteral()}\",")
            appendLine("            checksum: \"${spec.checksum.toSwiftStringLiteral()}\"")
            appendLine("        )")
            appendLine("    ]")
            appendLine(")")
        }
    }

    private fun String.toSwiftStringLiteral(): String {
        return buildString {
            this@toSwiftStringLiteral.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
        }
    }
}
