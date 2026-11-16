# PocketHost Architecture Decision Log

This concise log records product decisions future work must preserve. New decisions require date, context, decision, alternatives and consequences. Never silently reverse an accepted decision.

## Accepted decisions

### ADR-001 — Native Android, no Termux or root
PocketHost is a standalone Kotlin/Jetpack Compose Android app using supported Android APIs. Respect foreground-service, Doze, thermal, memory and OEM restrictions; do not promise guaranteed uptime.

### ADR-002 — Phone is the website origin
The Android phone runs the Rust HTTP server and serves website traffic. The core product must not require a VPS or remote management server.

### ADR-003 — Reuse the existing Cloudflare Tunnel
Reuse the owner's existing named tunnel/domain. Verify its Public Hostname maps to the Rust listener, never the management API. Tunnel connectivity is not visitor authentication.

### ADR-004 — Rust central request engine
Rust/Tokio/Hyper owns public HTTP ingress, static hosting, routing, proxying, admission and metrics. Kotlin handles Android integration; avoid duplicating per-request routing.

### ADR-005 — TypeScript for the web console
Authored browser-console source uses TypeScript with React/Vite. Generated JavaScript bundles are expected; do not migrate source to JavaScript.

### ADR-006 — Static hosting remains independent
Static sites are foundational; Python, Node, remote control and payments are optional. Optional runtime failures/configuration must not take down static hosting.

### ADR-007 — Python and Node are trusted-code only until sandboxed
Current app UID/process separation is insufficient for hostile or multi-tenant code. No untrusted-code or multi-tenant safety claims until distinct UID/OS confinement and adversarial tests pass.

### ADR-008 — Capacity is benchmark-derived
Report observed peak, sustainable safe capacity and current admission budget separately. No fixed visitor-count promise. Capacity percentages are shares of calibrated safe budget, not CPU utilization.

### ADR-009 — Automatic overload protection
Implement bounded queues, rate/concurrency limits, controlled 429/503, hysteresis and recovery states. Preserve management/logging reserve and avoid unbounded buffering.

### ADR-010 — Owner-configurable capacity profiles
Support presets and timed schedules, including 30% of safe capacity for three hours. Schedule expires and restores prior state; live device health may reduce the budget further.

### ADR-011 — Payment data stays with a PSP
Use hosted/tokenized checkout and store provider references/events, not raw cardholder data. No PAN/CVV/PIN storage; verify signed webhooks and reconcile against provider.

### ADR-012 — Conventional tamper-evident audit only
Use conventional append-only hash-chained records and signed checkpoints, with local verification and controlled export. Audit integrity must not introduce a network dependency into hosting.

### ADR-013 — Logs are bounded and redacted
Structured operational logs, rotation, retention caps and daily reports are core operations features. Logging cannot exhaust storage or leak credentials/payment data.

### ADR-014 — Honest status vocabulary
Distinguish implemented, automated-tested, emulator-verified, device-verified, externally-verified and production-approved. No production claims based on source presence or compile success.

### ADR-015 — Remote control-plane credentials exchange for short-lived sessions

**Date:** 2026-11-16

**Context:** Long-lived bootstrap and team-user bearer credentials were accepted directly by every remote management endpoint. That increased the impact of a credential copied into a browser or leaked from a client.

**Decision:** Accept long-lived control-plane credentials only at `POST /v1/admin/session`. Issue a random 256-bit, server-stored-hash session token with a 12-hour expiry. Require that session for every management operation. Store the originating user token hash with user sessions so token rotation invalidates sessions even across concurrent issuance; revoke user sessions transactionally on account revocation or token rotation. Support explicit session revocation and audit creation/revocation. Keep the browser's long-lived credential out of persistent browser storage and hold the short-lived session in memory.

**Alternatives:** Continue accepting long-lived bearer tokens directly; use stateless self-contained tokens; add refresh tokens immediately. Direct long-lived use was rejected. Stateless tokens complicate immediate revocation. Refresh tokens are deferred until device and visitor identity flows are designed together.

**Consequences:** Management clients must exchange credentials before API use and reconnect after session expiry. Device bearer credentials remain a separate device-authentication plane. Passkey-first Owner identity and site-scoped visitor authentication remain separate Gate 4 requirements.

### ADR-016 — First Relay delivery is bounded HTTP, not arbitrary TCP

**Date:** 2026-11-16

**Context:** The first production-shaped Relay path needs to prove device authentication, hostname ownership, routing, request bounds and revocation without prematurely shipping a general-purpose tunnel protocol.

**Decision:** Implement an initial authenticated outbound SSE request/response transport with strict request/response limits, per-device pending quotas, a 45-second deadline, DNS TXT hostname proof and device/site routing. Preserve the existing Cloudflare Tunnel as an independent option. Do not describe this HTTP path as arbitrary TCP, WebSocket or streaming Relay. Public TLS/certificate lifecycle, DNSSEC, WebSocket/streaming and external soak evidence remain separate acceptance requirements.

**Alternatives:** Keep Relay entirely planned; build a custom multiplexed TCP protocol immediately; use an unreviewed generic reverse tunnel. The bounded HTTP path was chosen as an incremental, explicitly limited foundation.

**Consequences:** The current implementation buffers each request/response up to 8 MiB and visitor authorization is enforced by the phone's Rust site policy after forwarding. It is source implementation only until external deployment and Android/native validation pass.

### ADR-017 — Do not move Chaquopy or Node into isolated UIDs without descriptor-rooted runtime proof

**Date:** 2026-11-16

**Context:** Android isolated services have distinct UIDs and no app permissions, while the current Python and Node workers depend on companion app-private project, HOME, cache and runtime paths. Android API 29 adds dynamic isolated-service binding; API 26–28 require fixed declared service slots for multiple simultaneous cells.

**Decision:** Keep Python/Node restricted to owner-trusted code while Gate 2 remains open. A per-project cell is not accepted until actual Python and Node apps run under distinct isolated UIDs with only project-scoped descriptor/broker access, listener-only network capabilities and verified egress denial on API 26/33/35. Do not weaken SELinux, grant app-data access or label a probe as production isolation.

**Alternatives:** Keep the shared companion UID permanently; assume isolated services can access companion files; replace runtimes with a limited WASM subset. None satisfies the current product contract without further proof.

**Consequences:** Gate 2 remains open. The current Binder/seccomp probe is feasibility evidence only. The Chaquopy AndroidPlatform uses Application context and app-private storage conventions; Node also uses companion private storage. Runtime-specific bootstrap and module/file access adapters are still required.

### ADR-018 — Keep the management API on a dedicated Host authority

**Date:** 2026-11-16

**Context:** The initial Relay visitor gateway and remote control/device APIs share the Axum process and listener. A visitor can choose an arbitrary HTTP path on a verified hostname; relying only on bearer authentication would leave management routes exposed on website hostnames.

**Decision:** Production deployment requires POCKETHOST_CONTROL_API_HOST. Admin session/management routes and device control/Relay stream routes accept requests only when the Host authority matches that dedicated hostname. Other verified hostnames reach only the visitor gateway fallback. The deployment workflow derives this API host from the dedicated control-plane domain; the similarly named GitHub Actions POCKETHOST_CONTROL_HOST remains the SSH host/IP variable.

**Alternatives:** Rely only on bearer authentication; run separate listener processes immediately; move to separate containers. Host authority guard was selected as an immediate defense-in-depth boundary while retaining the existing service topology.

**Consequences:** Cloudflare/Caddy ingress must route the dedicated management hostname and verified Relay visitor hostnames to the gateway. The visitor host cannot access /v1/admin or /v1/device paths. The dedicated management hostname remains a separately authenticated remote-control endpoint and should be protected by the chosen edge policy. Local development without the deployment environment variable retains an unguarded development mode; production Compose requires it.

## Changing a decision

Submit a PR updating this file, North Star, Master Plan and affected implementation docs. Explain why the old decision no longer serves the product, migration/security impact and acceptance tests. User-confirmed product intent takes precedence over contributor assumptions.
