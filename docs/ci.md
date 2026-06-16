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

## GitHub Actions workflow options

`release.yml` now supports two manifest publishing modes:

- Same repository, separate branch:
  Leave `manifest_repository` empty in `workflow_dispatch`, set `publish_manifest_repository=true`, and choose `manifest_repository_branch`.
- Separate repository:
  Set `publish_manifest_repository=true`, provide `manifest_repository=owner/package-spm`, and store a PAT in `MANIFEST_REPOSITORY_TOKEN`.

The workflow checks out the manifest destination into `manifest-repo/`, lets the plugin commit into that checkout, and then validates the resulting `Package.swift` with SwiftPM.
