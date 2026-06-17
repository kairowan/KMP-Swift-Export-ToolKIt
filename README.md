[English](README.md) | [简体中文](README.zh-CN.md)

# KMP Apple Packager

Publish Kotlin Multiplatform libraries to Swift Package Manager without the XCFramework release mess.

KMP Apple Packager is a Gradle plugin that builds your Apple XCFramework, zips it, computes the SwiftPM checksum, generates `Package.swift`, uploads the artifact to GitHub Releases, and validates the final package before you ship it.

## Why

Publishing a Kotlin Multiplatform library to native Swift teams still involves too many manual steps:

- build the XCFramework
- zip the artifact
- compute the checksum
- write or update `Package.swift`
- upload assets to a public URL
- verify SwiftPM can actually resolve the package

This plugin turns that workflow into a repeatable release pipeline.

## Features

- Build release XCFrameworks for Apple targets
- Zip the artifact with the `.xcframework` bundle kept intact
- Validate XCFramework slices and zip layout before publishing
- Compute SwiftPM checksums automatically
- Generate `Package.swift` for binary targets
- Configure SwiftPM deployment targets for iOS and additional Apple platforms
- Fail fast on invalid publish configuration before the heavy build work starts
- Publish archives to GitHub Releases
- Reuse already-published GitHub release assets when the checksum matches, and fail safely on mismatched reruns by default
- Sync `Package.swift` into a dedicated repository or release branch
- Support both regular git checkouts and git worktrees when syncing manifest repositories
- Validate the generated manifest with `swift package`
- Download and verify published artifacts against the locally generated checksum
- Emit machine-readable release metadata for CI and downstream automation
- Use configurable GitHub request retries/timeouts and external command timeouts
- Protect local manifest repositories from accidental publishes when the checkout is already dirty
- Print a release summary with checksum, URL, and output paths

## Project Layout

```text
kmp-apple-packager/
├── plugin/
├── samples/
│   ├── kmp-library/
│   └── ios-consumer/
├── docs/
└── .github/workflows/
```

## Quick Start

```kotlin
plugins {
    id("io.github.haonan.kmp.apple.packager") version "0.1.0"
}

kmpApplePackager {
    packageName.set("Shared")
    version.set("0.1.0")
    artifactModule.set(":shared")
    githubRepo.set("yourname/shared-package")
    manifestRepository.set("yourname/shared-package-spm")
    manifestRepositoryBranch.set("main")
    manifestCommitUserName.set("CI Release Bot")
    manifestCommitUserEmail.set("ci@example.com")
    iosTargets.set(listOf("iosArm64", "iosSimulatorArm64"))
    minimumMacosVersion.set("13.0")
    swiftExecutable.set("swift")
    gitExecutable.set("git")
    commandTimeoutSeconds.set(600)
    githubRequestTimeoutSeconds.set(120)
    githubMaxRetries.set(2)
    overwriteExistingReleaseAsset.set(false)
    verifyPublishedArtifact.set(true)
    artifactDownloadTimeoutSeconds.set(300)
    artifactDownloadMaxRetries.set(2)
}
```

Then run:

```bash
./gradlew publishApplePackage
```

## Core Tasks

- `assembleAppleXCFramework`
- `zipAppleArtifact`
- `computeApplePackageChecksum`
- `validateApplePackagerConfiguration`
- `generateApplePackageManifest`
- `publishGithubRelease`
- `publishPackageManifestRepository`
- `validateSwiftPmPackage`
- `verifyPublishedArtifact`
- `validateAppleArtifactStructure`
- `writeApplePackageMetadata`
- `publishApplePackage`

## Local MVP Notes

For local dry-runs, you can disable remote publishing and validation:

```kotlin
kmpApplePackager {
    publishRelease.set(false)
    publishManifestRepository.set(false)
    validatePackage.set(false)
}
```

The sample project under `samples/kmp-library` does exactly that by default, so the scaffold can be exercised locally without a real GitHub release target.

If you add `minimumMacosVersion`, `minimumTvosVersion`, `minimumWatchosVersion`,
`minimumVisionosVersion`, or `minimumMacCatalystVersion`, keep them aligned with the
platform slices that actually exist inside the XCFramework you publish.

Each run also writes a stable JSON metadata file at
`build/kmpApplePackager/metadata/package-metadata.json`, which is useful for CI steps
that need the checksum, resolved artifact URL, release asset status, validation status,
or manifest repo result.

For production pipelines, the operational defaults are now explicit:

- `commandTimeoutSeconds=600` for local tools such as `swift`, `git`, and `ditto`
- `githubRequestTimeoutSeconds=120` with `githubMaxRetries=2` for GitHub Releases API calls
- `overwriteExistingReleaseAsset=false` so reruns on the same tag never replace a published zip unless you opt in explicitly
- `verifyPublishedArtifact=true` with `artifactDownloadTimeoutSeconds=300` and `artifactDownloadMaxRetries=2` for post-publish download verification
- `failOnDirtyManifestRepository=true` so local manifest checkouts are rejected if they already contain unrelated changes

If the same GitHub release tag already contains an asset with the target file name, the plugin now:

- reuses the existing asset when its checksum already matches the local archive
- fails the publish when the checksum differs
- replaces the asset only when `overwriteExistingReleaseAsset=true`

When manifest publishing is enabled, the plugin can work with either a normal git checkout or a
git worktree via `manifestRepositoryPath`. Managed remote checkouts are refreshed before branch
sync, and manifest commit identity falls back to `git config user.name/user.email` if you do not
set `manifestCommitUserName` and `manifestCommitUserEmail` explicitly.

## Samples

- `samples/kmp-library`: a minimal KMP library that applies the plugin from this repository via `includeBuild("../..")`
- `samples/ios-consumer`: a small Swift package that demonstrates the expected consumer-side import

## Docs

- [Quick Start](docs/quick-start.md)
- [CI Guide](docs/ci.md)
- [FAQ](docs/faq.md)

## Status

Early MVP. The goal is to make SwiftPM distribution for KMP libraries boring, predictable, and automatable.

## Inspiration

This project builds on top of the official Kotlin Multiplatform Apple export flow and focuses on the missing release automation layer.
