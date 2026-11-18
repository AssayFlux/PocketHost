# Pocket Host — Documentation Index

This documentation set follows the new three-version pathway. The former five-step roadmap is retired. G0–G9 is mandatory for every release.

## Start here
1. [README](../README.md) — product summary, release pathway and build entry points.
2. [Master Plan](MASTER_PLAN.md) — product rules and version boundaries.
3. [Three-Version Roadmap](ROADMAP.md) — v0.1.0, v0.1.1 and v0.1.2.
4. [Mandatory Release Gates G0–G9](RELEASE_GATES.md)
5. [Immutable Release Baseline Policy](RELEASE_BASELINE_POLICY.md) — release tag, protected baseline and artifact-hash lock procedure.
6. [Complete Repository File Inventory](REPOSITORY_BASELINE.md) — every tracked file/directory and detailed source status at the audit snapshot.
7. [Project Baseline](PROJECT_BASELINE.md) — current module and release status.
8. [Repository Deep Audit](REPOSITORY_AUDIT.md) — risks, gaps and workflow evidence.
9. [Remediation Audit Register](REMEDIATION_AUDIT.md) — severity-ranked findings, fixes, source-backed limits and verification state.
10. [Development Context](DEVELOPMENT_CONTEXT.md) — contributor rules and active priorities.
11. [Architecture Decisions](DECISIONS.md) — durable product decisions.
12. [Product North Star](NORTH_STAR.md) — long-term direction.

## v0.1.0 — First Hosting
- [Static Hosting Baseline](STEP_4_WEB_BASELINE.md)
- [Website Deployment](WEBSITE_DEPLOYMENT.md)
- [Website Backup and Restore](SITE_BACKUP.md)
- [Capacity and Operations](ADAPTIVE_CAPACITY_SECURITY_OPERATIONS.md)
- [User Guide](LEARN_GUIDE.md)
- [Security and Isolation](SECURITY_ISOLATION_DESIGN.md)
- [Public Ingress v0.1.0](PUBLIC_INGRESS_V0_1_0.md)
- [AssayFlux Relay](ASSAYFLUX_RELAY.md)
- [Native Android Relay Architecture](NATIVE_ANDROID_RELAY.md) — approved Kotlin foreground-service + Rust/JNI implementation plan
- [Cloudflare Tunnel](CLOUDFLARE_TUNNEL.md)
- [Pocket Host Design Language V1](UI_DESIGN.md)

## v0.1.1 — Workbench, Runtime & Linux Relay Installation
- [Workbench Specification](WORKBENCH.md)
- [Web Toolchain Boundaries](WEB_TOOLCHAIN_BOUNDARIES.md)
- [Runtime Companion](RUNTIME_COMPANION.md)
- [Python Runtime](PYTHON_RUNTIME.md)
- [Node.js Runtime](NODE_RUNTIME.md)
- [Runtime Project Storage](RUNTIME_PROJECT_STORAGE.md)
- [Future Runtime Library](FUTURE_RUNTIME_LIBRARY.md)

## v0.1.2 — Connected Device Fabric
- [System Architecture](SYSTEM_ARCHITECTURE.md)
- [Device Software Matrix](DEVICE_SOFTWARE_MATRIX.md)
- [Host Agent Contract](HOST_AGENT_CONTRACT.md)
- [Device Trust and Protocol](STEP_1_TRUST_AND_PROTOCOL.md)
- [Android Device Trust Implementation](STEP_2_DEVICE_TRUST_IMPLEMENTATION.md)
- [Device Agents and Workers](STEP_3_DEVICE_AGENTS.md)
- [Remote Control Acceptance](REMOTE_CONTROL_ACCEPTANCE.md)
- [Control Plane Deployment](CONTROL_PLANE_DEPLOYMENT.md)
- [Release Signing and Provenance](RELEASE_SIGNING.md)

## Product, security and competitive references
- [Security and Isolation](SECURITY_ISOLATION_DESIGN.md)
- [Competitive Landscape](COMPETITIVE_LANDSCAPE.md)
- [Architecture Decisions](DECISIONS.md)

## Machine-readable contracts
- [Device protocol](../protocol/README.md)
- [Management API OpenAPI](management-api-v1.yaml)

Technical specifications may describe later capabilities. They do not change version scope or prove acceptance. Use ROADMAP.md and RELEASE_GATES.md as the delivery source of truth.
