# Encrypted application backup and restore

Pocket Host exports a portable, passphrase-encrypted `.afbackup` containing the deployed static website, optional trusted Python and Node project directories, hostname-to-project routes, and a small allowlist of non-secret settings.

## Format and cryptography

- Current format identifier: `AFPHB02\\n`.
- AES-256-GCM authenticated encryption with a 128-bit authentication tag.
- 16-byte random salt and 12-byte random nonce for each export.
- PBKDF2-HMAC-SHA-256 with 210,000 iterations derives the encryption key.
- A 12–128 character passphrase is required; the passphrase is never stored.
- Ciphertext authentication is completed before archive extraction or deployment activation.
- The decrypted archive is staged in app-private storage and validated before replacing active projects.

## Included data

- Active static website files and folders (`files/site`).
- Optional Python WSGI project (`files/python-project`).
- Optional Node.js project (`files/node-project-default`), including prebundled dependencies when present.
- Registered hostname routes for the supported static, Python and Node project roots.
- Registered custom static roots inside app-private storage, stored under a version-3 `custom-static/<site-id>` archive prefix and restored under `files/site-roots/<site-id>`.
- Non-secret settings: server/runtime ports, keep-awake preference, boot-start preference and light/dark theme.

## Excluded data

- Cloudflare tunnel tokens and credentials.
- Remote-control device tokens and endpoint credentials.
- Owner/Operator accounts, password verifiers, session tokens and Android Keystore keys.
- Device-specific biometric state and other device-bound secrets.
- Environment files, npm auth files, SSH material, private keys and certificate bundles.

Credentials remain device-bound and must be configured independently on a replacement device. Restored Python and Node projects are **not started automatically**. Runtime enablement is reset and the owner must review the trusted-code acknowledgement again before starting them. LAN exposure is reset to off.

## Validation and recovery

- Export and restore require static hosting, Python and Node runtimes to be stopped.
- Static files, Python and Node projects have separate size caps (50 MiB, 50 MiB and 100 MiB); total uncompressed content is capped at 200 MiB.
- The archive is capped at 20,000 files, 30,000 total entries and 200 MiB uncompressed. Default static/Python roots are limited to 5,000 files each; Node roots to 10,000 files. Custom static roots share a combined 5,000-file, 10,000-entry and 50 MiB cap.
- Path traversal, absolute paths, symbolic links, duplicate paths and sensitive filenames are rejected.
- Python projects must contain `wsgi.py`; Node projects must contain a valid `package.json` and a safe entry file; static sites must contain root `index.html`.
- Hostnames are revalidated for syntax, uniqueness and route limits before the route table is replaced.
- Existing runtime directories and the previous static deployment are preserved during activation. If a later restore step fails, the manager attempts rollback; if rollback itself cannot complete, the recovery directory is preserved rather than deleted.
- Route manifest version 3 records each registered static root and its archive mapping. Version-2 route manifests remain restorable.
- Older `AFPHB01\\n` static-website backups remain restorable. They restore only the website and do not alter runtime projects or settings.

## Operational guidance

- Keep the backup file and passphrase separately; anyone with both can recover the included project source.
- Treat Python and Node project backups as executable source code and do not share them with untrusted parties. Secret values embedded inside ordinary source/config files cannot be detected reliably; review project contents before sharing.
- Keep an independent copy of important backups. A successful export does not replace an off-device disaster-recovery copy.
### Project-root compatibility

- Active Python and Node project roots must be inside Pocket Host app-private storage. They are packaged and restored into the standard `python-project` and `node-project-default` locations; hostname routes are remapped to those restored roots.
- The main static website root and registered custom static roots are backed up. Custom roots must be separate real directories inside Pocket Host app-private storage, contain `index.html`, and must not overlap the main site or runtime roots.
- Custom static roots are restored to `files/site-roots/<site-id>`; the hostname registry is rebound to the restored path. Existing custom roots are preserved and rolled back if a later restore step fails.
- Custom Python/Node route roots that differ from the configured Python/Node project roots are still rejected. The configured runtime project roots themselves are backed up and remapped to their standard restore locations.
- Android instrumentation covers a successful custom-root round trip and a duplicate-host failure after staging, asserting that the prior custom root and default static site are restored. API 26/33/35 emulator execution is tracked in the backup regression workflow; do not claim it passed until that run completes.

## Authenticated restore fault coverage — 2026-11-16

- Android instrumentation source now explicitly checks that a wrong passphrase and a one-bit ciphertext modification both fail before changing the active static deployment.
- Existing custom-root round-trip coverage also exercises duplicate-host validation failure and rollback of the previous custom root, default site and route registry.
- These are test definitions only until the API 26/33/35 Android integration workflow executes successfully. Low-storage, interrupted I/O, process-death recovery and rollback-failure preservation still require dedicated fault injection and executed evidence.

## Interrupted deployment recovery — 2026-11-16

If activation is interrupted after the previous static site has been moved into release history, the next app startup checks the active website entry page. When the active site is missing or invalid, Pocket Host restores the newest retained release with a valid root index.html and preserves invalid active data for diagnosis. This recovery path is instrumented in source; API 26/33/35 execution and full backup-restore process-death recovery remain unverified.

## Crash-recoverable full restore transaction — 2026-11-16

Full application restore now writes and fsyncs a versioned recovery journal before moving the existing Python, Node, custom static roots or site registry/settings. The journal records the previous registry snapshot, non-secret settings, previous static-release marker and which project roots existed. Directory changes remain same-filesystem renames; a separate fsynced commit marker is written only after the new site, routes and safe settings have all been applied.

On the next app launch, Pocket Host checks for unfinished restore transactions before ordinary static-site recovery. If a transaction has no commit marker, it restores prior runtime directories and custom roots, rolls the static site back only when a new release appeared after the snapshot, then restores the prior route registry and settings. A committed transaction is only cleaned up. If recovery cannot complete, the journal and remaining rollback files are preserved and an operational error is recorded.

Instrumentation source simulates process interruption after partial Python, static-site, custom-root, route and settings mutation. API 26/33/35 execution, low-storage and real process-kill/device-reboot fault injection remain unverified.

Recovery also verifies all hosting runtimes are stopped before touching transaction files, treats journal/commit markers as no-follow regular files, rejects symlinked transaction directories and rollback roots, and validates custom static rollback trees without following symbolic links.

Recovery runs before both Activity startup and the HostingService ingress startup. This also covers Android boot auto-start and sticky foreground-service restart paths, so a service cannot begin serving a partially restored website before the recovery journal has been resolved.

The recovery journal is capped at 1 MB, the previous route-registry snapshot at 256 KB, and custom-root rollback records at 100. Journal writes are fsynced to a temporary file and atomically renamed before restore mutations begin.

Manual release rollback also handles a missing active site and quarantines an invalid/symlinked active tree rather than treating it as a valid release or deleting its target. Release pruning now considers only safe, non-symlink release trees.

A candidate release or custom static rollback root must also pass the same import limits (5,000 files, 10,000 total entries, 50 MiB and depth 32), contain no symbolic links or special files, and contain no blocked secret-bearing names. Recovery therefore does not reactivate an oversized or unsafe historical tree.

