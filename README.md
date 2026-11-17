<!-- POCKETHOST · ASSAYFLUX -->
<p align="center">
  <img src="Assets/Pocket Host Logo.png" alt="AssayFlux Pocket Host" width="100%">
</p>
<p align="center"><strong>YOUR DEVICES. YOUR SERVICES. YOUR CONTROL.</strong></p>
<p align="center">Build personal infrastructure from the devices you own.</p>
<p align="center">
  <img src="https://readme-typing-svg.demolab.com?font=Cinzel+Decorative&weight=600&size=18&pause=1600&color=B18B5E&center=true&vCenter=true&width=900&lines=PERSONAL+INFRASTRUCTURE%2C+BUILT+FROM+YOUR+DEVICES;ONE+MOBILE+SERVER%2C+AN+INTERCONNECTED+DEVICE+FABRIC;VERIFY+EVERY+DEVICE;ASSIGN+BOUNDED+ROLES;SERVE+YOUR+WEBSITES;LOCAL+FIRST%2C+REMOTE+WHEN+YOU+CHOOSE;ENGINEERED+FOR+CONTROL%2C+RESILIENCE+AND+EVIDENCE" alt="Pocket Host principles">
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

**AssayFlux Pocket Host** is a mobile-first personal infrastructure project. It is designed to let an owner use a phone as a website server and, when desired, as the command center for a verified network of other devices.

A desktop, laptop, server, NAS or supported single-board computer can eventually contribute a bounded role such as storage, backup, traffic handling, build/publish or native jobs. Devices are separate products for their respective platforms and connect through shared versioned contracts. A device is never trusted merely because it appears on the network.

Pocket Host is a pre-release engineering project. The repository contains substantial working foundations, but the complete device fabric, platform matrix and release acceptance are not finished.

<table>
  <tr>
    <td align="center" width="50%">
      <img src="https://api.iconify.design/lucide/server.svg?color=%23800020" width="30" height="30" alt="Server">
      <br><strong>SERVER ONLY</strong><br>
      Serve websites from the mobile device. Fleet discovery, approval and role administration stay unavailable.
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
      <strong>WEBSITE HOSTING</strong><br>
      HTML, CSS and browser JavaScript static serving through the Rust engine. Built-in TypeScript compilation is a Step 4 requirement and is not yet bundled/verified.
    </td>
    <td align="center" width="33%">
      <img src="https://api.iconify.design/lucide/fingerprint.svg?color=%23800020" width="28" height="28" alt=""><br>
      <strong>DEVICE TRUST</strong><br>
      P-256 identity, signed short-lived enrollment challenges, metadata-bound proof, role grants and revocation foundation.
    </td>
    <td align="center" width="33%">
      <img src="https://api.iconify.design/lucide/workflow.svg?color=%23800020" width="28" height="28" alt=""><br>
      <strong>DEVICE WORKERS</strong><br>
      Linux preview agent with durable jobs, HTTPS polling and default-deny health/storage/backup snapshot operations.
    </td>
  </tr>
  <tr>
    <td align="center">
      <img src="https://api.iconify.design/lucide/blocks.svg?color=%23800020" width="28" height="28" alt=""><br>
      <strong>OPTIONAL RUNTIMES</strong><br>
      Python 3.11 and Node.js 24.22.1 already exist in a separate Android companion APK; both are in progress.
    </td>
    <td align="center">
      <img src="https://api.iconify.design/lucide/route.svg?color=%23800020" width="28" height="28" alt=""><br>
      <strong>VISITOR CONNECTIVITY</strong><br>
      Local Network, AssayFlux Relay and Cloudflare Tunnel are independent ingress options.
    </td>
    <td align="center">
      <img src="https://api.iconify.design/lucide/shield-check.svg?color=%23800020" width="28" height="28" alt=""><br>
      <strong>CONTROL &amp; EVIDENCE</strong><br>
      Versioned contracts, least-privilege policies, audit foundations, bounded operations and CI validation.
    </td>
  </tr>
</table>

## TECHNOLOGY BOUNDARIES

The tools have separate responsibilities. They are not one giant Android bundle.

| Technology | Lives in | Responsibility | Status |
|---|---|---|---|
| HTML / CSS / browser JavaScript | Main Android app + Rust engine | Default static website hosting | Foundation implemented |
| TypeScript compiler | Main Android app | Compile user website TypeScript into browser JavaScript | Required by Step 4; not yet implemented/verified |
| React + TypeScript + Vite | <code>web-console/</code> | Separate browser operator console | Present; builds in CI |
| Python 3.11 | <code>runtime-worker/</code> companion APK | Optional server-side WSGI runtime | Existing integration; in progress |
| Node.js mobile 24.22.1 | <code>runtime-worker/</code> companion APK | Optional server-side JavaScript runtime | Existing integration; in progress |
| Future 20-language Runtime Library | <code>runtime-library/catalog/</code> | Independently downloadable server-side runtime packs | Catalog manifests only; future work |

**Important distinctions**
- The main app must serve static websites without the runtime companion.
- Python and Node.js are already in the repository. They currently ship together in the separate companion APK and share its application UID. This is not a per-project hostile-code sandbox; use trusted owner-controlled code only until isolation tests pass.
- The future Runtime Library will split Python and Node into independent signed packages and add 18 planned languages. The JSON manifests are not downloadable runtime binaries.
- React, TypeScript and Vite dependencies for the operator console are not bundled into the Android app.
- The main app's built-in TypeScript compiler is a separate website-authoring feature, not Node.js and not a server-side runtime.

See [Web Toolchain Boundaries](docs/WEB_TOOLCHAIN_BOUNDARIES.md), [Step 4 Web Baseline](docs/STEP_4_WEB_BASELINE.md) and [Future Runtime Library](docs/FUTURE_RUNTIME_LIBRARY.md).

## SYSTEM ARCHITECTURE

~~~mermaid
flowchart TB
    OWNER["OWNER"] --> MOBILE["ANDROID POCKET HOST"]
    MOBILE --> MODE{"OPERATING MODE"}
    MODE -->|"Server only"| SERVER["WEBSITE SERVER"]
    MODE -->|"Command Center + Server"| COMMAND["FLEET COMMAND CENTER"]
    COMMAND --> TRUST["DEVICE IDENTITY · OWNER APPROVAL · ROLE GRANTS"]
    TRUST --> AGENTS["SEPARATE DEVICE AGENTS"]
    AGENTS --> STORAGE["STORAGE / BACKUP"]
    AGENTS --> TRAFFIC["TRAFFIC / GATEWAY"]
    AGENTS --> BUILD["BUILD / PUBLISH"]
    AGENTS --> JOBS["BOUNDED NATIVE JOBS"]
    SERVER --> RUST["RUST HTTP INGRESS"]
    RUST --> STATIC["HTML · CSS · JAVASCRIPT"]
    RUST --> COMPANION["OPTIONAL RUNTIME COMPANION"]
    COMPANION --> PY["PYTHON 3.11"]
    COMPANION --> NODE["NODE.JS 24.22.1"]
    SERVER --> INGRESS["LOCAL NETWORK / ASSAYFLUX RELAY / CLOUDFLARE"]
    COMMAND --> CONTROL["OPTIONAL RUST CONTROL PLANE"]
    CONSOLE["REACT · TYPESCRIPT · VITE CONSOLE"] --> CONTROL
    EDGE["DEMONCORE · MCU"] --> AGENTS
~~~

The diagram shows target integration boundaries, not a claim that every connection or role is production-ready. Current implementation and acceptance status are listed below.

## REPOSITORY STATUS

| TRACK | CURRENT POSITION | OPEN WORK |
|---|---|---|
| **STEP 1 · CONTRACTS** | Protocol 1.0 schemas, role/job policies, validator and trust simulations committed | Confirm Alpha CI evidence and complete review |
| **STEP 2 · MOBILE TRUST** | Android two-mode UI, P-256 owner identity, signed challenge/proof, role grant, encrypted registry and revocation foundation | Authenticated transports, per-use key auth, full control-plane workflow, agent provisioning and physical-device acceptance |
| **STEP 3 · DEVICE AGENTS** | Linux preview package, durable SQLite jobs, HTTPS polling, signed grants/jobs, health and local storage/backup snapshot executors | Full mobile provisioning, grant renewal/revocation, stronger isolation, other OS packages, missing worker roles and hardware acceptance |
| **STEP 4 · WEB SERVER** | Rust static-serving foundation, site import/registry/backup and Android hosting lifecycle | Main-app TypeScript compiler, secure staging/preview/atomic publish and physical-device acceptance |
| **STEP 5 · RESILIENCE** | Control-plane, Relay, Cloudflare, backup and diagnostics foundations exist | External ingress acceptance, restore drills, security review, fault/soak testing and signed release acceptance |
| **FUTURE RUNTIME LIBRARY** | Python and Node.js runtime foundations; 20 catalog manifests | Independent package artifacts, Runtime Manager, security/lifecycle acceptance and 18 additional runtimes |

No step is considered complete solely because documentation, source code or a successful compile exists. See [Master Plan](docs/MASTER_PLAN.md) and [Repository Baseline](docs/PROJECT_BASELINE.md).

## ANDROID APPLICATION

The current Android app is a Kotlin/Jetpack Compose application with a native Rust website engine. Its repository includes:
- Website hosting foreground service and boot/recovery hooks.
- Site import from folder, ZIP and individual files; site registry and preview flow.
- Site/application backup and interrupted-operation recovery foundations.
- Local management API and remote control agent.
- Owner/account and biometric confirmation foundations.
- Device trust store and Command Center device screen.
- Capacity, telemetry, metrics and operational logs.
- Cloudflare Tunnel integration and AssayFlux Relay client foundation.
- Runtime companion client, trust acknowledgement and project transfer.

The Android module declares min SDK 26, target SDK 35 and native ABI targets arm64-v8a, armeabi-v7a and x86_64. Declared build targets are not a substitute for device acceptance.

## DEVICE SOFTWARE

| Device | Software | Repository status |
|---|---|---|
| Android phone/tablet | Pocket Host main app | Implemented foundation; device acceptance open |
| Android runtime provider | Separate runtime companion APK | Python + Node integrations in progress |
| Linux x86_64 | Pocket Host Linux Node Agent | Preview package |
| Windows | Native worker agent | Planned |
| macOS | Native worker agent | Planned |
| Raspberry Pi / SBC | Board/OS-specific agent or gateway | Planned; no board certified |
| iOS | Mobile client | Planned; no iOS source project here |
| DemonCore / MCU | Separate firmware and board profiles | External project; Pocket Host adapter acceptance open |

## DEVELOPMENT AND VERIFICATION

Run commands from the repository root. The Android build requires JDK 17, Android SDK/NDK (NDK 27.2.12479018 in CI), Go 1.27.4 for the cloudflared helper, Rust 1.88 and <code>cargo-ndk</code>. Install the Android Rust targets before compiling. The cloudflared Android native dependency is prepared by the repository build helper.

### Android main app

~~~bash
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk --version 4.1.2 --locked
bash scripts/build-cloudflared-android.sh
./gradlew :app:assembleDebug
~~~

### Optional Python/Node runtime companion

~~~bash
./gradlew :runtime-worker:assembleDebug
~~~

This builds a separate companion APK. It is not required for static website hosting.

### Rust website engine

~~~bash
cargo test --manifest-path rust-engine/Cargo.toml --locked
~~~

### Python runtime and Linux agent tests

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

### CI evidence

The [Pocket Host quality workflow](https://github.com/AssayFlux/PocketHost/actions/workflows/quality.yml) runs on pushes and pull requests targeting the default branch, Alpha. It checks Rust, Python/Linux agent, browser console, Node metrics, Gradle wrapper and control-plane configuration/tests. Use the live badge above and [workflow history](https://github.com/AssayFlux/PocketHost/actions/workflows/quality.yml) for the latest result. CI does not by itself prove Android runtime execution, physical-device behavior, external public reachability or production security.

The Android integration workflow has compile-only and manual APK/emulator paths. APK packaging and emulator tests are not automatic on every push. No production APK, signed release or device certification is claimed by this README.

## DOCUMENTATION

| Document | Purpose |
|---|---|
| [System Architecture](docs/SYSTEM_ARCHITECTURE.md) | Normative system boundaries, topology and trust |
| [Master Plan](docs/MASTER_PLAN.md) | Five-step implementation plan |
| [Roadmap](docs/ROADMAP.md) | Milestones and gate definitions |
| [Repository Baseline](docs/PROJECT_BASELINE.md) | Source-audited implementation inventory |
| [Repository Deep Audit](docs/REPOSITORY_AUDIT.md) | Component-level source findings, workflow wiring and open engineering risks |
| [Device Software Matrix](docs/DEVICE_SOFTWARE_MATRIX.md) | Platform/role support boundaries |
| [Web Toolchain Boundaries](docs/WEB_TOOLCHAIN_BOUNDARIES.md) | React/Vite, TypeScript compiler and runtime separation |
| [Step 1 — Protocol](docs/STEP_1_TRUST_AND_PROTOCOL.md) | Schemas, signing and threat model |
| [Step 2 — Mobile Trust](docs/STEP_2_DEVICE_TRUST_IMPLEMENTATION.md) | Android enrollment and owner trust foundation |
| [Step 3 — Device Agents](docs/STEP_3_DEVICE_AGENTS.md) | Linux agent and remaining platform work |
| [Step 4 — Web Baseline](docs/STEP_4_WEB_BASELINE.md) | Main-app static hosting and TypeScript compiler requirements |
| [Future Runtime Library](docs/FUTURE_RUNTIME_LIBRARY.md) | Twenty optional server-side runtime catalog |
| [Security and Isolation](docs/SECURITY_ISOLATION_DESIGN.md) | Threats, privilege boundaries and acceptance |
| [Website Deployment](docs/WEBSITE_DEPLOYMENT.md) | Site lifecycle, runtime placement and publishing |
| [Control Plane](docs/CONTROL_PLANE_DEPLOYMENT.md) | Optional remote fleet coordination |
| [AssayFlux Relay](docs/ASSAYFLUX_RELAY.md) | First-party visitor ingress provider |
| [Cloudflare Tunnel](docs/CLOUDFLARE_TUNNEL.md) | Independent public ingress provider |
| [Release Signing](docs/RELEASE_SIGNING.md) | Signing, SBOM and release provenance |
| [Documentation Index](docs/INDEX.md) | Full document catalogue |

## PROJECT IDENTITY

<p align="center">
  <img src="https://img.shields.io/badge/ASSAYFLUX-Personal_Infrastructure-800020?style=flat-square">
  <img src="https://img.shields.io/badge/PRODUCT-Pocket_Host-78649A?style=flat-square">
  <img src="https://img.shields.io/badge/DEFAULT_BRANCH-Alpha-527C72?style=flat-square">
  <img src="https://img.shields.io/badge/STATUS-Engineering_Pre--Release-B18B5E?style=flat-square">
  <img src="https://img.shields.io/badge/VALIDATION-Before_Claims-657F70?style=flat-square">
</p>

Pocket Host is licensed under the [Apache License 2.0](LICENSE).
