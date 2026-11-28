#!/usr/bin/env bash
set -euo pipefail

# Build pinned upstream cloudflared for Android using the Android NDK.
VERSION="2026.11.0"
UPSTREAM_COMMIT="b0297f0b24c60a4cef743b7b878e2da3798cb839"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "$NDK" || ! -d "$NDK" ]]; then
  echo "ANDROID_NDK_HOME must point to the installed Android NDK" >&2
  exit 2
fi
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/.build/cloudflared-$VERSION"
OUT="$ROOT/app/src/main/jniLibs"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"

for tool in go git file; do
  command -v "$tool" >/dev/null 2>&1 || { echo "Required tool not found: $tool" >&2; exit 2; }
done
for compiler in aarch64-linux-android26-clang armv7a-linux-androideabi26-clang x86_64-linux-android26-clang; do
  test -x "$TOOLCHAIN/$compiler" || { echo "Android NDK compiler missing: $TOOLCHAIN/$compiler" >&2; exit 2; }
done
mkdir -p "$WORK" "$OUT"

if [[ ! -d "$WORK/.git" ]] || [[ "$(git -C "$WORK" rev-parse HEAD 2>/dev/null || true)" != "$UPSTREAM_COMMIT" ]]; then
  rm -rf "$WORK"
  mkdir -p "$WORK"
  git -C "$WORK" init
  git -C "$WORK" remote add origin https://github.com/cloudflare/cloudflared.git
  git -C "$WORK" fetch --depth 1 origin "$UPSTREAM_COMMIT"
  git -C "$WORK" checkout --detach FETCH_HEAD
fi
cd "$WORK"
test "$(git rev-parse HEAD)" = "$UPSTREAM_COMMIT"
test "$(git describe --tags --exact-match HEAD)" = "$VERSION"

# Include notices for the full statically linked Go dependency graph in the APK.
go install github.com/google/go-licenses@v1.6.0
"$(go env GOPATH)/bin/go-licenses" save ./cmd/cloudflared \
  --save_path="$ROOT/app/src/main/assets/licenses/cloudflared"

# Never leave stale binaries from an earlier or partial ABI build.
for abi in arm64-v8a armeabi-v7a x86_64; do
  rm -f "$OUT/$abi/libcloudflared.so"
done

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
  test -s "$output"
  file "$output"
}

build_one arm64-v8a arm64 aarch64-linux-android26-clang
build_one armeabi-v7a arm armv7a-linux-androideabi26-clang
build_one x86_64 amd64 x86_64-linux-android26-clang

for abi in arm64-v8a armeabi-v7a x86_64; do
  test -s "$OUT/$abi/libcloudflared.so"
  test -x "$OUT/$abi/libcloudflared.so"
done
echo "Built pinned cloudflared $VERSION Android executables for all supported ABIs."
