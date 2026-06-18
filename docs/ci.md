# CI Guide

## CI strategy

The repository ships with two workflow templates:

- `ci.yml`: builds and checks the plugin, exercises the sample KMP library in dry-run mode, and compiles a Swift consumer against the generated package
- `release.yml`: demonstrates a tagged sample release flow on macOS with GitHub Releases

## Required secrets

For real publishing you need:

- `GITHUB_TOKEN` with `contents: write`
- `MANIFEST_REPOSITORY_TOKEN` when publishing `Package.swift` to a different GitHub repository

## Recommended sample command

```bash
./gradlew -p samples/kmp-library publishApplePackage \
  -Pkmp.apple.packager.publishRelease=false \
  -Pkmp.apple.packager.publishManifestRepository=false \
  -Pkmp.apple.packager.validatePackage=false
```

## Release command

```bash
./gradlew -p samples/kmp-library publishApplePackage \
  -Pkmp.apple.packager.publishRelease=true \
  -Pkmp.apple.packager.publishManifestRepository=true \
  -Pkmp.apple.packager.pushManifestRepository=true \
  -Pkmp.apple.packager.validatePackage=true
```

That command assumes the sample's `githubRepo` points at the repository running the workflow.
If you use a separate manifest repository, also provide either
`-Pkmp.apple.packager.manifestRepository=owner/package-spm` or
`-Pkmp.apple.packager.manifestRepositoryPath=/path/to/local/checkout`.
That local checkout may also be a git worktree when your release automation keeps multiple
branches checked out side by side.

If your pipelines run on GitHub Enterprise, also set:

```bash
-Pkmp.apple.packager.githubServerUrl=https://github.example.com \
-Pkmp.apple.packager.githubApiUrl=https://github.example.com/api/v3
```

When your appliance uses the standard `/api/v3` layout, you can omit the second property and let
the plugin derive it from `githubServerUrl`.

Every run now emits:

- `build/kmpApplePackager/configuration/report.properties`
- `build/kmpApplePackager/artifactStructure/report.properties`
- `build/kmpApplePackager/artifactVerification/report.properties`
- `build/kmpApplePackager/localPackage/Package.swift`
- `build/kmpApplePackager/metadata/package-metadata.json`
- `build/kmpApplePackager/release/support-assets.properties`
- `build/kmpApplePackager/release/assets/`
- `build/kmpApplePackager/release/bundle/`

The configuration report now includes early host checks for `swift`, `git`, `xcodebuild`, and
`ditto`, so CI failures caused by a misconfigured runner show up before the expensive packaging
tasks start.

The JSON metadata file is intended for CI consumption. It includes the resolved artifact URL,
checksum, platform declarations, publish status, release asset status, manifest repository result,
validation status, artifact structure status, artifact verification status, release support asset
status, and a captured toolchain environment snapshot for Java, Gradle, Swift, git, and Xcode.

The local path-based manifest is useful when you want to compile a Swift consumer against the
generated XCFramework without first uploading the zip to an `https` host.

The `release/assets/` directory is the dry-run friendly version of what the plugin later uploads
to GitHub Releases. Keeping those files in workflow artifacts makes it much easier to audit exactly
which manifest, checksum, and metadata snapshot belonged to a candidate release.

The `release/bundle/` directory goes one step further by packaging the archive, manifests, support
assets, and reports into a single release evidence bundle. That is the most convenient artifact to
retain when you want one self-contained audit package per candidate release.

The repository sample wraps that check in:

```bash
./gradlew -p samples/kmp-library smokeTestIosConsumer
```

That task generates `localPackage/Package.swift`, sets `SHARED_PACKAGE_PATH` to an absolute
path, and runs `xcodebuild` for `generic/platform=iOS Simulator` so the consumer is compiled for
the same Apple platform family exposed by the sample XCFramework.

For more predictable production builds, consider setting:

```bash
-Pkmp.apple.packager.commandTimeoutSeconds=600 \
-Pkmp.apple.packager.githubRequestTimeoutSeconds=120 \
-Pkmp.apple.packager.githubMaxRetries=2 \
-Pkmp.apple.packager.overwriteExistingReleaseAsset=false \
-Pkmp.apple.packager.manifestCommitUserName='CI Release Bot' \
-Pkmp.apple.packager.manifestCommitUserEmail='ci@example.com' \
-Pkmp.apple.packager.artifactDownloadTimeoutSeconds=300 \
-Pkmp.apple.packager.artifactDownloadMaxRetries=2
```

With that default, re-running the same release tag is still safe:

- if the existing GitHub asset already matches the local archive checksum, the plugin reuses it
- if the checksum differs, the publish fails instead of mutating the released zip
- set `-Pkmp.apple.packager.overwriteExistingReleaseAsset=true` only for deliberate repair workflows

When you publish into a local manifest checkout, the default
`-Pkmp.apple.packager.failOnDirtyManifestRepository=true` will block the release if that checkout
already contains unrelated uncommitted changes.

## GitHub Actions workflow options

`release.yml` now supports two manifest publishing modes:

- Same repository, separate branch:
  Leave `manifest_repository` empty in `workflow_dispatch`, set `publish_manifest_repository=true`, and choose `manifest_repository_branch`.
- Separate repository:
  Set `publish_manifest_repository=true`, provide `manifest_repository=owner/package-spm`, and store a PAT in `MANIFEST_REPOSITORY_TOKEN`.

It also resolves release metadata explicitly:

- `release_version`: the semantic package version, such as `1.0.0`
- `release_tag`: the GitHub release tag for the binary artifact, defaulting to `sample-v<release_version>` in manual runs
- `CHANGELOG.md`: the workflow extracts the matching section and passes it into the published GitHub release body

The workflow checks out the manifest destination into `manifest-repo/`, lets the plugin commit into that checkout, and then validates the resulting `Package.swift` with SwiftPM.
It also uploads the generated metadata and report files as workflow artifacts, including the
release support asset publish report.

## Release readiness workflow

The repository also ships with `.github/workflows/release-readiness.yml`.

That workflow is a manual macOS preflight for real releases. It runs
`scripts/check-release-readiness.sh` with a version input, can optionally require release secrets,
and uploads the resulting release notes, staging repository, and sample metadata as workflow
artifacts.
