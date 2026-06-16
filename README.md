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
- Compute SwiftPM checksums automatically
- Generate `Package.swift` for binary targets
- Configure SwiftPM deployment targets for iOS and additional Apple platforms
- Publish archives to GitHub Releases
- Sync `Package.swift` into a dedicated repository or release branch
- Validate the generated manifest with `swift package`
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
    iosTargets.set(listOf("iosArm64", "iosSimulatorArm64"))
    minimumMacosVersion.set("13.0")
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
- `generateApplePackageManifest`
- `publishGithubRelease`
- `publishPackageManifestRepository`
- `validateSwiftPmPackage`
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
