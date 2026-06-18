# Changelog

All notable changes to this project will be documented in this file.

## 1.0.0

### English

- Added an end-to-end Gradle release pipeline for SwiftPM binary distribution of KMP XCFrameworks.
- Added XCFramework archive structure validation before checksum, publish, and summary steps.
- Added local path-based `Package.swift` generation and an iOS consumer smoke test flow.
- Added GitHub Releases publication with immutable asset protection, checksum reuse, and repair opt-in.
- Added manifest repository syncing for both normal git checkouts and git worktrees.
- Added downloadable artifact verification, machine-readable metadata output, and release summary reporting.
- Added fail-fast host environment validation for `swift`, `git`, `xcodebuild`, and `ditto`.
- Added GitHub Enterprise support through configurable `githubServerUrl` and `githubApiUrl`.
- Added bilingual project documentation, CI workflows, plugin publish workflow, and release checklists.

### 中文

- 新增面向 SwiftPM 二进制分发的 KMP XCFramework 端到端 Gradle 发布流水线。
- 新增在 checksum、发布与摘要输出之前执行的 XCFramework 归档结构校验。
- 新增本地路径版 `Package.swift` 生成能力，以及 iOS 消费者 smoke test 流程。
- 新增 GitHub Releases 发布能力，包含已发布资产复用、不可变保护与受控修复覆盖。
- 新增对普通 git checkout 与 git worktree 两种 manifest 仓库同步方式的支持。
- 新增远端可下载产物校验、机器可读 metadata 输出，以及发布摘要报告能力。
- 新增对 `swift`、`git`、`xcodebuild` 与 `ditto` 的主机环境前置校验。
- 新增通过 `githubServerUrl` 与 `githubApiUrl` 支持 GitHub Enterprise 的能力。
- 新增双语项目文档、CI workflow、插件发布 workflow 与 release checklist。

## Unreleased

- No unreleased changes yet.
