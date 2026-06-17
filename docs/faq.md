# FAQ

## Does this replace SKIE or KMP-NativeCoroutines?

No. This project is intentionally narrower. It focuses on packaging and release automation for SwiftPM and XCFramework distribution.

## Does it support CocoaPods?

Not in the MVP. The first version stays focused on SwiftPM binary target publishing.

## Does it generate Swift wrappers?

No. The plugin assumes your KMP framework export is already configured and focuses on the release pipeline around it.

## Can I skip GitHub publishing during local development?

Yes.

```kotlin
kmpApplePackager {
    publishRelease.set(false)
    validatePackage.set(false)
}
```

## Can I customize the Swift executable used for validation?

Yes.

```kotlin
kmpApplePackager {
    swiftExecutable.set("/usr/bin/swift")
}
```

## What happens if my local manifest repository checkout already has changes?

Publishing fails by default.

That safety check is controlled by:

```kotlin
kmpApplePackager {
    failOnDirtyManifestRepository.set(true)
}
```

You can turn it off for intentionally managed workflows, but keeping it enabled is the safer production default.

## Can the plugin verify the final downloadable zip after publishing?

Yes.

That behavior is enabled by default through:

```kotlin
kmpApplePackager {
    verifyPublishedArtifact.set(true)
}
```

For dry-runs it skips automatically unless you provide a real `artifactUrlOverride`.

## What happens if I rerun the same GitHub release tag?

The plugin treats release assets as immutable by default.

If the release already contains the target zip name, it first compares checksums:

- matching checksum: reuse the existing asset and continue
- different checksum: fail the publish

Only set `overwriteExistingReleaseAsset=true` when you intentionally want to replace the asset.

## Can `manifestRepositoryPath` point to a git worktree?

Yes.

The plugin resolves the repository through `git rev-parse --show-toplevel`, so both normal
checkouts and git worktrees are supported. If you do not set
`manifestCommitUserName` / `manifestCommitUserEmail`, it will fall back to the checkout's
`git config user.name` and `git config user.email`.

## Which Apple targets are expected?

The sample defaults to `iosArm64` and `iosSimulatorArm64`, but the release pipeline is driven by the generated XCFramework rather than by target introspection.

The generated `Package.swift` now supports extra deployment target declarations through
`minimumMacosVersion`, `minimumTvosVersion`, `minimumWatchosVersion`,
`minimumVisionosVersion`, and `minimumMacCatalystVersion`. Only configure the platforms
that are actually present in the XCFramework you ship.
