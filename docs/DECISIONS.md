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

## Changing a decision

Submit a PR updating this file, North Star, Master Plan and affected implementation docs. Explain why the old decision no longer serves the product, migration/security impact and acceptance tests. User-confirmed product intent takes precedence over contributor assumptions.
