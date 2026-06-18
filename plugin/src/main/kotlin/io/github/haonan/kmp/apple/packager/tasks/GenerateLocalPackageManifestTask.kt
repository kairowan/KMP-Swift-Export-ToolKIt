package io.github.haonan.kmp.apple.packager.tasks

import io.github.haonan.kmp.apple.packager.internal.LocalSwiftBinaryTargetSpec
import io.github.haonan.kmp.apple.packager.internal.ManifestRenderer
import io.github.haonan.kmp.apple.packager.internal.PackageManifestSpec
import io.github.haonan.kmp.apple.packager.internal.SwiftPackagePlatformSpec
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Generates a local path-based Package.swift used for smoke-testing the produced XCFramework.")
/**
 * Generates a local-only Swift package manifest that references the assembled XCFramework by path.
 *
 * Author: kairowan
 */
abstract class GenerateLocalPackageManifestTask : DefaultTask() {
    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val swiftToolsVersion: Property<String>

    @get:Input
    abstract val minimumIosVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val minimumMacosVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val minimumTvosVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val minimumWatchosVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val minimumVisionosVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val minimumMacCatalystVersion: Property<String>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val xcframeworkDirectory: DirectoryProperty

    @get:OutputFile
    abstract val manifestFile: RegularFileProperty

    @TaskAction
    fun generateLocalManifest() {
        val platforms = buildList {
            minimumIosVersion.orNull.toPlatformSpec("iOS")?.let(::add)
            minimumMacosVersion.orNull.toPlatformSpec("macOS")?.let(::add)
            minimumTvosVersion.orNull.toPlatformSpec("tvOS")?.let(::add)
            minimumWatchosVersion.orNull.toPlatformSpec("watchOS")?.let(::add)
            minimumVisionosVersion.orNull.toPlatformSpec("visionOS")?.let(::add)
            minimumMacCatalystVersion.orNull.toPlatformSpec("macCatalyst")?.let(::add)
        }

        if (platforms.isEmpty()) {
            throw GradleException(
                "Configure at least one SwiftPM deployment target. " +
                    "For example: kmpApplePackager.minimumIosVersion.set(\"16.0\")"
            )
        }

        val output = manifestFile.get().asFile
        output.parentFile.mkdirs()
        val relativeArtifactPath = output.parentFile.toPath()
            .relativize(xcframeworkDirectory.get().asFile.toPath())
            .toString()
            .replace(File.separatorChar, '/')

        val manifest = ManifestRenderer.render(
            PackageManifestSpec(
                packageName = packageName.get(),
                swiftToolsVersion = swiftToolsVersion.get(),
                platforms = platforms,
                binaryTarget = LocalSwiftBinaryTargetSpec(
                    artifactPath = relativeArtifactPath,
                ),
            )
        )
        output.writeText(manifest)

        logger.lifecycle("Generated local Package.swift at ${output.absolutePath}")
    }

    private fun String?.toPlatformSpec(swiftPlatformName: String): SwiftPackagePlatformSpec? {
        val version = this?.trim().orEmpty()
        if (version.isEmpty()) {
            return null
        }
        return SwiftPackagePlatformSpec(
            swiftPlatformName = swiftPlatformName,
            minimumVersion = version,
        )
    }
}
