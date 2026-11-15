# Encrypted website backup and restore

PocketHost can export the currently deployed **static website** as a portable, passphrase-protected `.afbackup` file. The backup can be restored on another PocketHost installation when the same passphrase is available.

## Security format

- AES-256-GCM authenticated encryption.
- 128-bit authentication tag.
- 16-byte random salt and 12-byte random nonce for each export.
- PBKDF2-HMAC-SHA-256 with 210,000 iterations derives the encryption key from the user passphrase.
- A minimum 12-character passphrase is required.
- The authenticated ciphertext is fully decrypted to an app-private temporary file before the ZIP importer can activate a deployment. A wrong passphrase or modified backup therefore cannot replace the active website.
- ZIP extraction retains the normal path-traversal, file-count, nesting-depth, compressed-size, uncompressed-size and root `index.html` validation limits.

## Included and excluded data

Included:
- Files and folders in the active static website deployment.

Excluded intentionally:
- Cloudflare tunnel token and credentials.
- Python project source and dependencies.
- Device-specific preferences, ports and network permissions.
- Deployment history and previous releases.

This design avoids exporting secrets or assuming that app-private configuration can be safely moved to another device. Reconfigure Cloudflare and Python separately after restoring a site.

## Operational notes

- Export and restore are disabled while hosting is active so that a deployment cannot change underneath the backup operation.
- The passphrase is not stored by PocketHost and cannot be recovered.
- Keep at least one independent copy of the backup and store the passphrase separately.
- Review the selected destination and protect the resulting backup file like any other confidential website archive.
- A restore is validated and staged by the same importer used for normal ZIP deployments; the active site is preserved if validation or activation fails.

## Compatibility

The backup format is versioned by its fixed header. The current format identifier is `AFPHB01\n`. Future incompatible formats must use a new identifier rather than silently changing the meaning of existing backups.
