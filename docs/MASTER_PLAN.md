# ASSAYFLUX POCKET HOST — MASTER ENGINEERING PLAN

**Product:** AssayFlux Pocket Host · **Developer:** Designed by AssayFlux  
**Host package:** `com.assayflux.pockethost` · **Companion:** `com.assayflux.pockethost.worker`  
**Version target:** 0.1.0 · **Status:** pre-release, not production approved.

## 1. System definition

Pocket Host is a native Android website-origin platform. The phone runs Rust/Tokio/Hyper and serves static sites or proxies to optional application runtimes. Public connectivity is optional and can use the owner's existing Cloudflare Tunnel or the initial bounded HTTP AssayFlux Relay path implemented in source (not yet deployed or production-approved). Local hosting must work without either. The optional Axum/PostgreSQL control plane manages devices; it never serves the user's website.

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
- Cloudflare configuration and initial bounded AssayFlux Relay source path; Relay deployment and general streaming remain open.
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
| AssayFlux Relay | Rust/Axum gateway + Android outbound SSE agent (partial source implementation) | DNS TXT hostname proof, device/site routing, bounded HTTP request-response forwarding | General TCP/WebSocket streaming, management API exposure or arbitrary remote shell |
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

Website traffic and management traffic are distinct. Relay visitor hostnames are routed through the visitor gateway only; admin and device API routes are host-guarded to the dedicated POCKETHOST_CONTROL_API_HOST. The separately authenticated remote-control hostname is an explicit management endpoint on port 8080; the visitor gateway listens separately on port 8081 and contains no admin/device-management routes.

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

## 6. AssayFlux Relay architecture (partial source implementation)

### Implemented bounded HTTP path (source only)
1. Owner registers a hostname against a device and site ID; the control plane returns a one-time DNS TXT proof challenge and stores only its hash.
2. Owner verifies the TXT record. The gateway validates the resolver source, DNS transaction ID, question and answer owner before activating the hostname. DNSSEC is not currently validated.
3. The Android agent opens an authenticated outbound Server-Sent Events stream using the device bearer token. Device-token rotation/deletion revokes the in-memory stream and pending requests.
4. A verified visitor hostname routes to the registered device/site. Request bodies and responses are capped at 8 MiB; per-device pending requests are bounded; the gateway deadline is 45 seconds.
5. The Android agent resolves the exact site ID to an enabled local SiteRegistry entry, uses that site's configured local hostname for Rust routing, and preserves the verified public hostname for upstream X-Forwarded-Host. A bounded raw loopback HTTP socket is used to preserve Host semantics.
6. Multiple Set-Cookie values and WWW-Authenticate challenges are retained. Rust enforces Public/Private/Restricted visitor policy and per-site/effective-client-IP authentication throttling before proxying to customer runtimes.

This is bounded HTTP request/response forwarding, not a general-purpose streaming tunnel. Visitor authentication is currently enforced by the phone's Rust listener after the gateway forwards the request, not at the public gateway.

### Components
- Public gateway and TLS/certificate manager.
- Android outbound tunnel agent with heartbeat, reconnect and network-change handling.
- Device enrollment broker with mutual authentication, key rotation and revocation.
- Multiplexed stream protocol with flow control, backpressure and strict limits.
- Verified domain registry and device/site routing table.
- Visitor access gateway for Public, Private and Restricted site modes.
- Abuse prevention, quotas, metrics, audit and incident operations.

Start with a mature TLS-protected HTTP/2 stream transport or reviewed reverse-tunnel implementation. WebSocket/TLS may be a compatibility fallback. QUIC is a later optimization. Do not invent cryptography. Commands are typed, scoped, expiring and replay-resistant. Standard relay TLS termination means the gateway can inspect HTTP content and must disclose that. End-to-end TLS pass-through is a later advanced mode.

The relay hides the phone's IP from ordinary visitors, not from the relay operator or carrier. It must never expose management endpoints. The current implementation does not yet provide WebSocket upgrades, streaming downloads/SSE, end-to-end HTTP/2 multiplexing, certificate lifecycle, DNSSEC, external deployment or load/soak evidence. Cloudflare Tunnel remains independent.

## 7. Authentication contracts

### Owner / Operator
Passkey-first identity, secure recovery, least-privilege roles, device approval, session expiry/revocation, fresh authorization for sensitive operations and audit.

Remote control-plane administration now exchanges long-lived bootstrap/team API credentials for 12-hour server-side sessions. Session tokens are stored hashed, explicitly revocable, bound to the current team-user token version, and invalidated when the account is revoked or its token rotates. The browser keeps the long-lived credential out of browser storage and uses the session token only in memory. This is an incremental remote-control security improvement; it does not implement passkey-first local Owner identity or visitor authentication.

### Visitor
Per-site Public, Private or Restricted mode. Current source uses one-time 256-bit visitor credentials stored as SHA-256 hashes, browser Basic authentication, per-site/effective-client-IP failure throttling and immediate credential revocation. Visitor identity never authorizes management operations. Short-lived visitor tokens and public-gateway pre-forward authorization remain future work.

## 8. Adaptive capacity and safety

Telemetry includes hardware/API/ABI, CPU, PSS/RSS, heap, storage, battery/temperature, thermal state, network and per-engine request/queue/error metrics. Unknown values remain unknown. Keep bounded history.

Capacity Lab expands from initial static GET sweeps to cached/uncached content, payload sizes, keep-alive, tunnel, trusted Python/Node, p50/p95/p99, throughput, error rate, queue depth, memory, CPU, temperature and battery. Governor uses global/per-site fair admission, bounded queues, graceful 429/503, deadlines, static reserve and hysteresis. Never publish a universal visitor count or promise hard CPU quotas.

## 9. Backup and operations

Encrypted `.afbackup` includes supported site/runtime source, route mapping and allowlisted non-secret settings. Exclude tokens, sessions, Keystore keys, signing material and device-bound credentials. Test corruption, wrong passphrase, interruption, low storage, failed activation and rollback. Logs are bounded, rotated and redacted. Restore must preserve last known-good site.

## 10. UI contract

The owner-approved Android layout is authoritative in `docs/UI_DESIGN.md`: biometric app-unlock screen, then a branded home with hamburger drawer, account dropdown, swipeable enlarged-centre service carousel and stacked live information cards. The drawer is the primary global navigation and routes into all existing product feature implementations. The previous five-destination bottom-navigation shell is retired. Use classic warm-neutral cards, navy identity, monochrome icons, light default and coordinated dark mode. No gradients/neon/glass. Every operational state is backend/device-derived. Simple Mode prioritizes first static publish; Advanced exposes runtime/network/capacity controls with clear trust and resource warnings. Preserve all feature state/actions while reorganizing the UI.

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


## Owner UI architecture update — 2026-11-17

The login and home-screen sketches supplied by the owner supersede the older bottom-navigation visual contract. The authoritative layout, route mapping, truthful state rules and acceptance criteria are in `docs/UI_DESIGN.md`. The source shell is implemented on main; API 26/33/35 Android compilation/instrumentation and screenshot/accessibility acceptance remain open until exact-SHA evidence is inspected.
