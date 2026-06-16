package io.github.haonan.kmp.apple.packager.tasks

import io.github.haonan.kmp.apple.packager.internal.ArtifactLocationResolver
import io.github.haonan.kmp.apple.packager.internal.GithubApi
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
                    artifactUrlOverride = null,
                    githubRepo = githubRepo.orNull,
                    githubTag = githubTag.orNull,
                    assetName = archiveFile.get().asFile.name,
                )
            }.getOrNull().orEmpty()

            output.writeText(
                buildString {
                    appendLine("published=false")
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

        // Replace an existing asset with the same name so reruns on the same tag remain deterministic.
        release.assets.firstOrNull { asset -> asset.name == archive.name }?.let { existingAsset ->
            api.deleteAsset(repo, existingAsset.id)
        }

        val asset = api.uploadAsset(release, archive)
        output.writeText(
            buildString {
                appendLine("published=true")
                appendLine("downloadUrl=${asset.browserDownloadUrl}")
                appendLine("releaseUrl=${release.htmlUrl.orEmpty()}")
            }
        )

        logger.lifecycle("Published ${archive.name} to ${asset.browserDownloadUrl}")
    }
}
