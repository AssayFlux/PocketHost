# Pocket Host 0.1.0 — Documentation Index

## Start here

0. [Competitive Landscape and Product Gap Analysis](COMPETITIVE_LANDSCAPE.md) — competitor lessons, Pocket Host differentiation and implementation priorities.
1. [System Architecture](SYSTEM_ARCHITECTURE.md) — normative component model, mobile command center, device fabric and fault containment.
2. [Master Plan](MASTER_PLAN.md) — five-step implementation plan and release acceptance.
3. [Host Agent and Role Contract](HOST_AGENT_CONTRACT.md) — verified enrollment, role grants, job lifecycle and transports.
4. [Device Software Matrix](DEVICE_SOFTWARE_MATRIX.md) — installable products, supported roles and transports per device family.
5. [Roadmap](ROADMAP.md) — sequenced milestones.
6. [Project Baseline](PROJECT_BASELINE.md) — repository implementation inventory and version identity.
7. [Development Context](DEVELOPMENT_CONTEXT.md) — rules for contributors and coding agents.
8. [Architecture Decisions](DECISIONS.md) — accepted product and engineering decisions.

## Product and mobile UX

- [Mobile Design System](UI_DESIGN.md) — Android/iOS command center, branding, themes, navigation and accessibility.
- [Learn Guide](LEARN_GUIDE.md) — user-facing operating guidance.

## Engineering and security

- [Security Isolation Design](SECURITY_ISOLATION_DESIGN.md)
- [Remote Control Acceptance](REMOTE_CONTROL_ACCEPTANCE.md)
- [Step 2 Device Trust Implementation](STEP_2_DEVICE_TRUST_IMPLEMENTATION.md)
- [Step 3 Device Agents](STEP_3_DEVICE_AGENTS.md)
- [Step 4 Main-App Web Baseline](STEP_4_WEB_BASELINE.md)
- [Web Toolchain Boundaries](WEB_TOOLCHAIN_BOUNDARIES.md)
- [Future Runtime Library — 20 Optional Packages](FUTURE_RUNTIME_LIBRARY.md)
- [Runtime Companion](RUNTIME_COMPANION.md)
- [Runtime Project Storage](RUNTIME_PROJECT_STORAGE.md)
- [Python Runtime](PYTHON_RUNTIME.md)
- [Node Runtime](NODE_RUNTIME.md)
- [Website Deployment](WEBSITE_DEPLOYMENT.md)
- [Site Backup](SITE_BACKUP.md)
- [Adaptive Capacity and Operations](ADAPTIVE_CAPACITY_SECURITY_OPERATIONS.md)
- [Control Plane Deployment](CONTROL_PLANE_DEPLOYMENT.md)
- [Release Signing and Provenance](RELEASE_SIGNING.md)
- [AssayFlux Relay](ASSAYFLUX_RELAY.md)
- [Cloudflare Tunnel](CLOUDFLARE_TUNNEL.md)
- [Management API schema](management-api-v1.yaml)

## Shared machine-readable contracts

- [Interoperability Protocol](../protocol/README.md) — versioned device hello, one-time enrollment challenge, signed role grants, typed jobs and compatibility rules.
- [Step 1 Trust and Protocol](STEP_1_TRUST_AND_PROTOCOL.md) — normative trust ceremony, threat model, transport matrix, runtime/artifact boundaries and DemonCore contract.

## Repository boundaries

- Android app: `app/`
- Android runtime companion: `runtime-worker/`
- Rust website engine: `rust-engine/`
- Remote control plane: `control-plane/`
- Browser operator console: `web-console/`
- Linux node agent preview: `agents/linux/`
- MCU and edge fleet: [AssayFlux/DemonCore](https://github.com/AssayFlux/DemonCore)
