#!/usr/bin/env bash
# Build the Alpine + Node 22 + DeepSeek Harness guest image used by the
# Niobium full iOS build. This runs entirely on the Mac through the emulator
# CLI, exactly like dsh-ios. Requires: meson, ninja, ld.lld (brew install lld).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
IOS="$(cd "$HERE/.." && pwd)"
DSH="$IOS/ThirdParty/dsh-ios"

if [ ! -d "$DSH/ish-arm64" ]; then
  echo "error: dsh-ios submodule is missing. Run: git submodule update --init --recursive" >&2
  exit 1
fi
export PATH="/opt/homebrew/bin:$PATH"

make -C "$DSH" emulator rootfs
mkdir -p "$IOS/Resources"
cp "$DSH/build/root.tar.gz" "$IOS/Resources/root.tar.gz"
cp "$DSH/build/root.tar.gz.sha256" "$IOS/Resources/root.tar.gz.sha256"
( cd "$DSH/ish-arm64/deps/libapps" && ./hterm/bin/mkdist )
echo "Guest image ready: $IOS/Resources/root.tar.gz"
