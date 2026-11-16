# Production Release Signing

**Baseline:** 2026-11-16  
**Current status:** debug APK packaging only; no verified signed production release.

## Security rules

- Never commit keystores, passwords, private keys or Cloudflare tokens.
- Never paste signing secrets into chat, issues or logs.
- Use a dedicated release keystore backed up securely by the owner.
- Restrict GitHub Actions environment access and require approval where available.
- Do not reuse debug signing credentials for production.

## Required GitHub Actions secrets

| SECRET | PURPOSE |
|---|---|
| `POCKETHOST_KEYSTORE_BASE64` | Base64-encoded release keystore |
| `POCKETHOST_KEYSTORE_PASSWORD` | Keystore password |
| `POCKETHOST_KEY_ALIAS` | Signing key alias |
| `POCKETHOST_KEY_PASSWORD` | Key password |

The release workflow must decode the keystore to a temporary runner path, use it for host and companion release APKs, and remove it even on failure. Host and companion must use the same signing certificate because the companion Binder service uses a signature-level permission.

## Acceptance

1. Build host and companion release variants in a protected workflow.
2. Verify both with `apksigner verify --verbose --print-certs`.
3. Confirm the expected matching signer certificate.
4. Inspect package IDs, version, minSdk and ABI contents.
5. Generate SHA-256 checksums and attach them to release.
6. Verify install/upgrade on supported API levels.
7. Download and validate the actual artifacts.

## Current boundary

Debug APK packaging passed, but a previous artifact retrieval returned a truncated ZIP. It is not distributable. Production signing remains blocked until owner-controlled secrets are configured and release workflow passes. Debug signing is not production signing.
