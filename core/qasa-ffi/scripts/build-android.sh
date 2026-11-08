#!/usr/bin/env bash
# Builds libqasa_ffi.so for the Android app (arm64 phones, armv7 phones, x86_64 emulator)
# and the Kotlin bindings. Run from anywhere: `make android-ffi`. Needs cargo-ndk and the NDK.
set -euo pipefail
CORE="$(cd "$(dirname "$0")/../.." && pwd)"
APP="$CORE/../apps/android/app/src/main"
export CARGO_INCREMENTAL=0
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$(ls -d "$HOME"/Library/Android/sdk/ndk/* | sort -V | tail -1)}"
CARGO="${CARGO:-$HOME/.cargo/bin/cargo}"
export RUSTC="${RUSTC:-$HOME/.cargo/bin/rustc}"

cd "$CORE"
"$CARGO" ndk -t arm64-v8a -t armeabi-v7a -t x86_64 --platform 26 -o "$APP/jniLibs" build -p qasa-ffi --lib --release

"$CARGO" build -p qasa-ffi --lib
"$CARGO" run -q -p qasa-ffi --bin uniffi-bindgen -- generate \
  --library target/debug/libqasa_ffi.dylib --language kotlin --out-dir "$APP/java"
echo "built $APP/jniLibs and $APP/java/uniffi/qasa_ffi/qasa_ffi.kt"
