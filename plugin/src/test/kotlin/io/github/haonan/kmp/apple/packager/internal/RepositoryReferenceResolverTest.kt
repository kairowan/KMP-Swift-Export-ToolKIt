package io.github.haonan.kmp.apple.packager.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepositoryReferenceResolverTest {
    @Test
    fun `resolves github slug into clone and display urls`() {
        val reference = RepositoryReferenceResolver.resolve("yourname/shared-package")

        assertEquals("https://github.com/yourname/shared-package.git", reference.cloneSource)
        assertEquals("https://github.com/yourname/shared-package", reference.displayLocation)
    }

    @Test
    fun `resolves github ssh url into web display url`() {
        val reference = RepositoryReferenceResolver.resolve("git@github.com:yourname/shared-package.git")

        assertEquals("git@github.com:yourname/shared-package.git", reference.cloneSource)
        assertEquals("https://github.com/yourname/shared-package", reference.displayLocation)
    }

    @Test
    fun `resolves relative path as a local checkout`() {
        val reference = RepositoryReferenceResolver.resolve("../shared-package")

        assertTrue(reference.cloneSource.endsWith("/shared-package"))
        assertEquals(reference.cloneSource, reference.displayLocation)
    }
}
