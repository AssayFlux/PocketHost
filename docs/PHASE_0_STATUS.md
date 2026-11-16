# Pocket Host — Current Implementation Status

**Snapshot:** 2026-11-16  
**Source baseline:** `b7775a2450c0633aaa44b58cb2cdb468a47ea0ca`  
**Release:** 0.1.0 target · **Decision:** not production approved.

## Stage 0 — Repository truth and architecture freeze

**Complete.**

- Audited current repository structure, Android host/companion packages, Rust engine, Python/Node runtime, console, control plane, manifests and workflows.
- Reconciled the canonical North Star, roadmap, baseline and engineering handoff.
- Added Cargo lockfiles for both Rust crates, Gradle dependency locks for host and companion, and npm package-lock for the console.
- Pinned Rust 1.88.0, Gradle 8.9 with distribution checksum, Android SDK 35 / Build Tools 35.0.0 / NDK 27.2.12479018 / CMake 3.22.1, and cargo-ndk 4.1.2.
- Added a checksum-verified Gradle wrapper; local/CI Gradle entry point is `./gradlew`.
- CI uses locked Cargo and npm dependency installation. Android APK building is separated from ordinary quality CI.

## Stage 1 — Runtime migration

**Source implementation complete; Android verification deferred to Gate 8.**

- Kotlin host owns lifecycle; Rust/Tokio/Hyper remains the website ingress, hostname router, static server and reverse proxy.
- Companion Binder client validates protocol version, registers a Binder death recipient, invalidates stale connections and rebinds on the next supervised operation.
- Stop operations no longer start an absent foreground worker just to stop it.
- Orderly companion shutdown stops Python and the separate Node service.
- Project replacement uses staged extraction and a recoverable previous-project rename. Interrupted replacement is reconciled before a new transfer; failed activation restores the previous tree when possible.
- Python instrumentation now defines a real Host-header request through Rust into the companion WSGI server.
- Node instrumentation now binds the smoke app to loopback, routes it through Rust, stops the Node runtime, then verifies the independent static origin still serves.
- Python/Node remain trusted owner-controlled code only. This is not per-project isolation.

**Validation is intentionally deferred:** Android instrumentation source is written but has not been built or run. Per owner instruction, host/companion APK builds, ABI packaging, API 26/33/35 emulator tests and physical-device tests happen only at Gate 8 after Gates 0–7 implementation work.

## Android application

- Kotlin/Jetpack Compose; minimum SDK API 26; configured ARMv7, ARM64 and x86_64 targets.
- Foreground hosting service and notification.
- Dashboard, Sites, Activity, Learn and Settings destinations.
- Site folder/ZIP import, staged deployment, retained releases, rollback and file operations.
- Owner/Operator account and local authenticated management foundations.
- Device health sampling, operational logs, metrics and diagnostics.

## Rust website origin

- Tokio/Hyper server with hostname routing.
- Static GET/HEAD, streaming, MIME handling and byte ranges.
- Canonical path validation and traversal protection.
- Runtime upstream reverse proxy.
- Bounded connection admission/queue, per-client rate controls, per-site concurrency limits, overload responses and graceful drain code are present.
- Fairness under contention, fault injection and sustained-load evidence remain incomplete.

## Security truth

The companion UID differs from the host UID, but Python and Node projects share the companion UID and storage. Outbound egress is not OS-enforced per project. The isolated service/seccomp/Unix-socket work is a feasibility probe, not a production sandbox. Do not run untrusted visitor-submitted code.

## CI evidence

- Quality `43827669251`: all six jobs passed on `9f249f0faf3f103bcee9aaa763adf3c275211951`, including Gradle wrapper validation.
- Dependency-lock workflow `43826809601`: succeeded; Gradle wrapper and dependency locks generated.
- No successful Android APK artifact or accepted API 26/33/35 result is claimed for the current baseline.
- Legacy Android run `43824043298` on older SHA `12eaf3ba700b6e0c37ecc14f72e633c5800fd431` was cancelled after its APK job failed, API 26/33 jobs failed and API 35 was cancelled. The replacement no-op run `43828040040` skipped every Android job.

## Remaining North Star gates

2. Per-project OS isolation and adversarial security.  
3. AssayFlux Relay and device enrollment.  
4. Owner/Operator and visitor authentication.  
5. Rust governor and Capacity Lab.  
6. Backup, operations and lifecycle recovery.  
7. Product UX and onboarding.  
8. Final Android build, API 26/33/35 matrix, ABI/signing, external tests and independent review.

**Release decision:** not production-ready, not fully sandboxed, not signed and not externally verified.

## Incremental implementation record — 2026-11-16

- Control-plane administrative mutations now couple user/device lifecycle, device token rotation, configuration/command/deployment queueing and command acknowledgement with audit records in database transactions.
- Deployment ZIP persistence now uses exclusive file creation and `fsync`; a pending-file guard removes the blob if database metadata/command/audit commit fails. A regression test covers failed-transaction cleanup, committed-file retention and refusal to overwrite.
- Source commit: `dc18daa9855debdeb8fe7b5ed291b9ee8365bdcb`. CI run `43836611641` was still in progress at the time of this note; earlier six-job run `43836090906` passed on its own earlier SHA only.
- Gate 2 and Gate 4 remain open under the acceptance criteria in `docs/NORTH_STAR.md` and `docs/ROADMAP.md`.
