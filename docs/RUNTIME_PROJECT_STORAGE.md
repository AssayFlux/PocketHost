# Pocket Host — Runtime Project Storage Contract

## Ownership

Every website component has a stable project identity and dedicated data namespace. The host supervisor owns metadata and policy. A runtime receives only its project files, explicitly granted persistent volumes and short-lived scoped capabilities.

## Android provider

The Android host validates project roots as canonical descendants of approved app-private storage. Transfer to the runtime companion uses bounded archives and a read-only descriptor over the protected Binder contract. The companion validates entries, rejects traversal and unsafe paths, enforces file/count/expanded-size limits and extracts into a staged component directory before activation.

## Release placement

- v0.1.0: only static website storage/import and website backup are release-critical.
- v0.1.1: finish runtime project transfer, quota enforcement, multi-site workspace separation and runtime lifecycle.
- v0.2.0: optional encrypted remote namespaces and worker-assisted backup/restore.

## Requirements

- Canonicalize paths and reject traversal, symlinks and unexpected filesystem types.
- Enforce quotas before extraction and during runtime.
- Keep runtime project files separate from active static website and Workbench draft data.
- Keep credentials out of source archives, backups and logs unless explicitly encrypted and scoped.
- Bound file count, expanded bytes, depth, open files and temporary staging.
- Preserve atomic activation and cleanup failed staging.
- Audit read/write/delete and transfer operations without logging source contents.

## Acceptance

Test cross-project access, symlink/path escape, oversized archive, too many files, low storage, interrupted extraction, runtime crash and restore. Verify that static hosting remains independent of runtime project storage.
