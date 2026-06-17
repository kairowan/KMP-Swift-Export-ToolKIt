package io.github.haonan.kmp.apple.packager.internal

import java.io.File

/**
 * Validates that the XCFramework directory and release zip are structurally safe to publish.
 *
 * Author: kairowan
 */
internal object ArtifactStructureValidator {
    fun validate(spec: ArtifactStructureValidationSpec): ArtifactStructureValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val xcframeworkDirectory = spec.xcframeworkDirectory
        val expectedRootDirectory = "${spec.packageName}.xcframework"
        if (xcframeworkDirectory.name != expectedRootDirectory) {
            errors += "Expected XCFramework directory name $expectedRootDirectory but found ${xcframeworkDirectory.name}."
        }

        val bundleInfo = runCatching {
            XcframeworkInfoPlistParser.parse(File(xcframeworkDirectory, "Info.plist"))
        }.getOrElse { exception ->
            return ArtifactStructureValidationResult(
                errors = listOf(exception.message ?: "Failed to parse XCFramework Info.plist."),
                warnings = emptyList(),
                rootDirectory = xcframeworkDirectory.name,
                formatVersion = null,
                libraryCount = 0,
                supportedPlatforms = emptyList(),
                archiveTopLevelEntries = emptyList(),
                hasMacosMetadataEntries = false,
            )
        }

        if (bundleInfo.bundlePackageType != "XFWK") {
            errors += "XCFramework CFBundlePackageType must be XFWK but was ${bundleInfo.bundlePackageType.orEmpty()}."
        }
        if (bundleInfo.availableLibraries.isEmpty()) {
            errors += "XCFramework Info.plist does not declare any AvailableLibraries entries."
        }

        val supportedPlatforms = bundleInfo.availableLibraries
            .map { library -> library.toDistributionPlatformName() }
            .distinct()
            .sorted()
        validateLibraries(bundleInfo.availableLibraries, xcframeworkDirectory, errors)
        validateConfiguredPlatforms(spec.configuredPlatforms, supportedPlatforms, errors)

        val archiveLayout = ZipArchiveLayoutInspector.inspect(spec.archiveFile)
        if (archiveLayout.entries.isEmpty()) {
            errors += "Release archive ${spec.archiveFile.absolutePath} is empty."
        }
        if (archiveLayout.hasMacosMetadataEntries) {
            errors += "Release archive ${spec.archiveFile.name} contains __MACOSX metadata entries."
        }
        if (archiveLayout.topLevelEntries != listOf(expectedRootDirectory)) {
            errors += "Release archive must contain only the top-level directory $expectedRootDirectory but found ${archiveLayout.topLevelEntries.joinToString(", ")}."
        }
        if (!archiveLayout.entries.contains("$expectedRootDirectory/Info.plist")) {
            errors += "Release archive is missing $expectedRootDirectory/Info.plist."
        }
        bundleInfo.availableLibraries.forEach { library ->
            val expectedBinaryEntry = "$expectedRootDirectory/${library.libraryIdentifier}/${library.binaryPath}"
            if (!archiveLayout.entries.contains(expectedBinaryEntry)) {
                errors += "Release archive is missing $expectedBinaryEntry."
            }
        }

        return ArtifactStructureValidationResult(
            errors = errors,
            warnings = warnings,
            rootDirectory = expectedRootDirectory,
            formatVersion = bundleInfo.formatVersion,
            libraryCount = bundleInfo.availableLibraries.size,
            supportedPlatforms = supportedPlatforms,
            archiveTopLevelEntries = archiveLayout.topLevelEntries,
            hasMacosMetadataEntries = archiveLayout.hasMacosMetadataEntries,
        )
    }

    private fun validateLibraries(
        libraries: List<XcframeworkLibraryInfo>,
        xcframeworkDirectory: File,
        errors: MutableList<String>,
    ) {
        libraries.forEach { library ->
            if (containsPathTraversal(library.libraryIdentifier)) {
                errors += "XCFramework library identifier ${library.libraryIdentifier} must not contain path traversal segments."
            }
            if (containsPathTraversal(library.libraryPath)) {
                errors += "XCFramework library path ${library.libraryPath} must not contain path traversal segments."
            }
            if (containsPathTraversal(library.binaryPath)) {
                errors += "XCFramework binary path ${library.binaryPath} must not contain path traversal segments."
            }

            val sliceDirectory = File(xcframeworkDirectory, library.libraryIdentifier)
            if (!sliceDirectory.exists()) {
                errors += "XCFramework slice directory ${sliceDirectory.absolutePath} does not exist."
            }

            val libraryFile = File(sliceDirectory, library.libraryPath)
            if (!libraryFile.exists()) {
                errors += "XCFramework library path ${libraryFile.absolutePath} does not exist."
            }

            val binaryFile = File(sliceDirectory, library.binaryPath)
            if (!binaryFile.exists()) {
                errors += "XCFramework binary path ${binaryFile.absolutePath} does not exist."
            }
        }
    }

    private fun validateConfiguredPlatforms(
        configuredPlatforms: Set<String>,
        supportedPlatforms: List<String>,
        errors: MutableList<String>,
    ) {
        configuredPlatforms.forEach { configuredPlatform ->
            if (configuredPlatform !in supportedPlatforms) {
                errors += "Package manifest declares $configuredPlatform, but the XCFramework does not contain a matching slice."
            }
        }
    }

    private fun containsPathTraversal(path: String): Boolean {
        return path.split('/', '\\').any { segment -> segment == ".." }
    }

    private fun XcframeworkLibraryInfo.toDistributionPlatformName(): String {
        val normalizedPlatform = supportedPlatform.trim().lowercase()
        val normalizedVariant = supportedPlatformVariant?.trim()?.lowercase().orEmpty()
        return when {
            normalizedPlatform == "ios" && normalizedVariant == "maccatalyst" -> "macCatalyst"
            normalizedPlatform == "ios" -> "iOS"
            normalizedPlatform == "macos" -> "macOS"
            normalizedPlatform == "tvos" -> "tvOS"
            normalizedPlatform == "watchos" -> "watchOS"
            normalizedPlatform == "xros" || normalizedPlatform == "visionos" -> "visionOS"
            else -> supportedPlatform.trim()
        }
    }
}

internal data class ArtifactStructureValidationSpec(
    val packageName: String,
    val configuredPlatforms: Set<String>,
    val xcframeworkDirectory: File,
    val archiveFile: File,
)

internal data class ArtifactStructureValidationResult(
    val errors: List<String>,
    val warnings: List<String>,
    val rootDirectory: String,
    val formatVersion: String?,
    val libraryCount: Int,
    val supportedPlatforms: List<String>,
    val archiveTopLevelEntries: List<String>,
    val hasMacosMetadataEntries: Boolean,
)
