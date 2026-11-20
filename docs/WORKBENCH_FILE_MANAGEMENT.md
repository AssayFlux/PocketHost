# Workbench file-management implementation — Step 2

**Branch:** `Alpha`  
**Checkpoint:** 2026-11-20  
**Implementation scope:** private per-site draft workspace; no live-site mutation and no deployment.

## Delivered

- Explorer now exposes rename and delete actions for both files and folders.
- Folder deletion is recursive but constrained to the selected site's private Workbench draft. The live website and its files are not touched.
- The delete confirmation explicitly warns that folder contents are included and that the live website remains unchanged.
- The workspace manager rejects missing/root targets and symbolic links before deletion, validates a folder tree before removing it, and validates the remaining workspace afterward.
- Rename now attempts to move the entry back to its original path if post-move workspace validation fails.
- Added an Android instrumentation regression test proving recursive draft-folder deletion leaves the live website unchanged and returns the workspace to its clean baseline.

## Existing guarantees retained

- Workspaces are isolated per registered site and are initialized from the live site only once.
- Draft writes never target the live site root.
- Relative-path validation, canonical-root containment, symlink rejection, protected-name exclusions, depth/entry/file/size quotas and bounded commit snapshots remain enforced.
- Commits remain local immutable snapshots; this step does not add production deployment or rollback.

## Verification status

- Source changes and regression test are committed to `Alpha`.
- Android compilation, JVM/instrumentation execution, emulator testing and physical-device acceptance have **not** been run in this step.
- Do not mark this feature device-verified until the new test executes on Android and the folder/file workflows are exercised on a device.
- No APK packaging or APK build was run.
