#!/bin/bash
# Build rust-core for Android using cargo-ndk
# Targets: arm64-v8a (physical Seeker device) + x86_64 (Android Studio emulator)
# Run from WSL or Linux — requires cargo-ndk and Android NDK

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
RUST_CORE_DIR="$PROJECT_ROOT/rust-core"
# Let cargo-ndk handle the ABI subdirectories inside jniLibs
OUTPUT_DIR="$PROJECT_ROOT/app/src/main/jniLibs"

echo "🔧 Building rust-core for Android..."
echo "   Source: $RUST_CORE_DIR"
echo "   Output: $OUTPUT_DIR"

# Ensure output directory exists
mkdir -p "$OUTPUT_DIR"

cd "$RUST_CORE_DIR"

# Build both targets in one go or sequentially
# cargo-ndk takes multiple -t arguments
echo ""
echo "📱 Building arm64-v8a (Seeker) + 🖥️ x86_64 (Emulator)..."
cargo ndk -t arm64-v8a -t x86_64 -o "$OUTPUT_DIR" build --release

echo ""
echo "🚀 All targets built successfully!"
ls -R "$OUTPUT_DIR" | grep ".so"
