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
    minimumIosVersion.set("16.0")
}
```

By default the plugin expects the KMP module to expose the Gradle task `assembleSharedReleaseXCFramework`.

If `manifestRepository` is configured, the plugin can also commit the generated `Package.swift`
into a dedicated repository or branch. Keep `publishManifestRepository=false` for local dry-runs,
and set `pushManifestRepository=true` only when you are ready to update the remote branch.

## 4. Publish

```bash
GITHUB_TOKEN=ghp_your_token ./gradlew publishApplePackage
```

## Outputs

The plugin writes artifacts into `build/kmpApplePackager/`:

- `xcframework/`
- `distributions/`
- `checksum/`
- `package/Package.swift`
- `release/publish.properties`
- `packageRepository/publish.properties`
- `validation/report.properties`
