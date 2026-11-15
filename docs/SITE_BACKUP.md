# Encrypted application backup and restore

Pocket Host exports a portable, passphrase-encrypted `.afbackup` containing the deployed static website, optional trusted Python and Node project directories, hostname-to-project routes, and a small allowlist of non-secret settings.

## Format and cryptography

- Current format identifier: `AFPHB02\\n`.
- AES-256-GCM authenticated encryption with a 128-bit authentication tag.
- 16-byte random salt and 12-byte random nonce for each export.
- PBKDF2-HMAC-SHA-256 with 210,000 iterations derives the encryption key.
- A minimum 12-character passphrase is required; the passphrase is never stored.
- Ciphertext authentication is completed before archive extraction or deployment activation.
- The decrypted archive is staged in app-private storage and validated before replacing active projects.

## Included data

- Active static website files and folders (`files/site`).
- Optional Python WSGI project (`files/python-project`).
- Optional Node.js project (`files/node-project-default`), including prebundled dependencies when present.
- Registered hostname routes for the supported static, Python and Node project roots.
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
- The archive is capped at 20,000 files, 30,000 entries and 32 directory levels.
- Path traversal, absolute paths, symbolic links, duplicate paths and sensitive filenames are rejected.
- Python projects must contain `wsgi.py`; Node projects must contain a valid `package.json` and a safe entry file; static sites must contain root `index.html`.
- Hostnames are revalidated for syntax, uniqueness and route limits before the route table is replaced.
- Existing runtime directories and the previous static deployment are preserved during activation. If a later restore step fails, the manager attempts rollback; if rollback itself cannot complete, the recovery directory is preserved rather than deleted.
- Older `AFPHB01\\n` static-website backups remain restorable. They restore only the website and do not alter runtime projects or settings.

## Operational guidance

- Keep the backup file and passphrase separately; anyone with both can recover the included project source.
- Treat Python and Node project backups as executable source code and do not share them with untrusted parties. Secret values embedded inside ordinary source/config files cannot be detected reliably; review project contents before sharing.
- Keep an independent copy of important backups. A successful export does not replace an off-device disaster-recovery copy.
