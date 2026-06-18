package io.github.haonan.kmp.apple.packager.internal

import java.io.File
import org.gradle.api.GradleException

/**
 * Publishes one or more files to a GitHub release while keeping reruns idempotent.
 *
 * Author: kairowan
 */
internal class GithubReleaseAssetPublisher(
    private val api: GithubApi,
    private val repo: String,
    private val tag: String,
    private val release: GithubRelease,
    private val githubServerUrl: String,
    private val githubApiUrl: String,
    private val token: String,
    private val requestTimeoutSeconds: Int,
    private val maxRetries: Int,
    private val overwriteExistingReleaseAsset: Boolean,
    private val workingDirectory: File,
    private val onRetry: (String) -> Unit = {},
) {
    private val knownAssets = release.assets.associateBy(GithubAsset::name).toMutableMap()

    fun publish(file: File): PublishedGithubReleaseAsset {
        val existingAsset = knownAssets[file.name]
        if (existingAsset == null) {
            return upload(file, "uploaded")
        }

        val localChecksum = Sha256Hasher.compute(file)
        val existingChecksum = downloadExistingAssetChecksum(
            assetId = existingAsset.id.toString(),
            browserDownloadUrl = existingAsset.browserDownloadUrl,
            destinationFile = File(workingDirectory, "existing-${file.name}"),
        )
        val resolution = ReleaseAssetConflictResolver.resolve(
            assetName = file.name,
            tag = tag,
            localChecksum = localChecksum,
            existingChecksum = existingChecksum,
            overwriteExistingReleaseAsset = overwriteExistingReleaseAsset,
        )

        return when (resolution.action) {
            ReleaseAssetConflictAction.REUSE_EXISTING -> PublishedGithubReleaseAsset(
                asset = existingAsset,
                status = "reused",
                checksum = localChecksum,
                logMessage = "${resolution.message} Reusing ${existingAsset.browserDownloadUrl}",
            )

            ReleaseAssetConflictAction.REPLACE_EXISTING -> {
                api.deleteAsset(repo, existingAsset.id)
                upload(file, "replaced", resolution.message)
            }

            ReleaseAssetConflictAction.FAIL -> throw GradleException(resolution.message)
        }
    }

    private fun upload(
        file: File,
        status: String,
        messagePrefix: String? = null,
    ): PublishedGithubReleaseAsset {
        val uploadedAsset = api.uploadAsset(release, file)
        knownAssets[file.name] = uploadedAsset

        val message = buildString {
            if (!messagePrefix.isNullOrBlank()) {
                append(messagePrefix)
                append(' ')
            }
            append("Published ${file.name} to ${uploadedAsset.browserDownloadUrl}")
        }

        return PublishedGithubReleaseAsset(
            asset = uploadedAsset,
            status = status,
            checksum = Sha256Hasher.compute(file),
            logMessage = message,
        )
    }

    private fun downloadExistingAssetChecksum(
        assetId: String,
        browserDownloadUrl: String,
        destinationFile: File,
    ): String {
        val target = GithubReleaseAssetDownloadResolver.resolve(
            githubServerUrl = githubServerUrl,
            githubApiUrl = githubApiUrl,
            repo = repo,
            assetId = assetId,
            token = token,
            browserDownloadUrl = browserDownloadUrl,
        )
        HttpFileDownloader(
            requestTimeoutSeconds = requestTimeoutSeconds,
            maxRetries = maxRetries,
            onRetry = onRetry,
        ).download(
            url = target.downloadUrl,
            destinationFile = destinationFile,
            headers = target.headers,
        )
        return Sha256Hasher.compute(destinationFile)
    }
}

/**
 * Captures the final GitHub release asset state after an upload, reuse, or replacement decision.
 *
 * Author: kairowan
 */
internal data class PublishedGithubReleaseAsset(
    val asset: GithubAsset,
    val status: String,
    val checksum: String,
    val logMessage: String,
)
