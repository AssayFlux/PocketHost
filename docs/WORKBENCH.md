# Pocket Host Workbench — Product and Engineering Specification

**Status:** Accepted product requirement; implementation not yet complete  
**Owner surface:** Android Pocket Host app, available in both operating modes  
**Delivery step:** Step 4 — Main Pocket Host mobile website server  
**Relationship:** Extends Websites & Files; does not create a second site store or server

## 1. Product promise

Workbench is an integrated, GitHub-inspired website development workspace. A user selects a Pocket Host website, browses its complete project directory, edits source and assets, previews changes, reviews a change set, and commits a versioned release to the live website.

Workbench is not a GitHub clone and does not require a GitHub account. Its native commit/release history is the source of truth for Pocket Host deployments. Optional Git remote integration may be considered later as a separate feature.

## 2. Core UX

The hamburger sidebar contains a first-class **Workbench** destination. Workbench opens with a website/project selector and these areas:

- **Explorer:** complete nested directory tree, file metadata, create, upload, rename, move and delete.
- **Editor:** syntax-aware editing, tabs, unsaved-change indicators, find/replace, diagnostics and safe save.
- **Preview:** isolated development preview of the workspace, with refresh and viewport controls. Preview must not mutate production.
- **Changes:** added, modified, deleted and renamed files; per-file diff and selection.
- **Commits:** local commit history, author/time/message, changed-file summary and release mapping.
- **Deployments:** staged, validating, ready, active and failed release states; logs and rollback to a prior known-good release.

Initial language scope: HTML, CSS, browser JavaScript, JSON and common text/config/assets. TypeScript is enabled only after the Step 4 compiler is bundled and verified. Do not claim language-server-grade intelligence before it exists.

## 3. Two-environment model — mandatory

Each website has two distinct roots:

1. **Live / production release:** immutable version currently served to visitors.
2. **Workbench workspace:** editable working copy with uncommitted changes.

The editor never writes directly into the active production directory. Visitors continue receiving the active release while edits and previews are in progress. Saving changes affects only the workspace. A commit records a durable snapshot and metadata; deployment is a separate explicit action.

A production deployment follows this order:

1. Validate all relative paths and reject traversal, symlinks escaping the project, sensitive credentials and forbidden files.
2. Run configured bounded build/validation steps (including TypeScript compilation when available).
3. Produce an immutable staged release and content manifest/digest.
4. Preview and health-check the staged release without switching production.
5. Present the changed-file summary and deployment impact.
6. Require explicit owner approval.
7. Atomically switch the serving root/release pointer.
8. Verify serving health; if activation fails, restore the previous last-known-good release.
9. Record deployment result and preserve rollback history.

The active release is never partially overwritten. A failed build, validation or deployment leaves the previous live website intact.

## 4. Versioning and commit model

A commit is a Pocket Host-local immutable snapshot, not necessarily a Git commit. It records a unique ID, parent commit/release, project ID, message, timestamp, changed paths, content digests and build/validation metadata. Commits must be recoverable after app/process restart. Release history must support compare/diff and rollback.

Use content-addressed or otherwise integrity-checked snapshots with bounded storage, retention policy and safe garbage collection. Never delete the active release or the last-known-good release during cleanup. A commit does not publish automatically unless the user explicitly chooses a combined commit-and-deploy action.

## 5. Multi-site and data boundaries

- Workbench is project/site scoped; selecting one site must never expose another site's files.
- Preserve the existing SiteRegistry and site import/backup contracts; integrate them rather than create a parallel website registry.
- Protect .env*, private keys, keystores, tokens, credentials, .git internals and Pocket Host metadata from public publishing by default. Secret files must not appear in public file listings or preview artifacts.
- Bound file size, total workspace size, file count, nesting depth, archive expansion and build CPU/memory/time.
- Reject path traversal, absolute paths, control characters and symlink escapes. Use atomic file writes.
- Require confirmation for recursive deletion, overwrite, destructive restore and production deployment.
- Treat imported project code as owner-controlled code; never execute arbitrary project scripts as a privileged Pocket Host service.

## 6. Preview and build isolation

Preview must serve the workspace on a separate loopback-only endpoint or isolated preview mechanism, with a distinct origin/port and no production route mutation. It must not expose private files or secrets. Avoid unrestricted network access from build/preview tasks. TypeScript compilation must use the main-app compiler toolchain and bounded inputs/outputs; no npm install or arbitrary package scripts in the initial baseline.

## 7. Relationship to current repository implementation

The repository already contains SiteFileManager with bounded text-file editing, listing, upload, create, rename, delete and index selection. A first isolated WorkbenchWorkspaceManager foundation has now been added for per-project workspace initialization, bounded text edits, change digests and immutable local commit snapshots; it is not yet connected to the app UI or a deployment pipeline. The existing Websites & Files screen currently edits the deployed filesDir/site tree and disables many edits while hosting. The new workspace manager is a separate storage foundation; UI integration, release activation, isolated preview and complete acceptance remain open. This is a useful foundation, **not** the Workbench product described here: it does not yet provide an isolated workspace, full tree navigation/editor experience, change tracking, commit history, separate preview or immutable atomic release deployment.

The existing SiteImporter release/activation and recovery mechanisms should be audited and extended for Workbench rather than bypassed. Verify actual semantics before relying on them for production activation.

## 8. Delivery sequence inside Step 4

1. **Workbench foundation:** site/project selector, complete tree navigation, robust file operations, editor tabs, dirty state, diff and path/security tests.
2. **Workspace isolation:** separate editable workspace from active release; safe copy-on-write/snapshot model; preserve production while editing.
3. **Preview/build:** isolated preview server; HTML/CSS/JS validation; TypeScript compiler integration once bundled; bounded diagnostics.
4. **Commit/release:** durable local commits, immutable staged artifacts, digest manifest, compare/history and storage retention.
5. **Publish/rollback:** owner-approved atomic activation, health check, crash recovery, rollback and audit events.
6. **Acceptance:** Android lifecycle, large projects, low storage, process death, interrupted publish, malformed archives, path attacks, symlink attacks, multi-site isolation, live visitor continuity and physical-device tests.

Workbench does not add a sixth top-level step. It is a required Step 4 workstream and must pass its own acceptance criteria before Step 4 closes.

## 9. Acceptance checklist

- [ ] Multiple websites can be selected and remain isolated.
- [ ] Full nested directory tree is navigable and all supported file operations are safe.
- [ ] Editing and saving never modifies the active production release.
- [ ] Live visitors continue receiving the previous release during editing/build/preview.
- [ ] Workspace preview is isolated and cannot mutate production.
- [ ] Changes and diffs are accurate after app restart.
- [ ] Commits are immutable, durable and recoverable.
- [ ] TypeScript is compiled to JavaScript only when the bundled compiler is present and verified.
- [ ] Failed validation/build leaves production unchanged.
- [ ] Deployment requires explicit approval and switches atomically.
- [ ] Interrupted activation recovers to a valid known-good release.
- [ ] Rollback restores a previous release and is audited.
- [ ] Sensitive files, traversal, symlink escapes and oversized inputs are rejected.
- [ ] Android physical-device, storage-pressure and lifecycle acceptance passes.

No Workbench capability is considered complete from UI presence alone; each item requires implementation and test evidence.
