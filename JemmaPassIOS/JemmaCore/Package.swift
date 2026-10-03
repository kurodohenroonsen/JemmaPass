// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "JemmaCore",
    platforms: [
        .iOS(.v17),
        .macOS(.v14)
    ],
    products: [
        .library(
            name: "JemmaCore",
            targets: ["JemmaCore"]
        ),
    ],
    targets: [
        .target(
            name: "JemmaCore",
            dependencies: []
        ),
        .testTarget(
            name: "JemmaCoreTests",
            dependencies: ["JemmaCore"]
        ),
    ]
)
