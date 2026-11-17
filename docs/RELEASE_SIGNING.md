# Pocket Host 0.1.0 — Release Signing and Provenance

## Release identity

Every distributed artifact declares:
- Product/component name and version.
- Source commit SHA and build workflow/run.
- Target OS, architecture, ABI and minimum platform version.
- API/protocol compatibility.
- Artifact SHA-256 and signature identity.
- SBOM and license inventory.
- Installation, upgrade and rollback instructions.

## Android packages

The Android host and runtime companion must be signed with the same protected release certificate because the host/companion IPC contract uses a signature-level permission. Debug signing is not a release identity.

Release workflow inputs:
- `POCKETHOST_KEYSTORE_BASE64`
- `POCKETHOST_KEYSTORE_PASSWORD`
- `POCKETHOST_KEY_ALIAS`
- `POCKETHOST_KEY_PASSWORD`

Store these only in protected GitHub Actions secrets and a protected release environment. Never commit a keystore, signing password or private key.

## Cross-platform signing

- iOS: Apple signing identity, provisioning and notarization/distribution requirements for the chosen channel.
- Linux: signed package/repository metadata and verified host-agent artifacts.
- Windows: signed installer and service binaries.
- macOS: Developer ID signing and notarization where distributed outside the App Store.
- MCU: signed firmware and OTA metadata with device-side verification and rollback policy.

Each platform uses its own trust store and signing identity. Do not reuse Android APK signing keys as fleet device identity keys or firmware signing keys.

## Build requirements

- Reproducible dependency lockfiles and pinned toolchains.
- Verify downloaded upstream runtime archives against pinned digests.
- Build only from a reviewed commit.
- Generate SBOM and license inventory.
- Sign artifacts after build and verify signatures before publication.
- Publish checksums and provenance alongside artifacts.
- Never include owner secrets, device keys or runtime credentials in artifacts.

## Release gate

A successful build is not a signed release. A signed artifact is not platform acceptance. A release requires signature verification, exact target-device tests, upgrade/rollback, recovery, security review and a support matrix.
