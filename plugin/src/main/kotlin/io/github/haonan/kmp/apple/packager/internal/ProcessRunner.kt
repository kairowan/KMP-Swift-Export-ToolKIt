package io.github.haonan.kmp.apple.packager.internal

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import org.gradle.api.GradleException
import org.gradle.process.ExecOperations

/**
 * Executes local processes while preserving stdout and stderr for Gradle error reporting.
 *
 * Author: kairowan
 */
internal class ProcessRunner(
    private val execOperations: ExecOperations,
    private val commandTimeoutSeconds: Int,
) {
    fun run(
        commandLine: List<String>,
        workingDir: File? = null,
        environment: Map<String, String> = emptyMap(),
    ): ProcessResult {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val process = try {
            ProcessBuilder(commandLine)
                .apply {
                    workingDir?.let { directory(it) }
                    environment().putAll(environment)
                }
                .start()
        } catch (exception: Exception) {
            throw GradleException(
                "Failed to start command: ${commandLine.joinToString(" ")}",
                exception,
            )
        }

        val stdoutThread = startPump(process.inputStream, stdout)
        val stderrThread = startPump(process.errorStream, stderr)

        val finished = process.waitFor(commandTimeoutSeconds.toLong(), TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            stdoutThread.join(1_000)
            stderrThread.join(1_000)

            val standardOut = stdout.toString().trim()
            val standardErr = stderr.toString().trim()
            throw GradleException(
                buildString {
                    appendLine("Command timed out after ${commandTimeoutSeconds}s: ${commandLine.joinToString(" ")}")
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

        stdoutThread.join(1_000)
        stderrThread.join(1_000)

        val standardOut = stdout.toString().trim()
        val standardErr = stderr.toString().trim()

        if (process.exitValue() != 0) {
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

    private fun startPump(
        input: java.io.InputStream,
        output: ByteArrayOutputStream,
    ): Thread {
        return Thread {
            input.use { source ->
                source.copyTo(output)
            }
        }.apply {
            isDaemon = true
            start()
        }
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
