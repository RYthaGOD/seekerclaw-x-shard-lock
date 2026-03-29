#!/bin/bash
# fix-ndk-links.sh — Repairs broken text-file symlinks in NDK bin directory
NDK_BIN="/home/craig/android-sdk/ndk/android-ndk-r27b/toolchains/llvm/prebuilt/linux-x86_64/bin"

if [ ! -d "$NDK_BIN" ]; then
    echo "Error: NDK bin directory not found at $NDK_BIN"
    exit 1
fi

cd "$NDK_BIN"
echo "Repairing links in $NDK_BIN..."

count=0
for f in *; do
    # Check if it's a regular file (not a symlink) and very small
    if [ -f "$f" ] && [ ! -L "$f" ]; then
        size=$(stat -c%s "$f")
        if [ "$size" -lt 500 ]; then
            content=$(cat "$f")
            # If content is a single word (no spaces, look like a filename)
            if [[ "$content" =~ ^[a-zA-Z0-9.\-@]+$ ]]; then
                if [ -f "$content" ]; then
                    # echo "Linking $f -> $content"
                    ln -sf "$content" "$f"
                    ((count++))
                fi
            fi
        fi
    fi
done

echo "✅ Repaired $count broken symlinks."
