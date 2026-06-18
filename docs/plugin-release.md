# Plugin Release

## What this workflow publishes

The repository now separates two release concerns:

- sample package publishing for the example KMP library
- Gradle plugin publishing for `io.github.haonan.kmp.apple.packager`

The plugin release workflow lives in `.github/workflows/publish-plugin.yml`.
For a broader repository-level preflight before either plugin or sample publishing, use
`.github/workflows/release-readiness.yml`.

## Credentials

Publishing to the Gradle Plugin Portal requires:

- `GRADLE_PUBLISH_KEY`
- `GRADLE_PUBLISH_SECRET`

According to the official Plugin Portal documentation, those can be supplied either through
`$HOME/.gradle/gradle.properties` or via environment variables, which is what the GitHub Actions
workflow uses.

## Version source

The workflow resolves the plugin version from:

- the pushed git tag, such as `v1.0.0`
- or the `release_version` workflow input during `workflow_dispatch`

That version is passed into the build through `-PreleaseVersion=...`.
For repository-level consistency checks outside the workflow, `scripts/current-release-version.sh`
prints the default release line derived from the root build.

## Pre-publish checks

Before attempting a real portal publish, the workflow runs:

- `:plugin:check`
- `:plugin:publishAllPublicationsToPluginStagingRepository`
- `scripts/extract-release-notes.sh <version>` so the changelog section for the tagged version is required to exist

The staging repository is uploaded as a workflow artifact so the produced marker modules, POMs,
plugin JAR, and extracted release notes can be inspected if publication fails or if you want a
release audit trail.

For sample package releases, the plugin can now also upload named support assets alongside the zip:

- `<Package>-<version>.Package.swift`
- `<Package>-<version>.sha256`
- `<Package>-<version>.package-metadata.json`

That makes a single GitHub Release self-describing enough for later audits without reopening CI logs.

For local release prep, the repository also provides:

```bash
bash scripts/check-release-readiness.sh --version 1.0.0
```

That script runs the version consistency checks, changelog extraction, plugin verification, staging
publication, sample packaging flow, and the iOS consumer smoke test on macOS.

For a stricter preflight before creating tags or pressing the publish button, you can add checks
such as:

```bash
bash scripts/check-release-readiness.sh \
  --version 1.0.0 \
  --require-clean-tree \
  --check-tag v1.0.0 \
  --require-env GRADLE_PUBLISH_KEY \
  --require-env GRADLE_PUBLISH_SECRET
```

## Validate-only mode

The manual workflow supports `validate_only=true`.

That mode runs `publishPlugins --validate-only`, which is useful when you want Plugin Portal
server-side feedback before creating a real public release.

## First public release

Per the official Plugin Portal publishing guide, the first public version of a new plugin may go
through manual review before it becomes visible. Plan for that extra approval delay when preparing
`1.0.0`.
