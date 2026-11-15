# PocketHost — Completion and production-readiness milestone

**Purpose:** single actionable definition of completion. Source presence, documentation or a green compile is not production approval. Every gate must be marked PASS, FAIL or NOT TESTED with commit-specific evidence.

## Product baseline

- Standalone Kotlin/Jetpack Compose Android app; no Termux and no root.
- Android 8.0 / API 26+, 4 GB RAM baseline; ARMv7 + ARM64 phones, x86_64 emulator.
- Rust/Tokio/Hyper owns the website HTTP path; TypeScript/React console.
- Static sites plus optional trusted Python WSGI and Node.js runtimes.
- Existing Cloudflare Tunnel/domain is reused; phone remains the website origin.
- Simple and Advanced modes; local hosting must not require a VPS or remote control plane.
- Adaptive capacity, strong runtime isolation, payment security and operations are required before the corresponding capabilities are advertised as production-ready.

## Workstream gates

| Workstream | Current source status | Production completion evidence |
|---|---|---|
| Android shell/lifecycle | Integrated | API 26/33/35 and physical-device lifecycle acceptance |
| Rust static engine | Integrated | HTTP security, load, fault and multi-site end-to-end tests |
| Python WSGI | Preview runtime | Device tests, failure recovery, quotas and reviewed isolation model |
| Node.js | Integrated mobile SDK | Device execution, ABI, memory, restart and isolation acceptance |
| Local management API | Integrated | Pairing, CORS/PNA, abuse, revocation and DNS-rebinding review |
| TypeScript console | Connected in source | Full browser/device end-to-end validation |
| Cloudflare Tunnel | Connector source/build integrated | Existing tunnel configured in app; external HTTPS content verified |
| Remote control plane | Optional source implemented | Deploy and test real phone pairing/commands only if included in release |
| Adaptive telemetry | Partial implementation: versioned 30-second hardware samples and bounded history | Verify accuracy/missing values, battery/thermal behavior, retention, restart and physical-device performance |
| Capacity Lab | First static-GET loopback sweep implemented; p95 latency and safe concurrency ceiling recorded | Add representative workload classes, throughput/resource trends, repeatability and long-duration tests |
| Resource Governor | Partial: Rust concurrency cap/503 plus five-state Android health derating and optional-runtime suspension | Add Rust-native rate/fairness/queue policies, graceful drain, fault injection and hysteresis acceptance |
| Capacity schedules | Source implemented: custom timed percentage, expiry and previous-profile restoration | Verify restart, wall-clock changes, schedule replacement and failure recovery |
| Runtime sandbox | Critical gap | Distinct UID/OS boundary, filesystem/network restrictions, quotas and adversarial tests |
| Payments | Not implemented by PR #14 | PSP integration, signed webhooks, idempotency, durable ledger, reconciliation and security assessment |
| Audit integrity | Planned/specification | Conventional hash-chain tamper detection and signed checkpoints |
| Daily reports/logs | Partial source: bounded redacted logs, hourly daily snapshots, rollover archive, 90-report retention and JSON export | Verify midnight rollover, process restart, storage exhaustion, reproducibility and full uptime/latency/tunnel reporting |
| Backup/restore | Static-site scope | Tamper/wrong-passphrase/interruption and verified recovery; extend scope before payment ledger backup claims |
| Release | Workflow/signing guide | Signed APKs, upgrade tests, checksums and compatibility evidence |

## Non-negotiable implementation order

1. Keep the existing phone-hosting architecture and document actual source state.
2. Complete current CI/instrumentation on the exact final source SHA.
3. Validate static hosting, routing, deployment, tunnel and local management on target devices.
4. Extend and validate device telemetry, rollover reports and bounded operational logging.
5. Expand Capacity Lab beyond static GET and validate evidence-based safe capacity.
6. Complete the Rust-native Resource Governor, rate/fairness/queue handling and configurable schedules.
7. Resolve runtime isolation before accepting third-party/untrusted code.
8. Implement durable audit, secrets management and payment provider integration.
9. Implement reconciliation, encrypted backups and daily financial/operations reports.
10. Implement conventional signed, tamper-evident audit records and independent verification.
11. Complete external security review, physical-device stress/fault tests and signed release.

## Required device and network evidence

- [ ] Quality workflow green on exact release commit.
- [ ] Android integration workflow green on exact release commit.
- [ ] API 26, 33 and 35 emulator instrumentation pass.
- [ ] ARMv7 and ARM64 physical-device tests pass.
- [ ] Vivo Y95 and Moto G54 lifecycle/network results recorded.
- [ ] Static site deployment, host routing, rollback and multi-site behavior pass.
- [ ] Python/Node startup, routing, stop/restart and recovery pass where enabled.
- [ ] Existing Cloudflare tunnel serves expected content over HTTPS from a separate network.
- [ ] Public hostname cannot reach local management API.
- [ ] Backup/restore and diagnostics redaction pass.
- [ ] APK signing, upgrade and checksum verification pass.

## Adaptive capacity acceptance

- [x] First versioned device telemetry is implemented for model/API/ABIs/cores, sampled CPU, RAM/PSS/heaps, storage, battery/temperature, thermal state and network transport; physical accuracy and sustained behavior remain NOT TESTED.
- [ ] Benchmark covers representative static and trusted-runtime workloads and stops safely on thermal, memory, battery, timeout and cancellation triggers.
- [ ] Graph separates observed peak, sustainable safe capacity and current admission budget; current graph is a static-workload p95 chart only.
- [x] First concurrency admission cap and HTTP 503/Retry-After rejection are implemented; bounded waiting queues and rate/fairness policies remain NOT IMPLEMENTED.
- [ ] Critical management/logging reserve survives visitor overload under stress.
- [ ] 30% capacity profile source workflow exists; three-hour wall-clock, process-restart and restoration acceptance remain NOT TESTED.
- [x] Initial CPU/memory/storage/battery/thermal device-side health derating and protective runtime suspension are implemented; physical hysteresis/recovery acceptance remains NOT TESTED.

## Security, payments and audit acceptance

- [ ] No cross-project filesystem, credential or management API access.
- [ ] Runtime egress blocks loopback management, metadata, private network and DNS-rebinding bypasses according to policy.
- [ ] OS-enforced quotas/isolation are demonstrated; application heap limits alone do not count.
- [ ] Webhook invalid signature, stale timestamp, duplicate, replay and out-of-order events are safely handled.
- [ ] Client redirects cannot mark a payment paid; provider reconciliation detects missing/mismatched events.
- [ ] Raw card numbers, CVVs and PINs are never stored.
- [ ] Payment records and backups are encrypted and restore-tested.
- [ ] Audit hash-chain tampering, deletion and reordering are detected.
- [ ] Audit integrity uses conventional local hash chaining and signed checkpoints.
- [ ] Logs redact tokens, cookies, payment data and sensitive request bodies.
- [ ] Daily report is reproducible and reconciles totals with the PSP.

## Product and reliability acceptance

- [ ] Per-site limits, safe presets and custom controls are usable and validated.
- [ ] Logs rotate and cannot exhaust storage.
- [ ] Runtime crashes do not corrupt static hosting.
- [ ] Network transitions and tunnel recovery are tested.
- [ ] Known Android/OEM/battery/network limitations are disclosed.
- [ ] No unresolved critical/high security issue remains.
- [ ] No feature is labelled production-ready without implementation and test evidence.

## Honesty rules

- Source present ≠ runtime verified.
- Compile passed ≠ instrumentation passed.
- Emulator passed ≠ physical phone passed.
- Tunnel connected ≠ public hostname verified.
- Benchmark peak ≠ sustainable capacity.
- Capacity percentage ≠ CPU utilization.
- Node process separation ≠ security sandbox.
- A hash chain or signature detects alteration after recording; it does not prove that source events were truthful.
- Payment-provider integration ≠ PCI compliance.
- Remote-control source ≠ deployed service.

See [PHASE_0_STATUS.md](PHASE_0_STATUS.md) for current evidence and [DEVELOPMENT_CONTEXT.md](DEVELOPMENT_CONTEXT.md) for future-work continuity rules.
