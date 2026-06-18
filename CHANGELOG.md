# Changelog

All notable changes to this project will be documented in this file.

## 1.0.0

- Added an end-to-end Gradle release pipeline for SwiftPM binary distribution of KMP XCFrameworks.
- Added XCFramework archive structure validation before checksum, publish, and summary steps.
- Added local path-based `Package.swift` generation and an iOS consumer smoke test flow.
- Added GitHub Releases publication with immutable asset protection, checksum reuse, and repair opt-in.
- Added manifest repository syncing for both normal git checkouts and git worktrees.
- Added downloadable artifact verification, machine-readable metadata output, and release summary reporting.
- Added fail-fast host environment validation for `swift`, `git`, `xcodebuild`, and `ditto`.
- Added GitHub Enterprise support through configurable `githubServerUrl` and `githubApiUrl`.
- Added bilingual project documentation, CI workflows, plugin publish workflow, and release checklists.

## Unreleased

- No unreleased changes yet.
