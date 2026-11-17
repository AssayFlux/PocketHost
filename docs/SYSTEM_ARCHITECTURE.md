# Pocket Host — System Architecture

## 1. Product definition

Pocket Host is a modular personal infrastructure platform whose first deliverable is Android static website hosting with optional public custom-domain access through Cloudflare Tunnel or the first-party AssayFlux Relay. It expands in three releases: v0.1.0 First Hosting, v0.1.1 Workbench & Runtime Development and v0.2.0 Connected Device Fabric.

## 2. Mobile operating modes

| Mode | Website serving | Fleet administration |
|---|---|---|
| Server only | On | Off |
| Command Center + Server | On | On when v0.2.0 fleet features are accepted |

No standalone Command Center-only mode exists. Website serving remains on in both modes. v0.1.0 validates hosting; the complete fleet command path is v0.2.0.

## 3. Release architecture

### v0.1.0 — First Hosting
Android Compose UI → HostingService → Rust JNI/HTTP engine → active static website directory. SiteImporter validates into private staging before activation. Local preview uses loopback; LAN is explicitly enabled. Optional public paths are independent: (1) Android cloudflared connector → Cloudflare edge/custom hostname → local Rust HTTP origin; (2) Android RelayAgent → outbound authenticated HTTPS/SSE session → owner-deployed AssayFlux Relay TLS visitor gateway → verified hostname/site route → local Rust HTTP origin. The Relay gateway uses a minimal host/site registration contract; full fleet command orchestration remains v0.2.0. Local/LAN serving never depends on either provider.

### v0.1.1 — Workbench & Runtime Development
Workbench owns a per-site private draft workspace. Editor and build tools operate on drafts. Preview is isolated. A validated immutable artifact is staged and owner-approved before atomic activation. Python and Node remain separate companion runtimes and do not become dependencies of static serving.

### v0.2.0 — Connected Device Fabric
Mobile command center ↔ authenticated control plane ↔ separately installed agents. Shared protocol defines identity, enrollment, role grants, typed jobs, leases and audit. Agents execute only approved bounded roles. Later work scales Relay across multiple hosts/gateways and adds advanced health-based failover; the single-host public visitor paths ship in v0.1.0.

## 4. Main components

| Component | Responsibility | Release |
|---|---|---|
| Android app | Owner UI, host controls, settings, site import and diagnostics | v0.1.0 |
| Rust engine | Static HTTP serving and bounded request handling | v0.1.0 |
| Site registry/import/backup | Website identity, safe staging, activation and recovery | v0.1.0 |
| Workbench workspace | Draft tree, editor, changes and local commits | v0.1.1 |
| TypeScript compiler | Browser TypeScript to JavaScript build | v0.1.1 |
| Runtime companion | Existing Python/Node integrations | v0.1.1 |
| Device trust and protocol | Identity, proof, grants and revocation | v0.2.0 |
| Agents and workers | Bounded device roles | v0.2.0 |
| Control plane | Optional fleet coordination | v0.2.0 |
| Cloudflare Tunnel | Android outbound tunnel adapter and custom-domain ingress | v0.1.0 |
| AssayFlux Relay | Owner-deployed TLS visitor gateway and authenticated host stream | v0.1.0 |
| Relay scaling | Multi-host scheduling, regional gateways and advanced failover | v0.2.0 |

## 5. Website serving and resource limits

The importer accepts up to 500,000,000 bytes for ZIP input and independently 500,000,000 bytes of expanded website content, 5,000 files, 10,000 entries and 32 directory levels. The Rust engine's current per-file response cap is 50 MiB. These limits are intentionally distinct.

The server binds to 127.0.0.1 by default. LAN mode binds to 0.0.0.0 only after explicit user configuration. Management API and device-control routes must never be exposed through visitor ingress.

## 6. Workbench and production release architecture

Draft workspace, preview and active release are separate trust/data boundaries. A local commit is not deployment. Publishing requires bounded build, validation, digest, explicit owner approval, atomic activation and rollback. This is v0.1.1.

## 7. Device roles and trust

Planned roles include storage, backup/recovery, traffic, build/publish preparation, native jobs and edge gateway. Discovery is not trust. Every device needs a unique key, proof of possession, owner approval and bounded revocable grant. Agents revalidate grant, expiry, target, capability and budget before side effects.

## 8. Connectivity providers

Local Network, AssayFlux Relay and Cloudflare Tunnel are independent visitor-ingress providers in v0.1.0. The owner chooses a provider per hostname; the same hostname cannot point at two independent providers simultaneously without a separate DNS/load-balancer failover layer. HTTPS is terminated at the public edge; HTTP redirects to HTTPS by default, with plain HTTP content serving requiring an explicit owner opt-in. Public visitor routing must terminate at website ingress only. Management API, runtime broker, device enrollment and MCU controls remain private. Multi-host routing/failover is v0.2.0.

## 9. Fault domains and autonomy

A failure in Workbench, optional runtime, tunnel, remote control plane, worker or edge gateway must not stop unrelated static serving. A remote control-plane outage cannot authorize new privileged work, but locally accepted jobs may continue within their signed lease and policy.

## 10. Compatibility and acceptance

Every component declares version, OS, architecture, ABI, protocol range, permissions, limits, health and rollback. Unknown capabilities are unsupported. Each release passes G0–G9 with evidence from the required hardware and services.
