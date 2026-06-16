package io.github.haonan.kmp.apple.packager.internal

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import org.gradle.api.GradleException

/**
 * Wraps the GitHub Releases API calls used by the publish task.
 *
 * Author: kairowan
 */
internal class GithubApi(
    private val token: String,
) {
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    private val mapper = jacksonObjectMapper()

    fun getOrCreateRelease(
        repo: String,
        tag: String,
        releaseName: String,
        releaseNotes: String,
    ): GithubRelease {
        val existingResponse = sendJsonRequest(
            method = "GET",
            url = "https://api.github.com/repos/$repo/releases/tags/$tag",
            acceptNotFound = true,
        )

        if (existingResponse.statusCode() == 200) {
            val existingRelease = mapper.readValue(existingResponse.body(), GithubRelease::class.java)
            val releaseBody = releaseNotes.ifBlank { null }
            if (existingRelease.name != releaseName || existingRelease.body != releaseBody) {
                return updateRelease(
                    repo = repo,
                    releaseId = existingRelease.id,
                    releaseName = releaseName,
                    releaseNotes = releaseNotes,
                )
            }
            return existingRelease
        }

        if (existingResponse.statusCode() != 404) {
            throw GradleException(
                "GitHub release lookup failed with ${existingResponse.statusCode()}: ${existingResponse.body()}"
            )
        }

        val payload = mapper.writeValueAsString(
            CreateReleaseRequest(
                tagName = tag,
                name = releaseName,
                body = releaseNotes,
            )
        )

        val createResponse = sendJsonRequest(
            method = "POST",
            url = "https://api.github.com/repos/$repo/releases",
            body = payload,
        )

        return mapper.readValue(createResponse.body(), GithubRelease::class.java)
    }

    private fun updateRelease(
        repo: String,
        releaseId: Long,
        releaseName: String,
        releaseNotes: String,
    ): GithubRelease {
        val payload = mapper.writeValueAsString(
            mapOf(
                "name" to releaseName,
                "body" to releaseNotes,
            )
        )
        val response = sendJsonRequest(
            method = "PATCH",
            url = "https://api.github.com/repos/$repo/releases/$releaseId",
            body = payload,
        )
        return mapper.readValue(response.body(), GithubRelease::class.java)
    }

    fun deleteAsset(repo: String, assetId: Long) {
        val response = sendJsonRequest(
            method = "DELETE",
            url = "https://api.github.com/repos/$repo/releases/assets/$assetId",
        )
        if (response.statusCode() !in 200..299) {
            throw GradleException(
                "Failed to delete existing GitHub release asset $assetId: ${response.statusCode()} ${response.body()}"
            )
        }
    }

    fun uploadAsset(release: GithubRelease, assetFile: File): GithubAsset {
        val uploadUrl = release.uploadUrl.substringBefore("{") +
            "?name=${URLEncoder.encode(assetFile.name, StandardCharsets.UTF_8)}"

        val request = baseRequest(uploadUrl)
            .header("Content-Type", "application/zip")
            .POST(HttpRequest.BodyPublishers.ofFile(assetFile.toPath()))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw GradleException(
                "Failed to upload GitHub release asset: ${response.statusCode()} ${response.body()}"
            )
        }

        return mapper.readValue(response.body(), GithubAsset::class.java)
    }

    private fun sendJsonRequest(
        method: String,
        url: String,
        body: String? = null,
        acceptNotFound: Boolean = false,
    ): HttpResponse<String> {
        val builder = baseRequest(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")

        when {
            method == "GET" -> builder.GET()
            method == "DELETE" -> builder.DELETE()
            body != null -> builder.method(method, HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
            else -> builder.method(method, HttpRequest.BodyPublishers.noBody())
        }

        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299 && !(acceptNotFound && response.statusCode() == 404)) {
            throw GradleException(
                "GitHub API request failed ($method $url): ${response.statusCode()} ${response.body()}"
            )
        }
        return response
    }

    private fun baseRequest(url: String): HttpRequest.Builder {
        return HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "kmp-apple-packager")
    }
}

/**
 * Models the subset of GitHub release fields needed by the plugin.
 *
 * Author: kairowan
 */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class GithubRelease(
    val id: Long,
    @JsonProperty("upload_url")
    val uploadUrl: String,
    @JsonProperty("html_url")
    val htmlUrl: String?,
    val name: String?,
    val body: String?,
    val assets: List<GithubAsset> = emptyList(),
)

/**
 * Models a GitHub release asset that can be replaced or linked from the summary output.
 *
 * Author: kairowan
 */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class GithubAsset(
    val id: Long,
    val name: String,
    @JsonProperty("browser_download_url")
    val browserDownloadUrl: String,
)

/**
 * Represents the release creation payload sent to GitHub.
 *
 * Author: kairowan
 */
internal data class CreateReleaseRequest(
    @JsonProperty("tag_name")
    val tagName: String,
    val name: String,
    val body: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
)
