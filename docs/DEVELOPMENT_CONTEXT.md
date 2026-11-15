# PocketHost — Canonical Development Context

**Read this document at the start of every implementation session.** It preserves product decisions and prevents architecture drift, duplicated work, false completion claims and loss of context.

## 1. Repository identity

- Repository: https://github.com/AssayFlux/PocketHost
- Owner: AssayFlux
- Product: AssayFlux PocketHost
- Developer identity: Designed by AssayFlux
- Package: `com.assayflux.pockethost`
- Main branch: `main`
- Initial version target: `0.1.0`
- Current documentation branch: `docs/roadmap-development-context` (PR to be opened after updates).

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
12. Python and Node are optional trusted application runtimes until a real security sandbox is implemented and independently validated.
13. Target Android 8.0 / API 26+, 4 GB RAM baseline, ARMv7 and ARM64 phones; x86_64 for emulator.
14. Product goal is budget-conscious hosting for small sites/development/modest workloads, not cloud-region reliability.

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

Source includes Kotlin/Compose app, Rust HTTP engine, multi-site routing, Python WSGI preview, Node mobile SDK integration, TypeScript console, local pairing API, Cloudflare connector, import/backup/diagnostics and optional remote control-plane code.

Do not infer production readiness:
- Python/Node run with PocketHost app UID; no strong hostile-code sandbox.
- Node dedicated process is lifecycle separation, not UID isolation.
- V8 heap and worker limits are not hard total-RSS/CPU quotas.
- Python has no automatic dependency install or hard resource sandbox.
- Current backup scope is static sites, not every runtime/configuration/payment record.
- Local management sessions are in-memory and expire; process restart requires pairing again.
- Connected tunnel does not prove public hostname serves the expected website.
- Control-plane source does not prove deployed service or paired physical device.
- Payment processing, encrypted payment ledger, blockchain anchoring and full adaptive governor are not implemented merely because PR #14 specifies them.

Read runtime-specific docs before changing these components.

## 5. Required next product work

Current main includes a first static-GET Capacity Lab, dynamic Rust concurrency admission cap, scheduled capacity profile and daily aggregate rollover report. These are deliberately partial implementations; see [PHASE_0_STATUS.md](PHASE_0_STATUS.md) for exact boundaries. Continue with broader device telemetry and report completeness, then the full multi-state governor, runtime isolation, payments and audit. Follow [ROADMAP.md](ROADMAP.md):
1. Reconcile source state and complete baseline CI/device/Cloudflare validation.
2. Implement device telemetry and bounded structured logs/daily reports.
3. Build Capacity Lab and benchmark-specific safe capacity graphs.
4. Implement Rust Resource Governor, overload behavior and scheduled profiles (including 30% for three hours).
5. Resolve runtime isolation and network/filesystem restrictions before accepting untrusted code.
6. Implement PSP-based payment events, encrypted ledger, reconciliation and audit.
7. Add optional hash-only blockchain anchoring after privacy/security review.
8. Complete website performance, advanced controls, physical tests and signed release.

Do not jump to blockchain before tamper-evident audit. Do not enable live payments before payment security. Do not call runtime execution sandboxed before OS-level isolation evidence.

## 6. Capacity semantics

- Never advertise a universal maximum visitor count.
- Measure named workloads: static cached/uncached, payload sizes, concurrency, optional Python/Node.
- Report observed peak, sustainable safe capacity and current admission budget separately.
- Capacity percentage means a percentage of calibrated sustainable safe budget, further reduced by live health. It is not CPU utilization and does not reserve hardware resources.
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
- Hash-chain audit events; sign checkpoints. Blockchain anchors checkpoint hashes only.
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

PR #14 merged the initial adaptive capacity/security/payments/operations specification; PR #15 merged the canonical roadmap and development handoff. Main commit `96200c320e0a5d6fc14c9d7b78fcb62f08b5429f` adds the first static-workload Capacity Lab, Rust admission cap, scheduled profiles and daily aggregate snapshots. The next implementation pass adds low-frequency hardware telemetry (CPU/RAM/storage/battery/thermal/network), health-based admission derating and a p95 latency chart. Continue from the explicit partial-implementation boundaries in [PHASE_0_STATUS.md](PHASE_0_STATUS.md). The full multi-state Resource Governor, OS-enforced sandbox, payment ledger, blockchain anchoring and complete operational reporting still require implementation and validation.

Primary links:
- [Repository](https://github.com/AssayFlux/PocketHost)
- [Roadmap](ROADMAP.md)
- [Implementation status](PHASE_0_STATUS.md)
- [Adaptive capacity and security specification](ADAPTIVE_CAPACITY_SECURITY_OPERATIONS.md)
