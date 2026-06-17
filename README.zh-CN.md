[简体中文](README.zh-CN.md) | [English](README.md)

# KMP Apple Packager

KMP Apple Packager 是一个 Gradle 插件，用来自动完成 Apple XCFramework 构建、zip 打包、SwiftPM checksum 计算、`Package.swift` 生成、GitHub Releases 上传，以及最终的包校验。

## 为什么做这个

把 Kotlin Multiplatform 库交付给原生 Swift 团队时，通常还要手工处理很多重复步骤：

- 构建 XCFramework
- 压缩产物
- 计算 checksum
- 编写或更新 `Package.swift`
- 把产物上传到可公开访问的地址
- 确认 SwiftPM 真的可以解析并使用这个包

这个插件的目标，就是把这条流程变成稳定、可重复、可放进 CI 的发布管线。

## 功能

- 为 Apple 目标构建 release XCFramework
- 以保留 `.xcframework` 结构的方式打 zip 包
- 在发布前校验 XCFramework slice 和 zip 结构
- 自动计算 SwiftPM checksum
- 为 binary target 生成 `Package.swift`
- 可为 iOS 以及更多 Apple 平台声明 SwiftPM deployment target
- 在重型构建开始前先做发布配置校验，尽早失败
- 把归档文件发布到 GitHub Releases
- 如果同名 GitHub Release asset 的 checksum 已一致就直接复用；同 tag 冲突时默认安全失败
- 把 `Package.swift` 同步到独立仓库或单独发布分支
- 同步 manifest 仓库时同时支持普通 git checkout 和 git worktree
- 用 `swift package` 校验生成出来的 manifest
- 下载并校验最终可访问的产物，确认远端 checksum 与本地一致
- 为 CI 和后续自动化产出机器可读的发布元数据
- 可配置 GitHub 请求重试/超时和本地命令超时
- 默认阻止在已有未提交改动的 manifest 仓库上继续发布
- 在控制台输出版本、checksum、URL 和产物路径摘要

## 项目结构

```text
kmp-apple-packager/
├── plugin/
├── samples/
│   ├── kmp-library/
│   └── ios-consumer/
├── docs/
└── .github/workflows/
```

## 快速开始

```kotlin
plugins {
    id("io.github.haonan.kmp.apple.packager") version "0.1.0"
}

kmpApplePackager {
    packageName.set("Shared")
    version.set("0.1.0")
    artifactModule.set(":shared")
    githubRepo.set("yourname/shared-package")
    manifestRepository.set("yourname/shared-package-spm")
    manifestRepositoryBranch.set("main")
    manifestCommitUserName.set("CI Release Bot")
    manifestCommitUserEmail.set("ci@example.com")
    iosTargets.set(listOf("iosArm64", "iosSimulatorArm64"))
    minimumMacosVersion.set("13.0")
    swiftExecutable.set("swift")
    gitExecutable.set("git")
    commandTimeoutSeconds.set(600)
    githubRequestTimeoutSeconds.set(120)
    githubMaxRetries.set(2)
    overwriteExistingReleaseAsset.set(false)
    verifyPublishedArtifact.set(true)
    artifactDownloadTimeoutSeconds.set(300)
    artifactDownloadMaxRetries.set(2)
}
```

然后执行：

```bash
./gradlew publishApplePackage
```

## 核心任务

- `assembleAppleXCFramework`
- `zipAppleArtifact`
- `computeApplePackageChecksum`
- `validateApplePackagerConfiguration`
- `generateApplePackageManifest`
- `publishGithubRelease`
- `publishPackageManifestRepository`
- `validateSwiftPmPackage`
- `verifyPublishedArtifact`
- `validateAppleArtifactStructure`
- `writeApplePackageMetadata`
- `publishApplePackage`

## 本地 MVP 说明

本地 dry-run 时，可以先关闭远端发布和校验：

```kotlin
kmpApplePackager {
    publishRelease.set(false)
    publishManifestRepository.set(false)
    validatePackage.set(false)
}
```

`samples/kmp-library` 默认就是这样配置的，所以这个仓库可以在没有真实 GitHub Release 目标的情况下直接跑通骨架。

如果你继续配置了 `minimumMacosVersion`、`minimumTvosVersion`、`minimumWatchosVersion`、
`minimumVisionosVersion` 或 `minimumMacCatalystVersion`，记得让这些声明和最终发布出去的
XCFramework 实际包含的平台切片保持一致。

每次运行还会额外生成一个稳定的 JSON 元数据文件：
`build/kmpApplePackager/metadata/package-metadata.json`。CI 可以直接读取它来拿 checksum、
最终 artifact URL、release asset 状态、校验状态以及 manifest 仓库同步结果。

面向生产流水线时，当前默认运行策略也已经固定下来：

- `commandTimeoutSeconds=600`，用于 `swift`、`git`、`ditto` 这类本地命令
- `githubRequestTimeoutSeconds=120` 且 `githubMaxRetries=2`，用于 GitHub Releases API
- `overwriteExistingReleaseAsset=false`，同一个 tag 上默认绝不覆盖已经发布出去的 zip，除非你显式打开
- `verifyPublishedArtifact=true`，并使用 `artifactDownloadTimeoutSeconds=300`、`artifactDownloadMaxRetries=2` 做发布后下载校验
- `failOnDirtyManifestRepository=true`，本地 manifest 仓库如果本来就有脏改动会直接拒绝继续发布

如果目标 GitHub release tag 下已经存在同名 asset，插件现在会：

- checksum 一致时直接复用已有 asset
- checksum 不一致时默认直接失败
- 只有在 `overwriteExistingReleaseAsset=true` 时才允许替换

如果开启 manifest 仓库发布，`manifestRepositoryPath` 现在既可以指向普通 git checkout，
也可以指向 git worktree。插件管理的远端 checkout 会在切分支前刷新远端引用；如果没有显式
设置 `manifestCommitUserName` 和 `manifestCommitUserEmail`，则会回退到所选 checkout 的
`git config user.name/user.email`。

## 示例

- `samples/kmp-library`：最小可运行的 KMP 库示例，通过 `includeBuild("../..")` 使用本仓库里的插件
- `samples/ios-consumer`：一个简单的 Swift 包消费者示例，用来说明最终消费侧的导入方式

## 文档

- [快速开始](docs/quick-start.md)
- [CI 指南](docs/ci.md)
- [常见问题](docs/faq.md)

## 当前状态

这是一个早期 MVP。项目目标是把 KMP 到 SwiftPM 的分发流程做得更无聊、更稳定、更容易自动化。

## 灵感来源

这个项目建立在官方 Kotlin Multiplatform Apple 导出流程之上，重点补的是“发布自动化层”这一块空白。
