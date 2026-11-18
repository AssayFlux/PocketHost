<!-- POCKETHOST · ASSAYFLUX -->
<p align="center">
  <img src="Assets/Pocket Host 7.svg" alt="AssayFlux Pocket Host" width="100%">
</p>
<p align="center"><strong>YOUR DEVICES - YOUR SERVICES - YOUR CONTROL</strong></p>
<p align="center">Build Personal Infrastructure From The Devices You Own</p>
<p align="center">
  <img src="https://readme-typing-svg.demolab.com?font=Heavy+Data+Decorative&weight=700&size=30&pause=1600&color=EFBF04&center=true&vCenter=true&width=900&lines=PERSONAL+INFRASTRUCTURE%2C+BUILT+FROM+YOUR+DEVICES;ONE+MOBILE+SERVER%2C+AN+INTERCONNECTED+DEVICE+FABRIC;VERIFY+EVERY+DEVICE;ASSIGN+BOUNDED+ROLES;SERVE+YOUR+WEBSITES;LOCAL+FIRST%2C+REMOTE+WHEN+YOU+CHOOSE;ENGINEERED+FOR+CONTROL%2C+RESILIENCE+AND+EVIDENCE" alt="Pocket Host principles">
</p>

<p align="center">
  <a href="https://github.com/AssayFlux/PocketHost/blob/Alpha/LICENSE"><img src="https://img.shields.io/github/license/AssayFlux/PocketHost?style=flat-square&label=LICENSE&labelColor=302A35&color=9B6A7A"></a>
  <a href="https://github.com/AssayFlux/PocketHost/actions/workflows/quality.yml"><img src="https://img.shields.io/github/actions/workflow/status/AssayFlux/PocketHost/quality.yml?branch=Alpha&style=flat-square&label=QUALITY%20CI&labelColor=263B38&color=739B83"></a>
  <a href="https://github.com/AssayFlux/PocketHost/stargazers"><img src="https://img.shields.io/github/stars/AssayFlux/PocketHost?style=flat-square&label=STARS&labelColor=45372D&color=C69B58"></a>
  <a href="https://github.com/AssayFlux/PocketHost/forks"><img src="https://img.shields.io/github/forks/AssayFlux/PocketHost?style=flat-square&label=FORKS&labelColor=263C50&color=7399B5"></a>
  <a href="https://github.com/AssayFlux/PocketHost/issues"><img src="https://img.shields.io/github/issues/AssayFlux/PocketHost?style=flat-square&label=ISSUES&labelColor=49344B&color=A98AB8"></a>
  <a href="https://github.com/AssayFlux/PocketHost/commits/Alpha"><img src="https://img.shields.io/github/last-commit/AssayFlux/PocketHost/Alpha?style=flat-square&label=LAST%20COMMIT&labelColor=493B32&color=B88C72"></a>
  <img src="https://img.shields.io/badge/DEFAULT%20BRANCH-Alpha-78649A?style=flat-square">
  <img src="https://img.shields.io/badge/RELEASE-PRE--RELEASE-B18B5E?style=flat-square">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-Kotlin-578A69?style=flat-square&logo=android&logoColor=white">
  <img src="https://img.shields.io/badge/Jetpack_Compose-UI-6E75A6?style=flat-square&logo=jetpackcompose&logoColor=white">
  <img src="https://img.shields.io/badge/Rust-Website_Engine-B47752?style=flat-square&logo=rust&logoColor=white">
  <img src="https://img.shields.io/badge/Python-3.11_Runtime-416B83?style=flat-square&logo=python&logoColor=white">
  <img src="https://img.shields.io/badge/Node.js-24.22.1_Runtime-548B65?style=flat-square&logo=nodedotjs&logoColor=white">
  <img src="https://img.shields.io/badge/React-Operator_Console-527D9C?style=flat-square&logo=react&logoColor=white">
  <img src="https://img.shields.io/badge/TypeScript-Web_Console-647E9B?style=flat-square&logo=typescript&logoColor=white">
  <img src="https://img.shields.io/badge/Vite-Console_Build-8B789C?style=flat-square&logo=vite&logoColor=white">
</p>
<p align="center">
  <img src="https://img.shields.io/badge/Architecture-Modular-78649A?style=flat-square">
  <img src="https://img.shields.io/badge/Trust-Explicit_Enrollment-527C72?style=flat-square">
  <img src="https://img.shields.io/badge/Serving-Static_First-657F70?style=flat-square">
  <img src="https://img.shields.io/badge/Protocol-pockethost.device%2F1.0-9B7955?style=flat-square">
  <img src="https://img.shields.io/badge/License-Apache--2.0-9B6A7A?style=flat-square">
</p>

## ABOUT POCKET HOST

**AssayFlux Pocket Host** is a mobile-first personal infrastructure platform. The first release targets Android static website hosting locally, over LAN and worldwide through the owner's domain using optional Cloudflare Tunnel or an owner-operated AssayFlux Relay Node. The v0.1.0 Relay target is owner-controlled Termux/Android hardware; Linux-native installation and service packaging are deferred to v0.1.1. A paid VPS is not required, but the gateway must have public IPv4/IPv6 reachability. Later releases add an in-app development workbench, optional server runtimes and a verified device fabric.

A desktop, laptop, server, NAS or supported single-board computer may eventually contribute a bounded role such as storage, backup, traffic handling, build/publish or native jobs. Device products remain separate for their platforms and interoperate through shared contracts. Network discovery never establishes trust.

Pocket Host is in pre-release engineering. Existing source foundations are not the same as release acceptance. The official delivery path is the three-version roadmap below.

<table>
  <tr>
    <td align="center" width="50%">
      <img src="https://api.iconify.design/lucide/server.svg?color=%23800020" width="30" height="30" alt="Server">
      <br><strong>SERVER ONLY</strong><br>
      Serve websites from this mobile device. Fleet discovery, approval and role administration stay unavailable.
    </td>
    <td align="center" width="50%">
      <img src="https://api.iconify.design/lucide/network.svg?color=%23800020" width="30" height="30" alt="Command center">
      <br><strong>COMMAND CENTER + SERVER</strong><br>
      Serve websites and administer the verified device fleet from the same mobile device.
    </td>
  </tr>
</table>

**The two-mode rule is fundamental:** website serving remains enabled in both modes. There is no standalone Command Center-only mode.

## PRODUCT CAPABILITIES

<table>
  <tr>
    <td align="center" width="33%">
      <img src="https://api.iconify.design/lucide/globe.svg?color=%23800020" width="28" height="28" alt=""><br>
      <strong>FIRST HOSTING</strong><br>
      v0.1.0: static website import, local preview, same-Wi-Fi LAN and public custom-domain HTTP/HTTPS hosting from Android.
    </td>
    <td align="center" width="33%">
      <img src="https://api.iconify.design/lucide/code-2.svg?color=%23800020" width="28" height="28" alt=""><br>
      <strong>WORKBENCH</strong><br>
      v0.1.1: isolated editing, preview, diffs, commits, TypeScript build and safe publishing.
    </td>
    <td align="center" width="33%">
      <img src="https://api.iconify.design/lucide/fingerprint.svg?color=%23800020" width="28" height="28" alt=""><br>
      <strong>DEVICE TRUST</strong><br>
      v0.1.2: explicit enrollment, signed grants, revocation and bounded worker roles.
    </td>
  </tr>
  <tr>
    <td align="center">
      <img src="https://api.iconify.design/lucide/blocks.svg?color=%23800020" width="28" height="28" alt=""><br>
      <strong>OPTIONAL RUNTIMES</strong><br>
      Existing Python 3.11 and Node.js 24.22.1 companion integrations; complete acceptance in v0.1.1.
    </td>
    <td align="center">
      <img src="https://api.iconify.design/lucide/route.svg?color=%23800020" width="28" height="28" alt=""><br>
      <strong>VISITOR CONNECTIVITY</strong><br>
      Local Network, Cloudflare Tunnel and the planned standalone AssayFlux Relay Node are separate ingress paths; public HTTP/HTTPS acceptance is a v0.1.0 target. The Android client has independent host credentials, but the standalone Relay Node is not yet implemented. Termux/Android is the v0.1.0 install target; Linux-native installation moves to v0.1.1.
    </td>
    <td align="center">
      <img src="https://api.iconify.design/lucide/shield-check.svg?color=%23800020" width="28" height="28" alt=""><br>
      <strong>CONTROL &amp; EVIDENCE</strong><br>
      Mandatory G0–G9 release gates, versioned contracts, least privilege and reproducible verification.
    </td>
  </tr>
</table>

## THE NEW THREE-VERSION PATHWAY

| Version | Milestone | Release outcome |
|---|---|---|
| **v0.1.0** | First Hosting | Import and host a real static website from Android, locally, over LAN and publicly through the owner's domain |
| **v0.1.1** | Workbench, Runtime & Linux Relay Installation | Edit, preview, version and safely publish websites; complete optional Python/Node integration and add Linux-native Relay installation |
| **v0.1.2** | Connected Device Fabric | Enroll separate devices, assign bounded roles, coordinate workers and scale multi-host Relay/failover |

This is the initial roadmap, not the final product boundary. It will be expanded after these three milestones. The old five-step delivery sequence is retired as the release roadmap.

## v0.1.0 — FIRST HOSTING

The first release has one primary promise: **a website imported into Pocket Host can be served correctly by the Android phone locally, over LAN and publicly through the owner's domain.**

- Main Android application with the bundled Rust static website engine.
- Website import from a folder or ZIP.
- **500 MB maximum ZIP archive and 500 MB maximum extracted website contents** (500,000,000 bytes for each limit).
- Up to 5,000 files, 10,000 total entries and 32 directory levels.
- HTML, CSS, browser JavaScript and static assets.
- Local phone preview, configurable HTTP port and optional LAN binding.
- A second device on the same Wi-Fi can open the website.
- Public custom-domain hosting through Cloudflare Tunnel or the owner-operated AssayFlux Relay Node.
- Public HTTP and HTTPS with valid edge TLS; HTTP redirects to HTTPS by default. Plain HTTP content serving is a target opt-in; the current Relay edge does not implement that opt-in.
- The Relay Node does not require a paid VPS, but it must run on an owner-controlled device with public IPv4/port forwarding or globally routed IPv6. CGNAT without global IPv6 cannot accept inbound visitors.
- External DNS/TLS/content verification and strict separation between visitor and management routes.
- Foreground hosting service, notification, start/stop/restart, recovery and Android lifecycle checks.
- Encrypted full-app backup/restore and useful hosting diagnostics (static website backups support the full 500,000,000-byte import allowance; optional Python/Node projects have separate limits).
- APK build, installation and physical Android acceptance.

The static website path does not require Python, Node.js or a companion runtime. A React/Vite website can be hosted from its already-built static output (normally the contents of <code>dist/</code>); the phone does not compile a raw React/TypeScript project in v0.1.0.

The Rust engine has a separate **500,000,000-byte (500 MB decimal) per-file response cap**. This is independent of the 500,000,000-byte ZIP and expanded-site import caps. Manual single-file import into the live site is separately capped at 50 MiB, and the text editor is capped at 256 KiB. The current full-app backup implementation supports a **500,000,000-byte static-site tree**, a **650,000,000-byte total uncompressed archive**, and a **680,000,000-byte encrypted archive**. Optional Python and Node project roots remain separately capped at 50 MiB and 100 MiB. The static-site backup limit therefore matches the importer’s 500,000,000-byte expanded-content allowance; real-device storage-headroom and large-backup tests remain required.

## v0.1.1 — WORKBENCH, RUNTIME & LINUX RELAY INSTALLATION

- Complete the GitHub-inspired Workbench: project explorer, editor, tabs, file operations, changes/diffs, commits and deployment history.
- Keep private drafts separate from the live website.
- Add isolated preview and a bounded built-in TypeScript-to-JavaScript compiler.
- Stage immutable website releases, require explicit owner approval, activate atomically and support rollback.
- Complete and verify the existing Python 3.11 and Node.js 24.22.1 integrations in the separate runtime companion.
- Keep optional runtimes separate from the main app; static hosting must work without them.
- Add Linux-native Relay Node packaging, installation and service lifecycle tooling with a declared distribution/architecture support matrix.
- Add larger-project, quota, crash-recovery and cross-site isolation tests.

## WEBSITE WORKBENCH

Workbench is an initial first-class destination in the Android app. The existing foundation includes a per-site private draft workspace, nested explorer, basic text editor, draft saving, file/folder operations, change detection and local commit snapshots.

The complete development workflow is assigned to **v0.1.1**: editor tabs and unsaved-state protection, isolated preview, per-file diffs, bundled TypeScript compilation, immutable release staging, owner-approved publishing and rollback. Draft saves and commits must never modify the live website. See [Workbench specification](docs/WORKBENCH.md).

## v0.1.2 — CONNECTED DEVICE FABRIC

- Complete owner-approved device identity, enrollment, role grants and revocation.
- Connect the mobile command center to separate platform-specific worker software.
- Deliver bounded storage/backup, traffic, build/publish preparation and native-job roles.
- Harden authenticated transport, job lifecycle, audit, control plane and agent recovery.
- Scale the first-party Relay to multiple hosts/gateways, with advanced health-based failover and traffic distribution beyond the v0.1.0 single-host ingress paths.
- Prove isolation between visitor traffic, device administration, runtimes and edge controls.

## IMMUTABLE RELEASE BASELINE

After v0.1.0 passes all G0–G9 gates and receives owner approval, its exact source commit and release artifacts will be preserved with a release tag, protected baseline ref where repository permissions allow, and recorded SHA-256 digests. v0.1.1 work will branch from that baseline; the v0.1.0 baseline will not receive later feature or fix commits. See [Release Baseline Policy](docs/RELEASE_BASELINE_POLICY.md).

## MANDATORY RELEASE RULE: G0–G9

**Every version must document and pass all ten release gates, G0 through G9.** This rule applies to v0.1.0, v0.1.1, v0.1.2 and every later version. A gate is PASS only with implementation and recorded verification evidence. A document, source file, successful compile or simulator-only run is not sufficient. No release may ship with any gate open.

See [Mandatory Release Gates](docs/RELEASE_GATES.md) for the exact definitions, per-version acceptance matrix and required evidence ledger.

## TECHNOLOGY BOUNDARIES

| Technology | Repository location | Responsibility | Release placement |
|---|---|---|---|
| Kotlin + Jetpack Compose | <code>app/</code> | Main Android application | v0.1.0 |
| Rust + Tokio/Hyper | <code>rust-engine/</code> | Static website serving | v0.1.0 |
| Cloudflare Tunnel | <code>app/.../tunnel/</code> + native connector | Outbound public ingress for custom domains | v0.1.0 |
| AssayFlux Relay | <code>app/.../management/</code>, <code>control-plane/</code> (current foundation) | Standalone owner-operated Termux/Android Relay Node is the v0.1.0 target; Linux-native installation is deferred to v0.1.1; the node executable is not yet implemented | v0.1.0 |
| HTML/CSS/browser JavaScript | Main app + Rust engine | Default static website path | v0.1.0 |
| Workbench | Android app | Website development workflow | v0.1.1 |
| TypeScript compiler | Android app | Bounded browser-code build tool | v0.1.1 |
| React + TypeScript + Vite | <code>web-console/</code> | Existing Relay hostname panel is fleet-control-plane-coupled; extract a minimal independent setup flow for v0.1.0; full operator console in v0.1.2 | v0.1.0 / v0.1.2 |
| Python 3.11 | <code>runtime-worker/</code> companion APK | Optional WSGI server runtime | v0.1.1 |
| Node.js 24.22.1 | <code>runtime-worker/</code> companion APK | Optional server-side JavaScript runtime | v0.1.1 |
| Device agents and control plane | <code>agents/</code>, <code>control-plane/</code>, <code>protocol/</code> | Connected device fabric | v0.1.2 |
| Relay scale/failover | <code>control-plane/</code> | Multi-host scheduling and advanced failover | v0.1.2 |

Python and Node.js already have repository implementations in a separate companion APK. They are in progress, currently delivered together and not production-isolated. The future 20-language Runtime Library is a later expansion; catalog manifests are not downloadable runtime binaries.

## SYSTEM ARCHITECTURE

~~~mermaid
flowchart TB
    OWNER["OWNER"] --> MOBILE["ANDROID POCKET HOST"]
    MOBILE --> MODE{"OPERATING MODE"}
    MODE -->|"Server only"| SERVER["WEBSITE SERVER"]
    MODE -->|"Command Center + Server"| COMMAND["FLEET COMMAND CENTER"]
    COMMAND --> TRUST["DEVICE IDENTITY · OWNER APPROVAL · ROLE GRANTS"]
    TRUST --> AGENTS["SEPARATE DEVICE AGENTS"]
    AGENTS --> ROLES["BOUNDED STORAGE · TRAFFIC · BUILD · NATIVE JOBS"]
    SERVER --> RUST["RUST HTTP INGRESS"]
    RUST --> STATIC["HTML · CSS · JAVASCRIPT"]
    MOBILE --> WB["WORKBENCH · v0.1.1"]
    WB --> RELEASE["PREVIEW · VALIDATE · APPROVE · PUBLISH"]
    RUST --> COMPANION["OPTIONAL RUNTIME COMPANION · v0.1.1"]
    COMPANION --> PY["PYTHON 3.11"]
    COMPANION --> NODE["NODE.JS 24.22.1"]
    SERVER --> INGRESS["LOCAL NETWORK / RELAY / CLOUDFLARE"]
    COMMAND --> CONTROL["OPTIONAL CONTROL PLANE · v0.1.2"]
    CONSOLE["BROWSER OPERATOR CONSOLE"] --> CONTROL
~~~

The diagram includes future integration boundaries. It is not a claim that every connection, runtime or device role is already production-ready.

## REPOSITORY STATUS

The remediation work is tracked on `audit/remediation-baseline-2026-11`, based on Alpha commit `2087938a35dc88ed7588d97ffed8bd65fe8b40a9`. The baseline register and severity-ranked findings are in [Remediation Audit](docs/REMEDIATION_AUDIT.md); the refreshed snapshot is in [Repository Baseline](docs/REPOSITORY_BASELINE.md).

The Alpha source baseline contains 259 tracked files across 72 directories; the remediation branch snapshot contains 260 tracked files across 72 directories (including the new audit register): Android hosting, Rust static serving, website import/registry/backup, initial Workbench, Python/Node companion, Android trust foundations, Linux agent preview, Rust control plane, browser console, Cloudflare connector and a partial Relay visitor path.

**Important:** there is no standalone Termux Relay Node package or installer yet. The Android Relay agent now uses independent encrypted host credentials; the gateway implementation remains coupled to the fleet control-plane process/PostgreSQL. Linux-native installation is deferred to v0.1.1. The source baseline and complete file inventory are recorded in [Repository Baseline](docs/REPOSITORY_BASELINE.md), with the summary in [Project Baseline](docs/PROJECT_BASELINE.md) and risks in [Repository Deep Audit](docs/REPOSITORY_AUDIT.md).

## ANDROID APPLICATION

The Android app is Kotlin/Jetpack Compose with a native Rust website engine. Repository components include:
- Website hosting foreground service and recovery hooks.
- Folder/ZIP website import, site registry and preview flow.
- Website backup and interrupted-operation recovery foundations.
- Local management API, diagnostics, metrics and operational logs.
- Optional biometric app unlock, local API pairing/approval and diagnostics. The main Android app does not currently have a conventional username/password login screen; owner/passkey login is in the browser console.
- Device trust store and command-center screen foundation.
- Cloudflare Tunnel integration and AssayFlux Relay client foundation.
- Optional runtime companion client and project-transfer foundation.

The Android module declares min SDK 26, target SDK 35 and native ABI targets arm64-v8a, armeabi-v7a and x86_64. Declared targets are not a substitute for device acceptance.

## DEVICE SOFTWARE

| Device | Software | Honest status |
|---|---|---|
| Android phone/tablet | Pocket Host main app | Hosting foundation present; physical acceptance open |
| Android runtime provider | Separate companion APK | Python + Node integrations in progress |
| Termux / owner-owned Android device | AssayFlux Relay Node | Planned v0.1.0 product; standalone package/installer not yet present; public IP/IPv6 reachability required
| Owner-owned Linux device | Relay Node package and service | v0.1.1; Linux-native installer, service integration and support matrix deferred |
| Linux x86_64 | Pocket Host Linux Node Agent | Preview package |
| Windows / macOS | Native worker agent | Planned |
| Raspberry Pi / SBC | Board/OS-specific agent | Planned; no board certified |
| iOS | Mobile client | Planned; no iOS source project here |
| DemonCore / MCU | Separate firmware and board profiles | External project; adapter acceptance open |

## DEVELOPMENT AND VERIFICATION

Run commands from the repository root.

### Android main app

~~~bash
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk --version 4.1.2 --locked
bash scripts/build-cloudflared-android.sh
./gradlew :app:assembleDebug
~~~

### Optional runtime companion (v0.1.1 scope)

~~~bash
./gradlew :runtime-worker:assembleDebug
~~~

This creates a separate companion APK and is not required for v0.1.0 static hosting.

### Rust website engine

~~~bash
cargo test --manifest-path rust-engine/Cargo.toml --locked
~~~

### Python runtime and Linux agent

~~~bash
python -m pip install -e agents/linux
PYTHONPATH=agents/linux/src python -m unittest discover -s tests/python -v
~~~

### Node.js runtime smoke test

~~~bash
node tests/node/metrics-smoke.cjs
~~~

### Browser operator console

~~~bash
cd web-console
npm ci
npm run build
~~~

### Protocol contracts

~~~bash
python -m pip install jsonschema==4.23.0
python scripts/validate-protocol.py
python -m unittest discover -s tests -p 'test_protocol_contracts.py' -v
~~~

### CI and APK evidence

The [quality workflow](https://github.com/AssayFlux/PocketHost/actions/workflows/quality.yml) runs automated repository checks. The Android integration workflow has a compile-only path and a manually dispatched APK/emulator path. A compile-only job is not an APK build or device acceptance. Release claims require the exact workflow run, artifact digest and device test evidence.

## DOCUMENTATION

- [New Master Plan](docs/MASTER_PLAN.md)
- [Three-Version Roadmap](docs/ROADMAP.md)
- [Mandatory G0–G9 Release Gates](docs/RELEASE_GATES.md)
- [Complete Repository File Inventory & Baseline](docs/REPOSITORY_BASELINE.md)
- [Repository Baseline](docs/PROJECT_BASELINE.md)
- [Repository Deep Audit](docs/REPOSITORY_AUDIT.md)
- [System Architecture](docs/SYSTEM_ARCHITECTURE.md)
- [v0.1.0 Static Hosting Baseline](docs/STEP_4_WEB_BASELINE.md)
- [Website Deployment](docs/WEBSITE_DEPLOYMENT.md)
- [Public Ingress v0.1.0](docs/PUBLIC_INGRESS_V0_1_0.md)
- [Website Backup and Restore](docs/SITE_BACKUP.md)
- [Workbench Specification](docs/WORKBENCH.md)
- [Web Toolchain Boundaries](docs/WEB_TOOLCHAIN_BOUNDARIES.md)
- [Device Software Matrix](docs/DEVICE_SOFTWARE_MATRIX.md)
- [Security and Isolation](docs/SECURITY_ISOLATION_DESIGN.md)
- [Python Runtime](docs/PYTHON_RUNTIME.md)
- [Node.js Runtime](docs/NODE_RUNTIME.md)
- [Future Runtime Library](docs/FUTURE_RUNTIME_LIBRARY.md)
- [Device Trust and Protocol](docs/STEP_1_TRUST_AND_PROTOCOL.md)
- [Device Trust Implementation](docs/STEP_2_DEVICE_TRUST_IMPLEMENTATION.md)
- [Device Agents](docs/STEP_3_DEVICE_AGENTS.md)
- [Documentation Index](docs/INDEX.md)

## PROJECT IDENTITY

<p align="center">
  <img src="https://img.shields.io/badge/ASSAYFLUX-Personal_Infrastructure-800020?style=flat-square">
  <img src="https://img.shields.io/badge/PRODUCT-Pocket_Host-78649A?style=flat-square">
  <img src="https://img.shields.io/badge/DEFAULT_BRANCH-Alpha-527C72?style=flat-square">
  <img src="https://img.shields.io/badge/STATUS-Engineering_Pre--Release-B18B5E?style=flat-square">
  <img src="https://img.shields.io/badge/VALIDATION-Before_Claims-657F70?style=flat-square">
</p>

Pocket Host is licensed under the [Apache License 2.0](LICENSE).
