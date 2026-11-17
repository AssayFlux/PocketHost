# ASSAYFLUX POCKET HOST — NORTH STAR

**Product:** AssayFlux Pocket Host  
**Steward:** AssayFlux Scientific Systems  
**Developer identity:** Designed by AssayFlux  
**Host package:** `com.assayflux.pockethost`  
**Runtime companion:** `com.assayflux.pockethost.worker`  
**Initial product version target:** `0.1.0`  
**Status:** Pre-release engineering; no production approval.

---

## 1. THE NORTH STAR

**Make secure, understandable and affordable self-hosting possible on the devices people already own — from Android phones and PCs to servers, Raspberry Pi-class boards and constrained edge devices — without forcing a mandatory cloud server.**

Pocket Host begins with Android as its first host and expands into a cross-platform ecosystem where capable phones, tablets, PCs, servers and SBCs can run websites and services, while microcontrollers contribute bounded edge/device capabilities. The product should make publishing straightforward for beginners while giving advanced operators meaningful controls for capacity, security, networking, recovery and diagnostics.

**Brand promise:** Your website. Your device. Your control.

**Operating loop:** Import → Validate → Preview → Publish → Observe → Protect → Recover.

The phone is the origin. Kotlin owns Android lifecycle and native UI. Rust/Tokio/Hyper owns website ingress and routing. TypeScript/React provides the management console. Python and Node are optional application runtimes. External tunnel infrastructure is a connectivity path, not the website origin.

## 2. WHO THE PRODUCT SERVES

1. **Simple site owner:** publish a static site from a folder or ZIP without a terminal.
2. **Independent developer:** host a modest trusted Python WSGI or Node application.
3. **Technical operator:** manage multiple sites, hostnames, resource budgets, logs, backups and recovery.
4. **Privacy-conscious owner:** expose a website through an outbound tunnel without advertising the phone's public IP to visitors.
5. **Learning user:** understand the real limits of hosting on ordinary Android hardware.

## 3. PRODUCT PROMISES AND NON-NEGOTIABLES

### Phone-first, no-root hosting
- Core static hosting must run on the phone and work locally without a VPS, control plane, Cloudflare account or AssayFlux account.
- Support Android 8.0 / API 26 and newer, ARMv7 and ARM64 as declared targets; x86_64 supports emulator coverage.
- No Termux, root, custom kernel or privileged device-owner assumption.
- Be honest about Doze, battery, thermal, OEM process management, carrier NAT and power loss.

### Static-first reliability
- Static websites are the foundational workload.
- Python, Node, tunnel connectors, remote management and future billing must be optional.
- A runtime crash, tunnel outage or remote control-plane outage must not take down local static hosting.
- Stage imports and updates; preserve the last known-good deployment and support rollback.

### Rust owns the request path
- Rust/Tokio/Hyper owns HTTP ingress, hostname routing, static serving, reverse proxy, admission and request metrics.
- Kotlin owns Android integration and lifecycle.
- JNI is coarse-grained and versioned; do not implement a second request router in Kotlin or JavaScript.
- Management endpoints remain separate from website ingress.

### Security is an implementation gate
- The companion UID is not a per-project sandbox.
- Python/Node remain trusted owner-controlled code until every execution cell has a distinct OS-enforced identity, project-only file capabilities and enforced network policy.
- No hostile visitor-submitted code, marketplace code or multi-tenant execution before independent security review.
- No language-level monkey patch or filename filter may be represented as an OS security boundary.

### Evidence before claims
- Separate source-present, compiled, automated-tested, emulator-verified, physical-device-verified, externally-verified and production-approved states.
- Never invent visitor limits, availability, test results or security guarantees.
- Capacity claims must state device, workload, duration, network, thermal conditions and measurement method.

## 4. PRODUCT EXPERIENCE

### Simple Mode
1. Import a website folder or ZIP.
2. Validate paths, file sizes and root entry page.
3. Preview on the phone.
4. Assign hostname(s).
5. Choose local-only or public connectivity.
6. Publish and verify from another network if public.
7. Observe status and activity.
8. Back up and safely update/rollback.

### Advanced Mode
- Multiple static sites and hostname routing.
- Trusted Python WSGI and Node applications, only after the security gate is passed for their production support tier.
- Per-site capacity budgets, concurrency and request controls.
- Capacity Lab, hardware health, graphs, logs and alerts.
- Tunnel, domain and visitor-access policies.
- Backup schedules, retention, recovery and optional remote management.

### Navigation and native UI contract

The authoritative native UI specification is **Pocket Host Design Language V1** in `docs/UI_DESIGN.md`. The native launch experience is a branded local biometric app-unlock panel (not a claimed native username/password login), followed by a branded home dashboard.

V1 dashboard hierarchy: compact Pocket Host brand/header; horizontal control bar with hamburger on the left and separate user/account menu on the right; tall **portrait-rectangular** service cards in a horizontal carousel, with a slightly emphasized active card and partial adjacent previews; then a fixed information centre with **four or five compact information bars visible simultaneously**. The information centre uses minimal monochrome icons, useful metrics, meaningful small graphs/progress indicators and truthful status.

**Scrolling is strict:** the dashboard does not vertically scroll. Only the service carousel scrolls horizontally. The information centre does not scroll horizontally or vertically. Adapt the composition and information density to available screen space, Android font scale and display size; do not introduce dashboard scrolling or clip content as a workaround.

The hamburger drawer is the primary global navigation and contains supported destinations for Home, Hosting, Websites & Files, Relay & Networking, Python Runtime, Node Runtime, Security & Access, Capacity & Performance, Backups & Recovery, Activity & Logs, Learn, Settings and About. Each destination must route to its correct feature surface; no cosmetic duplicate routes or invented capabilities.

The separate user/account dropdown exposes only real identity/session actions and supported shortcuts. Do not invent a cloud identity, profile or sign-out flow. Login, drawer and user menu must have explicit visual hierarchy, monochrome iconography, accessible touch targets and correct dismissal/back behaviour.

V1 typography uses a bundled licensed calligraphic wordmark, elegant display headings and readable sans-serif operational text. Use a consistent minimal monochrome icon family. Provide ten coordinated classic palettes (Classic Ivory, Midnight, Forest, Burgundy, Ocean, Graphite, Sandstone, Lavender, Copper and Arctic) and persisted System/Light/Dark appearance modes across the entire app. Detailed dimensions, theme tokens, screen-specific layout, accessibility and acceptance criteria are in `docs/UI_DESIGN.md`.


## 5. NETWORKING NORTH STAR — USER-SELECTABLE DUAL TUNNEL SYSTEM

Pocket Host must provide its own first-party tunnel system **and** Cloudflare Tunnel. Users choose either one or operate both simultaneously according to their needs. Neither provider is mandatory. Local Network access is also available independently.

### A. AssayFlux Relay — first-party tunnel system

A first-party outbound tunnel owned and operated as part of the AssayFlux product. Users who do not want Cloudflare can use AssayFlux Relay alone.

**Target traffic path**
```
Visitor → AssayFlux Relay public gateway → authenticated encrypted device tunnel
        → Pocket Host Rust listener → hostname/site router → website
```

The phone initiates and maintains the outbound connection. Visitors connect to the Relay public address, not directly to the phone. This supports ordinary NAT/CGNAT where outbound connectivity is permitted. It hides the phone's public IP from ordinary visitors, not from the Relay operator, carrier or network provider.

Required components: public gateway; Android tunnel agent; device enrollment and revocation; multiplexed stream transport; verified domain registry; certificate manager; visitor access gateway; abuse prevention, quotas, metrics, audit and incident operations.

Use a mature reviewed TLS-protected transport (initially bounded HTTP/2 streams or an established reverse-tunnel protocol); do not invent cryptography. Support flow control, backpressure, bounded buffers, short-lived credentials, mutual device authentication and revocation. Disclose whether TLS terminates at Relay or passes through to the phone.

### B. Cloudflare Tunnel — independent optional provider

Cloudflare Tunnel remains a first-class supported provider, not a replacement for AssayFlux Relay. Users may select an existing named tunnel or complete an explicit supported setup flow. Never silently create duplicate tunnels or overwrite user configuration. Public hostnames must target the Rust website listener only. Keep Cloudflare Access available for private-site policy. Verify external HTTPS; a running connector alone is not proof of reachability.

### C. Simultaneous operation — explicitly supported

Users may choose:
- Local Network only.
- AssayFlux Relay only.
- Cloudflare Tunnel only.
- AssayFlux Relay and Cloudflare Tunnel together.

Both tunnel providers can serve the same site concurrently through separate hostnames, or different sites through their own hostname mappings, where DNS/domain ownership and provider constraints allow it. Detect hostname, DNS, certificate and route conflicts before activation. Never bind two providers ambiguously to one hostname.

Provider health and controls are independent: show each provider's connection, authentication, hostname, TLS/certificate, traffic, latency/error and external reachability state separately. A failure in one provider must not stop the other provider or local hosting. No silent failover or DNS change; any failover policy must be explicit and observable.

### D. Local Network — no public tunnel

Serve on the trusted LAN where the device/network permits it. No public DNS, Cloudflare or Relay account is required. LAN exposure is opt-in and shown separately from public ingress.

### E. Security and routing invariants

All tunnel traffic terminates at the Rust website ingress and site router. No tunnel provider exposes the owner management API, runtime broker, debug endpoints or internal control plane. Visitor authorization is enforced consistently regardless of ingress provider. Each provider has independent credentials, lifecycle, revocation and diagnostics. Tunnel outages never take down local static hosting.

## 6. AUTHENTICATION NORTH STAR

Two security planes must remain separate.

### Owner / Operator identity
- Owner enrollment and recovery.
- Passkeys as preferred interactive authentication; secure fallback and recovery codes.
- Operator roles with explicit least-privilege permissions.
- Device pairing, device approval, session expiry and revocation.
- Sensitive actions require fresh authorization where appropriate.
- Device-bound secrets use Android Keystore where available.

### Visitor identity
- **Public:** no login, intentionally public content.
- **Private:** visitor authenticates before the gateway forwards requests.
- **Restricted:** only approved identities, groups or invitation grants.
- Short-lived, audience-bound, site-scoped signed access tokens.
- Revocation, brute-force controls, audit and privacy disclosure.

A website visitor must never inherit Owner/Operator privileges. The public gateway must not expose the management API. The phone's local management API remains separately authenticated and loopback-only.

## 7. TARGET SYSTEM ARCHITECTURE

| Layer | Technology | Contract |
|---|---|---|
| Android host | Kotlin, Jetpack Compose | UI, SAF, lifecycle, foreground service, device telemetry |
| Website engine | Rust, Tokio, Hyper | Single request path, routing, static files, reverse proxy, admission |
| Native bridge | C++ / JNI | Versioned coarse-grained integration |
| Runtime broker | Android companion package + Binder | Authenticated orchestration; never execute project code in broker |
| Runtime cells | Android isolated services, native policy | One project per OS identity; file/network capability confinement |
| Python | CPython 3.11 / Chaquopy | WSGI only within accepted isolation profile |
| Node | Node mobile 24.22.1 | Embedded HTTP app within accepted isolation profile |
| Local console | TypeScript, React, Vite | Owner/operator management client |
| Relay | Rust gateway + authenticated stream transport | Public edge and device tunnel; independently deployable |
| Identity service | Auth service + device registry | Owner/device enrollment, tokens, visitor policy |
| Optional control plane | Rust/Axum + PostgreSQL | Remote management only; not website origin |
| Edge connector | cloudflared | Optional Cloudflare path |

## 8. PRIMARY-VERSION IMPLEMENTATION SEQUENCE

The primary version is complete only when the gates below have objective exit evidence. Work is sequential where later work depends on earlier security or runtime foundations; documentation and non-conflicting UI work may proceed in parallel.

### Gate 0 — Repository truth and architecture freeze
- Audit source, workflows, manifests, permissions, ABI packaging, API contracts and all active docs.
- Reconcile historical snapshots and remove stale implementation claims.
- Freeze Rust, Gradle, npm and Android dependency graphs with committed lockfiles.
- Pin Rust 1.88, checksum-verified Gradle 8.9 wrapper, Android SDK/NDK/CMake inputs and native runtime artifacts.
- Acceptance: one canonical baseline; active docs distinguish planned/source-present/verified work; locked dependency resolution succeeds on exact SHA.

**Status:** complete; canonical source/evidence baseline refreshed 2026-11-16 in `docs/PROJECT_BASELINE.md`.

### Gate 1 — Runtime migration
- Complete companion lifecycle, Binder death/rebind, version negotiation, bounded transfer, crash-recoverable replacement and coordinated shutdown.
- Route Python WSGI and Node HTTP through Rust hostname routing and reverse proxy.
- Prove static-origin continuity when an application runtime stops.
- Keep Python/Node trusted-code-only; this gate does not claim per-project isolation.
- Acceptance during implementation: production source path and regression-test definitions are present. The Owner subsequently authorized intermediate Android APK/ABI builds and API 26/33/35 emulator instrumentation during Gates 2–4; these are now required to validate security changes as they land. Gate 8 remains the final release, physical-device and independent-review acceptance stage.

**Status:** source implementation complete; Android compilation and runtime acceptance are being pulled forward into Gates 2–4 under the revised Owner-approved sequence. Final release acceptance remains Gate 8.

### Gate 2 — Per-project security isolation
- Prove isolated UID, project-only file descriptors, listener capability and seccomp policy on API 26/33/35.
- Build bounded isolated-cell supervisor and broker.
- Move archive extraction, Python interpreter and Node environment into one cell per project.
- Deny outbound sockets, DNS and alternate networking at OS boundary; prove child/native code cannot bypass.
- Add CPU/memory/process/FD/storage budgets and kill/restart semantics.
- Acceptance: two live adversarial projects cannot inspect or interfere with one another, host data or management state; real visitor requests still flow through Rust; independent review passes.
- If Android 8/SELinux cannot support the selected mechanism, stop and redesign. Do not weaken SELinux or relabel an unproven design as secure. Runtime support may be capability-tiered only with explicit owner approval and honest compatibility disclosure.

### Gate 3 — AssayFlux Relay and identity foundation
- Implement device enrollment, mutual authentication, revocation and hostname ownership proof.
- Implement gateway, Android outbound agent, multiplexed streams, flow control, backpressure and reconnect.
- Add TLS/certificate lifecycle, rate limiting, abuse controls, observability and visitor access gateway.
- Keep Cloudflare and LAN modes working.
- Acceptance: no phone inbound port; visitor sees relay address rather than phone address; unauthorized device/site mapping rejected; revoked devices lose sessions; network transitions recover; no management API route.

### Gate 4 — Visitor authentication and operator security
- Implement public/private/restricted site policy.
- Implement passkey-first owner identity, operator roles, session management, recovery and audit. Current source includes 12-hour revocable remote sessions, five-minute step-up for remote writes and sensitive local Owner actions, plus automatic in-memory credential re-exchange in the console; this is not a passkey implementation.
- Add short-lived site-scoped tokens, brute-force protection and revocation.
- Acceptance: site authorization tests pass independently of management authorization; cross-site token reuse and privilege escalation fail.

### Gate 5 — Rust governor and Capacity Lab
- Fair per-site scheduler and global/per-site bounded queues.
- Admission control, 429/503 retry hints, slow-client defense and cancellation.
- Static-origin reserve under runtime overload.
- Representative cached/uncached, payload, keep-alive, Python/Node and tunnel profiles.
- Measure RPS, throughput, p50/p95/p99, errors, memory, CPU, temperature and battery impact.
- Acceptance: repeatable safe envelopes on target phones; no universal visitor count.

### Gate 6 — Backup, operations and lifecycle
- Fault-test backup/restore, interrupted writes, corruption, wrong passphrase, low storage and rollback.
- Keep credentials/device keys excluded and rotate/re-enroll separately.
- Bounded redacted logs, durable reports, alerting and support bundle.
- Verify reboot, screen-off, battery saver, Doze, Wi-Fi/mobile changes, service death and low-memory recovery.
- Acceptance: recoverable last-known-good site and auditable operational state.

### Gate 7 — Product UX and onboarding
- Complete Dashboard, Sites, Activity, Learn and Settings.
- Guided static-site publishing, hostname and tunnel setup.
- AssayFlux Relay enrollment and visitor-access wizard.
- Simple/Advanced modes, accessible responsive layouts, meaningful empty/error states.
- Acceptance: first-time static site publication without terminal; UI states match backend; accessibility and compact-screen review pass.

### Gate 8 — Full CI and release candidate
- Exact-SHA API 26/33/35 matrix, ABI builds, host/companion packaging and test reports.
- SBOM, dependency provenance, license audit and vulnerability scan.
- Signed release APK pair, signer match, checksums and upgrade test.
- External Cloudflare and AssayFlux Relay HTTPS tests.
- Physical ARMv7/ARM64 acceptance and independent security review.
- Acceptance: all evidence attached to one release candidate and Owner approves.

## 9. CAPACITY, AVAILABILITY AND BUSINESS HONESTY

No universal visitor number or uptime guarantee. Capacity is workload-specific and phone-dependent. Android/OEM policies, heat, battery, storage, carrier network and relay availability can interrupt service. Relay infrastructure may have cost and availability dependencies; disclose pricing and limits before enabling paid plans. Static local hosting must not depend on relay account availability.

## 10. NON-GOALS FOR PRIMARY 0.1.0

- Unlimited cloud-scale hosting or guaranteed 24/7 availability.
- General-purpose Linux/Docker/root shell hosting.
- Arbitrary hostile-code execution without proven isolation.
- Custom cryptography or an undocumented tunnel protocol.
- Mandatory VPS, Cloudflare account or remote control plane.
- Payment-card storage or self-asserted compliance certification.
- Global multi-region relay mesh before one region is secure and reliable.

## 11. ACCEPTED DESIGN RULES

1. User-confirmed product requirements outrank contributor convenience.
2. Preserve the existing domain and Cloudflare Tunnel; do not create duplicates.
3. Static hosting remains independently useful.
4. Rust is the only website ingress and request router.
5. TypeScript remains the console source language.
6. No public management API.
7. Python/Node are trusted-only until per-project OS isolation is proven.
8. No secret values in source, docs, screenshots, logs or project archives.
9. Do not claim a feature based on a plan, dependency, UI toggle or compilation alone.
10. Update the North Star, baseline, roadmap and affected technical document whenever an architecture decision changes.

## 12. SOURCE-OF-TRUTH DOCUMENTS

- `docs/DEVELOPMENT_CONTEXT.md` — mandatory engineering handoff.
- `docs/PROJECT_BASELINE.md` — current implementation and weighted evidence.
- `docs/ROADMAP.md` — release gates and exit criteria.
- `docs/MASTER_PLAN.md` — technical architecture and contracts.
- `docs/DECISIONS.md` — accepted architecture decisions.
- `docs/INDEX.md` — complete documentation map.
- `docs/PROJECT_STATUS_2026-11-16.md` — dated evidence snapshot.

**Designed by AssayFlux · AssayFlux Scientific Systems**

### Current gate evidence addendum — 2026-11-16

- Gate 2 remains **open**. Lease/capability broker primitives, archive hardening and an isolated-UID/seccomp feasibility probe exist in source. The probe now closes inherited socket descriptors; instrumentation source asserts socket/connect/bind denial and a host-side Binder callback using actual Binder.getCallingUid() for brokered file read/write. Host-UID spoof rejection is covered in test source. The Binder path is not yet wired into production runtime lifecycle; the production runtime remains shared-UID and Python/Node have not migrated into per-project cells. Chaquopy/Node app-private storage bootstrap and API 26/33/35 adversarial acceptance remain open. No Android compile or instrumentation execution is claimed.
- Gate 3 remains **open, with an initial bounded HTTP Relay implemented in source**: Owner DNS TXT hostname proof, verified host-to-device/site routing, authenticated outbound Android SSE stream, bounded request/response forwarding, enabled local site-ID resolution, local Host routing with public-host preservation, cookie and Basic-auth challenge forwarding, per-site/client visitor-auth throttling, and immediate device-session revocation. It is not a general streaming Relay: 8 MiB body limits, 45-second request deadline, four concurrent local requests, no WebSocket upgrades or streaming response bodies. DNSSEC, TLS/certificate lifecycle, public deployment, Android/native validation and load/soak tests remain open. HTTP polling alone is not Relay; keep Cloudflare Tunnel independent.
- Gate 4 remains **open, with site-scoped visitor controls and five-minute credential step-up implemented in source**. Remote admin sessions, role checks, token rotation and transactional audit are covered by PostgreSQL CI. Local site modes Public/Private/Restricted, one-time high-entropy visitor credentials stored as hashes, Owner invite/revoke APIs and HTTPS-only Basic-auth enforcement are implemented in Kotlin/Rust source. The local console now prompts for a fresh Owner login after HTTP 428 and replaces the prior session. Passkey-first identity, biometric-bound step-up, route-by-route adversarial tests and executed Android/native integration tests remain open.


## 2026-11-17 master architecture decision — modular optional runtimes

The authoritative implementation and acceptance sequence is now `docs/MASTER_PLAN.md` (Master Engineering Plan V3). Pocket Host is one required host app with independently installable optional language runtime packs. Users install only the language runtimes required by their projects. Each pack has a signed manifest, version/protocol compatibility, ABI-specific payload, license inventory, health check, atomic update and rollback. Python and Node are the first packs; PHP, Ruby, JVM and other providers are future candidates, not current support promises.

A separate package/UID per language provides modular delivery and a host/provider boundary; it does **not** isolate projects sharing that language. The security target remains one OS-enforced execution identity per project, with brokered filesystem/network capabilities and a host-side supervisor. The host and broker must not execute project code. Static hosting remains fully usable without any runtime pack. Android package installation must use supported user-confirmed flows; do not claim silent installation or treat Play dynamic features as a per-project security boundary.

The master plan now separates product/Android reliability from secure modular execution, defines runtime-pack contracts, reorders gates to establish production isolation before converting Python/Node to optional packs, and adds explicit reliability/release acceptance evidence. Older gate numbering and historical status addenda below are historical snapshots; use the V2 master plan for current sequencing and acceptance.


## 6. UNIVERSAL DEVICE HOSTING NORTH STAR

Pocket Host is a distributed self-hosting ecosystem, not an Android-only app. The product supports Android phones/tablets, Linux, Windows, macOS, VPS/dedicated servers, Raspberry Pi-class SBCs and compatible microcontroller/edge devices through measured capability profiles.

A device is offered only workloads it can safely run. Full hosts run static and supported multi-runtime applications; compact edge hosts run resource-profiled services; MCUs provide bounded telemetry, device APIs, MQTT/DCP, local control and protocol bridges. A tiny MCU is not promised arbitrary Node/Python execution or general-purpose website hosting.

The portable architecture, device tiers, service catalog, host-agent contracts, workload placement, security boundaries and expansion gates are authoritative in `docs/UNIVERSAL_DEVICE_FABRIC.md`.

### DemonCore integration

AssayFlux/DemonCore remains a separate specialised microcontroller/fleet project. Pocket Host will integrate with it through a versioned, authenticated, allow-listed edge adapter after DemonCore's own physical hardware, identity, claim, transport and OTA acceptance. DCP v1's message envelope is not authentication. Never expose MCU control or GPIO operations through public website ingress. Current DemonCore documentation reports Phase 1 as not hardware-accepted, with app and hub implementation still incomplete.

### Cross-platform sequencing

Android reliability and per-project runtime isolation remain the first production gates. Freeze portable contracts, then build Linux host support and Raspberry Pi profiles, followed by Windows/macOS, MCU interoperability and multi-host placement. No rewrite, repository merge or cloud-only dependency is authorised by this expansion.
