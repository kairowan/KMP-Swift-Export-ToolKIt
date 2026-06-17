package io.github.haonan.kmp.apple.packager.tasks

import io.github.haonan.kmp.apple.packager.internal.ArtifactLocationResolver
import io.github.haonan.kmp.apple.packager.internal.GithubApi
import io.github.haonan.kmp.apple.packager.internal.GithubAsset
import io.github.haonan.kmp.apple.packager.internal.GithubRelease
import io.github.haonan.kmp.apple.packager.internal.GithubReleaseAssetDownloadResolver
import io.github.haonan.kmp.apple.packager.internal.HttpFileDownloader
import io.github.haonan.kmp.apple.packager.internal.ReleaseAssetConflictAction
import io.github.haonan.kmp.apple.packager.internal.ReleaseAssetConflictResolver
import io.github.haonan.kmp.apple.packager.internal.Sha256Hasher
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Publishes archives through the remote GitHub Releases API.")
/**
 * Publishes the zipped XCFramework to GitHub Releases or records a dry-run result.
 *
 * Author: kairowan
 */
abstract class PublishGithubReleaseTask : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val githubRepo: Property<String>

    @get:Input
    @get:Optional
    abstract val githubTag: Property<String>

    @get:Internal
    abstract val githubToken: Property<String>

    @get:Input
    abstract val releaseName: Property<String>

    @get:Input
    abstract val releaseNotes: Property<String>

    @get:Input
    abstract val publishRelease: Property<Boolean>

    @get:Input
    abstract val githubRequestTimeoutSeconds: Property<Int>

    @get:Input
    abstract val githubMaxRetries: Property<Int>

    @get:Input
    abstract val overwriteExistingReleaseAsset: Property<Boolean>

    @get:Input
    abstract val artifactDownloadTimeoutSeconds: Property<Int>

    @get:Input
    abstract val artifactDownloadMaxRetries: Property<Int>

    @get:Input
    @get:Optional
    abstract val artifactUrlOverride: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archiveFile: RegularFileProperty

    @get:OutputFile
    abstract val publishMetadataFile: RegularFileProperty

    @TaskAction
    fun publish() {
        val output = publishMetadataFile.get().asFile
        output.parentFile.mkdirs()

        if (!publishRelease.get()) {
            val downloadUrl = runCatching {
                ArtifactLocationResolver.resolve(
                    artifactUrlOverride = artifactUrlOverride.orNull,
                    githubRepo = githubRepo.orNull,
                    githubTag = githubTag.orNull,
                    assetName = archiveFile.get().asFile.name,
                )
            }.getOrNull().orEmpty()

            output.writeText(
                buildString {
                    appendLine("published=false")
                    appendLine("assetStatus=skipped")
                    appendLine("downloadUrl=$downloadUrl")
                }
            )
            logger.lifecycle("Skipping GitHub release publish because publishRelease=false")
            return
        }

        val repo = githubRepo.orNull?.takeIf(String::isNotBlank)
            ?: throw GradleException("githubRepo must be configured when publishRelease=true")
        val tag = githubTag.orNull?.takeIf(String::isNotBlank)
            ?: throw GradleException("githubTag must be configured when publishRelease=true")
        val token = githubToken.orNull?.takeIf(String::isNotBlank)
            ?: throw GradleException("GITHUB_TOKEN must be available when publishRelease=true")
        val archive = archiveFile.get().asFile

        val api = GithubApi(
            token = token,
            requestTimeoutSeconds = githubRequestTimeoutSeconds.get(),
            maxRetries = githubMaxRetries.get(),
            onRetry = logger::warn,
        )
        val release = api.getOrCreateRelease(
            repo = repo,
            tag = tag,
            releaseName = releaseName.get(),
            releaseNotes = releaseNotes.get(),
        )

        val assetPublication = release.assets.firstOrNull { asset -> asset.name == archive.name }
            ?.let { existingAsset ->
                publishAgainstExistingAsset(
                    api = api,
                    repo = repo,
                    tag = tag,
                    token = token,
                    archive = archive,
                    release = release,
                    existingAsset = existingAsset,
                )
            }
            ?: api.uploadAsset(release, archive).let { uploadedAsset ->
                PublishedAsset(
                    asset = uploadedAsset,
                    status = "uploaded",
                    logMessage = "Published ${archive.name} to ${uploadedAsset.browserDownloadUrl}",
                )
            }

        output.writeText(
            buildString {
                appendLine("published=true")
                appendLine("repo=$repo")
                appendLine("assetId=${assetPublication.asset.id}")
                appendLine("assetStatus=${assetPublication.status}")
                appendLine("downloadUrl=${assetPublication.asset.browserDownloadUrl}")
                appendLine("releaseUrl=${release.htmlUrl.orEmpty()}")
            }
        )

        logger.lifecycle(assetPublication.logMessage)
    }

    private fun publishAgainstExistingAsset(
        api: GithubApi,
        repo: String,
        tag: String,
        token: String,
        archive: File,
        release: GithubRelease,
        existingAsset: GithubAsset,
    ): PublishedAsset {
        val localChecksum = Sha256Hasher.compute(archive)
        val existingAssetChecksum = downloadExistingAssetChecksum(
            repo = repo,
            token = token,
            assetId = existingAsset.id.toString(),
            browserDownloadUrl = existingAsset.browserDownloadUrl,
            destinationFile = File(temporaryDir, "existing-${archive.name}"),
        )
        val resolution = ReleaseAssetConflictResolver.resolve(
            assetName = archive.name,
            tag = tag,
            localChecksum = localChecksum,
            existingChecksum = existingAssetChecksum,
            overwriteExistingReleaseAsset = overwriteExistingReleaseAsset.get(),
        )

        return when (resolution.action) {
            ReleaseAssetConflictAction.REUSE_EXISTING -> PublishedAsset(
                asset = existingAsset,
                status = "reused",
                logMessage = "${resolution.message} Reusing ${existingAsset.browserDownloadUrl}",
            )

            ReleaseAssetConflictAction.REPLACE_EXISTING -> {
                api.deleteAsset(repo, existingAsset.id)
                val uploadedAsset = api.uploadAsset(release, archive)
                PublishedAsset(
                    asset = uploadedAsset,
                    status = "replaced",
                    logMessage = "${resolution.message} Uploaded replacement to ${uploadedAsset.browserDownloadUrl}",
                )
            }

            ReleaseAssetConflictAction.FAIL -> throw GradleException(resolution.message)
        }
    }

    private fun downloadExistingAssetChecksum(
        repo: String,
        token: String,
        assetId: String,
        browserDownloadUrl: String,
        destinationFile: File,
    ): String {
        val target = GithubReleaseAssetDownloadResolver.resolve(
            repo = repo,
            assetId = assetId,
            token = token,
            browserDownloadUrl = browserDownloadUrl,
        )
        HttpFileDownloader(
            requestTimeoutSeconds = artifactDownloadTimeoutSeconds.get(),
            maxRetries = artifactDownloadMaxRetries.get(),
            onRetry = logger::warn,
        ).download(
            url = target.downloadUrl,
            destinationFile = destinationFile,
            headers = target.headers,
        )
        return Sha256Hasher.compute(destinationFile)
    }
}

private data class PublishedAsset(
    val asset: GithubAsset,
    val status: String,
    val logMessage: String,
)
