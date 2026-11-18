# Pocket Host — System Architecture

## 1. Product definition

Pocket Host is a modular personal infrastructure platform. The first release proves Android static website hosting with local, LAN and public custom-domain access. Public ingress options are Cloudflare Tunnel (optional third party) and the planned owner-operated AssayFlux Relay Node. The Relay Node is a separate software product for owner-controlled Termux/Android or Linux hardware; it does not require a paid VPS, but it does require a publicly reachable IPv4/IPv6 network endpoint.

The three release milestones are v0.1.0 First Hosting, v0.1.1 Workbench & Runtime Development and v0.2.0 Connected Device Fabric.

## 2. Mobile operating modes

| Mode | Website serving | Fleet administration |
|---|---|---|
| Server only | On | Off |
| Command Center + Server | On | On when v0.2.0 fleet features are accepted |

No standalone Command Center-only mode exists. Website serving remains enabled in both modes.

## 3. v0.1.0 — First Hosting architecture

### Website host

Android Compose UI → HostingService → Rust JNI/HTTP engine → active static website directory.

SiteImporter validates into private staging before activation. Local preview uses loopback; LAN is explicitly enabled. Static serving must work without public ingress, runtime companion or fleet control plane.

### Cloudflare Tunnel path

Android cloudflared connector → outbound Cloudflare connection → Cloudflare edge/custom hostname → local Rust HTTP origin.

The owner configures the named tunnel, public hostname, DNS and edge HTTP/TLS policy in Cloudflare. Pocket Host stores the tunnel token securely but does not currently create DNS/hostname routes through the Cloudflare API.

### AssayFlux Relay path — target

Browser → owner's public AssayFlux Relay Node → verified hostname/site/host route → authenticated outbound host stream → Android RelayAgent → local Rust HTTP origin.

The Relay Node is separate from the Android website-host app and the full fleet control plane. v0.1.0 must provide an installable Termux/Android and Linux target, independent host-ingress credentials, domain proof, TLS, route binding, revocation and visitor-only forwarding. No paid VPS, Docker or PostgreSQL should be required for the standalone Relay profile.

**Public network constraint:** the Relay Node must have public IPv4 with router port forwarding or globally routed IPv6. A device behind CGNAT without global IPv6 cannot accept inbound visitor traffic. The website-host phone may remain behind CGNAT because its Relay connection is outbound. If no owner-controlled public endpoint is available, Cloudflare Tunnel remains an optional fallback; the project must not claim that DNS alone bypasses CGNAT.

### Current implementation versus target

Current Android RelayAgent is coupled to RemoteControlAgent endpoint/device credentials. Current gateway lives inside the Rust/Axum fleet control-plane executable, requires PostgreSQL/admin configuration, and uses an in-memory RelayHub. There is no standalone Termux Relay Node or independent host-credential flow in the audited source. The standalone path is planned work, not a current feature.

## 4. v0.1.1 — Workbench & Runtime Development

Workbench owns a per-site private draft workspace. Editor and build tools operate on drafts. Preview must be isolated. A validated immutable artifact is staged and owner-approved before atomic activation. Python and Node remain optional separate companion runtimes and do not become dependencies of static serving.

## 5. v0.2.0 — Connected Device Fabric

Mobile command center ↔ authenticated control plane ↔ separately installed agents. Shared protocol defines identity, enrollment, role grants, typed jobs, leases and audit. Agents execute only approved bounded roles. Later work scales Relay across multiple hosts/gateways and adds advanced health-based failover.

## 6. Main components and release placement

| Component | Current source | Release placement |
|---|---|---|
| Android app | Kotlin/Compose UI, hosting service, site/file UI, settings, local API, Cloudflare controls, Workbench foundation | v0.1.0 host; v0.1.1 Workbench completion |
| Rust website engine | Tokio/Hyper static HTTP JNI library, host routes, access modes, bounded connections | v0.1.0 |
| Site import/backup | Folder/ZIP staging, safe activation, encrypted backup | v0.1.0; source quotas align for the static-site root; near-limit physical-device acceptance remains open |
| Cloudflare Tunnel | Android token store, process runner, readiness, build helper | v0.1.0 public acceptance |
| AssayFlux Relay client | Android outbound SSE request/response client with dedicated RelayCredentialStore and independent HostingService lifecycle | Android-side separation implemented; node-side credential issuer remains open |
| AssayFlux Relay gateway | Visitor handler, DNS TXT proof, Caddy TLS, PostgreSQL route registry in combined control-plane process | Extract standalone Termux/Linux Relay Node in v0.1.0 |
| Workbench | Draft workspace, explorer, basic editor, local commits | v0.1.1 completion |
| TypeScript compiler | Not present as a verified main-app compiler | v0.1.1 |
| Runtime companion | CPython 3.11 and Node.js 24.22.1 in one companion APK | v0.1.1 acceptance |
| Device trust/protocol | Protocol 1.0 schemas, P-256 trust foundations | v0.2.0 acceptance |
| Linux agent | Python preview agent with SQLite and HTTPS polling | v0.2.0 acceptance |
| Fleet control plane | Rust/Axum, PostgreSQL, admin/device API and current Relay code | v0.2.0 fleet acceptance |

## 7. Website serving and resource limits

The importer accepts up to 500,000,000 bytes for ZIP input and independently 500,000,000 bytes of expanded website content, 5,000 files, 10,000 entries and 32 directory levels. The Rust engine's current per-file response cap is 50 MiB. These limits are intentionally distinct.

The server binds to 127.0.0.1 by default. LAN mode binds to 0.0.0.0 only after explicit user configuration. Management API and device-control routes must never be exposed through visitor ingress.

## 8. Connectivity and HTTP policy

The target public behavior is valid HTTPS for each verified hostname and HTTP-to-HTTPS redirect by default. Plain HTTP content serving is an explicit insecure opt-in. Current Relay Caddy configuration redirects HTTP to HTTPS and does not implement the opt-in. Cloudflare's HTTP redirect policy is configured by the owner in Cloudflare; Pocket Host does not currently manage it.

The same hostname is assigned to one provider at a time. Separate hostnames may use different providers. Enabling both providers does not automatically provide same-host failover.

Public visitor routing terminates at website ingress only. Management API, runtime broker, device enrollment, Workbench drafts and MCU controls remain private.

## 9. Fault domains and autonomy

A failure in Workbench, optional runtime, tunnel, remote control plane, worker or Relay Node must not stop unrelated local static serving. Cloudflare and Relay failures must be isolated from each other. A Relay Node restart must recover durable hostname bindings and allow host agents to reconnect without losing accepted site configuration.

## 10. Compatibility and acceptance

Every component declares version, OS, architecture, ABI, protocol range, permissions, limits, health and rollback. Unknown capabilities are unsupported. Each release passes G0–G9 with evidence from the required hardware, public network and services.
