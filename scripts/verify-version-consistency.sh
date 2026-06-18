#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

expected_version="${1:-$(bash ./scripts/current-release-version.sh)}"
errors=0

assert_contains() {
  local file="$1"
  local needle="$2"
  if ! grep -Fq "${needle}" "${file}"; then
    echo "Missing expected version marker in ${file}: ${needle}" >&2
    errors=$((errors + 1))
  fi
}

assert_contains "CHANGELOG.md" "## ${expected_version}"
assert_contains "samples/kmp-library/build.gradle.kts" ".orElse(\"${expected_version}\")"
assert_contains "plugin/src/main/kotlin/io/github/haonan/kmp/apple/packager/KmpApplePackagerPlugin.kt" "?: \"${expected_version}\""
assert_contains "README.md" "version \"${expected_version}\""
assert_contains "README.md" "version.set(\"${expected_version}\")"
assert_contains "README.zh-CN.md" "version \"${expected_version}\""
assert_contains "README.zh-CN.md" "version.set(\"${expected_version}\")"
assert_contains "docs/quick-start.md" "version \"${expected_version}\""
assert_contains "docs/quick-start.md" "version.set(\"${expected_version}\")"
assert_contains "samples/ios-consumer/Package.swift" "from: \"${expected_version}\""

if [ "${errors}" -ne 0 ]; then
  echo "Version consistency validation failed for ${expected_version}." >&2
  exit 1
fi

echo "Version consistency validation passed for ${expected_version}."
