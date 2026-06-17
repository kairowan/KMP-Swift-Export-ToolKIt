package io.github.haonan.kmp.apple.packager.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReleaseAssetConflictResolverTest {
    @Test
    fun `reuses an existing asset when checksums match`() {
        val resolution = ReleaseAssetConflictResolver.resolve(
            assetName = "Shared.xcframework.zip",
            tag = "1.0.0",
            localChecksum = "abc123",
            existingChecksum = "abc123",
            overwriteExistingReleaseAsset = false,
        )

        assertEquals(ReleaseAssetConflictAction.REUSE_EXISTING, resolution.action)
        assertTrue(resolution.message.contains("already matches"))
    }

    @Test
    fun `fails when checksums differ and overwriting is disabled`() {
        val resolution = ReleaseAssetConflictResolver.resolve(
            assetName = "Shared.xcframework.zip",
            tag = "1.0.0",
            localChecksum = "abc123",
            existingChecksum = "def456",
            overwriteExistingReleaseAsset = false,
        )

        assertEquals(ReleaseAssetConflictAction.FAIL, resolution.action)
        assertTrue(resolution.message.contains("overwriteExistingReleaseAsset=true"))
    }

    @Test
    fun `replaces an existing asset when checksums differ and overwriting is enabled`() {
        val resolution = ReleaseAssetConflictResolver.resolve(
            assetName = "Shared.xcframework.zip",
            tag = "1.0.0",
            localChecksum = "abc123",
            existingChecksum = "def456",
            overwriteExistingReleaseAsset = true,
        )

        assertEquals(ReleaseAssetConflictAction.REPLACE_EXISTING, resolution.action)
        assertTrue(resolution.message.contains("overwriteExistingReleaseAsset=true"))
    }
}
