package io.github.haonan.kmp.apple.packager.tasks

import io.github.haonan.kmp.apple.packager.internal.GithubApi
import io.github.haonan.kmp.apple.packager.internal.GithubReleaseAssetPublisher
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Publishes generated manifest, checksum, and metadata snapshot assets to the release.")
/**
 * Uploads support files that make each release easier to audit and consume outside the build logs.
 *
 * Author: kairowan
 */
abstract class PublishGithubReleaseSupportAssetsTask : DefaultTask() {
    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val packageVersion: Property<String>

    @get:Input
    abstract val publishRelease: Property<Boolean>

    @get:Input
    abstract val publishReleaseSupportAssets: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val githubRepo: Property<String>

    @get:Input
    @get:Optional
    abstract val githubTag: Property<String>

    @get:Internal
    abstract val githubToken: Property<String>

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
    abstract val githubServerUrl: Property<String>

    @get:Input
    abstract val githubApiUrl: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifestFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val checksumFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val metadataFile: RegularFileProperty

    @get:OutputDirectory
    abstract val supportAssetsDirectory: org.gradle.api.file.DirectoryProperty

    @get:OutputFile
    abstract val publishMetadataFile: RegularFileProperty

    @TaskAction
    fun publishSupportAssets() {
        val output = publishMetadataFile.get().asFile
        output.parentFile.mkdirs()
        val preparedAssets = prepareSupportAssets()

        if (!publishRelease.get()) {
            output.writeText(
                buildString {
                    appendLine("status=skipped")
                    appendLine("reason=publishReleaseDisabled")
                    appendPreparedAssets(preparedAssets)
                }
            )
            logger.lifecycle("Skipping release support asset publish because publishRelease=false")
            return
        }

        if (!publishReleaseSupportAssets.get()) {
            output.writeText(
                buildString {
                    appendLine("status=skipped")
                    appendLine("reason=disabled")
                    appendPreparedAssets(preparedAssets)
                }
            )
            logger.lifecycle(
                "Skipping release support asset publish because publishReleaseSupportAssets=false"
            )
            return
        }

        val repo = githubRepo.orNull?.takeIf(String::isNotBlank)
            ?: throw GradleException("githubRepo must be configured when publishReleaseSupportAssets=true")
        val tag = githubTag.orNull?.takeIf(String::isNotBlank)
            ?: throw GradleException("githubTag must be configured when publishReleaseSupportAssets=true")
        val token = githubToken.orNull?.takeIf(String::isNotBlank)
            ?: throw GradleException("GITHUB_TOKEN must be available when publishReleaseSupportAssets=true")

        val api = GithubApi(
            token = token,
            apiUrl = githubApiUrl.get(),
            requestTimeoutSeconds = githubRequestTimeoutSeconds.get(),
            maxRetries = githubMaxRetries.get(),
            onRetry = logger::warn,
        )
        val release = api.getReleaseByTag(
            repo = repo,
            tag = tag,
        )
        val publisher = GithubReleaseAssetPublisher(
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
        )

        val assets = preparedAssets.map { preparedAsset ->
            publisher.publish(preparedAsset.file)
        }

        output.writeText(
            buildString {
                appendLine("status=published")
                appendLine("assetCount=${preparedAssets.size}")
                preparedAssets.zip(assets).forEachIndexed { index, (preparedAsset, asset) ->
                    appendLine("asset${index}Name=${asset.asset.name}")
                    appendLine("asset${index}Type=${preparedAsset.type}")
                    appendLine("asset${index}LocalPath=${preparedAsset.file.absolutePath}")
                    appendLine("asset${index}Status=${asset.status}")
                    appendLine("asset${index}Checksum=${asset.checksum}")
                    appendLine("asset${index}DownloadUrl=${asset.asset.browserDownloadUrl}")
                }
            }
        )

        assets.forEach { asset ->
            logger.lifecycle(asset.logMessage)
        }
    }

    private fun prepareSupportAssets(): List<PreparedReleaseSupportAsset> {
        val outputDirectory = supportAssetsDirectory.get().asFile
        outputDirectory.deleteRecursively()
        outputDirectory.mkdirs()

        return listOf(
            prepareAsset(
                sourceFile = manifestFile.get().asFile,
                assetFileName = "${packageName.get()}-${packageVersion.get()}.Package.swift",
                type = "manifest",
                outputDirectory = outputDirectory,
            ),
            prepareAsset(
                sourceFile = checksumFile.get().asFile,
                assetFileName = "${packageName.get()}-${packageVersion.get()}.sha256",
                type = "checksum",
                outputDirectory = outputDirectory,
            ),
            prepareAsset(
                sourceFile = metadataFile.get().asFile,
                assetFileName = "${packageName.get()}-${packageVersion.get()}.package-metadata.json",
                type = "metadata",
                outputDirectory = outputDirectory,
            ),
        )
    }

    private fun prepareAsset(
        sourceFile: File,
        assetFileName: String,
        type: String,
        outputDirectory: File,
    ): PreparedReleaseSupportAsset {
        val stagedFile = File(outputDirectory, assetFileName)
        sourceFile.copyTo(target = stagedFile, overwrite = true)
        return PreparedReleaseSupportAsset(
            type = type,
            file = stagedFile,
        )
    }

    private fun StringBuilder.appendPreparedAssets(
        assets: List<PreparedReleaseSupportAsset>,
    ) {
        appendLine("assetCount=${assets.size}")
        assets.forEachIndexed { index, asset ->
            appendLine("asset${index}Name=${asset.file.name}")
            appendLine("asset${index}Type=${asset.type}")
            appendLine("asset${index}LocalPath=${asset.file.absolutePath}")
            appendLine("asset${index}Status=staged")
        }
    }
}

private data class PreparedReleaseSupportAsset(
    val type: String,
    val file: File,
)
