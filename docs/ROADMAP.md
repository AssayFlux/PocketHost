# Pocket Host 0.1.0 — Delivery Roadmap

All milestones use the contracts and failure boundaries in [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md). A milestone is complete only with repeatable test evidence on an exact commit and named target.

| Milestone | Scope | Exit condition |
|---|---|---|
| 0.1.0-A | Architecture and contracts | Threat model, device identity, host-agent API, service/runtime manifests, desired state, events, telemetry and compatibility rules approved |
| 0.1.0-B | Mobile command center | Implement Android and iOS command-center clients against the same fleet API; local pairing, secure owner approvals, host list and health |
| 0.1.0-C | Autonomous host supervisor | Durable local state, independent lifecycle, health, resource budgets, restart, graceful drain and rollback |
| 0.1.0-D | Service component model | Multi-component sites, multi-runtime manifest, static ingress and authenticated internal routes |
| 0.1.0-E | Isolation and security | Per-project/component OS boundary, scoped filesystem/network, secret separation and adversarial tests |
| 0.1.0-F | Connectivity and recovery | Local network, independent AssayFlux Relay/Cloudflare, backup/restore and provider-failure containment |
| 0.1.x-G | Linux and SBC hosts | Linux x86_64/ARM64 host agent, Raspberry Pi exact-model profiles, headless install and power-loss recovery |
| 0.1.x-H | Windows and macOS | Native host lifecycle, installers, signing, network/filesystem policy and mobile management |
| 0.1.x-I | MCU/edge integration | DemonCore board security/identity/OTA acceptance and authenticated Pocket Host edge adapter |
| 0.1.x-J | Multi-host deployments | Compatibility-aware placement, signed artifact transfer, service discovery and state-aware recovery |
| 1.0.0 | Production release | Support matrix, independent security review, operational SLOs, incident response, signed releases and tested recovery |

## Workstreams

### Mobile command center
- Shared product/API contract for Android and iOS.
- Android native Kotlin/Compose implementation.
- iOS native Swift/SwiftUI implementation.
- QR/local discovery pairing, secure approvals, notifications, offline host view and recovery.
- Mobile app must never supervise workloads by keeping itself alive.

### Host agents
- Portable Rust contracts and engine where supported.
- OS-specific adapters for Android, Linux, Windows, macOS and embedded Linux.
- Durable desired state and local reconciliation.
- Host-specific resource/thermal/power profiles.

### Workload runtime
- Website/service manifests.
- Per-component execution cells and capability broker.
- Runtime provider catalog with version/ABI/OS compatibility.
- Static ingress independent of all language runtimes.

### Fleet and edge
- Host enrollment, revocation, capabilities and health.
- Direct authenticated LAN links and optional remote relay.
- DemonCore/DCP adapter with explicit device capabilities.
- MCU operation safety and per-board validation.

### Resilience
- Separate health domains, queues and storage ownership.
- Timeouts, bounded retries, circuit breakers and backpressure.
- Atomic deploy, rollback and backup restore.
- Chaos tests for phone loss, control-plane outage, node loss, runtime crash, tunnel failure, storage full and network partition.

## Definition of done

A task includes implementation, tests, security review where applicable, documentation, upgrade/rollback behavior and observed evidence. Source presence and successful compilation alone are insufficient.
