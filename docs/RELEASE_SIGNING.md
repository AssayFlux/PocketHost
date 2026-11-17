# Pocket Host — Release Signing and Provenance

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

The Android host and runtime companion currently use signature-level IPC permission boundaries; where that contract is retained, their release packages must be signed with the same protected release certificate. Debug signing is not a release identity.

Protected workflow inputs currently include:
- POCKETHOST_KEYSTORE_BASE64
- POCKETHOST_KEYSTORE_PASSWORD
- POCKETHOST_KEY_ALIAS
- POCKETHOST_KEY_PASSWORD

Store these only in protected GitHub Actions secrets and release environments. Never commit keystores or private keys.

## Cross-platform signing

- iOS: Apple signing/provisioning for the selected distribution channel.
- Linux: signed package/repository metadata and verified agent artifacts.
- Windows: signed installer and service binaries.
- macOS: Developer ID signing/notarization where applicable.
- MCU: signed firmware and OTA metadata with device-side verification/rollback.

Do not reuse Android APK signing keys as device identity or firmware signing keys.

## Build and provenance

Use pinned toolchains and lockfiles. Verify upstream archives against pinned digests. Build only from reviewed commits. Generate SBOM/license inventory, sign after build, verify signatures, publish checksums and provenance. Never include owner secrets, device keys or runtime credentials.

## Release gate

Signing is G4; release provenance and owner approval are G9. A successful build is not a signed release. A signed artifact is not physical-device acceptance. All G0–G9 gates apply.
