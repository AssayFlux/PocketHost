# Pocket Host 0.1.0 — Documentation Index

## Start here

1. [System Architecture](SYSTEM_ARCHITECTURE.md) — normative component model, mobile command center, device fabric, data/control planes and fault containment.
2. [Master Plan](MASTER_PLAN.md) — implementation gates and release acceptance.
3. [Roadmap](ROADMAP.md) — sequenced milestones.
4. [Project Baseline](PROJECT_BASELINE.md) — repository implementation inventory and version identity.
5. [Development Context](DEVELOPMENT_CONTEXT.md) — rules for contributors and coding agents.
6. [Architecture Decisions](DECISIONS.md) — accepted product and engineering decisions.

## Product and mobile UX

- [Mobile Design System](UI_DESIGN.md) — Android/iOS command center, branding, themes, navigation and accessibility.
- [Learn Guide](LEARN_GUIDE.md) — user-facing operating guidance.

## Engineering and security

- [Security Isolation Design](SECURITY_ISOLATION_DESIGN.md)
- [Remote Control Acceptance](REMOTE_CONTROL_ACCEPTANCE.md)
- [Runtime Companion](RUNTIME_COMPANION.md)
- [Runtime Project Storage](RUNTIME_PROJECT_STORAGE.md)
- [Python Runtime](PYTHON_RUNTIME.md)
- [Node Runtime](NODE_RUNTIME.md)
- [Website Deployment](WEBSITE_DEPLOYMENT.md)
- [Site Backup](SITE_BACKUP.md)
- [Adaptive Capacity and Operations](ADAPTIVE_CAPACITY_SECURITY_OPERATIONS.md)
- [Control Plane Deployment](CONTROL_PLANE_DEPLOYMENT.md)
- [AssayFlux Relay](ASSAYFLUX_RELAY.md)
- [Cloudflare Tunnel](CLOUDFLARE_TUNNEL.md)
- [Management API schema](management-api-v1.yaml)

## Repository boundaries

- Android app: `app/`
- Android runtime companion: `runtime-worker/`
- Rust website engine: `rust-engine/`
- Remote control plane: `control-plane/`
- Browser operator console: `web-console/`
- MCU and edge fleet: [AssayFlux/DemonCore](https://github.com/AssayFlux/DemonCore)
