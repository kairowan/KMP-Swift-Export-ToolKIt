# Quick Start

## 1. Apply the plugin

```kotlin
plugins {
    id("io.github.haonan.kmp.apple.packager") version "0.1.0"
}
```

## 2. Configure your Apple framework

In your KMP module, define a release XCFramework with a stable framework name:

```kotlin
kotlin {
    iosArm64()
    iosSimulatorArm64()

    val xcf = XCFramework("Shared")

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            xcf.add(this)
        }
    }
}
```

## 3. Configure the packager

```kotlin
kmpApplePackager {
    packageName.set("Shared")
    version.set("0.1.0")
    artifactModule.set(":shared")
    githubRepo.set("yourname/shared-package")
    manifestRepository.set("yourname/shared-package-spm")
    manifestRepositoryBranch.set("main")
    manifestCommitUserName.set("CI Release Bot")
    manifestCommitUserEmail.set("ci@example.com")
    minimumIosVersion.set("16.0")
    minimumMacosVersion.set("13.0")
    swiftExecutable.set("swift")
    gitExecutable.set("git")
    commandTimeoutSeconds.set(600)
    githubRequestTimeoutSeconds.set(120)
    githubMaxRetries.set(2)
    verifyPublishedArtifact.set(true)
    artifactDownloadTimeoutSeconds.set(300)
    artifactDownloadMaxRetries.set(2)
}
```

By default the plugin expects the KMP module to expose the Gradle task `assembleSharedReleaseXCFramework`.

If `manifestRepository` is configured, the plugin can also commit the generated `Package.swift`
into a dedicated repository or branch. Keep `publishManifestRepository=false` for local dry-runs,
and set `pushManifestRepository=true` only when you are ready to update the remote branch.
`manifestRepositoryPath` can point at either a normal git checkout or a git worktree.

Additional SwiftPM deployment targets are optional. You can also set
`minimumTvosVersion`, `minimumWatchosVersion`, `minimumVisionosVersion`, and
`minimumMacCatalystVersion` when your XCFramework includes those platform slices.

For production CI, the most relevant operational controls are:

- `commandTimeoutSeconds`: timeout for local commands such as `ditto`, `swift`, and `git`
- `githubRequestTimeoutSeconds`: per-request timeout for GitHub Releases API calls
- `githubMaxRetries`: retry budget for transient GitHub failures such as 429 or 5xx
- `failOnDirtyManifestRepository`: whether a local manifest checkout must be clean before the plugin commits `Package.swift`
- `manifestCommitUserName` / `manifestCommitUserEmail`: explicit commit identity for CI, otherwise the task falls back to `git config user.name/user.email`

## 4. Publish

```bash
GITHUB_TOKEN=ghp_your_token ./gradlew publishApplePackage
```

## Outputs

The plugin writes artifacts into `build/kmpApplePackager/`:

- `configuration/report.properties`
- `xcframework/`
- `distributions/`
- `checksum/`
- `package/Package.swift`
- `artifactVerification/report.properties`
- `metadata/package-metadata.json`
- `release/publish.properties`
- `packageRepository/publish.properties`
- `validation/report.properties`
