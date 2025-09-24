// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "GtBackgroundGeolacation",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "GtBackgroundGeolacation",
            targets: ["GtBackgroundGeolocationPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.0.0")
    ],
    targets: [
        .target(
            name: "GtBackgroundGeolocationPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/GtBackgroundGeolocationPlugin"),
        .testTarget(
            name: "GtBackgroundGeolocationPluginTests",
            dependencies: ["GtBackgroundGeolocationPlugin"],
            path: "ios/Tests/GtBackgroundGeolocationPluginTests")
    ]
)