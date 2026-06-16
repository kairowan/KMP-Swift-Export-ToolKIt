// swift-tools-version:6.0
import PackageDescription

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
        .package(url: "https://github.com/yourname/shared-package", from: "0.1.0")
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

