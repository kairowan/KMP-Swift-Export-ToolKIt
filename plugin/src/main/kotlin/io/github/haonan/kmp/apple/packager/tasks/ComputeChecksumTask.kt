package io.github.haonan.kmp.apple.packager.tasks

import io.github.haonan.kmp.apple.packager.internal.ProcessRunner
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Invokes the local swift toolchain to compute archive checksums.")
/**
 * Computes the checksum SwiftPM requires for remote binary targets.
 *
 * Author: kairowan
 */
abstract class ComputeChecksumTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archiveFile: RegularFileProperty

    @get:Input
    abstract val swiftExecutable: Property<String>

    @get:OutputFile
    abstract val checksumFile: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    init {
        swiftExecutable.convention("swift")
    }

    @TaskAction
    fun computeChecksum() {
        val archive = archiveFile.get().asFile
        val checksum = ProcessRunner(execOperations)
            .run(listOf(swiftExecutable.get(), "package", "compute-checksum", archive.absolutePath))
            .stdout

        val output = checksumFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText("$checksum\n")

        logger.lifecycle("Computed SwiftPM checksum: $checksum")
    }
}
