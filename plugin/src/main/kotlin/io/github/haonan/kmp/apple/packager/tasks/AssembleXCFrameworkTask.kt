package io.github.haonan.kmp.apple.packager.tasks

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Copies assembled framework artifacts from the KMP module output directory.")
/**
 * Copies the generated XCFramework into the plugin's own stable working directory.
 *
 * Author: kairowan
 */
abstract class AssembleXCFrameworkTask : DefaultTask() {
    @get:Input
    abstract val artifactModule: Property<String>

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val xcodeConfiguration: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun assemble() {
        val moduleProject = project.project(artifactModule.get())
        val assembledDirectory = File(
            moduleProject.layout.buildDirectory.asFile.get(),
            "XCFrameworks/${xcodeConfiguration.get()}/${packageName.get()}.xcframework",
        )

        if (!assembledDirectory.exists()) {
            throw GradleException(
                "Expected XCFramework at ${assembledDirectory.absolutePath}, but it does not exist. " +
                    "Check the KMP framework name and assemble task configuration."
            )
        }

        // Normalize the upstream Kotlin output into the plugin's output tree so downstream
        // tasks do not need to know the original KMP build directory layout.
        fileSystemOperations.delete { spec ->
            spec.delete(outputDirectory)
        }
        fileSystemOperations.copy { spec ->
            spec.from(assembledDirectory)
            spec.into(outputDirectory)
        }

        logger.lifecycle("Copied XCFramework to ${outputDirectory.get().asFile.absolutePath}")
    }
}
