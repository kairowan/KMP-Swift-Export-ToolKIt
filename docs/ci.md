# CI Guide

## CI strategy

The repository ships with two workflow templates:

- `ci.yml`: builds and checks the plugin, then exercises the sample KMP library in dry-run mode
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

Every run now emits:

- `build/kmpApplePackager/configuration/report.properties`
- `build/kmpApplePackager/artifactVerification/report.properties`
- `build/kmpApplePackager/metadata/package-metadata.json`

The JSON metadata file is intended for CI consumption. It includes the resolved artifact URL,
checksum, platform declarations, publish status, manifest repository result, validation status,
and artifact verification status.

For more predictable production builds, consider setting:

```bash
-Pkmp.apple.packager.commandTimeoutSeconds=600 \
-Pkmp.apple.packager.githubRequestTimeoutSeconds=120 \
-Pkmp.apple.packager.githubMaxRetries=2 \
-Pkmp.apple.packager.manifestCommitUserName='CI Release Bot' \
-Pkmp.apple.packager.manifestCommitUserEmail='ci@example.com' \
-Pkmp.apple.packager.artifactDownloadTimeoutSeconds=300 \
-Pkmp.apple.packager.artifactDownloadMaxRetries=2
```

When you publish into a local manifest checkout, the default
`-Pkmp.apple.packager.failOnDirtyManifestRepository=true` will block the release if that checkout
already contains unrelated uncommitted changes.

## GitHub Actions workflow options

`release.yml` now supports two manifest publishing modes:

- Same repository, separate branch:
  Leave `manifest_repository` empty in `workflow_dispatch`, set `publish_manifest_repository=true`, and choose `manifest_repository_branch`.
- Separate repository:
  Set `publish_manifest_repository=true`, provide `manifest_repository=owner/package-spm`, and store a PAT in `MANIFEST_REPOSITORY_TOKEN`.

The workflow checks out the manifest destination into `manifest-repo/`, lets the plugin commit into that checkout, and then validates the resulting `Package.swift` with SwiftPM.
It also uploads the generated metadata and report files as workflow artifacts.
