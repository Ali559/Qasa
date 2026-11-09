fn main() {
    // The Touch ID / Secure Enclave bridge is Swift (CryptoKit has no C API). Only a Mac can
    // build it, and only a macOS target needs it. Checking Windows code from a Mac
    // (`cargo check --target x86_64-pc-windows-msvc`) compiles this script for the Mac but
    // skips the Swift build.
    #[cfg(target_os = "macos")]
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("macos") {
        swift_rs::SwiftLinker::new("12.0").with_package("QasaBiometric", "./swift/").link();
    }
}
