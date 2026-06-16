package io.github.haonan.kmp.apple.packager.tasks

import io.github.haonan.kmp.apple.packager.internal.ArtifactLocationResolver
import io.github.haonan.kmp.apple.packager.internal.HttpFileDownloader
import io.github.haonan.kmp.apple.packager.internal.ProcessRunner
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
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Downloads the published artifact and verifies that the remote checksum matches the locally generated archive.")
/**
 * Verifies that the remotely downloadable artifact matches the checksum emitted by the release pipeline.
 *
 * Author: kairowan
 */
abstract class VerifyPublishedArtifactTask : DefaultTask() {
    @get:Input
    abstract val verifyPublishedArtifact: Property<Boolean>

    @get:Input
    abstract val swiftExecutable: Property<String>

    @get:Input
    abstract val commandTimeoutSeconds: Property<Int>

    @get:Input
    abstract val artifactDownloadTimeoutSeconds: Property<Int>

    @get:Input
    abstract val artifactDownloadMaxRetries: Property<Int>

    @get:Input
    @get:Optional
    abstract val artifactUrlOverride: Property<String>

    @get:Input
    @get:Optional
    abstract val githubRepo: Property<String>

    @get:Input
    @get:Optional
    abstract val githubTag: Property<String>

    @get:Internal
    abstract val githubToken: Property<String>

    @get:Input
    abstract val archiveFileName: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val checksumFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val publishMetadataFile: RegularFileProperty

    @get:OutputFile
    abstract val verificationReportFile: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun verifyPublishedArtifact() {
        val report = verificationReportFile.get().asFile
        report.parentFile.mkdirs()

        if (!verifyPublishedArtifact.get()) {
            report.writeText(
                buildString {
                    appendLine("status=skipped")
                    appendLine("reason=disabled")
                }
            )
            logger.lifecycle("Skipping published artifact verification because verifyPublishedArtifact=false")
            return
        }

        val localChecksum = checksumFile.get().asFile.readText().trim()
        val publishMetadata = readProperties(publishMetadataFile.get().asFile)
        val published = publishMetadata["published"]?.trim()?.equals("true", ignoreCase = true) == true
        val explicitOverride = artifactUrlOverride.orNull?.trim().orEmpty()

        val verificationTarget = when {
            published -> resolvePublishedArtifactTarget(publishMetadata)
            explicitOverride.isNotEmpty() -> VerificationTarget(
                downloadUrl = explicitOverride,
                headers = emptyMap(),
            )

            else -> null
        }

        if (verificationTarget == null) {
            val artifactUrl = runCatching {
                ArtifactLocationResolver.resolve(
                    artifactUrlOverride = artifactUrlOverride.orNull,
                    githubRepo = githubRepo.orNull,
                    githubTag = githubTag.orNull,
                    assetName = archiveFileName.get(),
                )
            }.getOrNull().orEmpty()

            report.writeText(
                buildString {
                    appendLine("status=skipped")
                    appendLine("reason=noPublishedArtifactUrl")
                    if (artifactUrl.isNotEmpty()) {
                        appendLine("url=$artifactUrl")
                    }
                }
            )
            logger.lifecycle(
                "Skipping published artifact verification because no downloadable remote artifact is available yet."
            )
            return
        }

        val downloadFile = File(temporaryDir, archiveFileName.get())
        val downloader = HttpFileDownloader(
            requestTimeoutSeconds = artifactDownloadTimeoutSeconds.get(),
            maxRetries = artifactDownloadMaxRetries.get(),
            onRetry = logger::warn,
        )
        downloader.download(
            url = verificationTarget.downloadUrl,
            destinationFile = downloadFile,
            headers = verificationTarget.headers,
        )

        val remoteChecksum = ProcessRunner(
            execOperations = execOperations,
            commandTimeoutSeconds = commandTimeoutSeconds.get(),
        ).run(
            listOf(swiftExecutable.get(), "package", "compute-checksum", downloadFile.absolutePath)
        ).stdout

        if (remoteChecksum != localChecksum) {
            report.writeText(
                buildString {
                    appendLine("status=checksumMismatch")
                    appendLine("url=${verificationTarget.displayUrl}")
                    appendLine("downloadedFile=${downloadFile.absolutePath}")
                    appendLine("expectedChecksum=$localChecksum")
                    appendLine("actualChecksum=$remoteChecksum")
                }
            )
            throw GradleException(
                "Published artifact checksum mismatch for ${verificationTarget.displayUrl}. " +
                    "Expected $localChecksum but downloaded artifact resolved to $remoteChecksum."
            )
        }

        report.writeText(
            buildString {
                appendLine("status=verified")
                appendLine("url=${verificationTarget.displayUrl}")
                appendLine("downloadedFile=${downloadFile.absolutePath}")
                appendLine("checksum=$remoteChecksum")
            }
        )

        logger.lifecycle("Verified published artifact at ${verificationTarget.displayUrl}")
    }

    private fun resolvePublishedArtifactTarget(publishMetadata: Map<String, String>): VerificationTarget? {
        val assetId = publishMetadata["assetId"].orEmpty()
        val repo = publishMetadata["repo"].orEmpty()
        val token = githubToken.orNull?.trim().orEmpty()
        if (assetId.isNotEmpty() && repo.isNotEmpty() && token.isNotEmpty()) {
            return VerificationTarget(
                downloadUrl = "https://api.github.com/repos/$repo/releases/assets/$assetId",
                displayUrl = publishMetadata["downloadUrl"].orEmpty().ifEmpty {
                    "https://github.com/$repo/releases"
                },
                headers = mapOf(
                    "Accept" to "application/octet-stream",
                    "Authorization" to "Bearer $token",
                    "X-GitHub-Api-Version" to "2022-11-28",
                ),
            )
        }

        val downloadUrl = publishMetadata["downloadUrl"].orEmpty()
        if (downloadUrl.isEmpty()) {
            return null
        }
        return VerificationTarget(
            downloadUrl = downloadUrl,
            headers = emptyMap(),
        )
    }

    private fun readProperties(file: File): Map<String, String> {
        if (!file.exists()) {
            return emptyMap()
        }
        return file.readLines()
            .mapNotNull { line ->
                val separatorIndex = line.indexOf('=')
                if (separatorIndex <= 0) {
                    null
                } else {
                    line.substring(0, separatorIndex) to line.substring(separatorIndex + 1)
                }
            }
            .toMap()
    }
}

private data class VerificationTarget(
    val downloadUrl: String,
    val displayUrl: String = downloadUrl,
    val headers: Map<String, String>,
)
