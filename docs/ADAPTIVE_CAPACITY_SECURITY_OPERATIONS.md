# PocketHost — Adaptive Capacity, Security, Payments and Operations

**Status: partially implemented; not production approved.** The static-route Capacity Lab, versioned low-frequency device telemetry, CPU/memory/storage/battery/thermal health-based concurrency derating, concurrency admission ceiling, temporary capacity schedules and basic daily aggregate reports now have source implementations. The full Rust-native rate/fairness/queue governor, broad historical telemetry, durable financial reporting, runtime sandbox, PSP payment flow and tamper-evident audit remain unimplemented. The first device-side health state machine now derates admission and suspends optional runtimes under critical pressure. A feature is not complete until code, automated tests, device evidence and security review exist.

## 1. Product objective

Make a low-cost Android phone a predictable, self-protecting website origin. PocketHost must discover the device's real capabilities, measure sustainable throughput, enforce an owner-selected capacity budget, protect the phone from overload, preserve service during graceful degradation, and give the owner understandable operational and financial records.

The phone remains the website origin. Rust serves and routes visitor traffic. The optional remote control plane is for management only. Cloudflare Tunnel is an outbound connectivity layer, not a resource governor or visitor-authentication substitute.

## 2. Adaptive hardware inventory and live telemetry

Create a versioned DeviceProfile and continuous TelemetrySnapshot collected by Android/Kotlin and Rust:

- Device manufacturer/model, Android API/build, ABI, CPU core count and available frequency information where Android exposes it.
- Total/available RAM, app process PSS/RSS, native heap, Java heap, Node V8 heap, runtime process counts.
- Internal storage total/free, app data/cache usage, site and backup footprint.
- Battery percentage, charging state, battery temperature and Android thermal status.
- CPU load and per-engine request/connection/queue metrics, sampled without expensive polling.
- Network type, link state, observed throughput/latency and tunnel health; never infer internet bandwidth from link type alone.
- Runtime state (Rust, Python, Node, cloudflared), uptime, restarts, crashes and recovery attempts.

All metrics carry timestamp, source, sampling interval and validity. Missing or permission-restricted values are unknown, never fabricated. Store high-frequency telemetry for a bounded rolling window and aggregate longer-term data to avoid filling the phone.

## 3. Capacity calibration and safe operating envelope

A single theoretical requests-per-second figure is misleading. Capacity depends on route mix, response size, cache hit rate, TLS/tunnel overhead, runtime type, network and thermal state.

Implement a user-initiated Capacity Lab:
1. Capture device baseline and thermal/battery state.
2. Run isolated local loopback workloads against the Rust engine: static cached, static uncached, small/large responses, concurrent keep-alive, and optional trusted Python/Node test routes.
3. Increase concurrency in controlled steps; record throughput, p50/p95/p99 latency, errors, queue depth, memory growth, CPU, temperature and energy/battery impact.
4. Stop immediately on safety thresholds, thermal escalation, low memory, user cancellation or test timeout.
5. Repeat enough samples to calculate stable ranges; discard noisy or interrupted runs.
6. Persist benchmark version, app build, device/OS identity, workload profile, test conditions and results. Invalidate/re-run after meaningful app/runtime/device changes.

Present three values, not one false guarantee:
- Observed peak: best measured result in the specific test.
- Sustainable safe capacity: conservative rate/concurrency sustained within latency, memory and thermal limits.
- Current safe admission budget: sustainable capacity further reduced by current device health and the owner's selected capacity percentage.

Display a chart with measured throughput and p95 latency against concurrency, plus CPU, memory and thermal trends. Shade the safe operating region and mark the tested overload boundary. Show date, workload, test conditions and confidence/sample count. Label estimates as benchmark-specific, not an SLA or guarantee for arbitrary websites. Never extrapolate an untested workload as a proven maximum.

## 4. Adaptive Resource Governor and overload protection

Implement a central Rust ResourceGovernor fed by Android telemetry and engine metrics. Explicit state machine:

- NORMAL: admit within configured budget.
- CONSTRAINED: lower concurrency/rate, queue briefly, prioritize health/management and static cached traffic.
- PROTECTIVE: stop admitting new expensive work; serve controlled 503/429 with Retry-After where appropriate; allow in-flight requests a bounded drain period.
- RECOVERY: cautiously ramp limits after health stabilizes; use hysteresis and cooldown to prevent oscillation.
- EMERGENCY: stop optional runtimes and/or hosting gracefully when Android reports critical thermal state, critically low memory/storage, or repeated crash loops. Preserve logs and recovery reason.

Controls and enforcement:
- Global and per-site request-rate and concurrent-connection limits.
- Bounded queues with explicit overflow responses; never unbounded buffering.
- Per-route/body/header limits, request and idle timeouts, maximum response size where appropriate.
- Per-runtime worker, heap and process limits; runtime start/stop budgets.
- Per-client/IP limits only when a trustworthy client address is available; correctly handle trusted proxy chains.
- Fair scheduling across sites; static cached assets should not be starved by a slow dynamic app.
- Circuit breakers for unhealthy Python/Node upstreams; bounded retries only for safe/idempotent operations.
- Graceful shutdown, in-flight drain, restart backoff and recovery after process death.
- Emergency reserve for management, telemetry and log flushing.
- Never silently drop payment callbacks or critical durable writes: persist/queue them safely or return a retryable failure.

Thresholds are configurable, have safe defaults, and include hysteresis. The governor fails closed for unknown critical telemetry: reduce admission rather than assume spare capacity. Do not promise Android can prevent OS-level process kills.

## 5. Owner-configurable capacity schedules

Provide a Capacity Profiles screen with:
- Maximum capacity percentage (for example, 30%).
- Scheduled profile and duration (for example, Development profile: 30% for 3 hours).
- Start now / end now, repeat schedule, timezone, weekday selection and expiry.
- Per-site budgets, static/dynamic traffic weighting and runtime enablement.
- Maximum concurrent requests, requests/second, queue length and response timeout.
- Memory reserve, disk reserve, thermal ceiling, battery minimum and charging-only options.
- Development, Balanced, Performance, Battery Saver and Custom presets.
- Preview of effective limits before applying; warn when requested limits exceed the device's safe envelope.
- Manual override with expiry and a visible reason.
- Remote profile changes require fresh authorization, audit record, version check and rollback on invalid configuration.

Capacity percentage is a share of the calibrated safe budget, not a percentage of all theoretical hardware. Thus 30% means 30% of the currently safe measured budget, further reduced by live thermal/memory conditions. It does not mean 30% CPU utilization and cannot force Android to reserve CPU time.

## 6. Runtime isolation and sandboxing

This is a release-blocking security workstream for running untrusted or third-party application code.

- Prefer separate Android UIDs per project using a separately installed/managed execution package where feasible; ordinary Android app processes sharing one UID are not a security boundary.
- Evaluate a hardened isolated process/service or OS-supported sandbox with a distinct UID, private filesystem, no access to PocketHost credentials, and explicit IPC capabilities.
- If strong per-project UID isolation cannot be delivered within Android packaging constraints, limit the product to trusted user-owned code and do not market it as multi-tenant or hostile-code safe.
- Rust remains the only public ingress. Python/Node bind loopback only; deny direct public/LAN listeners.
- Network egress policy: block access to loopback management APIs, link-local/cloud metadata endpoints, private network ranges and other sites by default; add explicit owner allowlists and DNS-rebinding-safe resolution checks.
- Restrict filesystem access to the project workspace and dedicated temp/cache areas; never expose app secrets, signing keys, payment keys or other sites' data.
- Enforce CPU, memory, file, process, socket, open-file, disk and execution-time quotas at OS boundary where possible. Application-level V8 heap limits are not hard RSS/CPU quotas.
- Remove arbitrary shell execution and package lifecycle scripts; dependencies must be prebundled, pinned and scanned.
- Test path traversal, symlinks, archive bombs, Unicode/path normalization, TOCTOU races, SSRF, DNS rebinding, request smuggling, decompression bombs and cross-project access.
- Provide explicit trusted-code acknowledgement until isolation has been independently reviewed.

## 7. Payment security and financial records

PocketHost must not become a card processor or store raw card numbers, CVV, PINs, payment credentials or unencrypted payment secrets. Use a PCI-compliant payment service provider (PSP) and hosted/tokenized checkout. Payment processing scope and PCI obligations must be reviewed with the PSP and a qualified assessor before launch.

Implement a provider-neutral payment integration boundary:
- Provider adapter for checkout-session creation, status retrieval, refunds and dispute events.
- Server-side secret storage using Android Keystore for local credentials and a proper secrets manager for any remote control-plane deployment. Never put secret keys in website roots, client JavaScript, logs, source code or backups without envelope encryption.
- Verify webhook signatures against the raw request body, enforce timestamp tolerance and rotate secrets.
- Idempotency keys and unique provider event IDs; replay protection; duplicate/out-of-order event handling.
- Explicit payment state machine: created, pending, authorized, captured/paid, failed, cancelled, partially refunded, refunded, disputed/chargeback.
- Reconcile provider transactions against local records; never trust a client redirect as proof of payment.
- Separate test/live credentials and environments; least-privilege roles; dual approval for sensitive refunds/config changes where applicable.
- Do not store cardholder data. Store only provider IDs, amount in integer minor units, currency, status, timestamps, order reference, redacted customer reference where necessary, event IDs, signature-verification outcome and reconciliation metadata.
- Encrypt payment records and backups at rest; TLS in transit; key rotation and tested restore procedures.
- Define retention, deletion, data residency, tax/invoice and dispute evidence policies with legal/accounting review for launch jurisdictions.
- Fail safely if the phone is offline: provider webhook retries must be acknowledged only after durable event persistence; reconcile missed events after reconnect. Do not promise payment availability when the phone or network is offline.

## 8. Conventional tamper-evident audit

Use a conventional append-only audit log as the source of truth. Each event includes event ID, sequence, UTC timestamp, actor/device, action, target, outcome, reason, correlation ID and redacted metadata. Link records using a cryptographic hash chain; periodically sign checkpoints with a key protected by Android Keystore or a remote KMS. Detect missing, reordered or modified records during verification.


## 9. Operational logs, daily reports and retention

Create structured, redacted event streams:
- HTTP access summaries (configurable sampling), status codes, latency, bytes, route/site and request ID.
- Runtime start/stop/crash/restart and resource-limit events.
- Device health, thermal, memory, storage and network changes.
- Tunnel connect/disconnect/reconnect and public health-check results.
- Configuration edits, profile schedule changes, pairing, login, session revocation and remote commands.
- Deployment/import/rollback, backup/restore, security alerts and payment lifecycle events.

Provide live log viewer, filters, search, export, per-site views, alert thresholds and configurable retention by size/time. Rotate logs, compress older files, cap disk use, reserve free space and never allow logs to exhaust storage. Redact authorization headers, cookies, tokens, query secrets, payment data and sensitive request bodies before persistence.

Generate a daily report in the owner's timezone containing uptime, requests, bandwidth, latency percentiles, error rates, peak concurrency, resource trends, thermal events, restarts, tunnel availability, deployments, security events, payment totals by status/currency, refunds/disputes, reconciliation differences, backup state and capacity profile changes. Report generation must survive app restarts and support export; notify only with user permission. Payment reports must be reconciled to the PSP and distinguish gross, fees, refunds and net values.

## 10. Reliability and website performance

- Static asset cache with correct ETag/Last-Modified, conditional requests, range support, compression negotiation and immutable caching only for fingerprinted assets.
- HTTP/2/HTTP/3 only where the actual Android/tunnel stack supports it; never claim protocol support without end-to-end tests.
- Keep-alive and bounded connection pools, streaming responses, efficient MIME lookup and zero-copy/file-channel paths where practical.
- Configurable gzip/Brotli only after CPU/size benchmarks; avoid compressing already-compressed media.
- Health/readiness/liveness checks separated from public site routes.
- Per-site isolation of failures, graceful deploy swaps, rollback and backup verification.
- WebSocket/streaming support only with bounded sessions, idle timeout and resource accounting.
- CDN/cache integration guidance for large static assets; local phone storage is not a substitute for durable object storage.
- Monitor tunnel origin health, DNS, certificate/HTTPS behavior and external synthetic checks.
- Document phone battery, thermal throttling, carrier NAT, data caps, Android OEM background restrictions and network outages as availability limits.

## 11. Dashboard and customization

Add a Capacity & Health dashboard:
- Live CPU/RAM/storage/battery/thermal/network and runtime status.
- Safe capacity gauge with current admission budget, observed peak and sustainable benchmark.
- Historical charts for RPS, concurrency, p95/p99 latency, errors, queue depth, memory and temperature.
- Clear NORMAL / CONSTRAINED / PROTECTIVE / RECOVERY / EMERGENCY state and explanation.
- Per-site traffic and resource usage, top routes, slow requests and upstream failures.
- One-click capacity profile selection and schedule management.
- Alerts for low storage, high thermal state, memory pressure, crash loops, tunnel outages, payment webhook failures and reconciliation mismatches.
- Advanced settings grouped into safe defaults and expert controls, with validation, preview, audit and reset-to-safe-defaults.

## 12. Suggested data model

Core records: DeviceProfile, TelemetrySnapshot, BenchmarkRun, CapacityEnvelope, CapacityProfile, CapacitySchedule, GovernorStateTransition, SiteResourceBudget, RuntimeSandboxPolicy, AuditEvent, AuditCheckpoint, PaymentOrderReference, PaymentProviderEvent, PaymentReconciliation, DailyOperationsReport, AlertRule and AlertDelivery.

Use monotonic time for durations and UTC wall-clock timestamps for durable events. Use integer minor currency units and ISO currency codes. Apply schema versions and migration tests. Do not duplicate cardholder data.

## 13. Delivery plan and release gates

### Phase A — Observe safely
- Device profile, telemetry sampler, metrics schema, bounded storage and redaction.
- Rust engine metrics and Capacity & Health dashboard.
- Daily operations report and export.
- Tests for metric correctness, privacy, rotation and restart recovery.

### Phase B — Measure capacity
- Capacity Lab workload generator and benchmark persistence.
- Per-device sustainable envelope and graph.
- Repeatability tests and benchmark invalidation.
- Publish methodology and limitations.

### Phase C — Protect service
- ResourceGovernor state machine, bounded queues, rate/concurrency limits, graceful drain and circuit breakers.
- Capacity schedules, custom presets and 30%-for-3-hours workflow.
- Fault-injection, overload and recovery tests on API 26+ and representative 4 GB ARMv7/ARM64 phones.

### Phase D — Secure runtimes
- Threat model, separate UID/sandbox feasibility decision, filesystem/network restrictions and OS-enforced quotas.
- Adversarial importer, SSRF, DNS rebinding and cross-project tests.
- No production claim until independent review passes.

### Phase E — Payments and audit
- PSP adapter, webhook verification, idempotency, reconciliation, encrypted ledger, audit chain and backup/restore.
- Local checkpoint verification and signed checkpoint export.
- Security assessment and legal/PCI scope review before accepting live payments.

### Phase F — Operational maturity
- Alerting, remote management policy, RBAC/MFA, command audit, recovery, performance tuning, synthetic external checks and release documentation.

## 14. Mandatory acceptance tests

- Capacity benchmark stops safely on thermal, memory, battery, timeout and user-cancel triggers.
- Governor never exceeds configured budgets; queue size remains bounded under overload.
- Overload returns documented 429/503 responses and recovers without restart loops.
- 30% capacity profile runs for three hours, expires on schedule and restores the previous profile.
- Critical management/logging path remains available under visitor overload.
- Runtime crash or OOM does not corrupt static hosting or other sites.
- No project can read another project's files, app secrets, management API or payment credentials.
- SSRF/DNS rebinding and private-address egress attempts are blocked by policy.
- Payment webhook invalid signature, stale timestamp, duplicate, replay and out-of-order events are safely handled.
- Payment reconciliation detects missing/duplicate/mismatched events.
- Audit chain tampering, deletion and reordering are detected; signed checkpoints can be verified offline.
- Logs redact secrets and payment data; rotation cannot exhaust storage; daily report is reproducible.
- Backup restore verifies encryption, integrity, schema migration and payment-ledger consistency.
- External HTTPS checks confirm public hostnames route only to website origins, never management endpoints.
- Tests pass on supported API levels and physical ARMv7/ARM64 devices; release notes distinguish verified capabilities from unsupported features.

## 15. Product promise and honest limits

PocketHost should make self-hosting affordable and understandable, not claim that a smartphone equals a cloud region. The product promise is transparent capacity, automatic protection, secure defaults, portability, clear diagnostics and owner control. Never promise a fixed visitor count independent of website workload, unlimited uptime, full hostile-code isolation, PCI compliance, or guaranteed payment processing on a disconnected phone.


## Capacity Lab measurement detail (2026-11-15)

The Capacity Lab performs a bounded loopback static GET sweep against the active Rust origin. Each concurrency level records successful requests, errors, bytes served, requests per second, bytes per second, average/p50/p95/maximum latency and elapsed time. Requests use unique query strings and no-cache headers. The sweep retains its cancellation, low-memory, severe-thermal and low-battery stop conditions and only calibrates when the zero-error and p95-latency threshold is met. It remains a static-origin microbenchmark, not a representative multi-workload or long-duration thermal benchmark.


## Rust admission queue (2026-11-15)

The Rust listener reserves active connection slots atomically under the configured ceiling (hard maximum 16). When all active slots are occupied, it accepts at most 32 additional sockets into a bounded queue and waits up to 200 ms for a slot. Queue overflow or timeout receives HTTP 503 with Retry-After: 2. Queue depth, active connections and rejected connections are exposed through the native metrics, Android dashboard and authenticated management API. This is global admission control; per-site fairness and per-client request-rate limits are not implemented by this queue.
