# PocketHost — Canonical Development Context

**Read this document at the start of every implementation session.** It preserves product decisions and prevents architecture drift, duplicated work, false completion claims and loss of context.

## Current implementation snapshot — 2026-11-15

- Current main commit: `467f42e23d12eb3879677b36f6dd8c04c5da0d1a`.
- PRs #40–#45 are merged. MainActivity uses ComponentActivity for Compose Activity Result launchers to avoid FragmentActivity's 16-bit request-code validation path. BiometricPrompt is hosted in a dedicated activity.
- UI navigation is Dashboard, Sites, Activity, Learn and Settings. Settings are grouped into General, Network, Runtimes and About; Learn guides start collapsed. Keep descriptions concise and preserve the approved teal/bronze/ivory palette.
- Encrypted full-app backup includes the active static website, optional Python/Node projects, hostname routes and selected non-secret settings. It excludes device-bound credentials, requires a 12–128 character passphrase, bounds archive sizes/counts, and keeps restored runtimes stopped pending fresh owner acknowledgement. Legacy static-only backups remain restorable.
- Python and Node remain supported owner-managed hosting features. They currently run under the app UID and are not OS sandboxes; do not disable them as a workaround, but do not claim isolation or accept visitor-submitted executable code.
- Latest quality run [43720035682](https://github.com/AssayFlux/PocketHost/actions/runs/43720035682) passed six jobs including Android source compilation. Android integration run [43720035704](https://github.com/AssayFlux/PocketHost/actions/runs/43720035704) is packaging ABI-specific debug APKs; emulator instrumentation is skipped and physical-device acceptance is owner-run.
- Remaining production gates include OS-backed runtime isolation and quotas, deployment/verification of the optional remote control plane, payment-provider integration, independent security review, physical-device acceptance and owner-controlled release signing. Full backup currently rejects custom static roots outside the main website directory.

## 1. Repository identity

- Repository: https://github.com/AssayFlux/PocketHost
- Owner: AssayFlux
- Product: AssayFlux PocketHost
- Developer identity: Designed by AssayFlux
- Package: `com.assayflux.pockethost`
- Main branch: `main`
- Initial version target: `0.1.0`
- Canonical documentation: merged to `main`; PR #15 is closed/merged.

Always fetch current default branch, latest commit, open PRs, relevant source files and CI state before editing. Never assume an earlier chat summary is newer than GitHub. GitHub source is authoritative.

## 2. Immutable product decisions

1. Standalone native Android app; no Termux and no root.
2. Android phone itself is the website server/origin.
3. Existing owner domain and Cloudflare Tunnel are already configured. Reuse them; do not ask for a duplicate tunnel.
4. Public path: Cloudflare edge → existing tunnel → Rust listener on phone → site router → static files or loopback runtime.
5. Cloudflare Public Hostname must never target the local management API.
6. Remote Rust/PostgreSQL control plane is optional remote management only. It is not the website server, is not automatically deployed and is not required for local hosting.
7. Both Simple and Advanced modes are required.
8. Static hosting is foundational and must work without Python, Node, payments or remote management.
9. Rust is the central HTTP/request engine.
10. Kotlin + Jetpack Compose owns Android UX, permissions, lifecycle and services.
11. TypeScript is the authored React/Vite console language; do not switch source to JavaScript.
12. Python and Node are optional trusted application runtimes. A local acknowledgement gate blocks accidental starts and revocation stops them, but this is not a sandbox; OS-backed isolation must be implemented and independently validated before untrusted code is accepted.
13. Target Android 8.0 / API 26+, 4 GB RAM baseline, ARMv7 and ARM64 phones; x86_64 for emulator.
14. Product goal is budget-conscious hosting for small sites/development/modest workloads, not cloud-region reliability.
15. UI direction is fixed: PocketHost teal `#24434A`, bronze `#B18B5E`, ivory `#F6F4EF`, slate `#657174`; modern clear cards and restrained accents. Keep the Android dashboard vertically scrollable on small screens, retain Simple/Advanced modes, and show the 16-connection engine ceiling accurately.
16. The TypeScript console must remain responsive and clearly distinguish disconnected/preview states from live device data. Never fabricate metrics or imply a pairing succeeded before the API confirms it.

## 3. System architecture

| Component | Location | Responsibility |
|---|---|---|
| Android UI | `app/` Kotlin/Compose | User workflow, device state and configuration |
| Hosting lifecycle | `app/` services | Foreground hosting, tunnel and runtime supervision |
| Rust engine | `rust-engine/` | HTTP ingress, static files, multi-host routing, proxy, admission and metrics |
| JNI | `app/src/main/cpp/` and Kotlin bridge | Coarse-grained engine/runtime control |
| Web console | `web-console/` | TypeScript/React management UI |
| Python runtime | Android app process | Embedded CPython WSGI; currently trusted-code only |
| Node runtime | Android `:node` process | Embedded Node; process separation is not a sandbox |
| Control plane | `control-plane/` | Optional Rust/Axum/PostgreSQL remote management |
| Cloudflare | Existing user-owned tunnel | Public connectivity to phone website origin |

Keep module contracts explicit. Do not duplicate HTTP routing in Kotlin or mix management APIs into public request handling.

## 4. Current known implementation boundaries

Source includes Kotlin/Compose app, Rust HTTP engine, multi-site routing, Python WSGI preview, Node mobile SDK integration, TypeScript console, local pairing API, Cloudflare connector, import/backup/diagnostics, versioned device telemetry, static-workload Capacity Lab, timed capacity profiles, first device-side health governor and optional remote control-plane code.

Do not infer production readiness:
- Python/Node run with PocketHost app UID; no strong hostile-code sandbox.
- Node dedicated process is lifecycle separation, not UID isolation.
- V8 heap and worker limits are not hard total-RSS/CPU quotas.
- Python has no automatic dependency install or hard resource sandbox.
- Current backup scope is static sites, not every runtime/configuration/payment record.
- Local management sessions are in-memory and expire; process restart requires pairing again.
- Connected tunnel does not prove public hostname serves the expected website.
- Control-plane source does not prove deployed service or paired physical device.
- Payment processing, encrypted payment ledger and the full Rust-native rate/fairness/queue governor are not implemented merely because PR #14 specifies them.

Read runtime-specific docs before changing these components.

## 5. Required next product work

Current main `c6e07a7248a123aac757a7358dc2df13caec9e86` includes versioned low-frequency device telemetry (including sampled CPU load), a conservative four-connection provisional ceiling, a static-GET Capacity Lab with persisted results, dynamic Rust concurrency admission cap, timed capacity profiles, a five-state Android health derating policy with protective suspension of optional runtimes, live idle telemetry, and daily aggregate rollover reports. These are deliberately partial implementations; see [PHASE_0_STATUS.md](PHASE_0_STATUS.md) for exact boundaries. Continue with broader workload benchmarking and report completeness, then complete the Rust-native rate/fairness/queue governor, runtime isolation, payments and audit. Follow [ROADMAP.md](ROADMAP.md):
1. Reconcile source state and complete baseline CI/device/Cloudflare validation.
2. Validate telemetry, rollover, redaction and bounded retention on devices.
3. Expand Capacity Lab workload classes and benchmark-specific safe capacity graphs.
4. Complete Rust Resource Governor, overload behavior and scheduled profiles (including 30% for three hours).
5. Resolve runtime isolation and network/filesystem restrictions before accepting untrusted code.
6. Implement PSP-based payment events, encrypted ledger, reconciliation and audit.
7. Implement conventional audit verification, signed checkpoints and recovery.
8. Complete website performance, advanced controls, physical tests and signed release.

Do not enable live payments before payment security. Do not call runtime execution sandboxed before OS-level isolation evidence.

## 6. Capacity semantics

- Never advertise a universal maximum visitor count.
- Measure named workloads: static cached/uncached, payload sizes, concurrency, optional Python/Node.
- Report observed peak, sustainable safe capacity and current admission budget separately.
- Capacity percentage means a percentage of calibrated sustainable safe budget, further reduced by live CPU, memory, storage, battery and thermal health. Before the first benchmark, use a conservative provisional ceiling of four concurrent connections. It is not CPU utilization and does not reserve hardware resources.
- Owner can schedule 30% for 3 hours; schedule expires and restores previous profile.
- Governor states: NORMAL, CONSTRAINED, PROTECTIVE, RECOVERY, EMERGENCY.
- Use bounded queues, controlled 429/503, hysteresis, graceful drain and cooldown.
- Keep emergency reserve for management, telemetry and durable event writes.
- Android can still kill processes; PocketHost cannot override OS/OEM decisions.

## 7. Security and payment invariants

- Public website and management interfaces remain separate.
- Rust is the only public ingress; runtime backends bind loopback.
- Runtime egress must not reach management APIs, metadata or private networks unless explicitly allowed.
- Secrets never live in website roots, browser bundles, logs or source control.
- Payment provider hosted/tokenized checkout; never store PAN/CVV/PIN.
- Verify webhook signatures on raw body, timestamp, replay and idempotency.
- Client redirect is not proof of payment; reconcile with provider.
- Store integer minor-unit amounts, ISO currency, provider IDs and necessary redacted metadata only.
- Encrypt payment ledger/backups and test restoration.
- Hash-chain audit events; sign checkpoints.
- No PCI/security certification claims without qualified independent assessment.

## 8. How every coding session must operate

### Before coding
1. Inspect repository status, main HEAD and open PRs.
2. Read this document, North Star, Roadmap, Master Plan and latest implementation status.
3. Inspect actual source for each affected module.
4. Identify interfaces, migrations, tests and workflows.
5. Write a task plan with exact files and acceptance criteria.
6. Preserve architecture unless a documented decision is approved.

### During coding
1. Implement working code, not TODO comments or speculative interfaces.
2. Keep changes focused; avoid unrelated redesign.
3. Add unit, integration, abuse and recovery tests.
4. Update schemas/migrations and compatibility handling together.
5. Keep secrets out of repo and logs.
6. Preserve static hosting if optional runtimes fail.
7. Do not silently change a documented product decision.

### Before claiming completion
1. Run relevant tests and CI.
2. Check exact commit/PR workflow results.
3. Run Android compile, APK packaging and emulator tests where required.
4. Identify physical-device tests that remain.
5. Review security, privacy, resource and migration risks.
6. Update status, roadmap, README and feature docs to match implementation.
7. Report changes, commit/PR, tests passed, tests not run and blockers.

## 9. Status vocabulary

Use only:
- **Implemented:** source code exists.
- **Automated-tested:** named tests passed on exact commit.
- **Emulator-verified:** Android emulator instrumentation passed on exact commit.
- **Device-verified:** named physical device, OS/API, ABI and evidence recorded.
- **Externally-verified:** public hostname/service checked from a separate network.
- **Production-approved:** all release gates passed and no unresolved critical/high security finding.

Never claim “complete”, “secure”, “production-ready”, “deployed” or “works on device” based solely on documentation, compilation or source presence.

## 10. Required report format

For substantial work, report:
1. Repository/branch/commit inspected.
2. Files and behaviors changed.
3. Tests run with exact result.
4. CI/PR links.
5. Physical/external tests not performed.
6. Remaining blockers ordered by risk.
7. Next implementation task.

Do not repeatedly ask the owner to restate decisions already recorded. Do not stop after describing a plan when implementation was requested. If a credential, device action or deployment approval is genuinely required, implement all independent work first and state the exact blocker.

## 11. Canonical document hierarchy

If documents conflict, resolve in this order:
1. User-confirmed product decisions in this document and [NORTH_STAR.md](NORTH_STAR.md).
2. Actual source code and current main commit.
3. [PHASE_0_STATUS.md](PHASE_0_STATUS.md) for tested evidence.
4. [ROADMAP.md](ROADMAP.md) for planned work.
5. [MASTER_PLAN.md](MASTER_PLAN.md) for architecture.
6. [COMPLETION_MILESTONE.md](COMPLETION_MILESTONE.md) for release gates.
7. Feature-specific docs.

Update conflicting documents in the same PR. Do not leave contradictory claims.

## 12. Current handoff

PR #14 merged the adaptive capacity/security/payments/operations specification; PR #15 merged the canonical roadmap and development handoff. Latest implementation commit `c6e07a7248a123aac757a7358dc2df13caec9e86` adds versioned low-frequency device telemetry (including sampled CPU load), a four-connection provisional safe ceiling, persisted static-workload Capacity Lab results, dynamic Rust concurrency admission with HTTP 503/Retry-After, timed capacity profiles, a five-state Android health derating policy, repeated protective suspension of optional runtimes, live idle telemetry and daily aggregate rollover reports. The Android dashboard and TypeScript console preserve the AssayFlux teal/bronze/ivory design and expose current device health and benchmark data.

Quality CI run [43688959461](https://github.com/AssayFlux/PocketHost/actions/runs/43688959461) passed all six jobs on this exact main commit, including Rust, control-plane/PostgreSQL, Python, Node, TypeScript production build, Android native/Kotlin source compilation and Android-test source compilation. It did not build an APK or run emulator instrumentation. Continue from [PHASE_0_STATUS.md](PHASE_0_STATUS.md). Remaining release blockers include the full Rust-native rate/fairness/queue governor, OS-enforced runtime sandbox, PSP payment ledger/webhooks and hash-chained signed audit, complete uptime/latency/financial reporting, physical-device acceptance and external Cloudflare hostname validation.

Primary links:
- [Repository](https://github.com/AssayFlux/PocketHost)
- [Roadmap](ROADMAP.md)
- [Implementation status](PHASE_0_STATUS.md)
- [Adaptive capacity and security specification](ADAPTIVE_CAPACITY_SECURITY_OPERATIONS.md)


## Current repository evidence snapshot — 2026-11-15

This snapshot is anchored to main commit `671f3f731f4bf093449a95e285327115f41bb75b`. Re-check the linked workflow runs before using this document as a release approval; statuses can change after this snapshot.

### Merged implementation

- PR [#16 — interactive Learn section and card-first navigation](https://github.com/AssayFlux/PocketHost/pull/16) is merged. The app/console include the Learn experience and an initial measured-capacity visualization. The chart is based on recorded static benchmark samples; it is not synthetic live telemetry.
- PR [#17 — Owner and Operator host accounts](https://github.com/AssayFlux/PocketHost/pull/17) is merged. The source includes an encrypted app-private account registry, password verifiers, Owner/Operator authorization, account revocation and console sign-in/bootstrap flows. The PR describes instrumentation coverage, but this snapshot does not assert that the latest main-branch emulator workflow has passed.
- The app has a Learn destination and documentation at [LEARN_GUIDE.md](LEARN_GUIDE.md). The UI remains an initial implementation requiring responsive and device acceptance.

### Exact CI evidence at review time

- Main commit: `671f3f731f4bf093449a95e285327115f41bb75b`.
- [Quality workflow 43699334752](https://github.com/AssayFlux/PocketHost/actions/runs/43699334752): completed successfully. Its six source-quality jobs include Rust, control plane, Python, Node, TypeScript console and Android source compilation.
- [Android integration workflow 43699334785](https://github.com/AssayFlux/PocketHost/actions/runs/43699334785): pending when inspected. Do not infer APK assembly or emulator instrumentation success from the quality workflow.

### Remaining release gates — not completed by these merges

1. **Android acceptance:** finish integration workflow; verify APK packaging/signatures/ABI contents; pass API 26/33/35 instrumentation on the exact release SHA; test real ARMv7/ARM64 phones, foreground service, Doze, reboot, battery/thermal behavior, keyboard, accessibility, landscape and responsive layouts.
2. **Native authentication polish:** Android BiometricPrompt/Keystore unlock, secure Owner recovery, complete native login/logout/expired/revoked-session UX and end-to-end instrumentation remain unverified or incomplete. Console sign-in and API role enforcement do not by themselves prove native biometric login.
3. **Runtime isolation:** Python and Node execute under the Pocket Host app UID. The Node process and V8 heap limit are not a sandbox. Keep optional runtimes restricted to trusted code until OS-enforced filesystem/network isolation and enforceable resource quotas pass adversarial review.
4. **Cloudflare:** configure/reuse the owner's existing named tunnel on a physical device and verify the Public Hostname serves the intended site over HTTPS from an external network. The public hostname must never target the loopback management API.
5. **Capacity:** current benchmark and admission controls are first implementations. Complete representative cached/uncached and payload-size workloads, Python/Node trusted workload measurements, throughput and p50/p95/p99, Rust-native rate/fairness/bounded-queue controls, graceful drain, long-duration thermal tests and schedule restart/expiry acceptance.
6. **Operations:** extend backup beyond static-site scope; test restore and interruption; complete request-level redacted logs, durable/tamper-evident audit verification, storage-pressure behavior and reproducible daily reports.
7. **Optional remote management:** deploy the control plane on a trusted host, pair the real phone, then test desired configuration, leased commands, acknowledgements, revocation and recovery. Source code alone is not deployment evidence.
8. **Payments:** no live payment processing is implemented/approved. If enabled for launch, select a PSP and implement hosted/tokenized checkout, signed/replay-protected webhooks, idempotent durable ledger, reconciliation, encrypted backups and qualified security review. Never store PAN/CVV/PIN.
9. **Release:** configure production signing, verify upgrade paths and checksums, review dependencies/secrets and resolve all critical/high security findings.

### Product boundaries

- Static hosting remains the primary independent feature; optional runtimes, payments and remote management must not be prerequisites.
- Android foreground services improve continuity but cannot guarantee 24/7 uptime or override OEM/OS battery policies.
- Cloudflare Tunnel connectivity is not visitor authentication; use Cloudflare Access for private sites.
- Do not claim universal visitor counts, complete sandboxing, payment compliance, deployed remote control, production readiness or a signed release without the corresponding evidence.

**Release decision:** integrated development build; not production approved. This status changes only when the relevant code, tests, device/network evidence and security review are recorded against the exact release commit.
