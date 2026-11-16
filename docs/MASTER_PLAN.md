# ASSAYFLUX POCKET HOST — MASTER ENGINEERING PLAN

**Product:** AssayFlux Pocket Host · **Developer:** Designed by AssayFlux  
**Host package:** `com.assayflux.pockethost` · **Companion:** `com.assayflux.pockethost.worker`  
**Version target:** 0.1.0 · **Status:** pre-release, not production approved.

## 1. System definition

Pocket Host is a native Android website-origin platform. The phone runs Rust/Tokio/Hyper and serves static sites or proxies to optional application runtimes. Public connectivity is optional and can use the owner's existing Cloudflare Tunnel or the planned first-party AssayFlux Relay. Local hosting must work without either. The optional Axum/PostgreSQL control plane manages devices; it never serves the user's website.

## 2. Supported product workflows

### Simple Mode
1. Import a static folder or ZIP.
2. Validate archive, paths, size and root entry.
3. Stage and preview.
4. Register hostname.
5. Choose local-only or a configured public connector.
6. Publish, externally verify if public, observe and back up.

### Advanced Mode
- Multiple static sites and hostname routing.
- Python WSGI and Node apps for owner-trusted code only until Gate 2 passes.
- Per-site request/concurrency budgets and Capacity Lab.
- Device health, metrics, logs, alerts and recovery.
- Cloudflare or future AssayFlux Relay configuration.
- Separate Owner/Operator and visitor authorization.
- Optional remote management.

## 3. Architecture and responsibility boundaries

| Layer | Technology | Owns | Must not own |
|---|---|---|---|
| Android host | Kotlin / Jetpack Compose | UI, SAF, permissions, lifecycle, telemetry | Duplicated HTTP routing |
| Hosting service | Android foreground service | Visible host lifecycle and supervision | Unbounded background work |
| Website engine | Rust / Tokio / Hyper | Ingress, host routing, static serving, reverse proxy, admission, metrics | UI and Android permission logic |
| JNI/native bridge | C++ / JNI / Rust | Coarse-grained versioned native boundary | Per-request UI callbacks |
| Runtime companion | Android package + Binder | Authenticated broker, project transfer, cell lifecycle | Executing untrusted project code in broker |
| Execution cells | Android isolated services + native policy | One project per isolated UID; file/network capabilities | Host secrets or arbitrary egress |
| Python | CPython 3.11 / Chaquopy | WSGI app within accepted cell | Automatic pip or general Linux assumptions |
| Node | Node mobile 24.22.1 | Embedded Node HTTP app within accepted cell | Desktop process manager assumptions |
| Console | TypeScript / React / Vite | Owner/operator client | Public management ingress |
| Cloudflare | cloudflared | Optional outbound edge tunnel | Visitor identity or capacity governance |
| AssayFlux Relay | Rust gateway + outbound agent (planned) | First-party public gateway and encrypted device streams | Management API exposure or arbitrary remote shell |
| Identity | Auth/device registry (planned) | Owner, device and visitor authorization | Shared privilege between visitors and operators |
| Control plane | Rust/Axum + PostgreSQL | Optional remote device registry and leased commands | Website origin |
| Resource governor | Rust policy + Android telemetry | Safe admission, fairness and degradation | Hard CPU quota or Android process immortality |

## 4. Traffic and trust paths

### Website request
```text
Visitor → Cloudflare Tunnel OR AssayFlux Relay → Rust listener on phone
        → hostname/site router → static files OR isolated runtime upstream
```

### Local management
```text
Owner/Operator → pairing and authentication → loopback management API
               → Android services / Rust engine / runtime broker
               → TypeScript/React console
```

### Remote management
```text
Android agent → outbound authenticated channel → Axum control plane
             → PostgreSQL device state / leased commands / audit
```

Website traffic and management traffic are distinct. No public connector may route to the management API.

## 5. Runtime contracts and current limitations

### Static engine
Rust owns GET/HEAD, MIME, byte ranges, streaming, path canonicalization, hostname routing, SPA fallback, runtime reverse proxy and bounded admission. Static service remains independent of optional runtime availability.

### Python
CPython 3.11 / Chaquopy source is packaged in the runtime companion. Host transfers a bounded archive through signature-protected Binder; companion stores the project and runs WSGI. Root `wsgi.py` exports `application`; current WSGI worker supports configured thread bounds and host routing. This is source integration, not proof of isolated production execution.

### Node
Pinned fogtape Node mobile `v24.22.1-0` (upstream Node 24.22.1) and JNI bridge are packaged in the companion. Dependencies are prebundled; one active app is supported. Node process and Python worker share companion UID. Listener preload is not outbound-network security.

### Companion IPC
Signature-level permission, caller package/signature validation, protocol version negotiation, bounded ZIP transfer and companion-owned storage exist in source. Host-vs-companion UID separation does not isolate projects from one another.

### Gate 2 execution-cell target
One Android isolated service process/UID per project. Trusted broker passes only project/scratch directory capabilities and a host-created Unix-domain visitor listener. Native policy installs before project code and fails closed. Rust remains ingress. This target is not complete until real Python and Node code runs inside cells and adversarial tests pass API 26/33/35.

## 6. AssayFlux Relay architecture (planned)

### Data path
1. Visitor connects to a public relay hostname over TLS.
2. Gateway validates hostname, site policy, rate and visitor identity.
3. Gateway selects an enrolled device and active tunnel session.
4. Gateway opens a bounded multiplexed stream over the phone-initiated authenticated tunnel.
5. Android agent forwards the stream to the phone's Rust website listener.
6. Rust routes to static site or isolated application cell; response returns through the stream.

### Components
- Public gateway and TLS/certificate manager.
- Android outbound tunnel agent with heartbeat, reconnect and network-change handling.
- Device enrollment broker with mutual authentication, key rotation and revocation.
- Multiplexed stream protocol with flow control, backpressure and strict limits.
- Verified domain registry and device/site routing table.
- Visitor access gateway for Public, Private and Restricted site modes.
- Abuse prevention, quotas, metrics, audit and incident operations.

Start with a mature TLS-protected HTTP/2 stream transport or reviewed reverse-tunnel implementation. WebSocket/TLS may be a compatibility fallback. QUIC is a later optimization. Do not invent cryptography. Commands are typed, scoped, expiring and replay-resistant. Standard relay TLS termination means the gateway can inspect HTTP content and must disclose that. End-to-end TLS pass-through is a later advanced mode.

The relay hides the phone's IP from ordinary visitors, not from the relay operator or carrier. It must never expose management endpoints.

## 7. Authentication contracts

### Owner / Operator
Passkey-first identity, secure recovery, least-privilege roles, device approval, session expiry/revocation, fresh authorization for sensitive operations and audit.

### Visitor
Per-site Public, Private or Restricted mode. Use short-lived site-scoped tokens, rate limits, revocation and authorization before forwarding. Visitor identity must never authorize management operations.

## 8. Adaptive capacity and safety

Telemetry includes hardware/API/ABI, CPU, PSS/RSS, heap, storage, battery/temperature, thermal state, network and per-engine request/queue/error metrics. Unknown values remain unknown. Keep bounded history.

Capacity Lab expands from initial static GET sweeps to cached/uncached content, payload sizes, keep-alive, tunnel, trusted Python/Node, p50/p95/p99, throughput, error rate, queue depth, memory, CPU, temperature and battery. Governor uses global/per-site fair admission, bounded queues, graceful 429/503, deadlines, static reserve and hysteresis. Never publish a universal visitor count or promise hard CPU quotas.

## 9. Backup and operations

Encrypted `.afbackup` includes supported site/runtime source, route mapping and allowlisted non-secret settings. Exclude tokens, sessions, Keystore keys, signing material and device-bound credentials. Test corruption, wrong passphrase, interruption, low storage, failed activation and rollback. Logs are bounded, rotated and redacted. Restore must preserve last known-good site.

## 10. UI contract

Five destinations: Dashboard, Sites, Activity, Learn, Settings. Classic clean navy visual identity, monochrome icons, card-based hierarchy, light default and optional dark. No gradients/neon/glass. Every state is backend-derived. Simple Mode prioritizes first static publish; Advanced exposes runtime/network/capacity controls with clear trust and resource warnings.

## 11. Implementation sequence

0. Repository truth and documentation freeze.
1. Companion runtime migration and Rust end-to-end.
2. Per-project OS isolation and adversarial security.
3. AssayFlux Relay and device enrollment.
4. Owner/Operator and visitor authentication.
5. Rust governor and Capacity Lab.
6. Backup, operations and lifecycle recovery.
7. Product UX and onboarding.
8. Exact-SHA API 26/33/35, signed release, external connectivity, physical-device acceptance and independent review.

Detailed exit criteria live in `docs/NORTH_STAR.md` and `docs/ROADMAP.md`.

## 12. Build, verification and release discipline

Use repository-pinned Gradle, SDK, NDK, Rust and native dependencies. CI results must identify exact SHA, workflow, API level, test counts, failures and skips. A compile pass is not runtime proof; emulator is not physical device; source presence is not a shipped feature. Host and companion release APKs share a signer. Validate package IDs, ABIs, signatures, checksums and upgrades. Never commit secrets.

**Release definition:** all gates accepted on one release candidate, physical ARM device evidence, external HTTPS tests, independent security review and explicit owner approval.
