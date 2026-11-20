# Website Hosting — Alpha v0.1.0 Engineering Notes

**Branch:** `Alpha`  
**Implementation verification commit:** `fe2af2277bf85eba16e2d3b67ec95d86acf56637`  
**Status:** implementation and automated CI gate passed; physical-device acceptance remains open for Step 5.

## Security and isolation

- Static sites use dedicated managed site roots; a route cannot point at Pocket Host's general private files directory.
- Site roots must be canonical, app-managed, non-symlinked and non-overlapping. Persisted route tables are revalidated on read and fail closed if malformed or unsafe.
- Site roots for different sites cannot nest or overlap. Hostnames and route counts are bounded and duplicate hostnames are rejected.
- Folder, ZIP and prepared-directory imports reject traversal, symbolic links, sensitive credential/private-key paths, unsupported filesystem entries and invalid/case-colliding archive paths.
- Static serving checks sensitive paths after resolving symlinks; regression tests cover in-root symlink aliases to protected files.
- Static hosting remains part of the Android app/native engine and does not require the runtime-worker or cloud control plane.

## Shared website budgets

The central `WebsiteLimits` policy currently sets:

| Limit | Alpha value |
|---|---:|
| Files per website | 5,000 |
| Files + directory entries | 10,000 |
| Website aggregate bytes | 500,000,000 bytes |
| Single imported file ceiling | 500,000,000 bytes, also subject to aggregate quota |
| Directory depth | 32 |
| Editable text file | 256 KiB |
| Registered sites | 100 |
| Hostnames per site | 100 |
| Total hostname routes | 1,000 |

ZIP ingestion additionally caps compressed input at 500,000,000 bytes and validates the extracted tree against the shared file, entry, depth, byte and sensitive-path rules. Full application backups have a larger envelope because they can contain multiple websites and optional runtime projects; their own aggregate archive and per-runtime limits remain separately enforced.

## Import and deployment behavior

- Folder and ZIP imports are staged before activation.
- ZIP paths are normalized and checked for traversal, duplicates, case collisions, file/directory conflicts and archive entry exhaustion.
- A valid root `index.html` is required. A single enclosing folder containing `index.html` is accepted.
- Activation preserves the previous deployment as a release; failed activation attempts restore the previous active directory.
- Interrupted activation recovery only promotes a validated last-known-good release and preserves invalid active data for diagnosis.
- Manual file uploads are staged and synced before the final rename; aggregate quota and file/entry/depth limits are checked.
- Backup restore has a journal and recovery path; export is staged before copying the completed encrypted artifact to the selected destination.

## Automated evidence and open acceptance

- Rust hosting engine tests cover traversal, invalid paths, MIME handling, SPA fallback, host-to-root isolation and sensitive-file symlink aliases.
- Android source and instrumentation Kotlin compilation passed; compilation does not mean the instrumentation tests were executed.
- Quality CI and Android native/source validation passed on the implementation commit above.
- Still required on a physical device: clean-install folder/ZIP import, loopback and LAN requests, multiple hostnames/sites, large-file behavior, quota exhaustion, backup/restore and interrupted-operation recovery. These are tracked in Step 5, not represented as already device verified.
