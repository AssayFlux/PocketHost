# Pocket Host — Learn and Quick Start

This guide is also available inside the Android app under **Learn**. It describes the current implementation and calls out limitations rather than promising unsupported behavior.

## Quick start
1. Open **Files** and import a static website folder or ZIP archive.
2. Confirm that the site contains an entry page such as `index.html`.
3. Open **Dashboard** and start hosting.
4. Use **Preview site** to verify the local website.
5. Configure the existing Cloudflare Tunnel and Public Hostname to reach the Rust website listener when public access is required.

## Local, LAN and public access
- Loopback access is available only on the phone.
- LAN access requires enabling the LAN option and restarting hosting after changing it. Devices must be on a reachable local network.
- Public access uses the owner's existing Cloudflare Tunnel and hostname. The hostname must route to the Rust website listener, never the management API.
- Cloudflare edge HTTPS does not itself authenticate website visitors. Use Cloudflare Access for private sites.

## Dashboard and capacity
The dashboard reports current hosting state, static-engine traffic totals, uptime, device telemetry and admission budget. Metrics are not necessarily inclusive of Python, Node or tunnel traffic.

Capacity Lab performs a bounded local static GET sweep. Run it on a cool, adequately charged phone. Its safe concurrency ceiling is workload/device-specific. A percentage profile controls admission concurrency, not CPU use or a guaranteed visitor count.

## Python and Node runtimes
These runtimes are for trusted code only. Python currently supports one WSGI application. Node runs one active application with prebundled dependencies. Both run under the Pocket Host app identity; neither is an OS-enforced sandbox. Do not run untrusted projects.

## Activity and diagnostics
Use Activity to inspect operational events, filter errors, search and export redacted logs or diagnostics. Review exports before sharing and remove private information that should not leave the device.

## Backup and restore
The current encrypted backup covers static website content. It does not include runtime projects, credentials, all settings or remote-control state. Keep the passphrase separately; it cannot be recovered.

## Device safety and availability
Keep the phone ventilated, monitor battery/temperature/storage and use conservative capacity settings. Android Doze, OEM power policies, process termination and network changes can interrupt hosting. Pocket Host does not guarantee 24/7 uptime or unlimited traffic.

## Known product limitations
- Built-in visitor password protection is not currently implemented.
- Python/Node hostile-code isolation is not implemented.
- Full configuration and runtime backup is not implemented.
- Local management sessions are not a replacement for Owner/Operator account management.
- Physical-device, screen-size, large-font and keyboard acceptance must be completed before a production release.


## Host Owner and Operator accounts

1. Pair a browser with the phone only when you can approve the six-digit request in the Android app.
2. Use the first approved Owner session to create the Owner username and a unique password of at least 12 characters.
3. Use the Owner credentials for subsequent sign-ins.
4. From the Owner session, create or revoke Operator accounts.
5. Operators can inspect data, operate sites and adjust capacity. Owner-only settings and account administration are rejected by the device API.

Password verifiers use PBKDF2-HMAC-SHA256; the account registry is encrypted with Android Keystore AES-GCM. Browser tokens are tab-scoped; device sessions expire after 12 hours and are revocable.

Biometric unlock is not yet implemented. Never expose the management API through the public website hostname.


## Current repository evidence snapshot — 2026-11-15

This snapshot is anchored to main commit `671f3f731f4bf093449a95e285327115f41bb75b`. Re-check the linked workflow runs before using this document as a release approval; statuses can change after this snapshot.

### Merged implementation

- PR [#16 — interactive Learn section and card-first navigation](https://github.com/AssayFlux/PocketHost/pull/16) is merged. The app/console include the Learn experience and an initial measured-capacity visualization. The chart is based on recorded static benchmark samples; it is not synthetic live telemetry.
- PR [#17 — Owner and Operator host accounts](https://github.com/AssayFlux/PocketHost/pull/17) is merged. The source includes an encrypted app-private account registry, password verifiers, Owner/Operator authorization, account revocation and console sign-in/bootstrap flows. The PR describes instrumentation coverage, but this snapshot does not assert that the latest main-branch emulator workflow has passed.
- The app has a Learn destination and documentation at [LEARN_GUIDE.md](LEARN_GUIDE.md). The UI remains an initial implementation requiring responsive and device acceptance.

### Exact CI evidence at review time

- Main commit: `671f3f731f4bf093449a95e285327115f41bb75b`.
- [Quality workflow 43699334752](https://github.com/AssayFlux/PocketHost/actions/runs/43699334752): completed successfully. Its six source-quality jobs include Rust, control plane, Python, Node, TypeScript console and Android source compilation.
- [Android integration workflow 43699334785](https://github.com/AssayFlux/PocketHost/actions/runs/43699334785): pending when inspected. Do not infer APK assembly or emulator instrumentation success from the quality workflow.

### Remaining release gates — not completed by these merges

1. **Android acceptance:** finish integration workflow; verify APK packaging/signatures/ABI contents; pass API 26/33/35 instrumentation on the exact release SHA; test real ARMv7/ARM64 phones, foreground service, Doze, reboot, battery/thermal behavior, keyboard, accessibility, landscape and responsive layouts.
2. **Native authentication polish:** Android BiometricPrompt/Keystore unlock, secure Owner recovery, complete native login/logout/expired/revoked-session UX and end-to-end instrumentation remain unverified or incomplete. Console sign-in and API role enforcement do not by themselves prove native biometric login.
3. **Runtime isolation:** Python and Node execute under the Pocket Host app UID. The Node process and V8 heap limit are not a sandbox. Keep optional runtimes restricted to trusted code until OS-enforced filesystem/network isolation and enforceable resource quotas pass adversarial review.
4. **Cloudflare:** configure/reuse the owner's existing named tunnel on a physical device and verify the Public Hostname serves the intended site over HTTPS from an external network. The public hostname must never target the loopback management API.
5. **Capacity:** current benchmark and admission controls are first implementations. Complete representative cached/uncached and payload-size workloads, Python/Node trusted workload measurements, throughput and p50/p95/p99, Rust-native rate/fairness/bounded-queue controls, graceful drain, long-duration thermal tests and schedule restart/expiry acceptance.
6. **Operations:** extend backup beyond static-site scope; test restore and interruption; complete request-level redacted logs, durable/tamper-evident audit verification, storage-pressure behavior and reproducible daily reports.
7. **Optional remote management:** deploy the control plane on a trusted host, pair the real phone, then test desired configuration, leased commands, acknowledgements, revocation and recovery. Source code alone is not deployment evidence.
8. **Payments:** no live payment processing is implemented/approved. If enabled for launch, select a PSP and implement hosted/tokenized checkout, signed/replay-protected webhooks, idempotent durable ledger, reconciliation, encrypted backups and qualified security review. Never store PAN/CVV/PIN.
9. **Release:** configure production signing, verify upgrade paths and checksums, review dependencies/secrets and resolve all critical/high security findings.

### Product boundaries

- Static hosting remains the primary independent feature; optional runtimes, payments and remote management must not be prerequisites.
- Android foreground services improve continuity but cannot guarantee 24/7 uptime or override OEM/OS battery policies.
- Cloudflare Tunnel connectivity is not visitor authentication; use Cloudflare Access for private sites.
- Do not claim universal visitor counts, complete sandboxing, payment compliance, deployed remote control, production readiness or a signed release without the corresponding evidence.
- Blockchain technology remains excluded from the product and codebase. Conventional local audit integrity, if implemented, is not blockchain.

**Release decision:** integrated development build; not production approved. This status changes only when the relevant code, tests, device/network evidence and security review are recorded against the exact release commit.
