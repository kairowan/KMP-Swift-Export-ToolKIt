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
- 自动计算 SwiftPM checksum
- 为 binary target 生成 `Package.swift`
- 把归档文件发布到 GitHub Releases
- 把 `Package.swift` 同步到独立仓库或单独发布分支
- 用 `swift package` 校验生成出来的 manifest
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
    iosTargets.set(listOf("iosArm64", "iosSimulatorArm64"))
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
- `generateApplePackageManifest`
- `publishGithubRelease`
- `publishPackageManifestRepository`
- `validateSwiftPmPackage`
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
