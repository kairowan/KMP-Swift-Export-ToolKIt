package io.github.haonan.kmp.apple.packager.internal

/**
 * Describes an authenticated GitHub release asset download request.
 *
 * Author: kairowan
 */
internal data class GithubReleaseAssetDownloadTarget(
    val downloadUrl: String,
    val displayUrl: String,
    val headers: Map<String, String>,
)

/**
 * Resolves the GitHub asset API endpoint and headers needed to fetch private release assets.
 *
 * Author: kairowan
 */
internal object GithubReleaseAssetDownloadResolver {
    fun resolve(
        repo: String,
        assetId: String,
        token: String,
        browserDownloadUrl: String?,
    ): GithubReleaseAssetDownloadTarget {
        val displayUrl = browserDownloadUrl?.trim().orEmpty().ifEmpty {
            GithubUrls.releasesPageUrl(repo)
        }
        return GithubReleaseAssetDownloadTarget(
            downloadUrl = GithubUrls.releaseAssetApiUrl(repo, assetId),
            displayUrl = displayUrl,
            headers = mapOf(
                "Accept" to "application/octet-stream",
                "Authorization" to "Bearer $token",
                "X-GitHub-Api-Version" to "2022-11-28",
            ),
        )
    }
}
