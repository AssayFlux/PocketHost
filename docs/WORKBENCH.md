# Pocket Host Workbench — v0.1.1 Specification

**Release assignment:** v0.1.1 — Workbench & Runtime Development  
**Current repository status:** initial workspace/editor foundation exists; acceptance remains open.

## 1. Product promise

Workbench is an integrated, GitHub-inspired website development environment. The owner selects a Pocket Host site, browses its full directory, edits source/assets, previews changes, reviews diffs, records local commits and explicitly publishes a validated release. It does not require a GitHub account.

## 2. User experience

The Workbench destination lives in the existing hamburger navigation and is available in both mobile modes. It contains:
- **Explorer:** nested, bounded project tree and file/folder operations.
- **Editor:** tabs, dirty indicators, save state, readable syntax presentation and safe text editing.
- **Preview:** isolated draft preview, separate from production visitor traffic.
- **Changes:** per-file status and reviewable diffs.
- **Commits:** durable immutable local snapshots and history.
- **Deployments:** staged, active, failed and rolled-back release history.

On phones use a single-pane editor with a collapsible file drawer; on tablets use a two-pane layout where practical. Respect keyboard insets, font scaling and long filenames.

## 3. Three-environment model

1. **Active production release:** immutable website tree served to visitors.
2. **Workbench workspace:** private editable draft, isolated per site.
3. **Preview/build staging:** temporary bounded environment with no production mutation or owner-secret access.

Saving changes only the workspace. A commit records a snapshot. A commit is not deployment. Visitors continue receiving the active release while drafts are edited and previewed.

## 4. Build and publish

1. Validate file paths, names, size, count and project quotas.
2. Build supported source with bounded time, memory, disk and network.
3. Compile supported TypeScript to browser JavaScript using the v0.1.1 bundled compiler.
4. Create an immutable staged artifact and content digest.
5. Run static checks and preview/health checks.
6. Present per-file diff and target site.
7. Require explicit owner approval.
8. Atomically activate the release.
9. Preserve last-known-good and support rollback.
10. Audit the result and expose deployment status.

Full npm package resolution and arbitrary package scripts are not implied by the basic TypeScript compiler. React/Vite source builds require a separately defined bounded toolchain; users can always import prebuilt static output.

## 5. Multi-site and data boundaries

A workspace is site-scoped. One site's editor, preview, commits or artifacts must never expose another site's files. The active website root is read-only to ordinary editor operations. Production activation is a privileged release operation.

## 6. Current implementation foundation

The repository contains WorkbenchWorkspaceManager and PocketHostWorkbenchScreen. Current foundations include per-site private workspace/baseline, nested explorer, basic text editing, draft save, create folder/file, rename/delete file, change digest and immutable local commit snapshots/history. It is exposed in both mobile modes. Preview, per-file diffs, TypeScript build, release/deployment UI, atomic activation, rollback and physical-device acceptance remain open.

## 7. Acceptance

- Reopen workspace without losing drafts.
- Dirty editor text cannot be silently discarded.
- Path traversal, cross-site access and sensitive files are rejected.
- Preview cannot mutate production or access secrets.
- Build quotas and timeout are enforced.
- Diff matches actual artifact contents.
- Failed publish leaves active release unchanged.
- Process death during activation recovers last-known-good.
- Owner approval is required and audited.
- Rollback restores a previously verified immutable release.

All G0–G9 gates apply. Workbench UI presence alone does not close v0.1.1.
