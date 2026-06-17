package io.github.haonan.kmp.apple.packager.internal

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256HasherTest {
    @Test
    fun `computes lowercase sha256 hex digests`() {
        val file = Files.createTempFile("kmp-apple-packager", ".txt")
        file.writeText("hello world")

        assertEquals(
            "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
            Sha256Hasher.compute(file.toFile()),
        )
    }
}
