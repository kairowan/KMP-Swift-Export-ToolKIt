package io.github.haonan.kmp.apple.packager.internal

/**
 * Describes the host environment observed before release tasks begin.
 *
 * Author: kairowan
 */
internal data class ApplePackagerHostEnvironment(
    val operatingSystemName: String,
    val swift: CommandProbeResult,
    val git: CommandProbeResult,
    val xcodebuild: CommandProbeResult,
    val ditto: CommandProbeResult,
)

/**
 * Validates that the host machine has the minimum toolchain required for Apple packaging tasks.
 *
 * Author: kairowan
 */
internal object EnvironmentValidator {
    fun validate(
        spec: ApplePackagerConfigurationSpec,
        environment: ApplePackagerHostEnvironment,
    ): ConfigurationValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val isMacOs = environment.operatingSystemName.contains("mac", ignoreCase = true)

        if (!isMacOs) {
            errors += "KMP Apple Packager release tasks currently require macOS because XCFramework assembly, xcodebuild, and ditto-based archiving depend on Apple tooling."
        }

        if (!environment.swift.available) {
            errors += "swiftExecutable='${spec.swiftExecutable}' is not available. Install Swift or configure kmpApplePackager.swiftExecutable to point at a working toolchain."
        }

        if (!environment.git.available && spec.publishManifestRepository) {
            errors += "gitExecutable='${spec.gitExecutable}' is not available. Install git or configure kmpApplePackager.gitExecutable when publishManifestRepository=true."
        }

        if (isMacOs && !environment.xcodebuild.available) {
            errors += "xcodebuild is not available. Install Xcode or Xcode command line tools before packaging Apple frameworks."
        }

        if (isMacOs && !environment.ditto.available) {
            errors += "ditto is not available. Install Xcode command line tools on macOS to create SwiftPM-compatible XCFramework archives."
        }

        return ConfigurationValidationResult(
            errors = errors,
            warnings = warnings,
        )
    }
}
