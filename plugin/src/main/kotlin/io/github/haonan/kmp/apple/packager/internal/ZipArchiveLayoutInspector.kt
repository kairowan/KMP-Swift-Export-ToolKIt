package io.github.haonan.kmp.apple.packager.internal

import java.io.File
import java.util.zip.ZipFile

/**
 * Inspects release zip contents so the plugin can reject malformed archives before publishing.
 *
 * Author: kairowan
 */
internal object ZipArchiveLayoutInspector {
    fun inspect(file: File): ZipArchiveLayout {
        ZipFile(file).use { archive ->
            val entries = archive.entries().asSequence()
                .map { entry -> entry.name }
                .toList()
            val topLevelEntries = entries.mapNotNull { entry ->
                entry.substringBefore('/').takeIf(String::isNotBlank)
            }.distinct().sorted()

            return ZipArchiveLayout(
                entries = entries,
                topLevelEntries = topLevelEntries,
                hasMacosMetadataEntries = entries.any { entry ->
                    entry == "__MACOSX/" || entry.startsWith("__MACOSX/")
                },
            )
        }
    }
}

internal data class ZipArchiveLayout(
    val entries: List<String>,
    val topLevelEntries: List<String>,
    val hasMacosMetadataEntries: Boolean,
)
