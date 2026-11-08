fn main() {
    // The Touch ID / Secure Enclave bridge is Swift (CryptoKit has no C API). Decided by
    // the target, not the machine building it, so Windows can be checked from a Mac.
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("macos") {
        swift_rs::SwiftLinker::new("12.0").with_package("QasaBiometric", "./swift/").link();
    }
}
