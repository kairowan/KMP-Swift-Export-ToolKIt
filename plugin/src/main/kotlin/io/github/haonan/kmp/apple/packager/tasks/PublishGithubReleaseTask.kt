package io.github.haonan.kmp.apple.packager.tasks

import io.github.haonan.kmp.apple.packager.internal.ArtifactLocationResolver
import io.github.haonan.kmp.apple.packager.internal.GithubApi
import io.github.haonan.kmp.apple.packager.internal.GithubReleaseAssetPublisher
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

    @get:Input
    abstract val githubServerUrl: Property<String>

    @get:Input
    abstract val githubApiUrl: Property<String>

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
                    githubServerUrl = githubServerUrl.get(),
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
            apiUrl = githubApiUrl.get(),
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
        val assetPublication = GithubReleaseAssetPublisher(
            api = api,
            repo = repo,
            tag = tag,
            release = release,
            githubServerUrl = githubServerUrl.get(),
            githubApiUrl = githubApiUrl.get(),
            token = token,
            requestTimeoutSeconds = artifactDownloadTimeoutSeconds.get(),
            maxRetries = artifactDownloadMaxRetries.get(),
            overwriteExistingReleaseAsset = overwriteExistingReleaseAsset.get(),
            workingDirectory = temporaryDir,
            onRetry = logger::warn,
        ).publish(archive)

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
}
