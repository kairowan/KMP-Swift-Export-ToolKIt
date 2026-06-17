package io.github.haonan.kmp.apple.packager.internal

/**
 * Determines how the publish task should react when a release already contains an asset
 * with the same file name as the one being uploaded.
 *
 * Author: kairowan
 */
internal object ReleaseAssetConflictResolver {
    fun resolve(
        assetName: String,
        tag: String,
        localChecksum: String,
        existingChecksum: String,
        overwriteExistingReleaseAsset: Boolean,
    ): ReleaseAssetConflictResolution {
        if (localChecksum == existingChecksum) {
            return ReleaseAssetConflictResolution(
                action = ReleaseAssetConflictAction.REUSE_EXISTING,
                message = "Existing release asset $assetName already matches the local archive checksum.",
            )
        }

        if (overwriteExistingReleaseAsset) {
            return ReleaseAssetConflictResolution(
                action = ReleaseAssetConflictAction.REPLACE_EXISTING,
                message = "Replacing existing release asset $assetName on tag $tag because overwriteExistingReleaseAsset=true.",
            )
        }

        return ReleaseAssetConflictResolution(
            action = ReleaseAssetConflictAction.FAIL,
            message = "GitHub release tag $tag already contains asset $assetName with checksum $existingChecksum, " +
                "but the local archive checksum is $localChecksum. Refusing to overwrite the published artifact " +
                "unless kmpApplePackager.overwriteExistingReleaseAsset=true is configured.",
        )
    }
}

internal data class ReleaseAssetConflictResolution(
    val action: ReleaseAssetConflictAction,
    val message: String,
)

internal enum class ReleaseAssetConflictAction {
    REUSE_EXISTING,
    REPLACE_EXISTING,
    FAIL,
}
