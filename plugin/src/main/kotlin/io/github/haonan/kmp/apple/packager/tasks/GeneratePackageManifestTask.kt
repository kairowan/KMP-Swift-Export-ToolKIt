package io.github.haonan.kmp.apple.packager.tasks

import io.github.haonan.kmp.apple.packager.internal.ArtifactLocationResolver
import io.github.haonan.kmp.apple.packager.internal.ManifestRenderer
import io.github.haonan.kmp.apple.packager.internal.PackageManifestSpec
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Generates Package.swift based on release metadata and checksum outputs.")
/**
 * Generates `Package.swift` for the zipped XCFramework artifact.
 *
 * Author: kairowan
 */
abstract class GeneratePackageManifestTask : DefaultTask() {
    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val swiftToolsVersion: Property<String>

    @get:Input
    abstract val minimumIosVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val artifactUrlOverride: Property<String>

    @get:Input
    @get:Optional
    abstract val githubRepo: Property<String>

    @get:Input
    @get:Optional
    abstract val githubTag: Property<String>

    @get:Input
    abstract val archiveFileName: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val checksumFile: RegularFileProperty

    @get:OutputFile
    abstract val manifestFile: RegularFileProperty

    @TaskAction
    fun generateManifest() {
        val checksum = checksumFile.get().asFile.readText().trim()
        // Prefer an explicit override for local experiments, then fall back to the canonical
        // GitHub release download pattern used by the default publishing flow.
        val artifactUrl = ArtifactLocationResolver.resolve(
            artifactUrlOverride = artifactUrlOverride.orNull,
            githubRepo = githubRepo.orNull,
            githubTag = githubTag.orNull,
            assetName = archiveFileName.get(),
        )

        val manifest = ManifestRenderer.render(
            PackageManifestSpec(
                packageName = packageName.get(),
                swiftToolsVersion = swiftToolsVersion.get(),
                minimumIosVersion = minimumIosVersion.get(),
                artifactUrl = artifactUrl,
                checksum = checksum,
            )
        )

        val output = manifestFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(manifest)

        logger.lifecycle("Generated Package.swift at ${output.absolutePath}")
    }
}
