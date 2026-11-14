#!/usr/bin/env bash
set -euo pipefail

# Build pinned upstream cloudflared for Android using the Android NDK.
VERSION="2026.11.0"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "$NDK" || ! -d "$NDK" ]]; then
  echo "ANDROID_NDK_HOME must point to the installed Android NDK" >&2
  exit 2
fi
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/.build/cloudflared-$VERSION"
OUT="$ROOT/app/src/main/jniLibs"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
mkdir -p "$WORK" "$OUT"

if [[ ! -d "$WORK/.git" ]]; then
  git clone --depth 1 --branch "$VERSION" https://github.com/cloudflare/cloudflared.git "$WORK"
fi
cd "$WORK"
test "$(git describe --tags --exact-match HEAD)" = "$VERSION"

build_one() {
  local abi="$1" arch="$2" compiler="$3"
  local output="$OUT/$abi/libcloudflared.so"
  mkdir -p "$(dirname "$output")"
  echo "Building cloudflared $VERSION for $abi"
  if [[ "$arch" == "arm" ]]; then
    GOOS=android GOARCH=arm GOARM=7 CGO_ENABLED=1 CC="$TOOLCHAIN/$compiler" \
      go build -trimpath -buildvcs=false -ldflags="-s -w" -o "$output" ./cmd/cloudflared
  else
    GOOS=android GOARCH="$arch" CGO_ENABLED=1 CC="$TOOLCHAIN/$compiler" \
      go build -trimpath -buildvcs=false -ldflags="-s -w" -o "$output" ./cmd/cloudflared
  fi
  chmod 755 "$output"
  file "$output"
}

build_one arm64-v8a arm64 aarch64-linux-android26-clang
build_one armeabi-v7a arm armv7a-linux-androideabi26-clang
build_one x86_64 amd64 x86_64-linux-android26-clang

echo "Built pinned cloudflared $VERSION Android binaries."
