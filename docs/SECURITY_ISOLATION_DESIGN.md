# Pocket Host — Security and Isolation Design

## Security objective

Pocket Host must keep website visitors, owner controls, website files, optional runtimes, worker devices and edge hardware in separate trust domains. Local hosting should remain useful without remote infrastructure.

## Release placement

- v0.1.0: Android static serving, safe import/activation, LAN opt-in, Cloudflare Tunnel, AssayFlux Relay, custom-domain HTTP/HTTPS, visitor-route isolation, website backup/restore and Android permission/lifecycle handling.
- v0.1.1: Workbench workspace isolation, preview, build boundaries, publishing approval and optional runtime hardening.
- v0.2.0: device identity, enrollment, authenticated transport, least-privilege role grants, agents, control plane and multi-host Relay scaling/advanced failover.
- Later: further runtime packages, OS agents, SBC and MCU profiles.

## v0.1.0 website safety

- Import into an app-private staging directory; validate before activation.
- Enforce independent 500,000,000-byte ZIP input and expanded-site limits.
- Enforce 5,000 files, 10,000 entries and 32 directory levels.
- Reject absolute paths, traversal, unsafe names, symlinks and unsupported filesystem entries.
- Exclude private key and credential material, including known SSH key files, .env files, PEM/key/P12/PFX files and internal Pocket Host metadata.
- Preserve the current live website until the replacement is validated and activated.
- Recover the last-known-good site after interrupted activation.
- Keep Rust static response cap at 500,000,000 bytes (500 MB decimal) per file unless separately reviewed and tested.
- Bind to loopback by default. LAN binding is explicit opt-in and exposes the website listener to the local network.
- Never expose management API or device-control endpoints through the visitor listener.
- Target: Cloudflare Tunnel and Relay host-ingress credentials are separate from owner/device-management credentials; redact tokens from logs, diagnostics and backups. Android now stores Relay endpoint/token separately in Android Keystore-backed `RelayCredentialStore`; node-side credential issuance, revocation and public ingress acceptance remain open.
- Require domain ownership proof before Relay hostname activation; revoke hostname bindings and host sessions promptly. The current DNS TXT/route foundation exists in the combined control-plane service; independent Termux Relay Node setup is not implemented.
- Public HTTPS must use a valid edge certificate. HTTP redirects to HTTPS by default. An explicit plain-HTTP mode is a target only; current Relay Caddy configuration has no plain-HTTP content opt-in.
- Relay visitor traffic is bounded by request/body/queue/time limits and rate/abuse controls; it forwards only to the selected website origin. The current visitor path is bounded HTTP request/response, not arbitrary TCP/WebSocket.
- Encrypt full-app backups and require a strong passphrase. The current static-site backup cap is 500,000,000 bytes, matching the importer expanded-content cap; Python/Node roots remain separately capped at 50/100 MiB, with 650,000,000 bytes total uncompressed and 680,000,000 bytes encrypted. Enforce storage headroom and retain near-limit/recovery tests.

## Android platform safety

Test foreground-service restrictions, notification permission, storage provider behavior, network permission, Doze, battery saver, screen-off, app process death, reboot, thermal state and low-storage behavior. Explain Android/OEM limits; do not promise 24/7 hosting on every phone.

## v0.1.1 Workbench/runtime safety

- Draft workspace and production release are separate roots and permissions.
- Preview is isolated and cannot read owner secrets or modify production.
- Build inputs, outputs, CPU, memory, time, disk and network are bounded.
- Publishing stages an immutable artifact, verifies digest and paths, requires explicit owner approval and activates atomically.
- Rollback selects a previously verified immutable release.
- Python and Node currently share the runtime companion UID. This is not per-project OS isolation. Use trusted owner-controlled code until adversarial isolation and egress tests pass.
- A runtime failure must not stop static hosting or unrelated components.

## v0.2.0 device and fleet safety

- Distinct device identity, owner proof, single-use short-lived challenge and explicit role approval.
- Default-deny capability policy; unknown roles/capabilities are rejected.
- Grants bind owner, device, role, capabilities, budgets, expiry and generation.
- Authenticated transport, replay protection, revocation and idempotent jobs.
- No arbitrary shell or unrestricted filesystem role.
- Multi-host scheduling, regional gateways and automated failover must preserve visitor/management separation.
- Provider credentials are separate, short-lived/rotatable and never logged.

## Threat tests

Archive traversal, duplicate path collision, symlink escape, oversized compressed and expanded content, sensitive-file import, storage exhaustion, malicious MIME/path, LAN exposure, management-route pivot, backup tampering, preview escape, cross-site access, runtime egress, device cloning, replay, role escalation, stolen credential, job replay and provider outage.

## Acceptance

Security and privacy are mandatory G1/G8 items for each release. See [RELEASE_GATES.md](RELEASE_GATES.md). A threat model is not a substitute for test evidence.
