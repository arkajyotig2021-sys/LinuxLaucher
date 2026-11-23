#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PIN="2b12909a74373b2ba1621b1de253f0225f8a137d"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$ROOT/third_party" "$ROOT/proot-engine"
curl -fsSL "https://codeload.github.com/oonid/pr/tar.gz/$PIN" -o "$ROOT/third_party/oonid-pr-source.tar.gz"
tar -xzf "$ROOT/third_party/oonid-pr-source.tar.gz" -C "$TMP" --strip-components=1
cp -a "$TMP/android/proot-engine/." "$ROOT/proot-engine/"
cp "$TMP/LICENSE" "$ROOT/third_party/PRootEngine-LICENSE.txt"
sed -i 's/compileSdk = 37/compileSdk = 35/; s/minSdk = 28/minSdk = 24/' "$ROOT/proot-engine/build.gradle.kts"
sed -i 's/id("com.android.library")/id("com.android.library")\n    id("org.jetbrains.kotlin.android")/' "$ROOT/proot-engine/build.gradle.kts"
