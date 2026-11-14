# Third-party software notices

## cloudflared

PocketHost's Android tunnel connector builds Cloudflare's upstream cloudflared project from the pinned 2026.11.0 source tag.

- Source: https://github.com/cloudflare/cloudflared
- License: Apache License 2.0
- Included license text: third_party/licenses/cloudflared-APACHE-2.0.txt
- Android cross-build script: scripts/build-cloudflared-android.sh

The Android executable is built from source for each ABI; PocketHost does not relabel a Linux binary as an Android binary. Cloudflared's transitive Go module dependencies retain their respective licenses and notices. The release pipeline must generate and include a complete dependency-license report before a distributable release is published.
