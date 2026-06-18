#!/usr/bin/env bash

set -euo pipefail

version="${1:-}"
changelog_file="${2:-CHANGELOG.md}"
output_file="${3:-}"

if [ -z "${version}" ]; then
  echo "Usage: $0 <version> [changelog-file] [output-file]" >&2
  exit 1
fi

if [ ! -f "${changelog_file}" ]; then
  echo "Changelog file not found: ${changelog_file}" >&2
  exit 1
fi

temp_file="$(mktemp)"
trap 'rm -f "${temp_file}"' EXIT

awk -v version="${version}" '
  $0 == "## " version || $0 == "## [" version "]" {
    in_section = 1
    next
  }
  /^## / && in_section {
    exit
  }
  in_section {
    print
  }
' "${changelog_file}" > "${temp_file}"

trimmed_notes="$(
  awk '
    {
      lines[++count] = $0
    }
    END {
      start = 1
      while (start <= count && lines[start] ~ /^[[:space:]]*$/) {
        start++
      }

      end = count
      while (end >= start && lines[end] ~ /^[[:space:]]*$/) {
        end--
      }

      for (line_index = start; line_index <= end; line_index++) {
        print lines[line_index]
      }
    }
  ' "${temp_file}"
)"

if [ -z "${trimmed_notes}" ]; then
  echo "No changelog section found for version ${version} in ${changelog_file}." >&2
  exit 1
fi

if [ -n "${output_file}" ]; then
  mkdir -p "$(dirname "${output_file}")"
  printf '%s\n' "${trimmed_notes}" > "${output_file}"
else
  printf '%s\n' "${trimmed_notes}"
fi
