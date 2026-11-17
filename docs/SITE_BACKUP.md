# Pocket Host — Website Backup and Restore Contract

## Purpose

Protect the active static website from accidental replacement, import failure and device migration. Backup/restore is part of the v0.1.0 hosting milestone; distributed multi-device backup is later.

## Current Android implementation

The repository contains SiteBackupManager, a portable passphrase-encrypted backup implementation for the active static website. It uses AES-GCM authenticated encryption and a passphrase-derived key. Export currently requires the native website server to be stopped. The backup is a website archive, not a full phone image.

## v0.1.0 limits

- Website source content: up to 500,000,000 bytes.
- Backup archive output: capped at 500,000,000 bytes by the current safety limit.
- Maximum 5,000 files and 32 directory levels.
- A compressed backup may be smaller than the source, but encrypted ZIP overhead can cause an archive near the source limit to exceed the backup cap. A clear error must be shown; never truncate silently.
- Import/restore validates the staged directory, paths, names, file count, total entries, depth and total bytes before activation.

## Included and excluded data

The current portable website backup covers the active static website. Cloudflare tokens, owner/device private keys, runtime credentials, runtime project state, control-plane sessions and unrelated Android settings are intentionally excluded. Do not claim full application backup.

## Passphrase handling

- Require at least 12 characters.
- Never log or transmit the passphrase.
- Do not store the passphrase in plaintext.
- The user must retain the passphrase; recovery is not possible if it is lost.
- Authenticate the backup before activation; corrupted or wrong-passphrase data must not replace the active site.

## Restore lifecycle

1. Stop hosting where required by the current implementation.
2. Select backup and enter passphrase.
3. Decrypt into an app-private staging location.
4. Validate archive paths, entries, file count, expanded bytes and index.html.
5. Preserve the active site and record a recovery snapshot.
6. Activate the restored site atomically.
7. Recover last-known-good if the process stops during activation.
8. Restart hosting and verify the restored website.

## v0.2.0 expansion

Remote encrypted namespaces, worker-assisted backup, off-device replication, device replacement and restore orchestration are later features and require their own role/security acceptance.

## Acceptance

Test wrong passphrase, tampering, truncated archive, oversized archive, path traversal, duplicate/unsafe entries, low storage, process death during restore, restart recovery, active-site preservation and a successful website smoke test. See G0–G9.
