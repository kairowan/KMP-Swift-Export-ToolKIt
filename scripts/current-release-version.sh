#!/usr/bin/env bash

set -euo pipefail

build_file="${1:-build.gradle.kts}"

if [ ! -f "${build_file}" ]; then
  echo "Build file not found: ${build_file}" >&2
  exit 1
fi

raw_version="$(
  sed -n 's/.*\.orElse("\([^"]*\)").*/\1/p' "${build_file}" | head -n 1
)"

if [ -z "${raw_version}" ]; then
  echo "Unable to resolve default release version from ${build_file}." >&2
  exit 1
fi

printf '%s\n' "${raw_version%-SNAPSHOT}"
