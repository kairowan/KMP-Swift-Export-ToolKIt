package io.github.haonan.kmp.apple.packager.internal

/**
 * Centralizes GitHub release URL formatting so docs and manifest generation stay consistent.
 *
 * Author: kairowan
 */
internal object GithubUrls {
    fun releaseDownloadUrl(
        githubServerUrl: String,
        repo: String,
        tag: String,
        assetName: String,
    ): String {
        val serverUrl = githubServerUrl.trim().trimEnd('/')
        return "$serverUrl/$repo/releases/download/$tag/$assetName"
    }

    fun releaseAssetApiUrl(
        githubApiUrl: String,
        repo: String,
        assetId: String,
    ): String {
        val apiUrl = githubApiUrl.trim().trimEnd('/')
        return "$apiUrl/repos/$repo/releases/assets/$assetId"
    }

    fun releasesPageUrl(
        githubServerUrl: String,
        repo: String,
    ): String {
        val serverUrl = githubServerUrl.trim().trimEnd('/')
        return "$serverUrl/$repo/releases"
    }
}
