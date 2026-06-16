package io.github.haonan.kmp.apple.packager.internal

import org.gradle.api.GradleException

/**
 * Resolves the download URL that will be written into the generated Swift package manifest.
 *
 * Author: kairowan
 */
internal object ArtifactLocationResolver {
    fun resolve(
        artifactUrlOverride: String?,
        githubRepo: String?,
        githubTag: String?,
        assetName: String,
    ): String {
        val overrideValue = artifactUrlOverride?.trim().orEmpty()
        if (overrideValue.isNotEmpty()) {
            return overrideValue
        }

        val repo = githubRepo?.trim().orEmpty()
        val tag = githubTag?.trim().orEmpty()
        if (repo.isNotEmpty() && tag.isNotEmpty()) {
            return GithubUrls.releaseDownloadUrl(repo, tag, assetName)
        }

        throw GradleException(
            "Unable to resolve artifact URL. Set kmpApplePackager.artifactUrlOverride or configure githubRepo/githubTag."
        )
    }
}
