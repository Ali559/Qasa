#!/usr/bin/env bash
# Builds QasaFFI.xcframework (device + Apple Silicon simulator) and the Swift bindings
# for the iOS app. Run from anywhere: `make ios-ffi`.
set -euo pipefail
CORE="$(cd "$(dirname "$0")/../.." && pwd)"
IOS="$CORE/../apps/ios"
OUT="$IOS/Frameworks/QasaFFI.xcframework"
export CARGO_INCREMENTAL=0
# rustup's toolchain has the iOS standard libraries (Homebrew's cargo does not).
CARGO="${CARGO:-$HOME/.cargo/bin/cargo}"
export RUSTC="${RUSTC:-$HOME/.cargo/bin/rustc}"

cd "$CORE"
for target in aarch64-apple-ios aarch64-apple-ios-sim; do
  "$CARGO" build -p qasa-ffi --lib --release --target "$target"
done

# Bindings from the host build (same API).
"$CARGO" build -p qasa-ffi --lib
HEADERS="$(mktemp -d)"
"$CARGO" run -q -p qasa-ffi --bin uniffi-bindgen -- generate \
  --library target/debug/libqasa_ffi.dylib --language swift --out-dir "$HEADERS"
mv "$HEADERS/qasa_ffiFFI.modulemap" "$HEADERS/module.modulemap"
mkdir -p "$IOS/qasa_ios/Services/Sync"
mv "$HEADERS/qasa_ffi.swift" "$IOS/qasa_ios/Services/Sync/QasaFFI.swift"

rm -rf "$OUT"
xcodebuild -create-xcframework \
  -library target/aarch64-apple-ios/release/libqasa_ffi.a -headers "$HEADERS" \
  -library target/aarch64-apple-ios-sim/release/libqasa_ffi.a -headers "$HEADERS" \
  -output "$OUT" >/dev/null
rm -rf "$HEADERS"
echo "built $OUT"
