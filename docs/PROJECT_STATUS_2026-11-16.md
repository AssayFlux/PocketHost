# ASSAYFLUX POCKET HOST — PROJECT STATUS

**Review timestamp:** 2026-11-16  
**Reviewed source baseline:** `6c1ba2fe2274b3137dc7e0b62bdb1f50f151bade`  
**Documentation refresh:** in progress on main after this baseline; refresh commit SHAs must not be confused with source verification.  
**Release:** 0.1.0 target · **Decision:** not production approved.

## 1. What this report means

This is an evidence-oriented status snapshot, not a claim that every feature is complete. Source presence, compilation, automated tests, emulator execution, physical-device tests, external connectivity and production approval are separate states.

The latest source baseline checked for this documentation refresh was `6c1ba2f`. Quality workflow `43813160835` completed successfully. Android integration workflow `43813160678` was queued when inspected. Therefore this report does not claim API 26/33/35 runtime success on that head. Recheck Actions before treating the state as current.

## 2. Implemented source foundations

- Native Kotlin/Jetpack Compose host app with Dashboard, Sites, Activity, Learn and Settings destinations.
- Rust/Tokio/Hyper website engine with static serving, hostname routing, reverse proxy and bounded admission foundations.
- TypeScript/React/Vite local management console.
- Authenticated loopback management API and pairing/session foundations.
- CPython 3.11 / Chaquopy and Node mobile 24.22.1 source moved into a separate companion package.
- Signature-protected, versioned Binder IPC, caller checks and bounded archive transfer.
- Android cloudflared build integration for supported native ABIs; owner has an existing named Cloudflare Tunnel.
- Encrypted .afbackup and staged restore foundations.
- Initial device telemetry, Capacity Lab and health-based admission derating.
- Optional Axum/PostgreSQL remote-management source and deployment workflow.
- Isolated UID, Unix-domain listener and native seccomp proof code exists in source.

## 3. Critical limitations

### Runtime migration
Companion source integration does not prove Python/Node startup, visitor routing, lifecycle recovery or ABI packaging on the final source head. End-to-end API 26/33/35 evidence remains required.

### Security Gate 2
The companion UID is separate from the host UID, but projects share the companion UID and private storage. Python multi-site code shares a worker process; Node shares companion identity. Outbound network access is not OS-enforced. The isolated service/seccomp/Unix socket code is a proof spike, not a production sandbox. Keep Python/Node owner-trusted-code-only.

### AssayFlux Relay
First-party relay is now a product requirement and architecture plan. Gateway, Android agent, device enrollment, multiplexed transport, hostname registry, certificates and visitor access gateway are not implemented. Cloudflare remains the available external tunnel integration; reuse the existing named tunnel.

### Capacity
Telemetry and a first static Capacity Lab exist in source. Full fair scheduling, representative workload benchmarking, long-duration evidence, per-site fairness and measured safe operating envelopes remain open. No visitor-count guarantee.

### Operations
Encrypted backup and restore foundations exist; interruption, corruption, low-storage, wrong-passphrase and rollback fault coverage remains open. Settings and visual/accessibility acceptance need review.

### Release
No production-approved signed APK pair, external connectivity evidence, physical-device acceptance or independent security review is recorded here.

## 4. Primary-version gates

1. Finish companion Python/Node lifecycle and Rust end-to-end routing.
2. Complete per-project OS isolation and adversarial security.
3. Build AssayFlux Relay and device enrollment.
4. Complete separate Owner/Operator and visitor authentication.
5. Complete Rust governor and Capacity Lab.
6. Complete backup, operations and lifecycle recovery.
7. Complete product UX and onboarding.
8. Pass exact-SHA API 26/33/35, ABI builds, signing, external tests and independent review.

See `docs/NORTH_STAR.md` for detailed tasks and objective exit criteria; `docs/ROADMAP.md` is the execution sequence.

## 5. Readiness

The earlier 37% estimate is historical and predates the latest runtime/security and Relay scope changes. It is not a current audited readiness score. Do not publish a replacement percentage until every gate is re-scored against exact evidence and the added Relay/authentication scope is included.

## 6. Verification ledger

| Evidence | Result at review |
|---|---|
| Source SHA | `6c1ba2fe2274b3137dc7e0b62bdb1f50f151bade` |
| Quality workflow | `43813160835` completed success |
| Android integration | `43813160678` queued at review; no pass claimed |
| API 26/33/35 matrix | Not accepted on this baseline |
| Physical ARMv7/ARM64 device | Not recorded |
| External Cloudflare HTTPS | Not recorded |
| AssayFlux Relay | Planned; not implemented |
| Independent security review | Not recorded |

Update this ledger only with observed results. Historical failed or cancelled runs should be retained as history, not misrepresented as current failures or passes.
