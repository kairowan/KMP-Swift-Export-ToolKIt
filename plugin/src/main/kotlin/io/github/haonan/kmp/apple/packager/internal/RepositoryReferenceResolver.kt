package io.github.haonan.kmp.apple.packager.internal

import java.io.File

/**
 * Resolves a repository reference into a clone source and a display-friendly location.
 *
 * Author: kairowan
 */
internal object RepositoryReferenceResolver {
    private val githubSlugPattern = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

    fun resolve(repository: String): RepositoryReference {
        val value = repository.trim()
        return when {
            value.startsWith("/") || value.startsWith("./") || value.startsWith("../") || value.startsWith("~") -> {
                val path = File(expandHome(value)).absoluteFile
                RepositoryReference(
                    cloneSource = path.absolutePath,
                    displayLocation = path.absolutePath,
                )
            }

            value.startsWith("git@github.com:") -> {
                val slug = value.substringAfter("git@github.com:").removeSuffix(".git")
                RepositoryReference(
                    cloneSource = value,
                    displayLocation = "https://github.com/$slug",
                )
            }

            githubSlugPattern.matches(value) -> RepositoryReference(
                cloneSource = "https://github.com/$value.git",
                displayLocation = "https://github.com/$value",
            )

            else -> RepositoryReference(
                cloneSource = value,
                displayLocation = value.removeSuffix(".git"),
            )
        }
    }

    private fun expandHome(value: String): String {
        if (!value.startsWith("~")) {
            return value
        }
        val home = System.getProperty("user.home")
        return if (value == "~") {
            home
        } else {
            value.replaceFirst("~", home)
        }
    }
}

/**
 * Represents the repository location the plugin will clone or describe in logs.
 *
 * Author: kairowan
 */
internal data class RepositoryReference(
    val cloneSource: String,
    val displayLocation: String,
)
