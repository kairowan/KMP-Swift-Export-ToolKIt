package io.github.haonan.kmp.apple.packager.internal

import org.gradle.process.ExecOperations

/**
 * Probes whether a local command is executable and captures its version-style output when available.
 *
 * Author: kairowan
 */
internal object CommandAvailabilityProbe {
    fun probe(
        execOperations: ExecOperations,
        executable: String,
        arguments: List<String>,
        commandTimeoutSeconds: Int,
    ): CommandProbeResult {
        val commandLine = buildList {
            add(executable)
            addAll(arguments)
        }

        return try {
            val result = ProcessRunner(
                execOperations = execOperations,
                commandTimeoutSeconds = commandTimeoutSeconds,
            ).run(commandLine)
            val combinedOutput = listOf(result.stdout, result.stderr)
                .filter(String::isNotBlank)
                .joinToString("\n")
                .ifBlank { null }

            CommandProbeResult(
                executable = executable,
                available = true,
                output = combinedOutput,
                failureMessage = null,
            )
        } catch (exception: Exception) {
            CommandProbeResult(
                executable = executable,
                available = false,
                output = null,
                failureMessage = exception.message?.trim().orEmpty(),
            )
        }
    }
}

/**
 * Captures the outcome of probing a local command such as `swift --version`.
 *
 * Author: kairowan
 */
internal data class CommandProbeResult(
    val executable: String,
    val available: Boolean,
    val output: String?,
    val failureMessage: String?,
)
