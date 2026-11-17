# Pocket Host — Repository Deep Audit

**Audited branch:** Alpha  
**Audited baseline:** afb0cceadfabca840590eb42eb4b5f3aaf9eac3e  
**Active release target:** v0.1.0 — First Hosting  
**Audit result:** substantial foundations exist; no production release is certified by source inspection alone.

## 1. Executive finding

Pocket Host is not a blank scaffold. The repository contains an Android Compose app, a Rust static website engine, a website importer and backup manager, a first Workbench workspace, a Python/Node runtime companion, Android device-trust foundations, a Linux agent preview, a Rust control plane, a browser operator console and visitor-ingress integrations.

The key planning error in the former roadmap was making all of those workstreams part of one first release. The new pathway separates them:
- v0.1.0 proves Android static website hosting.
- v0.1.1 completes Workbench and optional runtime development.
- v0.2.0 completes the connected device fabric and public-ingress operations.

## 2. Product boundaries confirmed

- Android has exactly two modes: Server only and Command Center + Server. Both serve websites.
- There is no standalone Command Center-only mode.
- Static website hosting must not require Python, Node.js or a companion APK.
- Python and Node.js exist in the separate runtime-worker APK, but are in progress, shipped together and share the companion UID.
- React/TypeScript/Vite are in the separate web-console browser client.
- The main-app TypeScript compiler is not verified in the Android dependency graph.
- Workbench currently has private workspace/editor and local commit foundations, not complete preview/publish/rollback.
- Control plane is not required for local website hosting.
- Relay and Cloudflare source integration does not prove external public reachability.

## 3. Hosting path review

### Android service

HostingService starts a foreground notification, runs interrupted backup/site activation recovery, starts the local management API and remote-control agent, creates a starter site when absent, and starts the Rust native engine. The Rust engine binds to loopback unless LAN access is explicitly enabled. Cloudflare Tunnel is optional and starts only when configured.

Acceptance still needs a physical device: notification permission, foreground-service restrictions, Doze/battery saver, screen-off, network transitions, process death, reboot, stop/restart and thermal/storage behavior.

### Website importer

SiteImporter stages folder/ZIP contents, checks file and entry counts, nesting, path traversal, sensitive filenames, index.html and activation/recovery. It currently has a 5,000-file cap, 10,000-entry cap and 32-level depth cap.

The former source had a 50 MB ZIP input and 50 MB expanded-site limit. The new v0.1.0 importer requirement is 500,000,000 bytes for each independent limit. UI, ZIP input cap, expanded byte counter, folder copy, prepared-directory validation and importer tests must use the same value. The current ApplicationBackupManager is separate: static site root 50 MiB, Python 50 MiB, Node 100 MiB, total uncompressed 200 MiB and encrypted archive 220 MiB. It cannot currently back up a site near the new 500 MB import cap. This is a distinct v0.1.0 gap; do not claim import and backup limits are aligned.

### Static engine

The Rust server uses loopback by default and 0.0.0.0 only when LAN is enabled. It has a separate 50 MiB per-file response cap and bounded connection/resource behavior. Increasing website import size must not silently increase the per-response cap. Document this distinction and test a site with large total size made of many smaller assets.

## 4. Workbench review

WorkbenchWorkspaceManager and PocketHostWorkbenchScreen provide a per-site draft workspace, nested explorer, basic editor, file/folder creation, rename/delete, change digest and immutable local commit snapshots. The screen is exposed in both mobile modes.

Missing for v0.1.1: complete editor behavior, upload/move/recursive operations, per-file diffs, isolated preview, TypeScript build, deployment history, owner-approved atomic activation, rollback and full Android acceptance. Existing Websites & Files operations still target the deployed site tree; they are not a substitute for safe Workbench publishing.

## 5. Runtime review

Python 3.11 uses Chaquopy in runtime-worker. Node.js mobile 24.22.1 uses JNI/libnode and native SDK assets. Both have host-side test foundations, but both are shipped together in one companion APK and share its UID. This is not hostile multi-tenant isolation. Android device acceptance, crash/restart, package/IPC lifecycle, ABI verification, egress restrictions and isolation remain open. These are v0.1.1 work and do not block v0.1.0 static hosting.

## 6. Device fabric review

The protocol schemas and policy validator exist. Android has P-256 owner identity, challenge/proof and grant foundations. The Linux agent is a preview package with durable SQLite jobs and HTTPS polling, but manual provisioning, bearer transport limitations, grant renewal/revocation propagation, other OS packages and role adapters remain open. The control plane is a foundation, not a local hosting dependency. This work belongs to v0.2.0.

## 7. Workflow and artifact review

The Android integration workflow has a compile-only job and a manually dispatched APK/emulator path. A compile-only run is not APK packaging. The release workflow is manual and requires protected signing inputs. A successful workflow is not physical-device acceptance. Every release must retain the workflow URL, artifact SHA-256, device model, Android version and test evidence.

## 8. Priority risks

1. 500 MB import limits must be aligned across ZIP input, extracted contents, folder import, prepared restore, backup and UI.
2. 500 MB total import does not guarantee that a single 500 MB file can be served: the Rust engine's per-file cap remains 50 MiB.
3. Android background behavior varies by OEM and Android release; test on actual devices.
4. LAN binding exposes the website to the local network; require explicit opt-in and explain the risk.
5. Full-app backup export is disabled while hosting or optional runtimes are active. Its static site cap is 50 MiB and total uncompressed cap is 200 MiB; this does not cover a 500 MB imported website.
6. Runtime companion processes share one app UID; do not call it a hardened project sandbox.
7. TypeScript build, Workbench preview and safe publishing are not v0.1.0 features.
8. Public Relay/Cloudflare connectivity must be tested from an independent network before claiming reachability.

## 9. Audit policy

This audit describes the audited source snapshot. Refresh it when code changes. Release status is determined by the G0–G9 ledger, not by this document. No feature is considered complete because its source file exists.
