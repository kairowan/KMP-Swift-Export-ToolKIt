package io.github.haonan.kmp.apple.packager.internal

import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import org.gradle.api.GradleException

/**
 * Downloads remote files with timeouts and retry handling suitable for release automation.
 *
 * Author: kairowan
 */
internal class HttpFileDownloader(
    private val requestTimeoutSeconds: Int,
    private val maxRetries: Int,
    private val onRetry: (String) -> Unit = {},
) {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(requestTimeoutSeconds.toLong()))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun download(
        url: String,
        destinationFile: File,
        headers: Map<String, String> = emptyMap(),
    ): File {
        destinationFile.parentFile.mkdirs()

        var attempt = 0
        var lastFailure: Exception? = null

        while (attempt <= maxRetries) {
            destinationFile.delete()

            try {
                val builder = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds.toLong()))
                    .header("User-Agent", "kmp-apple-packager")
                headers.forEach { (name, value) ->
                    builder.header(name, value)
                }

                val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofFile(destinationFile.toPath()))
                if (response.statusCode() in 200..299) {
                    return destinationFile
                }

                if (!shouldRetry(response.statusCode()) || attempt == maxRetries) {
                    throw GradleException(
                        "Failed to download artifact from $url: HTTP ${response.statusCode()} ${readErrorSnippet(destinationFile)}"
                    )
                }

                onRetry(
                    "Retrying artifact download after HTTP ${response.statusCode()} " +
                        "(attempt ${attempt + 2}/${maxRetries + 1}) from $url."
                )
            } catch (exception: IOException) {
                if (attempt == maxRetries) {
                    throw GradleException(
                        "Artifact download failed from $url after ${maxRetries + 1} attempts.",
                        exception,
                    )
                }
                lastFailure = exception
                onRetry(
                    "Retrying artifact download after network error (attempt ${attempt + 2}/${maxRetries + 1}) " +
                        "from $url: ${exception.message.orEmpty()}"
                )
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                throw GradleException("Artifact download was interrupted for $url.", exception)
            }

            attempt += 1
            sleepBeforeRetry(attempt)
        }

        throw GradleException(
            "Artifact download failed from $url after ${maxRetries + 1} attempts.",
            lastFailure,
        )
    }

    private fun shouldRetry(statusCode: Int): Boolean {
        return statusCode == 408 || statusCode == 429 || statusCode in 500..599
    }

    private fun sleepBeforeRetry(attempt: Int) {
        val backoffMillis = (attempt * 1000L).coerceAtMost(5_000L)
        try {
            Thread.sleep(backoffMillis)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw GradleException("Artifact download retry backoff was interrupted.", exception)
        }
    }

    private fun readErrorSnippet(file: File): String {
        if (!file.exists()) {
            return ""
        }
        return runCatching {
            Files.readString(file.toPath()).take(512).trim()
        }.getOrDefault("")
    }
}
