# Pocket Host 0.1.0 — Backup and Restore Contract

## Goals

Backups protect service configuration and user-owned data across host failure, device replacement and deployment mistakes. Backup and restore are host-local capabilities coordinated by the mobile command center; the phone does not need to remain online during a host's backup or restore operation.

## Backup scope

A backup manifest identifies:
- Product and component versions.
- Host-independent service definitions and schema versions.
- Site/component source and immutable deployment releases.
- Persistent volumes and supported database snapshots.
- Hostname/site routing metadata.
- Non-secret operational settings.
- Artifact digests, file counts, sizes and timestamps.

Device-bound identity keys, private keys, active sessions, tunnel tokens and platform keystore material are excluded. A restored host must enroll as a new device and receive fresh credentials.

## Encryption and integrity

- Use authenticated encryption with a modern, reviewed construction.
- Derive passphrase keys using a memory-hard KDF where supported and version its parameters.
- Use unique random salt and nonce per backup.
- Authenticate the manifest and all payloads before activation.
- Sign backup metadata where account/device signing infrastructure supports it.
- Never store the passphrase or plaintext secrets in the backup.
- Verify artifact hashes, sizes, file counts and schema compatibility before extraction.

## Restore lifecycle

1. Verify format, signature, encryption and compatibility.
2. Decrypt to a restricted staging area.
3. Validate all paths, file types, quotas and schemas.
4. Restore data into a new staged component/volume.
5. Run component-specific validation and health checks.
6. Atomically activate the restored deployment.
7. Preserve last-known-good data until the operation is confirmed.
8. Record an audit event and allow rollback.

## Fault isolation

- A failed site restore does not overwrite unrelated sites.
- A corrupt component archive does not invalidate other backups.
- Storage exhaustion preserves the active release and recovery material.
- Interrupted restore resumes or rolls back deterministically.
- Restore of one host does not clone its device identity or secrets.

## Android provider

The Android application currently has an encrypted application backup implementation for supported website/runtime data and selected settings. It is an Android-specific provider, not yet a universal multi-host backup service. Its exact supported contents and limits must be read from the implementation and verified against the build under test.

## Acceptance

Test wrong passphrase, tampering, truncated archive, path traversal, symlink, duplicate entry, archive bomb, low disk, process kill, power loss, partial volume restore, schema incompatibility, key rotation and full host replacement. Perform real restore rehearsals, not export-only tests.
