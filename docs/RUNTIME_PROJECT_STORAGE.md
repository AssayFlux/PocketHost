# Runtime project storage boundary

Host-side Python and Node project roots are validated as canonical descendants of Pocket Host's private `files`, `cache`, `noBackup` or `codeCache` directories before transfer.

The host packages the selected project into a bounded ZIP archive and passes a read-only file descriptor over the signature-protected, versioned companion Binder API. The companion validates each archive entry, rejects path traversal and common credential paths, enforces per-file and total extracted-size limits, and extracts into its own private `files/projects/<site-id>` directory using a staging-and-rename update.

Current bounds: 10,000 entries, 64 MiB per extracted file and 256 MiB total extracted project size. Symlinked source entries are rejected. Common `.env*`, credential, SSH/cloud credential and private-key files are excluded or rejected. This is a practical transfer filter, not a comprehensive secret scanner.

Python and Node execute under the companion app UID, not the host UID. The companion UID still has access to all projects within the companion package; per-site filesystem isolation and enforceable network-egress controls remain incomplete. Only owner-controlled trusted code may execute. Do not store credentials in runtime project folders or expose arbitrary third-party code.
