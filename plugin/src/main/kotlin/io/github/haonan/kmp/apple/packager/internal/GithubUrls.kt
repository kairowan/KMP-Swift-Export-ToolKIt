package io.github.haonan.kmp.apple.packager.internal

/**
 * Centralizes GitHub release URL formatting so docs and manifest generation stay consistent.
 *
 * Author: kairowan
 */
internal object GithubUrls {
    fun releaseDownloadUrl(
        repo: String,
        tag: String,
        assetName: String,
    ): String {
        return "https://github.com/$repo/releases/download/$tag/$assetName"
    }
}
