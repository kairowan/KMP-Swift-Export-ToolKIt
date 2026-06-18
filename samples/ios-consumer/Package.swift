// swift-tools-version:6.0
import Foundation
import PackageDescription

let environment = ProcessInfo.processInfo.environment
let sharedPackageDependency: Package.Dependency

if let sharedPackagePath = environment["SHARED_PACKAGE_PATH"], !sharedPackagePath.isEmpty {
    sharedPackageDependency = .package(
        name: "shared-package",
        path: sharedPackagePath
    )
} else if let sharedPackageUrl = environment["SHARED_PACKAGE_URL"], !sharedPackageUrl.isEmpty {
    sharedPackageDependency = .package(url: sharedPackageUrl, from: "1.0.0")
} else {
    sharedPackageDependency = .package(url: "https://github.com/yourname/shared-package", from: "1.0.0")
}

let package = Package(
    name: "SmokeConsumer",
    platforms: [
        .iOS("16.0")
    ],
    products: [
        .library(
            name: "SmokeConsumer",
            targets: ["SmokeConsumer"]
        )
    ],
    dependencies: [
        sharedPackageDependency
    ],
    targets: [
        .target(
            name: "SmokeConsumer",
            dependencies: [
                .product(name: "Shared", package: "shared-package")
            ]
        )
    ]
)
