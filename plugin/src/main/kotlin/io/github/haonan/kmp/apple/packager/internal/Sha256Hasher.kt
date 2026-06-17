package io.github.haonan.kmp.apple.packager.internal

import java.io.File
import java.security.MessageDigest

/**
 * Computes lowercase SHA-256 hex digests for release artifacts.
 *
 * Author: kairowan
 */
internal object Sha256Hasher {
    fun compute(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead <= 0) {
                    break
                }
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
