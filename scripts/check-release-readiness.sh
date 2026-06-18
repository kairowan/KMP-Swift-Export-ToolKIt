#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

release_version="$(bash ./scripts/current-release-version.sh)"
skip_sample_pipeline=false
skip_smoke_test=false
require_clean_tree=false
required_env_vars=()
checked_tags=()

usage() {
  cat <<'EOF'
Usage: scripts/check-release-readiness.sh [options]

Options:
  --version <version>        Override the release version to validate.
  --skip-sample-pipeline     Skip macOS-only sample packaging validation.
  --skip-smoke-test          Skip the iOS consumer smoke test.
  --require-clean-tree       Fail when the git working tree contains uncommitted changes.
  --require-env <name>       Require an environment variable to be present. Repeatable.
  --check-tag <tag>          Fail when the given git tag already exists locally. Repeatable.
  --help                     Show this help message.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --version)
      if [ "$#" -lt 2 ]; then
        echo "--version requires a value." >&2
        exit 1
      fi
      release_version="$2"
      shift 2
      ;;
    --skip-sample-pipeline)
      skip_sample_pipeline=true
      shift
      ;;
    --skip-smoke-test)
      skip_smoke_test=true
      shift
      ;;
    --require-clean-tree)
      require_clean_tree=true
      shift
      ;;
    --require-env)
      if [ "$#" -lt 2 ]; then
        echo "--require-env requires a variable name." >&2
        exit 1
      fi
      required_env_vars+=("$2")
      shift 2
      ;;
    --check-tag)
      if [ "$#" -lt 2 ]; then
        echo "--check-tag requires a tag name." >&2
        exit 1
      fi
      checked_tags+=("$2")
      shift 2
      ;;
    --help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [ "${skip_sample_pipeline}" = true ] && [ "${skip_smoke_test}" = false ]; then
  echo "--skip-smoke-test is implied when --skip-sample-pipeline is set." >&2
  skip_smoke_test=true
fi

echo "Checking release readiness for version ${release_version}"

if [ "${require_clean_tree}" = true ]; then
  if [ -n "$(git status --short)" ]; then
    echo "Git working tree is not clean. Commit, stash, or discard changes before releasing." >&2
    exit 1
  fi
  echo "Validated clean git working tree"
fi

validated_required_env=false
for env_var in "${required_env_vars[@]:-}"; do
  if [ -z "${env_var}" ]; then
    continue
  fi
  if [ -z "${!env_var:-}" ]; then
    echo "Required environment variable is missing: ${env_var}" >&2
    exit 1
  fi
  validated_required_env=true
done
if [ "${validated_required_env}" = true ]; then
  echo "Validated required environment variables: ${required_env_vars[*]}"
fi

validated_checked_tags=false
for tag_name in "${checked_tags[@]:-}"; do
  if [ -z "${tag_name}" ]; then
    continue
  fi
  if git rev-parse --verify --quiet "refs/tags/${tag_name}" >/dev/null; then
    echo "Git tag already exists locally: ${tag_name}" >&2
    exit 1
  fi
  validated_checked_tags=true
done
if [ "${validated_checked_tags}" = true ]; then
  echo "Validated release tags do not already exist: ${checked_tags[*]}"
fi

bash ./scripts/verify-version-consistency.sh "${release_version}"

release_readiness_dir="build/release-readiness/${release_version}"
mkdir -p "${release_readiness_dir}"
release_notes_file="${release_readiness_dir}/release-notes.md"
bash ./scripts/extract-release-notes.sh "${release_version}" CHANGELOG.md "${release_notes_file}"
echo "Validated changelog section for ${release_version}"

./gradlew :plugin:check -PreleaseVersion="${release_version}"
./gradlew :plugin:publishAllPublicationsToPluginStagingRepository -PreleaseVersion="${release_version}"

if [ "${skip_sample_pipeline}" = false ]; then
  if [ "$(uname -s)" != "Darwin" ]; then
    echo "Sample packaging validation requires macOS. Re-run with --skip-sample-pipeline for plugin-only validation." >&2
    exit 1
  fi

  ./gradlew -p samples/kmp-library publishApplePackage \
    -Pkmp.apple.packager.sampleVersion="${release_version}"

  archive_path="samples/kmp-library/build/kmpApplePackager/distributions/Shared-${release_version}.xcframework.zip"
  metadata_path="samples/kmp-library/build/kmpApplePackager/metadata/package-metadata.json"
  support_assets_report_path="samples/kmp-library/build/kmpApplePackager/release/support-assets.properties"
  support_assets_dir="samples/kmp-library/build/kmpApplePackager/release/assets"
  release_bundle_report_path="samples/kmp-library/build/kmpApplePackager/release/bundle/report.properties"
  release_bundle_dir="samples/kmp-library/build/kmpApplePackager/release/bundle/Shared-${release_version}-release-bundle"
  release_bundle_archive_path="samples/kmp-library/build/kmpApplePackager/release/bundle/Shared-${release_version}-release-bundle.zip"

  test -f "${archive_path}"
  test -f "${metadata_path}"
  test -f "${support_assets_report_path}"
  test -d "${support_assets_dir}"
  test -f "${support_assets_dir}/Shared-${release_version}.Package.swift"
  test -f "${support_assets_dir}/Shared-${release_version}.sha256"
  test -f "${support_assets_dir}/Shared-${release_version}.package-metadata.json"
  test -f "${release_bundle_report_path}"
  test -d "${release_bundle_dir}"
  test -f "${release_bundle_dir}/bundle-manifest.json"
  test -f "${release_bundle_archive_path}"

  if [ "${skip_smoke_test}" = false ]; then
    ./gradlew -p samples/kmp-library smokeTestIosConsumer \
      -Pkmp.apple.packager.sampleVersion="${release_version}"
  else
    echo "Skipped smokeTestIosConsumer by request."
  fi
else
  echo "Skipped sample pipeline by request."
fi

echo
echo "Release readiness checks passed for ${release_version}"
echo "Release notes file: ${release_notes_file}"
if [ "${skip_sample_pipeline}" = false ]; then
  echo "Sample archive: samples/kmp-library/build/kmpApplePackager/distributions/Shared-${release_version}.xcframework.zip"
  echo "Sample metadata: samples/kmp-library/build/kmpApplePackager/metadata/package-metadata.json"
  echo "Sample support assets: samples/kmp-library/build/kmpApplePackager/release/assets"
  echo "Sample release bundle: samples/kmp-library/build/kmpApplePackager/release/bundle"
fi
