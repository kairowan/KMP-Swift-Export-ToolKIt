package io.github.haonan.kmp.apple.packager.tasks

import io.github.haonan.kmp.apple.packager.internal.ProcessRunner
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Creates release zip archives from local XCFramework directories.")
/**
 * Archives the XCFramework in a format SwiftPM can download and verify.
 *
 * Author: kairowan
 */
abstract class ZipArtifactTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val xcframeworkDirectory: DirectoryProperty

    @get:Input
    abstract val commandTimeoutSeconds: Property<Int>

    @get:OutputFile
    abstract val archiveFile: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun zipArtifact() {
        val xcframework = xcframeworkDirectory.get().asFile
        val archive = archiveFile.get().asFile
        archive.parentFile.mkdirs()
        archive.delete()

        // `ditto` preserves the bundle layout and parent directory in the way Apple tooling
        // expects for `.xcframework` archives distributed through SwiftPM. `--norsrc`
        // keeps the release zip free from `__MACOSX` metadata entries.
        ProcessRunner(
            execOperations = execOperations,
            commandTimeoutSeconds = commandTimeoutSeconds.get(),
        ).run(
            commandLine = listOf(
                "ditto",
                "-c",
                "-k",
                "--norsrc",
                "--keepParent",
                xcframework.absolutePath,
                archive.absolutePath,
            ),
        )

        logger.lifecycle("Created archive at ${archive.absolutePath}")
    }
}
