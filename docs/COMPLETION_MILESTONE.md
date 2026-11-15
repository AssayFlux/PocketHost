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
| Adaptive telemetry | Planned/specification | Correct device metrics, validity, retention and redaction tests |
| Capacity Lab | Planned/specification | Repeatable device benchmarks with documented workload and safe envelope |
| Resource Governor | Planned/specification | Bounded admission, queues, overload behavior, recovery and fault injection |
| Capacity schedules | Planned/specification | 30%-for-3-hours schedule, expiry, restoration and persistence test |
| Runtime sandbox | Critical gap | Distinct UID/OS boundary, filesystem/network restrictions, quotas and adversarial tests |
| Payments | Not implemented by PR #14 | PSP integration, signed webhooks, idempotency, durable ledger, reconciliation and security assessment |
| Audit/blockchain | Planned/specification | Tamper detection, signed checkpoints; optional hash-only chain anchoring |
| Daily reports/logs | Partial logging source; expanded design specified | Durable structured events, redaction, rotation, daily report and restore tests |
| Backup/restore | Static-site scope | Tamper/wrong-passphrase/interruption and verified recovery; extend scope before payment ledger backup claims |
| Release | Workflow/signing guide | Signed APKs, upgrade tests, checksums and compatibility evidence |

## Non-negotiable implementation order

1. Keep the existing phone-hosting architecture and document actual source state.
2. Complete current CI/instrumentation on the exact final source SHA.
3. Validate static hosting, routing, deployment, tunnel and local management on target devices.
4. Implement telemetry and bounded operational logging.
5. Implement Capacity Lab and evidence-based safe capacity.
6. Implement Resource Governor, overload handling and configurable schedules.
7. Resolve runtime isolation before accepting third-party/untrusted code.
8. Implement durable audit, secrets management and payment provider integration.
9. Implement reconciliation, encrypted backups and daily financial/operations reports.
10. Add optional blockchain checkpoint anchoring only after audit integrity and privacy review.
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

- [ ] Hardware inventory and live telemetry are valid, timestamped and do not fabricate missing data.
- [ ] Benchmark runs representative static and trusted-runtime workloads and stops safely on thermal, memory, battery, timeout and cancellation triggers.
- [ ] Graph separates observed peak, sustainable safe capacity and current admission budget.
- [ ] Governor keeps concurrency and queues bounded under overload.
- [ ] Overload produces documented 429/503 behavior and recovers without restart loops.
- [ ] Critical management/logging reserve survives visitor overload.
- [ ] 30% capacity profile runs for three hours, expires, restores the previous profile and survives app restart as specified.
- [ ] Thermal/memory degradation reduces admission automatically with hysteresis.

## Security, payments and audit acceptance

- [ ] No cross-project filesystem, credential or management API access.
- [ ] Runtime egress blocks loopback management, metadata, private network and DNS-rebinding bypasses according to policy.
- [ ] OS-enforced quotas/isolation are demonstrated; application heap limits alone do not count.
- [ ] Webhook invalid signature, stale timestamp, duplicate, replay and out-of-order events are safely handled.
- [ ] Client redirects cannot mark a payment paid; provider reconciliation detects missing/mismatched events.
- [ ] Raw card numbers, CVVs and PINs are never stored.
- [ ] Payment records and backups are encrypted and restore-tested.
- [ ] Audit hash-chain tampering, deletion and reordering are detected.
- [ ] Blockchain, if enabled, anchors hashes only and is not a hosting dependency.
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
- Hash chain/blockchain ≠ proof that source events were truthful.
- Payment-provider integration ≠ PCI compliance.
- Remote-control source ≠ deployed service.

See [PHASE_0_STATUS.md](PHASE_0_STATUS.md) for current evidence and [DEVELOPMENT_CONTEXT.md](DEVELOPMENT_CONTEXT.md) for future-work continuity rules.
