package io.github.haonan.kmp.apple.packager.tasks

import io.github.haonan.kmp.apple.packager.internal.ProcessRunner
import io.github.haonan.kmp.apple.packager.internal.RepositoryReferenceResolver
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Synchronizes Package.swift into a separate git repository and may push remote changes.")
/**
 * Publishes the generated `Package.swift` into a dedicated repository or branch.
 *
 * Author: kairowan
 */
abstract class PublishPackageManifestRepositoryTask : DefaultTask() {
    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val packageVersion: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifestFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val manifestRepository: Property<String>

    @get:Input
    @get:Optional
    abstract val manifestRepositoryPath: Property<String>

    @get:Input
    abstract val manifestRepositoryBranch: Property<String>

    @get:Input
    abstract val manifestRepositorySubdirectory: Property<String>

    @get:Input
    @get:Optional
    abstract val manifestCommitUserName: Property<String>

    @get:Input
    @get:Optional
    abstract val manifestCommitUserEmail: Property<String>

    @get:Input
    abstract val gitExecutable: Property<String>

    @get:Input
    abstract val commandTimeoutSeconds: Property<Int>

    @get:Input
    abstract val failOnDirtyManifestRepository: Property<Boolean>

    @get:Input
    abstract val publishManifestRepository: Property<Boolean>

    @get:Input
    abstract val pushManifestRepository: Property<Boolean>

    @get:OutputFile
    abstract val publishMetadataFile: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun publishManifestRepository() {
        val metadataFile = publishMetadataFile.get().asFile
        metadataFile.parentFile.mkdirs()

        if (!publishManifestRepository.get()) {
            metadataFile.writeText(
                buildString {
                    appendLine("status=skipped")
                    appendLine("pushed=false")
                }
            )
            logger.lifecycle("Skipping manifest repository publish because publishManifestRepository=false")
            return
        }

        val runner = ProcessRunner(
            execOperations = execOperations,
            commandTimeoutSeconds = commandTimeoutSeconds.get(),
        )
        val gitExecutableValue = gitExecutable.get()
        val manifestRepositoryPathValue = manifestRepositoryPath.orNull?.trim().orEmpty()
        val repositoryBranch = manifestRepositoryBranch.get().trim()
        val repositorySubdirectory = manifestRepositorySubdirectory.get().trim().trim('/')

        val preparedRepository = if (manifestRepositoryPathValue.isNotEmpty()) {
            val repositoryRoot = ensureGitRepository(
                repositoryRoot = File(manifestRepositoryPathValue).absoluteFile,
                runner = runner,
                gitExecutable = gitExecutableValue,
            )
            PreparedRepository(
                repositoryRoot = repositoryRoot,
                displayLocation = repositoryRoot.absolutePath,
                originRemoteUrl = readOriginRemoteUrl(repositoryRoot, runner, gitExecutableValue),
                usesLocalCheckout = true,
            )
        } else {
            val repositoryValue = manifestRepository.orNull?.trim().orEmpty()
            if (repositoryValue.isEmpty()) {
                throw GradleException(
                    "Set kmpApplePackager.manifestRepositoryPath or kmpApplePackager.manifestRepository " +
                        "when publishManifestRepository=true."
                )
            }
            val reference = RepositoryReferenceResolver.resolve(repositoryValue)
            val checkoutDirectory = File(temporaryDir, "manifestRepositoryCheckout")
            prepareRemoteCheckout(checkoutDirectory, reference.cloneSource, runner, gitExecutableValue)
            PreparedRepository(
                repositoryRoot = checkoutDirectory,
                displayLocation = reference.displayLocation,
                originRemoteUrl = readOriginRemoteUrl(checkoutDirectory, runner, gitExecutableValue),
                usesLocalCheckout = false,
            )
        }

        if ((!preparedRepository.usesLocalCheckout || pushManifestRepository.get()) &&
            preparedRepository.originRemoteUrl != null
        ) {
            refreshOriginRemote(preparedRepository.repositoryRoot, runner, gitExecutableValue)
        }

        if (preparedRepository.usesLocalCheckout && failOnDirtyManifestRepository.get()) {
            ensureCleanWorkingTree(preparedRepository.repositoryRoot, runner, gitExecutableValue)
        }

        checkoutBranch(preparedRepository.repositoryRoot, repositoryBranch, runner, gitExecutableValue)

        val targetDirectory = if (repositorySubdirectory.isEmpty()) {
            preparedRepository.repositoryRoot
        } else {
            File(preparedRepository.repositoryRoot, repositorySubdirectory).apply {
                mkdirs()
            }
        }
        val targetFile = File(targetDirectory, "Package.swift")
        val targetRelativePath = targetFile.relativeTo(preparedRepository.repositoryRoot).invariantSeparatorsPath
        val manifestText = manifestFile.get().asFile.readText()

        if (!targetFile.exists() || targetFile.readText() != manifestText) {
            targetFile.parentFile.mkdirs()
            targetFile.writeText(manifestText)
        }

        runner.run(
            listOf(
                gitExecutableValue,
                "-C",
                preparedRepository.repositoryRoot.absolutePath,
                "add",
                "--",
                targetRelativePath,
            )
        )

        val statusOutput = runGitStatus(preparedRepository.repositoryRoot, targetRelativePath, runner, gitExecutableValue)
        if (statusOutput.isEmpty()) {
            metadataFile.writeText(
                buildString {
                    appendLine("status=unchanged")
                    appendLine("repository=${preparedRepository.displayLocation}")
                    appendLine("branch=$repositoryBranch")
                    appendLine("path=${targetFile.absolutePath}")
                    appendLine("pushed=false")
                    appendLine("originRemoteUrl=${preparedRepository.originRemoteUrl.orEmpty()}")
                    appendLine("usesLocalCheckout=${preparedRepository.usesLocalCheckout}")
                }
            )
            logger.lifecycle("Manifest repository already up to date at ${targetFile.absolutePath}")
            return
        }

        val commitIdentity = resolveCommitIdentity(
            repositoryRoot = preparedRepository.repositoryRoot,
            runner = runner,
            gitExecutable = gitExecutableValue,
        )
        logger.lifecycle(
            "Using manifest repository git identity ${commitIdentity.name} <${commitIdentity.email}>"
        )
        val commitCommand = mutableListOf(
            gitExecutableValue,
            "-C",
            preparedRepository.repositoryRoot.absolutePath,
            "-c",
            "user.name=${commitIdentity.name}",
            "-c",
            "user.email=${commitIdentity.email}",
        )
        commitCommand += listOf(
            "commit",
            "-m",
            "Update Package.swift for ${packageName.get()} ${packageVersion.get()}",
            "--",
            targetRelativePath,
        )
        runner.run(commitCommand)

        val commitSha = runner.run(
            listOf(
                gitExecutableValue,
                "-C",
                preparedRepository.repositoryRoot.absolutePath,
                "rev-parse",
                "HEAD",
            )
        ).stdout

        var pushed = false
        if (pushManifestRepository.get()) {
            if (preparedRepository.originRemoteUrl == null) {
                throw GradleException(
                    "Cannot push manifest repository changes because the selected checkout has no origin remote."
                )
            }
            runner.run(
                listOf(
                    gitExecutableValue,
                    "-C",
                    preparedRepository.repositoryRoot.absolutePath,
                    "push",
                    "origin",
                    repositoryBranch,
                )
            )
            pushed = true
        }

        metadataFile.writeText(
            buildString {
                appendLine("status=committed")
                appendLine("repository=${preparedRepository.displayLocation}")
                appendLine("branch=$repositoryBranch")
                appendLine("path=${targetFile.absolutePath}")
                appendLine("commit=$commitSha")
                appendLine("commitAuthorName=${commitIdentity.name}")
                appendLine("commitAuthorEmail=${commitIdentity.email}")
                appendLine("pushed=$pushed")
                appendLine("originRemoteUrl=${preparedRepository.originRemoteUrl.orEmpty()}")
                appendLine("usesLocalCheckout=${preparedRepository.usesLocalCheckout}")
            }
        )

        logger.lifecycle(
            "Published Package.swift to ${preparedRepository.displayLocation} on branch $repositoryBranch"
        )
    }

    private fun ensureGitRepository(
        repositoryRoot: File,
        runner: ProcessRunner,
        gitExecutable: String,
    ): File {
        if (!repositoryRoot.exists()) {
            throw GradleException(
                "Expected manifestRepositoryPath to point at an existing git checkout, but ${repositoryRoot.absolutePath} does not exist."
            )
        }
        val topLevel = runCatching {
            runner.run(
                listOf(
                    gitExecutable,
                    "-C",
                    repositoryRoot.absolutePath,
                    "rev-parse",
                    "--show-toplevel",
                )
            ).stdout
        }.getOrNull()

        if (topLevel.isNullOrBlank()) {
            throw GradleException(
                "Expected manifestRepositoryPath to point at a git checkout or git worktree, but git could not resolve a repository at ${repositoryRoot.absolutePath}."
            )
        }

        return File(topLevel).absoluteFile
    }

    private fun prepareRemoteCheckout(
        checkoutDirectory: File,
        cloneSource: String,
        runner: ProcessRunner,
        gitExecutable: String,
    ) {
        val gitDirectory = File(checkoutDirectory, ".git")
        if (gitDirectory.exists()) {
            val currentOrigin = runCatching {
                runner.run(
                    listOf(
                        gitExecutable,
                        "-C",
                        checkoutDirectory.absolutePath,
                        "remote",
                        "get-url",
                        "origin",
                    )
                ).stdout
            }.getOrNull()

            if (currentOrigin != null && currentOrigin != cloneSource) {
                checkoutDirectory.deleteRecursively()
            }
        } else if (checkoutDirectory.exists()) {
            checkoutDirectory.deleteRecursively()
        }

        if (!checkoutDirectory.exists()) {
            runner.run(listOf(gitExecutable, "clone", cloneSource, checkoutDirectory.absolutePath))
        }
    }

    private fun checkoutBranch(
        repositoryRoot: File,
        branch: String,
        runner: ProcessRunner,
        gitExecutable: String,
    ) {
        val hasHead = runCatching {
            runner.run(listOf(gitExecutable, "-C", repositoryRoot.absolutePath, "rev-parse", "--verify", "HEAD"))
        }.isSuccess

        if (!hasHead) {
            runner.run(listOf(gitExecutable, "-C", repositoryRoot.absolutePath, "checkout", "--orphan", branch))
            return
        }

        val hasLocalBranch = runCatching {
            runner.run(
                listOf(
                    gitExecutable,
                    "-C",
                    repositoryRoot.absolutePath,
                    "rev-parse",
                    "--verify",
                    branch,
                )
            )
        }.isSuccess
        val hasRemoteBranch = runCatching {
            runner.run(
                listOf(
                    gitExecutable,
                    "-C",
                    repositoryRoot.absolutePath,
                    "rev-parse",
                    "--verify",
                    "refs/remotes/origin/$branch",
                )
            )
        }.isSuccess

        when {
            hasLocalBranch -> runner.run(
                listOf(gitExecutable, "-C", repositoryRoot.absolutePath, "checkout", branch)
            )

            hasRemoteBranch -> runner.run(
                listOf(
                    gitExecutable,
                    "-C",
                    repositoryRoot.absolutePath,
                    "checkout",
                    "-B",
                    branch,
                    "origin/$branch",
                )
            )

            else -> runner.run(
                listOf(gitExecutable, "-C", repositoryRoot.absolutePath, "checkout", "-B", branch)
            )
        }

        if (hasRemoteBranch) {
            runner.run(
                listOf(
                    gitExecutable,
                    "-C",
                    repositoryRoot.absolutePath,
                    "pull",
                    "--ff-only",
                    "origin",
                    branch,
                )
            )
        }
    }

    private fun runGitStatus(
        repositoryRoot: File,
        relativePath: String,
        runner: ProcessRunner,
        gitExecutable: String,
    ): String {
        return runner.run(
            listOf(
                gitExecutable,
                "-C",
                repositoryRoot.absolutePath,
                "status",
                "--porcelain",
                "--",
                relativePath,
            )
        ).stdout
    }

    private fun readOriginRemoteUrl(
        repositoryRoot: File,
        runner: ProcessRunner,
        gitExecutable: String,
    ): String? {
        return runCatching {
            runner.run(
                listOf(
                    gitExecutable,
                    "-C",
                    repositoryRoot.absolutePath,
                    "remote",
                    "get-url",
                    "origin",
                )
            ).stdout
        }.getOrNull()?.takeIf(String::isNotBlank)
    }

    private fun refreshOriginRemote(
        repositoryRoot: File,
        runner: ProcessRunner,
        gitExecutable: String,
    ) {
        runner.run(
            listOf(
                gitExecutable,
                "-C",
                repositoryRoot.absolutePath,
                "fetch",
                "--prune",
                "origin",
            )
        )
    }

    private fun resolveCommitIdentity(
        repositoryRoot: File,
        runner: ProcessRunner,
        gitExecutable: String,
    ): CommitIdentity {
        val resolvedName = manifestCommitUserName.orNull?.trim().orEmpty().ifEmpty {
            readGitConfig(repositoryRoot, "user.name", runner, gitExecutable).orEmpty()
        }
        val resolvedEmail = manifestCommitUserEmail.orNull?.trim().orEmpty().ifEmpty {
            readGitConfig(repositoryRoot, "user.email", runner, gitExecutable).orEmpty()
        }

        if (resolvedName.isEmpty() || resolvedEmail.isEmpty()) {
            throw GradleException(
                "Unable to resolve the git commit identity for manifest repository publishing at " +
                    "${repositoryRoot.absolutePath}. Configure kmpApplePackager.manifestCommitUserName " +
                    "and kmpApplePackager.manifestCommitUserEmail, or set git user.name and user.email " +
                    "for the selected checkout."
            )
        }

        return CommitIdentity(
            name = resolvedName,
            email = resolvedEmail,
        )
    }

    private fun readGitConfig(
        repositoryRoot: File,
        key: String,
        runner: ProcessRunner,
        gitExecutable: String,
    ): String? {
        return runCatching {
            runner.run(
                listOf(
                    gitExecutable,
                    "-C",
                    repositoryRoot.absolutePath,
                    "config",
                    "--get",
                    key,
                )
            ).stdout
        }.getOrNull()?.takeIf(String::isNotBlank)
    }

    private fun ensureCleanWorkingTree(
        repositoryRoot: File,
        runner: ProcessRunner,
        gitExecutable: String,
    ) {
        val status = runner.run(
            listOf(
                gitExecutable,
                "-C",
                repositoryRoot.absolutePath,
                "status",
                "--porcelain",
            )
        ).stdout

        if (status.isNotEmpty()) {
            throw GradleException(
                "Manifest repository checkout is dirty at ${repositoryRoot.absolutePath}. " +
                    "Commit or stash local changes before publishing, or set " +
                    "kmpApplePackager.failOnDirtyManifestRepository=false if you intentionally want to allow this.\n" +
                    status
            )
        }
    }
}

private data class PreparedRepository(
    val repositoryRoot: File,
    val displayLocation: String,
    val originRemoteUrl: String?,
    val usesLocalCheckout: Boolean,
)

private data class CommitIdentity(
    val name: String,
    val email: String,
)
