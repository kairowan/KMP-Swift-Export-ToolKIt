# Compatibility

## Support policy

The full packaging and release pipeline is currently macOS-only.

That is not just a CI choice. The production path depends on Apple tooling for:

- XCFramework assembly from Kotlin/Native outputs
- `ditto` archive generation for SwiftPM-compatible `.xcframework.zip` files
- `swift package compute-checksum`
- Swift consumer smoke tests through `xcodebuild`

If you run the repository on Linux or Windows, you can still execute some JVM-side tests, but
the end-to-end Apple release path is expected to run on macOS with Xcode command line tools
installed.

## Repository baseline

The repository is currently configured around these baseline versions:

- Java: 17
- Gradle wrapper: 8.11.1
- Kotlin Gradle plugin used by the plugin and sample: 2.0.21
- Generated `Package.swift` default tools version: 6.0
- GitHub Actions macOS runner: `macos-15`

Those values describe the baseline the repository is built and tested against, not a promise that
every neighboring version is supported automatically.

## Release audit metadata

Every `publishApplePackage` run writes `build/kmpApplePackager/metadata/package-metadata.json`.

That metadata now includes an `environment` section with:

- operating system name, version, and architecture
- Java runtime vendor and version
- Gradle version
- detected `swift`, `git`, and `xcodebuild` version output when available

This is the main compatibility audit artifact for CI, release investigations, and support tickets.

The earlier `build/kmpApplePackager/configuration/report.properties` file now also records
fail-fast tool availability for:

- `swift`
- `git`
- `xcodebuild`
- `ditto`

That report is the fastest place to look when a new runner or developer machine cannot even enter
the release pipeline.

## Git hosting baseline

The release automation defaults to GitHub.com, but the URL layer now also supports GitHub
Enterprise through `githubServerUrl` and `githubApiUrl`.

That means the current production baseline covers:

- public GitHub repositories
- private GitHub repositories whose release assets require authenticated API downloads
- GitHub Enterprise instances that expose the standard `/api/v3` surface or a custom API base URL

## What is actually validated today

The repository currently validates these signals:

- plugin unit tests and Gradle TestKit functional tests through `:plugin:check`
- sample dry-run publishing flow on macOS
- generated local manifest consumption through `smokeTestIosConsumer`
- XCFramework and zip structure validation before publish metadata is written

## Before expanding the matrix

If you want to claim broader compatibility, update all of these together:

- CI workflows
- TestKit coverage
- sample smoke tests
- this compatibility document
- release notes or migration notes when behavior changes
