# Release Checklist

## Before tagging

- Confirm the library version, Git tag, and expected archive file name all match.
- Confirm `CHANGELOG.md` reflects the exact scope of the version you are tagging.
- Run `bash scripts/current-release-version.sh` and confirm it prints the expected release line.
- Run `bash scripts/extract-release-notes.sh <version>` to verify the changelog section exists and is non-empty.
- Run `bash scripts/verify-version-consistency.sh <version>` to confirm README snippets, sample defaults, and plugin fallbacks all match.
- Confirm `githubRepo`, manifest repository settings, and release branch inputs point at the real destinations.
- Confirm `GITHUB_TOKEN` and any separate manifest repository token are available in CI.
- Confirm `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET` are configured before any Gradle Plugin Portal release.
- Keep `overwriteExistingReleaseAsset=false` unless you are intentionally repairing a broken release.

## Local or pre-merge validation

- Prefer `bash scripts/check-release-readiness.sh --version <version>` for a single local release sweep.
- For stricter preflight checks, add `--require-clean-tree`, `--check-tag <tag>`, and `--require-env <NAME>` as needed.
- If you want the same sweep on a GitHub-hosted macOS runner, trigger `.github/workflows/release-readiness.yml`.
- Run `./gradlew :plugin:check`
- Run `./gradlew :plugin:publishAllPublicationsToPluginStagingRepository`
- Run `./gradlew -p samples/kmp-library smokeTestIosConsumer`
- If you changed publish behavior, also run `./gradlew -p samples/kmp-library publishApplePackage`

## Release-time checks

- Verify the generated `build/kmpApplePackager/package/Package.swift` points at the expected public artifact URL.
- Verify `build/kmpApplePackager/localPackage/Package.swift` still resolves the local XCFramework by path.
- Verify `build/kmpApplePackager/artifactStructure/report.properties` contains `status=valid`.
- Verify `build/kmpApplePackager/artifactVerification/report.properties` contains `status=verified` or an intentional `status=skipped`.
- Verify `build/kmpApplePackager/release/support-assets.properties` contains `status=published` or an intentional `status=skipped`.
- Verify `build/kmpApplePackager/release/assets/` contains the exact named manifest, checksum, and metadata files expected for the release.
- Verify `build/kmpApplePackager/release/bundle/report.properties` contains `status=assembled` and the expected bundle archive path.
- Verify `build/kmpApplePackager/configuration/report.properties` does not contain unexpected warnings.

## Audit artifacts to keep

- `build/kmpApplePackager/metadata/package-metadata.json`
- `build/kmpApplePackager/release/publish.properties`
- `build/kmpApplePackager/release/support-assets.properties`
- `build/kmpApplePackager/release/assets/`
- `build/kmpApplePackager/release/bundle/`
- `build/kmpApplePackager/packageRepository/publish.properties`
- `build/kmpApplePackager/artifactStructure/report.properties`
- `build/kmpApplePackager/artifactVerification/report.properties`

The metadata JSON is the most important single artifact because it now captures both release
results and the exact toolchain environment that produced them.

For the first public plugin version, also plan for Plugin Portal manual review before the release
becomes fully visible.

The sample release workflow now resolves the package version separately from the GitHub release tag.
For example, a trigger tag like `sample-v1.0.0` can still publish a binary artifact release whose
body comes directly from the `## 1.0.0` section in `CHANGELOG.md`.
