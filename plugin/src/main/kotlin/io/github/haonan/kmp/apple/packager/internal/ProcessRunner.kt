package io.github.haonan.kmp.apple.packager.internal

import java.io.ByteArrayOutputStream
import java.io.File
import org.gradle.api.GradleException
import org.gradle.process.ExecOperations

/**
 * Executes local processes while preserving stdout and stderr for Gradle error reporting.
 *
 * Author: kairowan
 */
internal class ProcessRunner(
    private val execOperations: ExecOperations,
) {
    fun run(
        commandLine: List<String>,
        workingDir: File? = null,
        environment: Map<String, String> = emptyMap(),
    ): ProcessResult {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val result = execOperations.exec { spec ->
            spec.commandLine(commandLine)
            workingDir?.let { dir ->
                spec.workingDir(dir)
            }
            environment.forEach { (key, value) ->
                spec.environment(key, value)
            }
            spec.isIgnoreExitValue = true
            spec.standardOutput = stdout
            spec.errorOutput = stderr
        }

        val standardOut = stdout.toString().trim()
        val standardErr = stderr.toString().trim()

        if (result.exitValue != 0) {
            throw GradleException(
                buildString {
                    appendLine("Command failed: ${commandLine.joinToString(" ")}")
                    if (standardOut.isNotEmpty()) {
                        appendLine("stdout:")
                        appendLine(standardOut)
                    }
                    if (standardErr.isNotEmpty()) {
                        appendLine("stderr:")
                        appendLine(standardErr)
                    }
                }
            )
        }

        return ProcessResult(
            stdout = standardOut,
            stderr = standardErr,
        )
    }
}

/**
 * Represents the captured output of a completed local command.
 *
 * Author: kairowan
 */
internal data class ProcessResult(
    val stdout: String,
    val stderr: String,
)
