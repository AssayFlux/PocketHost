# Pocket Host — Backup and Restore Contract

## Purpose

Protect active website data and supported runtime project data. The current Android user-facing flow is a **full-app backup**, not a website-only 500 MB archive. Backup capacity is independent of the website importer quota.

## Current Android implementation

The app UI calls ApplicationBackupManager. It creates a passphrase-encrypted archive with authenticated encryption and a versioned header. It can include:
- Active static website tree.
- Optional Python project data.
- Optional Node.js project data.
- Hostname routes and a small allow-list of non-secret settings.

It deliberately excludes Android Keystore credentials, owner/operator accounts, Cloudflare tokens, remote-control tokens, sessions and device-bound keys. Restored runtimes remain stopped and require a fresh trusted-code acknowledgement.

## Current limits (source-audited)

| Item | Current limit |
|---|---:|
| Static website root | 50 MiB |
| Python project | 50 MiB |
| Node.js project | 100 MiB |
| Total uncompressed backup | 200 MiB |
| Encrypted archive | 220 MiB |
| Total files | 20,000 |
| Total entries | 30,000 |
| Directory depth | 32 |

The current implementation therefore cannot back up a static website near the new 500 MB import allowance. **This is an open v0.1.0 capacity mismatch.** Before release, either raise backup limits safely and test them or explicitly approve, expose and document the lower backup ceiling. Do not claim a 500 MB imported site can be fully backed up.

## Passphrase handling

Use at least 12 characters. Never log, transmit or store the passphrase in plaintext. The user must retain it; recovery is not possible if it is lost. Authentication failure or archive corruption must not replace active data.

## Current operational behavior

The UI disables export/restore while hosting or optional runtimes are active. Stop the host and runtimes before starting the operation. Do not kill the process or delete staging directories during backup/restore.

## Restore lifecycle

1. Select the backup and enter the passphrase.
2. Decrypt to a private temporary archive.
3. Validate archive roots, paths, names, file/entry/depth and byte quotas.
4. Extract into private staging.
5. Preserve current data and record transaction state.
6. Apply validated static/runtime data and safe settings.
7. Keep restored runtimes stopped.
8. Recover interrupted restore before hosting resumes.

## v0.2.0 expansion

Remote encrypted namespaces, worker-assisted backup, off-device replication, device replacement and distributed restore orchestration are later features.

## Acceptance

Test wrong passphrase, tampering, truncated archive, oversized compressed/decrypted data, traversal, duplicate paths, low storage, process death during restore, recovery, active-site preservation and a successful website smoke test. Add a dedicated test for the agreed relationship between 500 MB imports and backup limits. Apply G0–G9.
