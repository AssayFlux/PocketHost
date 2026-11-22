# PocketHost Direct Hosting Implementation Plan

Status: Architecture locked for Alpha
Branch: Alpha
Target: Self-contained Android website hosting without Termux or Cloudflare Tunnel.

## Locked architecture

- Public access: support both Cloudflare DNS-only direct-origin mode and Cloudflare-proxied mode. Neither mode uses Cloudflare Tunnel.
- Workloads: static websites, Node.js applications, and Python applications.
- Gateway: integrate the existing Native Relay TLS/ACME implementation as the candidate unified inbound gateway, after security and lifecycle review. Do not run two competing public listeners.
- Runtime worker remains a separate companion APK.
- Cloudflare DNS is managed through the API using least-privilege credentials stored with Android Keystore encryption.
- Alpha remains the development branch; no promotion to another branch is authorized.

## Non-negotiable network constraints

PocketHost cannot bypass carrier CGNAT, upstream firewalls, missing port forwarding, or unavailable inbound IPv6. DNS updates do not create inbound reachability. The app must report capability per address family and must never show "Public" merely because a local server is running.

Direct DNS-only mode requires a globally reachable origin and appropriate inbound routing. Cloudflare-proxied mode requires a supported Cloudflare proxy port and an origin path that is reachable from Cloudflare. The origin listener must use an Android-compatible unprivileged port; do not assume the app can bind 80/443.

Prefer ACME DNS-01 for certificate issuance so certificate creation does not depend on inbound port 80. Scope DNS credentials narrowly and protect private keys. Do not claim HTTPS is ready until external TLS verification succeeds.

## Phase 0 — Repair baseline and lock contracts

- Fix current Android compilation regression in Dynamic DNS navigation.
- Establish reproducible baseline builds for main APK and runtime companion APK.
- Document service ownership, ports, bind addresses, lifecycle and health contracts.
- Define typed states: server, runtime, local network, origin reachability, DNS, TLS, and public website health.
- Add regression tests for route registration, DNS record mapping, address validation and configuration persistence.

Acceptance:
- Android debug build compiles for supported ABIs.
- Rust unit tests and Kotlin compilation pass.
- No service claims public reachability based only on local readiness.
- Existing static hosting and runtime companion contracts remain intact.

## Phase 1 — Unified hosting lifecycle and gateway integration

- Create one authoritative site registry for static, Node and Python workloads.
- Create a process supervisor contract: start, stop, restart, readiness, health, exit status, logs and resource limits.
- Integrate Native Relay as the sole public HTTP/HTTPS gateway candidate.
- Route gateway hostnames to the existing Rust static engine or runtime upstreams.
- Keep management APIs and runtime control endpoints loopback-only and authenticated.
- Define explicit bind modes: loopback, LAN, and public-origin. Public-origin is enabled only after explicit user action and diagnostics.
- Prevent port conflicts and duplicate listeners.
- Add graceful shutdown and Android foreground-service lifecycle handling.

Acceptance:
- Multiple static sites route by Host header.
- Node and Python sites route independently.
- A failed upstream is reported unhealthy without taking down unrelated sites.
- Public gateway does not expose management endpoints.
- Service restart does not lose site configuration.

## Phase 2 — Direct publishing and HTTPS

- Add publishing profiles: DNS-only direct origin and Cloudflare-proxied origin.
- Associate domain, site, address family, proxy setting, origin port and TLS policy.
- Validate Cloudflare zone ownership and token permissions before mutation.
- Add safe DNS reconciliation with record ownership/conflict detection and rollback where possible.
- Implement ACME DNS-01 issuance and renewal; persist certificates/private keys securely.
- Configure HTTPS gateway on a supported unprivileged origin port.
- Support apex and www hostnames and multiple domains per site.
- Add origin health checks and external verification instructions.
- Clearly distinguish edge HTTPS from origin HTTPS in proxied mode.

Acceptance:
- A real external client reaches the selected website over HTTPS in each supported mode.
- Certificate chain and hostname validate.
- A failed DNS or certificate operation never reports Publish success.
- Tunnel is not required or started by this workflow.

## Phase 3 — IPv4/IPv6 diagnostics and Dynamic DNS

- Detect IPv4 and IPv6 independently using multiple sources.
- Validate address scope and reject loopback, private, link-local, multicast and invalid results for public publishing.
- Probe inbound reachability per family and detect likely CGNAT / blocked inbound access.
- Reconcile only records explicitly managed by PocketHost.
- Handle A and AAAA independently; preserve successful family updates when the other fails.
- Add WorkManager periodic sync (minimum 15-minute interval) and foreground-session checks.
- Add retry/backoff, rate limiting, last-success state, history and notifications.
- Verify authoritative/public DNS answers after updates.
- Never replace tunnel, relay, static or manually managed records without explicit ownership and confirmation.

Acceptance:
- IPv4 and IPv6 have independent capability and status.
- A-family failure does not hide AAAA success or vice versa.
- IP changes trigger safe record updates.
- CGNAT and blocked inbound ports produce actionable diagnostics rather than false-positive publishing.

## Phase 4 — Android UX and operations

- Reorganize navigation into Dashboard, Websites, Publish, Runtime Manager, Networking, Security, Activity and Settings.
- Keep one shared topbar and one scrollable content region per page.
- Build a guided Publish workflow: select site, add domain, choose DNS mode, validate network, configure DNS, provision TLS, verify public access.
- Separate server running, runtime healthy, DNS synchronized, origin reachable, TLS valid and public website healthy.
- Add traffic charts, logs, notifications, backup/restore and resource visibility.
- Remove misleading "coming soon" actions from primary workflows or clearly label them as unavailable.
- Isolate legacy Tunnel features from the direct-hosting path; do not silently delete existing functionality.

Acceptance:
- Small-screen layouts scroll correctly with no content hidden under system bars/topbar.
- Every visible control has a working action or an explicit unavailable state.
- Dashboard status is evidence-based.

## Phase 5 — Verification and release gate

- Build and install main APK and runtime companion APK separately.
- Verify artifact hashes and ABI coverage.
- Run unit, instrumentation, emulator and native tests.
- Test physical Android devices and Android background restrictions.
- Test static, Node and Python workloads.
- Test multiple hostnames and simultaneous sites.
- Test DNS-only and proxied Cloudflare modes.
- Test IPv4-only, IPv6-only, dual-stack, CGNAT and blocked-port scenarios.
- Test DNS-01 issuance, renewal, expired/invalid credentials and failure recovery.
- Test reboot, process death, network changes, battery restrictions and clean uninstall.
- Test from a separate external network, not only localhost or the same Wi-Fi.
- Publish APKs only after all critical gates pass; report any environment-dependent tests explicitly.

## Security requirements

- Cloudflare tokens use least privilege and are never logged.
- Runtime management and file APIs are not publicly exposed.
- Public gateway uses request limits, timeouts and safe header handling.
- Website paths are canonicalized and protected against traversal and symlink escapes.
- TLS private keys are protected at rest.
- Logs redact tokens, passwords, cookies and private key material.
- DNS changes are scoped to explicit zone/record ownership.
- Public binding requires explicit user consent and a clear warning.

## Execution rules

- Work only on Alpha.
- Make small, reviewable commits with a build/test checkpoint after each coherent unit.
- Do not claim public hosting is complete based on source compilation or localhost tests.
- Do not deliver an APK until the exact artifact from the final commit is downloaded and verified.
