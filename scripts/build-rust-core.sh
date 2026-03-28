#!/bin/bash
# Build rust-core for Android (arm64-v8a) using cargo-ndk
# Run from WSL or Linux — requires cargo-ndk and Android NDK

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
RUST_CORE_DIR="$PROJECT_ROOT/rust-core"
OUTPUT_DIR="$PROJECT_ROOT/app/src/main/jniLibs/arm64-v8a"

echo "🔧 Building rust-core for Android arm64-v8a..."
echo "   Source: $RUST_CORE_DIR"
echo "   Output: $OUTPUT_DIR"

# Ensure output directory exists
mkdir -p "$OUTPUT_DIR"

# Build using cargo-ndk
cd "$RUST_CORE_DIR"
cargo ndk -t arm64-v8a -o "$OUTPUT_DIR" build --release

echo "✅ librust_core.so built successfully"
ls -la "$OUTPUT_DIR/librust_core.so"
