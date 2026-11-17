<!-- ASSAYFLUX POCKET HOST · REPOSITORY README -->

<p align="center">
  <img src="Assets/Pocket Host Logo.png" alt="AssayFlux Pocket Host logo" width="500">
</p>
<p align="center"><strong>Your Website - Your Device - Your Control</strong></p>
<p align="center">Turn An Ordinary Android Phone Into The Origin Server For Your Website Without Root - Without Termux - Without Mandatory VPS</p>
<p align="center">
  <img src="https://readme-typing-svg.demolab.com?font=IBM+Plex+Mono&weight=600&size=35&pause=1600&color=B18B5E&center=true&vCenter=true&width=900&height=32&lines=PHONE-FIRST+WEBSITE+HOSTING;STATIC+SITES+AND+TRUSTED+APPLICATIONS;RUST+REQUEST+ENGINE;ANDROID-NATIVE+OPERATIONS;IMPORT+%E2%86%92+PUBLISH+%E2%86%92+OBSERVE+%E2%86%92+RECOVER" alt="Pocket Host product principles">
</p>

<!-- REPOSITORY STATUS -->
<p align="center">
  <img src="https://img.shields.io/github/license/AssayFlux/PocketHost?style=flat-square&label=LICENSE&labelColor=263746&color=B18B5E" alt="Apache 2.0 license">
  <img src="https://img.shields.io/github/actions/workflow/status/AssayFlux/PocketHost/quality.yml?branch=main&style=flat-square&label=QUALITY%20CI&labelColor=263746&color=6D9B83" alt="Quality workflow status">
  <img src="https://img.shields.io/github/actions/workflow/status/AssayFlux/PocketHost/android-integration.yml?branch=main&style=flat-square&label=ANDROID%20CI&labelColor=263746&color=6D9B83" alt="Android integration workflow status">
  <img src="https://img.shields.io/github/last-commit/AssayFlux/PocketHost/main?style=flat-square&label=LAST%20COMMIT&labelColor=263746&color=8B9EAD" alt="Last commit">
  <img src="https://img.shields.io/badge/RELEASE-PRE--RELEASE-B18B5E?style=flat-square&labelColor=263746" alt="Pre-release status">
</p>

<!-- TECHNOLOGY IDENTITY -->
<p align="center">
  <img src="https://img.shields.io/badge/ANDROID-API%2026%2B-3D8061?style=flat-square&logo=android&logoColor=white" alt="Android API 26 and newer">
  <img src="https://img.shields.io/badge/KOTLIN-JETPACK%20COMPOSE-7657A6?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin and Jetpack Compose">
  <img src="https://img.shields.io/badge/RUST-TOKIO%20%2B%20HYPER-B47752?style=flat-square&logo=rust&logoColor=white" alt="Rust Tokio and Hyper">
  <img src="https://img.shields.io/badge/PYTHON-CPYTHON%203.11-416B83?style=flat-square&logo=python&logoColor=white" alt="Python 3.11">
  <img src="https://img.shields.io/badge/NODE.JS-24.22.1-548B65?style=flat-square&logo=nodedotjs&logoColor=white" alt="Node.js 24.22.1">
  <img src="https://img.shields.io/badge/TYPESCRIPT-REACT%20%2B%20VITE-3178C6?style=flat-square&logo=typescript&logoColor=white" alt="TypeScript React and Vite">
</p>
<p align="center">
  <img src="https://img.shields.io/badge/ANDROID%20NDK-C%2B%2B%20%2F%20JNI-658B6C?style=flat-square&logo=android&logoColor=white" alt="Android NDK and JNI">
  <img src="https://img.shields.io/badge/NETWORKING-HTTP%20%2F%20TLS-527D75?style=flat-square" alt="HTTP and TLS networking">
  <img src="https://img.shields.io/badge/EDGE-CLOUDFLARE%20TUNNEL-F38020?style=flat-square&logo=cloudflare&logoColor=white" alt="Cloudflare Tunnel integration">
  <img src="https://img.shields.io/badge/CONTROL%20PLANE-AXUM%20%2B%20POSTGRES-416B83?style=flat-square&logo=postgresql&logoColor=white" alt="Axum and PostgreSQL">
  <img src="https://img.shields.io/badge/IDENTITY-DESIGNED%20BY%20ASSAYFLUX-800020?style=flat-square" alt="Designed by AssayFlux">
  <img
src="https://img.shields.io/badge/AUTHOR-WRITTEN%20BY%20MD%20SAFARUJJAMAN-417B85?style=flat-square" alt="Designed by AssayFlux">
</p>

---

## ANDROID APP EXPERIENCE

The native Android UI follows **Pocket Host Design Language V1** in [docs/UI_DESIGN.md](docs/UI_DESIGN.md): a secure local biometric app-unlock screen and a branded home dashboard. The dashboard has a hamburger control on the left, a separate user/account menu on the right, tall portrait-rectangular service cards in a horizontally swipeable carousel, and a fixed information centre showing four or five compact, information-rich bars at once. **The dashboard does not vertically scroll; only the service carousel scrolls horizontally; the information centre never scrolls.** Information bars use monochrome icons, meaningful metrics, small graphs and truthful live status. V1 uses a calligraphic Pocket Host wordmark, elegant display headings, readable sans-serif operational text, minimal monochrome icons, and ten coordinated classic palettes (Classic Ivory, Midnight, Forest, Burgundy, Ocean, Graphite, Sandstone, Lavender, Copper and Arctic). Users choose System, Light or Dark appearance. Login/unlock, drawer, user menu and every feature destination share the same design tokens while retaining layouts suited to their tasks. Responsive and accessibility acceptance remains required.

The drawer routes into the existing Hosting/Dashboard, Websites/Sites, Relay & Networking settings, Python/Node runtime settings, Security/Access controls, Capacity, Backups, Activity/Logs, Learn and general Settings. The native app currently uses optional biometric app lock; native username/password account login is not claimed. Management-console authentication is a separate feature. See [docs/UI_DESIGN.md](docs/UI_DESIGN.md) for implementation truth and acceptance requirements.

## ABOUT THE PROJECT

**AssayFlux Pocket Host** is a phone-first website hosting system. An Android device stores and serves the website; its native Rust engine handles HTTP ingress, hostname routing, static delivery and reverse-proxy traffic. Optional connectivity services make a locally hosted site reachable beyond the phone's network.

The product is designed for people who want to publish a website from hardware they already own, while retaining understandable controls over hosting, networking, resource use and recovery.

<table>
  <tr>
    <td align="center" width="33%">
      <img src="https://api.iconify.design/lucide/smartphone.svg?color=%23B18B5E" width="30" height="30" alt="Phone"><br>
      <strong>PHONE AS ORIGIN</strong><br><sub>The website runs from the Android device, not a required VPS.</sub>
    </td>
    <td align="center" width="33%">
      <img src="https://api.iconify.design/lucide/network.svg?color=%23B18B5E" width="30" height="30" alt="Network"><br>
      <strong>ONE REQUEST ENGINE</strong><br><sub>Rust owns website ingress, routing and application proxying.</sub>
    </td>
    <td align="center" width="33%">
      <img src="https://api.iconify.design/lucide/shield-check.svg?color=%23B18B5E" width="30" height="30" alt="Security"><br>
      <strong>EVIDENCE BEFORE CLAIMS</strong><br><sub>Source, build, test, device and production status are distinct.</sub>
    </td>
  </tr>
</table>

### Product identity

| Property | Value |
|---|---|
| Product | AssayFlux Pocket Host |
| Developer identity | Designed by AssayFlux |
| Steward | AssayFlux Scientific Systems |
| Android host package | <code>com.assayflux.pockethost</code> |
| Runtime companion package | <code>com.assayflux.pockethost.worker</code> |
| Minimum Android target | Android 8.0 · API 26 |
| Device architecture targets | ARMv7 and ARM64; x86_64 for emulator coverage |
| Operating model | Native Android · No Root · No Termux |
| Release position | Pre-release engineering · not production approved |

## PRODUCT CAPABILITIES

<table>
  <tr>
    <td align="center" width="50%">
      <img src="https://api.iconify.design/lucide/globe.svg?color=%23B18B5E" width="28" height="28" alt="Website"><br>
      <strong>STATIC WEBSITE HOSTING</strong><br><sub>Import a folder or ZIP, validate the site, preview it and publish from the phone.</sub>
    </td>
    <td align="center" width="50%">
      <img src="https://api.iconify.design/lucide/server.svg?color=%23B18B5E" width="28" height="28" alt="Server"><br>
      <strong>RUST HTTP ENGINE</strong><br><sub>Hostname routing, static files, byte ranges, SPA fallback and loopback reverse proxy.</sub>
    </td>
  </tr>
  <tr>
    <td align="center">
      <img src="https://api.iconify.design/lucide/code-2.svg?color=%23B18B5E" width="28" height="28" alt="Python"><br>
      <strong>PYTHON WSGI</strong><br><sub>Companion-hosted CPython 3.11 for owner-trusted Python applications.</sub>
    </td>
    <td align="center">
      <img src="https://api.iconify.design/lucide/terminal.svg?color=%23B18B5E" width="28" height="28" alt="Node"><br>
      <strong>NODE.JS APPLICATIONS</strong><br><sub>Companion-hosted Node.js mobile runtime with prebundled dependencies.</sub>
    </td>
  </tr>
  <tr>
    <td align="center">
      <img src="https://api.iconify.design/lucide/cloud.svg?color=%23B18B5E" width="28" height="28" alt="Tunnel"><br>
      <strong>PUBLIC CONNECTIVITY</strong><br><sub>Reuse an existing Cloudflare Tunnel; a bounded HTTP AssayFlux Relay is partially implemented in source (not deployed or production-approved).</sub>
    </td>
    <td align="center">
      <img src="https://api.iconify.design/lucide/activity.svg?color=%23B18B5E" width="28" height="28" alt="Operations"><br>
      <strong>OPERATIONS AND RECOVERY</strong><br><sub>Hosting status, activity, resource telemetry, encrypted backup foundations and recovery work.</sub>
    </td>
  </tr>
</table>

**Two operating modes**

| Mode | Designed workflow |
|---|---|
| **Simple** | Import static site → validate → preview → publish → monitor |
| **Advanced** | Manage multiple sites → configure hostnames → operate trusted runtimes → inspect capacity and diagnostics |

Static hosting is the foundational service. Python, Node, public tunnels and remote management are optional layers. Pocket Host targets modest workloads on ordinary Android devices; it is not an unlimited cloud-server replacement or uptime guarantee.

## TECHNOLOGY ECOSYSTEM

<p align="center">
  <img src="https://skillicons.dev/icons?i=androidstudio,kotlin,gradle,rust,cpp,python,nodejs,ts,react,vite,postgres,cloudflare,linux,git&perline=7" alt="Android, Kotlin, Gradle, Rust, C++, Python, Node.js, TypeScript, React, Vite, PostgreSQL, Cloudflare, Linux and Git technology icons">
</p>

| Layer | Technology | Responsibility |
|---|---|---|
| Android application | Kotlin · Jetpack Compose · AndroidX | UI, lifecycle, storage access, permissions and device integration |
| Website engine | Rust · Tokio · Hyper | HTTP listener, hostname/site routing, static delivery, reverse proxy and admission |
| Native integration | C++ · Android NDK · JNI | Native bridge and runtime integration |
| Python runtime | CPython 3.11 · Chaquopy | WSGI application execution in the companion package |
| Node runtime | Node.js mobile 24.22.1 | Embedded Node application execution in the companion package |
| Management console | TypeScript · React · Vite | Browser-based owner/operator console |
| Optional control plane | Rust · Axum · SQLx · PostgreSQL | Remote management, device registration and leased commands |
| Public connector | cloudflared | Optional outbound Cloudflare Tunnel connection |
| Secret protection | Android Keystore · AES-GCM | Device-bound secret protection and encrypted backup primitives |

Technology badges identify components present in the repository. They are not a claim that every subsystem has passed production acceptance.

## SYSTEM ARCHITECTURE

~~~mermaid
flowchart TB
    VISITOR["Website visitor"] --> EDGE["Public edge<br/>Cloudflare today · AssayFlux Relay planned"]
    EDGE -->|"forwarded website traffic"| RUST["Rust · Tokio · Hyper<br/>Android website listener"]
    RUST --> ROUTER["Hostname and site router"]
    ROUTER --> STATIC["Static site files"]
    ROUTER --> PY["Python WSGI<br/>trusted code"]
    ROUTER --> NODE["Node.js<br/>trusted code"]

    OWNER["Owner / Operator"] --> ANDROID["Kotlin · Jetpack Compose"]
    ANDROID --> LIFE["Android lifecycle<br/>foreground hosting service"]
    ANDROID --> MGMT["Authenticated loopback<br/>management API"]
    MGMT <--> CONSOLE["TypeScript · React<br/>management console"]

    ANDROID <-->|"signature-protected Binder"| BROKER["Runtime companion broker"]
    BROKER --> WORKERS["Python and Node workers<br/>shared companion UID today"]

    ANDROID -. optional heartbeat .-> CP["Optional Axum / PostgreSQL<br/>remote control plane"]
    RELAY["AssayFlux Relay<br/>planned gateway and device agent"] -. future path .-> EDGE
~~~

### Responsibility boundaries

- **Kotlin** owns Android lifecycle, permissions, storage access, foreground operation and device telemetry.
- **Rust** is the website request engine and the intended single public ingress path.
- **The runtime companion** hosts Python and Node separately from the main host package and communicates through protected Binder IPC.
- **The TypeScript console** is a management client; it is not a second website request router.
- **The optional control plane** manages devices and remote operations; it is not the website origin.
- **Public hostnames must never target the management API.**

## HOW A WEBSITE REQUEST IS SERVED

1. A visitor requests a configured hostname.
2. The selected connector forwards website traffic to the phone's Rust listener.
3. Rust validates the hostname and resolves the registered site.
4. Static content is served directly, or the request is proxied to the configured loopback application runtime.
5. The response returns through the same network path.

For local hosting, the connector is not required. The phone remains the origin in every mode.

## NETWORKING OPTIONS

| Option | What it does | Current position |
|---|---|---|
| **Local network** | Makes a site available to devices on the owner's trusted LAN | Local hosting path; device and network acceptance required |
| **Cloudflare Tunnel** | Uses the owner's existing outbound tunnel and domain | Integration exists; verify the named tunnel and external HTTPS |
| **AssayFlux Relay** | First-party outbound tunnel and public visitor gateway | **Planned — not implemented** |
| **Direct public ingress** | Owner-managed DNS, firewall and port forwarding | Advanced future option; not the default |

### AssayFlux Relay — first-party connectivity direction

The phone will initiate an authenticated outbound connection to an AssayFlux-operated relay. Visitors will connect to the relay hostname, and the relay will forward bounded visitor streams through the device tunnel to the Rust listener. This is intended to work behind common NAT/CGNAT environments where outbound connectivity is permitted.

The relay should hide the phone's public IP from ordinary visitors, **not from the relay operator, carrier or network provider**. Standard TLS termination allows the gateway to inspect HTTP traffic and must be disclosed. End-to-end TLS pass-through is a later advanced mode. Device enrollment, mutual authentication, revocation, hostname authorization, stream limits, abuse controls and operations must be implemented before this can be offered.

## IMPLEMENTATION STATUS AND ACCEPTANCE

The following table separates source foundations from evidence still required. A source file or successful compile is not proof of production behavior.

| Engineering area | Current source position | Remaining acceptance |
|---|---|---|
| Android host | Kotlin/Compose application and foreground hosting architecture | Exact-head API 26/33/35 and physical-device lifecycle verification |
| Rust engine | Tokio/Hyper listener, static delivery, routing, proxy and bounded admission foundations | Fairness, fault injection, sustained-load and thermal evidence |
| Python | CPython 3.11 / WSGI moved to companion package | Real Rust-to-WSGI routing and per-project isolation |
| Node.js | Node 24.22.1 mobile runtime moved to companion package | Real Rust-to-Node routing, ABI coverage and per-project isolation |
| Companion IPC | Signature permission, caller validation, protocol versioning and bounded project transfer | API-level lifecycle and adversarial IPC acceptance |
| Runtime isolation | Separate host/companion UID and isolation proof work | Distinct OS identity per project, file confinement, egress denial and independent review |
| Web console | TypeScript/React/Vite management UI | Responsive, accessibility and end-to-end control acceptance |
| Cloudflare | Android cloudflared integration | Validate the owner's existing tunnel and public HTTPS path |
| AssayFlux Relay | Architecture and requirements documented | Gateway, Android agent, enrollment, transport, visitor policy and operations |
| Capacity | Telemetry and initial admission/health foundations | Full governor, workload profiles, percentile measurements and long-duration tests |
| Backup | Encrypted backup and staged-restore foundations | Corruption, interruption, low-storage and rollback fault matrix |
| Control plane | Axum/PostgreSQL service source and deployment workflow | Actual deployment, device pairing and remote-command recovery evidence |
| Release | CI packaging workflow | Signed host/companion release, checksums, upgrade validation and independent review |

### Security boundary — read before enabling application runtimes

<blockquote>
<strong>Python and Node are currently for owner-trusted code only.</strong> The host and runtime companion use separate Android package identities, but projects inside the companion still share its UID. Outbound network egress is not yet enforced as a per-project operating-system boundary. The current trust acknowledgement is not a sandbox. Do not execute hostile, visitor-submitted or untrusted third-party code.
</blockquote>

Gate 2 in the North Star defines the required per-project isolated execution cells, project-only file capabilities, brokered visitor streams, fail-closed network policy and adversarial validation.

## QUICK START · PUBLISH A STATIC SITE

### 1. Prepare your website

Build your site with its normal development toolchain. Pocket Host serves the generated production output; it does not run your desktop build process.

| Website toolchain | Common output |
|---|---|
| Vite | <code>dist/</code> |
| Static Next.js export | <code>out/</code> |
| Plain HTML/CSS/JS | Project folder containing <code>index.html</code> |

Confirm that the selected folder contains a root <code>index.html</code>.

### 2. Import and validate

1. Open Pocket Host and go to **Sites**.
2. Import a website folder or ZIP archive.
3. Review validation results for paths, archive size and the entry page.
4. Preview the site locally before publishing.

### 3. Configure and publish

1. Assign the hostname that should resolve to the site.
2. Choose local network or a configured public connector.
3. If using Cloudflare, reuse the existing named tunnel.
4. Ensure the Cloudflare Public Hostname targets the Rust website listener — never the management API.
5. Start hosting and verify the hostname from a separate network.

### 4. Protect the deployment

Create an encrypted backup, review activity and logs, and preserve the last known-good website when replacing site files. A connector showing as running is not proof that the public hostname is reachable.

## PYTHON AND NODE APPLICATIONS

These are advanced runtime paths and remain restricted to owner-trusted code.

### Python WSGI

1. Prepare a project with a <code>wsgi.py</code> entry point.
2. Export a callable named <code>application(environ, start_response)</code>.
3. Bundle supported dependencies before importing the project; automatic on-device package installation is not assumed.
4. Import the project using the Python site workflow.
5. Start the companion worker and verify that Rust proxies the configured hostname to its loopback listener.
6. Inspect logs and lifecycle behavior.

### Node.js

1. Prepare a self-contained Node project with <code>package.json</code> and a resolvable server entry point.
2. Include prebundled dependencies; arbitrary on-device package installation and process managers are not part of the supported workflow.
3. Import the project using the Node site workflow.
4. Start the companion worker and verify Rust-to-Node loopback routing.
5. Inspect logs, memory behavior, shutdown and restart.

**Important:** Do not deploy untrusted code until Gate 2 is implemented and independently verified.

## CAPACITY, DEVICE HEALTH AND RECOVERY

A phone's sustainable hosting capacity depends on workload, response size, route mix, caching, memory pressure, thermal state, battery policy, network quality and tunnel overhead. There is no universal visitor count.

Pocket Host's capacity work distinguishes:

- **Observed throughput:** what a named test actually measured.
- **Sustainable operating budget:** the load that remains within latency, memory and thermal limits.
- **Configured admission budget:** the limit currently applied by the engine after health derating.

The initial engine has bounded admission and device-health foundations. Full per-site fairness, representative Python/Node profiles, p50/p95/p99 latency, long-duration load, energy and recovery evidence remain open. Android OEM power management can stop background work; the application cannot promise to override it.

## BACKUP AND OPERATIONS

The encrypted <code>.afbackup</code> format is intended to protect supported website data and selected non-secret settings. Device-bound credentials, account sessions, Cloudflare tokens and Android Keystore keys are excluded and must be configured again when moving to another device. Treat Python and Node project backups as executable source and protect them accordingly.

Production acceptance must include corrupted archives, wrong passphrases, interrupted restore, low-storage conditions, failed activation, rollback and secret-exclusion tests. Logs must be bounded and redact tokens, passwords, session values and sensitive headers.

## BUILD FROM SOURCE

### Prerequisites

- Android Studio or a compatible Gradle environment.
- Android SDK and NDK versions declared by the project.
- The JDK version required by the configured Gradle and Android Gradle Plugin.
- Rust toolchain and Android compilation targets required by the native build.
- Network access for pinned dependencies and native runtime archives.

Use the repository's Gradle configuration and CI workflows as the source of truth for toolchain versions. Do not substitute arbitrary SDK, NDK, JDK or Rust versions.

### Clone and inspect

~~~bash
git clone https://github.com/AssayFlux/PocketHost.git
cd PocketHost
git checkout main
./gradlew tasks
~~~

### Build the Android application

~~~bash
./gradlew assembleDebug
~~~

The project includes a main host application and a separate runtime companion Android module. Inspect Gradle task output and generated APK metadata to identify both package artifacts and their ABI coverage. A local compile does not prove runtime routing, Android lifecycle behavior or device compatibility.

## CI, TESTING AND RELEASE EVIDENCE

The repository has separate quality and Android integration workflows.

| Workflow | Responsibility |
|---|---|
| <code>.github/workflows/quality.yml</code> | Console, Rust, Python runtime, control-plane, Android source and Node runtime checks |
| <code>.github/workflows/android-integration.yml</code> | Native dependency preparation, Android packaging and emulator integration coverage |
| <code>.github/workflows/deploy-control-plane.yml</code> | Optional control-plane deployment workflow |
| <code>.github/workflows/release.yml</code> | Release packaging and release checks |

A release record must identify the exact commit SHA, workflow run, host and companion APKs, ABI, package IDs, API levels, test counts, failures, skipped tests, signing certificate and SHA-256 checksums. Public connectivity and physical-device verification must be recorded separately.

Never treat a queued workflow as a pass, compilation as runtime proof, an emulator as a physical phone, or source presence as feature completion.

## PRIMARY IMPLEMENTATION JOURNEY

The primary version follows the ordered North Star gates. Later gates depend on earlier runtime and security foundations.

| Sequence | Gate | Required outcome |
|:---:|---|---|
| 0 | Repository truth and architecture freeze | One evidence-based baseline; consistent docs, workflows and contracts |
| 1 | Runtime migration | Python and Node route end-to-end through Rust; static hosting survives runtime failure |
| 2 | Per-project security isolation | Separate project identities, confined files, enforced egress policy and adversarial review |
| 3 | AssayFlux Relay | Authenticated device tunnel, public gateway, enrollment, visitor policy and operations |
| 4 | Owner/Operator and visitor identity | Separate management and visitor authentication, roles, revocation and audit |
| 5 | Rust governor and Capacity Lab | Fair admission, health-aware limits and measured operating envelopes |
| 6 | Backup, operations and lifecycle recovery | Verified restore, rollback, monitoring and process-death recovery |
| 7 | Product UX and onboarding | Simple/Advanced workflows, Learn guidance and accessible operations |
| 8 | Release acceptance | Exact-SHA API 26/33/35, signed artifacts, external connectivity, physical devices and independent review |

Read <code>docs/NORTH_STAR.md</code> for the complete acceptance criteria and dependencies.

## REPOSITORY MAP

| Directory | Responsibility |
|---|---|
| <code>app/</code> | Main Android host app, UI, lifecycle, management and Rust integration |
| <code>runtime-worker/</code> | Companion Android package, CPython/Node integration and isolation work |
| <code>rust-engine/</code> | Tokio/Hyper website engine and JNI-facing native core |
| <code>web-console/</code> | TypeScript/React/Vite management console |
| <code>control-plane/</code> | Optional Axum/PostgreSQL remote-management service |
| <code>scripts/</code> | Native dependency and build helpers |
| <code>.github/workflows/</code> | Quality, Android integration, deployment and release workflows |
| <code>docs/</code> | North Star, architecture, security, networking, operations and release specifications |
| <code>examples/</code> | Starter website and runtime projects |
| <code>third_party/</code> | Pinned third-party source and notices |

## DOCUMENTATION AND ENGINEERING GUIDE

Read the project in this order:

1. <code>docs/NORTH_STAR.md</code> — product principles, network options, authentication and implementation sequence.
2. <code>docs/PROJECT_BASELINE.md</code> — canonical source truth and evidence rules.
3. <code>docs/ROADMAP.md</code> — ordered gates, tasks and exit criteria.
4. <code>docs/MASTER_PLAN.md</code> — system architecture and technical contracts.
5. <code>docs/DEVELOPMENT_CONTEXT.md</code> — mandatory engineering constraints and contributor handoff.
6. <code>docs/DECISIONS.md</code> — accepted architecture decisions.
7. <code>docs/INDEX.md</code> — complete documentation catalogue.
8. <code>docs/RUNTIME_COMPANION.md</code>, <code>docs/PYTHON_RUNTIME.md</code> and <code>docs/NODE_RUNTIME.md</code> — runtime integration.
9. <code>docs/SECURITY_GATE_2_EXECUTION_PLAN.md</code> and <code>docs/SECURITY_ISOLATION_DESIGN.md</code> — isolation and adversarial acceptance.
10. <code>docs/CLOUDFLARE_TUNNEL.md</code> and <code>docs/ASSAYFLUX_RELAY.md</code> — current and planned public connectivity.
11. <code>docs/ADAPTIVE_CAPACITY_SECURITY_OPERATIONS.md</code> and <code>docs/SITE_BACKUP.md</code> — capacity and recovery.
12. <code>docs/RELEASE_SIGNING.md</code> and <code>docs/COMPLETION_MILESTONE.md</code> — signed release and production definition of done.

## CONTRIBUTION STANDARD

1. Read the North Star, baseline and active gate before changing architecture.
2. Preserve the single Rust website request path and Android lifecycle ownership.
3. Keep static hosting independent from optional runtimes and remote services.
4. Add tests, contracts, security controls and operational evidence with each implementation.
5. Update affected documentation in the same change set.
6. Report exact checks run, checks not run, limitations and remaining acceptance gates.
7. Never claim a security boundary, visitor capacity, device compatibility or production readiness without corresponding evidence.

## LICENSE AND THIRD-PARTY NOTICES

The repository is distributed under the Apache License 2.0. Third-party runtimes, native libraries, build tools and assets may have separate licenses and redistribution conditions. Review the relevant upstream notices and repository third-party inventory before bundling or redistributing components.

---
<p align="center"><strong>Designed by AssayFlux</strong></p>
<p align="center"><sub>Build Deliberately - Host Responsibly - Verify Everything</sub></p>
