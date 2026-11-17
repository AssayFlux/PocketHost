# Pocket Host 0.1.0 — Project Storage Contract

## Ownership

Every website component has a stable project identity and a dedicated data namespace. The host supervisor owns metadata and policy. A runtime cell receives only its own project files, explicitly granted persistent volumes and short-lived scoped capabilities.

## Android provider behavior

The Android host validates project roots as canonical descendants of approved app-private storage. Project transfer to the runtime companion uses bounded archives and a read-only descriptor over the protected Binder contract. The companion validates entries, rejects traversal and unsafe paths, enforces file/count/expanded-size limits, and extracts into a staged component directory before activation.

The companion package UID is shared by its projects today; therefore storage placement and archive validation do not provide per-project OS isolation. Only owner-trusted code may execute until distinct per-project isolation and egress denial pass adversarial tests.

## Cross-platform storage contract

Each host adapter declares:
- Project root and volume identity.
- Read/write/execute permissions.
- Quotas and inode/file-count limits.
- Persistence and backup policy.
- Encryption-at-rest capability.
- Snapshot and restore support.
- Atomic deployment activation and rollback.
- Data locality and replication constraints.

Do not assume Android private storage, Linux paths, Windows ACLs, macOS sandbox containers and MCU flash have identical semantics.

## Security requirements

- No shared writable project roots.
- No parent-directory or sibling-project access.
- Secrets are stored separately and injected as scoped capabilities.
- Reject symlinks, traversal, duplicate paths, special files and archive bombs.
- Enforce quotas before extraction and during runtime.
- Preserve last-known-good release if staging fails.
- Do not clone device identity or device-bound secrets during restore.
