package io.github.haonan.kmp.apple.packager.internal

import kotlin.test.Test
import kotlin.test.assertEquals

class GithubUrlsTest {
    @Test
    fun `builds release download urls against the configured server`() {
        val url = GithubUrls.releaseDownloadUrl(
            githubServerUrl = "https://github.example.com/",
            repo = "team/shared-package",
            tag = "1.0.0",
            assetName = "Shared-1.0.0.xcframework.zip",
        )

        assertEquals(
            "https://github.example.com/team/shared-package/releases/download/1.0.0/Shared-1.0.0.xcframework.zip",
            url,
        )
    }

    @Test
    fun `builds release asset api urls against the configured api root`() {
        val url = GithubUrls.releaseAssetApiUrl(
            githubApiUrl = "https://github.example.com/api/v3/",
            repo = "team/shared-package",
            assetId = "42",
        )

        assertEquals(
            "https://github.example.com/api/v3/repos/team/shared-package/releases/assets/42",
            url,
        )
    }

    @Test
    fun `uses configured server and api roots for authenticated asset downloads`() {
        val target = GithubReleaseAssetDownloadResolver.resolve(
            githubServerUrl = "https://github.example.com",
            githubApiUrl = "https://github.example.com/api/v3",
            repo = "team/shared-package",
            assetId = "42",
            token = "secret",
            browserDownloadUrl = null,
        )

        assertEquals(
            "https://github.example.com/api/v3/repos/team/shared-package/releases/assets/42",
            target.downloadUrl,
        )
        assertEquals(
            "https://github.example.com/team/shared-package/releases",
            target.displayUrl,
        )
    }
}
