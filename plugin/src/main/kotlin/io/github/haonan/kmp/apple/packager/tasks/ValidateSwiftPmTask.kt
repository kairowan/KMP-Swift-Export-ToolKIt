package io.github.haonan.kmp.apple.packager.tasks

import io.github.haonan.kmp.apple.packager.internal.ProcessRunner
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileSystemOperations
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
import java.io.File
import javax.inject.Inject

@DisableCachingByDefault(because = "Uses the local Swift toolchain to validate the generated package manifest.")
/**
 * Validates the generated Swift package manifest with local SwiftPM commands.
 *
 * Author: kairowan
 */
abstract class ValidateSwiftPmTask : DefaultTask() {
    @get:Input
    abstract val validatePackage: Property<Boolean>

    @get:Input
    abstract val swiftExecutable: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifestFile: RegularFileProperty

    @get:OutputFile
    abstract val validationReportFile: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    init {
        swiftExecutable.convention("swift")
    }

    @TaskAction
    fun validate() {
        val report = validationReportFile.get().asFile
        report.parentFile.mkdirs()

        if (!validatePackage.get()) {
            report.writeText("status=skipped\n")
            logger.lifecycle("Skipping SwiftPM validation because validatePackage=false")
            return
        }

        val validationDirectory = File(temporaryDir, "swiftpm")
        fileSystemOperations.delete { spec ->
            spec.delete(validationDirectory)
        }
        validationDirectory.mkdirs()
        // Validate from an isolated temp directory so SwiftPM reads the generated manifest exactly
        // as a consumer would, without picking up unrelated repository files.
        fileSystemOperations.copy { spec ->
            spec.from(manifestFile)
            spec.into(validationDirectory)
        }

        val runner = ProcessRunner(execOperations)
        runner.run(listOf(swiftExecutable.get(), "package", "dump-package"), validationDirectory)
        runner.run(listOf(swiftExecutable.get(), "package", "show-dependencies"), validationDirectory)

        report.writeText(
            buildString {
                appendLine("status=validated")
                appendLine("path=${validationDirectory.absolutePath}")
            }
        )

        logger.lifecycle("Validated SwiftPM manifest in ${validationDirectory.absolutePath}")
    }
}
