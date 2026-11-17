# Pocket Host 0.1.0 — Engineering Context

Read [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md) before changing system boundaries. Read [MASTER_PLAN.md](MASTER_PLAN.md) for implementation gates.

## Non-negotiable architecture

- Android and iOS mobile applications are first-class command centers.
- The phone is not a required runtime dependency or fleet single point of failure.
- Each host locally supervises its workloads and retains signed desired state and last-known-good release.
- Components are independent fault domains with their own identity, lifecycle, permissions, health, limits and rollback.
- Components communicate only through authenticated, encrypted, versioned and bounded contracts.
- Separate control plane and visitor/service data plane.
- Local hosting must continue through phone, Internet, control-plane, tunnel and telemetry outages.
- Static hosting must continue when a language runtime fails.
- Tunnel providers are independent and may run concurrently.
- MCU devices expose only board-verified bounded capabilities.
- Do not advertise support without exact-device evidence.

## Repository boundaries

- `app/`: Android application.
- `runtime-worker/`: Android runtime companion.
- `rust-engine/`: Rust HTTP website engine.
- `control-plane/`: optional Rust/PostgreSQL remote fleet service.
- `web-console/`: secondary browser management client.
- `DemonCore`: separate MCU firmware and device protocol repository.

## Implementation rules

- Preserve platform-native lifecycle and security mechanisms.
- Prefer versioned contracts and narrow adapters over shared mutable state.
- Avoid a premature microservices explosion; isolate by process and trust boundary where needed, but keep deployment practical for small devices.
- No arbitrary remote shell, unrestricted filesystem access or public management API.
- Use idempotent commands, durable acknowledgements, expiry and explicit owner approval.
- Every component update must be staged and independently rollback-capable.
- Record tests and evidence for exact commit SHA and named target.
