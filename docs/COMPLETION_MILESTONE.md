# PocketHost — Unified Completion Milestone

**Milestone:** PocketHost 0.1.0 system completion  
**Rule:** Do not build or package an APK during implementation. Complete source systems and run focused source-level checks first. One integrated APK build is reserved for the final acceptance gate.

This document is the single source of truth for the remaining implementation. A feature is not complete because it is documented or has a placeholder UI: it must have a working implementation, tests, failure handling, and user-facing status.

## Product baseline

- Standalone native Android app; no Termux and no root.
- Android 8.0 / API 26 minimum; practical operation from 4 GB RAM.
- Kotlin + Jetpack Compose Android shell; Rust static HTTP engine; embedded Python; TypeScript + React/Vite console.
- ARMv7 and ARM64 device support; x86_64 emulator support where dependencies permit.
- Localhost-only defaults. LAN and public exposure are explicit opt-ins.
- AssayFlux branding and current design language retained.
- No secrets in logs, URLs, command-line arguments, diagnostics, backups, or console bundles.

## Workstream A — Runtime supervisor and Node.js

1. Select and pin an Android-compatible Node runtime with a reproducible source/build provenance for all supported ABIs.
2. Build and package the runtime as an app-owned executable/library; do not rely on Termux, a shell installation, or downloading an opaque binary at runtime.
3. Implement lifecycle supervision: start, stop, restart, health/readiness, bounded logs, exit-code reporting, cancellation, and crash recovery.
4. Define project entry-point and package-manager behavior; support only Node APIs and native modules verified by the chosen runtime. Never claim full upstream Node compatibility if the embedded runtime has limitations.
5. Apply per-project working directories, environment filtering, request/worker limits where supported, storage quotas, and explicit permissions.
6. Test supported ABIs, API 26 compatibility, app upgrade/restart, low-memory behavior, and clean shutdown.

**Acceptance:** A sample Node HTTP project starts from the app, serves requests, stops and restarts reliably, reports failure accurately, and cannot bind to LAN unless the user enables it.

## Workstream B — One authenticated local control plane

1. Implement a versioned Android management API bound to loopback by default.
2. Pair clients with a short-lived, single-use challenge and user-approved pairing flow. Store credentials securely; support revocation and rotation.
3. Require authentication and authorization for every state-changing endpoint. Add CSRF/origin protections where browser cookies are used, strict CORS allowlists, request-size limits, rate limits, and replay protection.
4. Expose typed endpoints for status, sites, runtimes, logs, metrics, tunnel state, configuration, backup/restore, and lifecycle operations.
5. Validate every input server-side; return stable error codes; avoid leaking filesystem paths, tokens, stack traces, or secrets.
6. Add API contract tests, negative authorization tests, malformed-input tests, and compatibility/version negotiation.

**Acceptance:** An unpaired client cannot read private state or perform actions; a paired client can perform only authorized operations; revocation takes effect immediately.

## Workstream C — Connected TypeScript console

1. Replace preview-only placeholders with a typed API client and explicit connection states.
2. Add QR/manual pairing, secure session handling, disconnect/revoke, API version negotiation, and reconnect behavior.
3. Implement real dashboard metrics, site list/details, runtime controls, logs, tunnel status, backup/restore, and settings against the API.
4. Keep secrets out of local storage and telemetry; do not expose tunnel tokens to the browser console.
5. Add loading, empty, offline, stale-data, permission-denied, and server-error states; accessible responsive layouts.
6. Add TypeScript tests for API contracts and critical UI state transitions, plus production build checks.

**Acceptance:** Console connects only after explicit pairing and every displayed operational value is sourced from the Android API, not hardcoded sample data.

## Workstream D — Multi-site routing and Rust engine

1. Define a persistent site registry with stable IDs, display names, canonical roots, lifecycle state, configured ports/hostnames, and deployment history.
2. Add per-site start/stop and a safe default site. Prevent path traversal, symlink escapes, host-header confusion, and cross-site file access.
3. Implement hostname routing with normalized hostnames and explicit conflict detection.
4. Add reverse-proxy routing to Python and Node upstreams using loopback-only upstreams by default, bounded connections, timeouts, cancellation, and graceful shutdown.
5. Add configurable cache policy, custom 404 behavior, MIME handling, security headers, range requests, and safe error pages.
6. Ensure metrics are attributed by site and runtime; clearly distinguish Rust static traffic, Python traffic, Node traffic, and tunnel connection state.
7. Add integration tests for routing, malformed Host headers, traversal, proxy timeouts, runtime death, and concurrent requests.

**Acceptance:** Multiple sites can run concurrently with deterministic hostname routing; one site's requests cannot read or control another site's files or runtime.

## Workstream E — Python runtime hardening

1. Preserve WSGI support while documenting that imported project code runs with the app's Android UID and is not a security sandbox.
2. Bound request bodies, connections, queue depth, request duration, output size, and log volume; ensure limits are enforced during streaming, not only by declared Content-Length.
3. Add runtime lifecycle supervision, restart/stop handling, health checks, and process/thread leak checks.
4. Use per-project roots and sanitized environments; prevent accidental exposure of app-private paths and secrets.
5. Add adversarial tests for chunked/unknown-length bodies, slow clients, exceptions, oversized responses, and repeated start/stop.

**Acceptance:** Limits are enforced for declared and streamed request bodies; stop releases sockets and threads; limitations and permissions are clear to users.

## Workstream F — Cloudflare Tunnel and exposure safety

1. Validate connector lifecycle, readiness, reconnection/backoff, service stop, app process death, and battery/network transitions.
2. Do not infer public reachability from the local /ready endpoint. Surface tunnel-connected separately from externally verified hostname reachability.
3. Add a user-run external verification flow and clear Cloudflare dashboard requirements; do not claim DNS/hostname configuration is automatic.
4. Keep tunnel token encrypted; redact all output; provide explicit disconnect and token revocation instructions.
5. Document Cloudflare Access for private sites and explain that origin hosting is not visitor authentication.

**Acceptance:** Tunnel state is truthful, recoverable, and never presented as externally reachable without an external check.

## Workstream G — Backup, restore, diagnostics, and operations

1. Extend backup/restore beyond static sites to include explicitly selected site configuration and supported project files; never include credentials by default.
2. Version the archive format, authenticate all encrypted content, validate manifests and paths, enforce decompression limits, and restore transactionally.
3. Add export/import previews, conflict handling, rollback, and recovery after interruption or low storage.
4. Complete diagnostic bundles with redaction tests, app/runtime versions, supported ABI/API details, health states, and clearly scoped metrics.
5. Add bounded log rotation, filters, export, and retention controls.

**Acceptance:** Corrupt, tampered, oversized, or incompatible archives fail safely without damaging the active deployment; secrets are absent from exports.

## Workstream H — Android compatibility, security, and release readiness

1. Audit API 26 calls, foreground-service behavior, notification permissions, storage access, Keystore behavior, and ABI packaging.
2. Test minimum API 26, a modern API level, ARMv7, ARM64, and x86_64 emulator paths where feasible.
3. Review memory, CPU, storage, wake-lock, Doze, screen-off, network switching, and low-storage behavior on 4 GB-class devices.
4. Review exported components, intent filters, file providers, cleartext traffic, backup rules, permissions, JNI boundaries, and dependency licenses.
5. Verify release workflow reproducibility, pinned dependencies, artifact provenance, signing instructions, and no secrets in CI.
6. Update README, setup guides, limitations, threat model, troubleshooting, and release notes from actual implemented behavior.

**Acceptance:** Source tests and CI pass; security review has no unresolved critical/high issue; supported device matrix is explicit. Physical-device claims are made only after real device testing.

## Execution order (single coordinated completion effort)

1. Freeze the API and data model shared by Android, Rust, and TypeScript.
2. Implement the site registry and runtime supervisor interfaces.
3. Complete Node and Python lifecycle/resource enforcement.
4. Implement multi-site Rust routing and runtime reverse proxy.
5. Implement the authenticated management API.
6. Connect the TypeScript console to the real API.
7. Complete Cloudflare lifecycle/external validation and backup/diagnostics.
8. Run source-level tests, static analysis, ABI/API compatibility checks, security review, and CI. Fix all failures.
9. Only after all source systems pass, build one integrated APK and perform end-to-end acceptance.

## Definition of done

- No placeholder controls or hardcoded operational metrics remain.
- All supported runtime actions work end-to-end from the Android app and paired console.
- Multi-site routing and isolation tests pass.
- Security, backup/restore, and recovery tests pass.
- CI is green for source validation and reproducible runtime builds.
- Known limitations are explicit and match actual behavior.
- APK packaging remains intentionally deferred until the source-system completion gate is met.

## Current known blockers at milestone creation

- Embedded Node runtime is not implemented.
- Authenticated management API and console pairing/connection are not implemented.
- Multi-site registry/hostname routing/reverse proxy are not implemented.
- Python imported code is not sandboxed; resource hardening is incomplete.
- Cloudflare local readiness does not prove public hostname reachability.
- Backup/restore scope and diagnostics require end-to-end validation.
- Latest source changes have not yet been confirmed by a passing CI run.
