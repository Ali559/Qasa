// swift-tools-version:5.7
import PackageDescription

// Touch ID + Secure Enclave bridge for the Qasa desktop (see ../src/macos.rs). C ABI only,
// no SwiftRs dependency, so it builds offline.
let package = Package(
    name: "QasaBiometric",
    platforms: [.macOS(.v12)],
    products: [
        .library(name: "QasaBiometric", type: .static, targets: ["QasaBiometric"]),
    ],
    targets: [
        .target(name: "QasaBiometric", path: "Sources/QasaBiometric"),
    ]
)
