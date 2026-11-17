# Pocket Host — Security and Isolation Design

## Security objective

Pocket Host must keep website visitors, owner controls, website files, optional runtimes, worker devices and edge hardware in separate trust domains. Local hosting should remain useful without remote infrastructure.

## Release placement

- v0.1.0: Android static serving, safe import/activation, LAN opt-in, website backup/restore and Android permission/lifecycle handling.
- v0.1.1: Workbench workspace isolation, preview, build boundaries, publishing approval and optional runtime hardening.
- v0.2.0: device identity, enrollment, authenticated transport, least-privilege role grants, agents, control plane and public ingress.
- Later: further runtime packages, OS agents, SBC and MCU profiles.

## v0.1.0 website safety

- Import into an app-private staging directory; validate before activation.
- Enforce independent 500,000,000-byte ZIP input and expanded-site limits.
- Enforce 5,000 files, 10,000 entries and 32 directory levels.
- Reject absolute paths, traversal, unsafe names, symlinks and unsupported filesystem entries.
- Exclude private key and credential material, including known SSH key files, .env files, PEM/key/P12/PFX files and internal Pocket Host metadata.
- Preserve the current live website until the replacement is validated and activated.
- Recover the last-known-good site after interrupted activation.
- Keep Rust static response cap at 50 MiB per file unless separately reviewed and tested.
- Bind to loopback by default. LAN binding is explicit opt-in and exposes the website listener to the local network.
- Never expose management API or device-control endpoints through the visitor listener.
- Encrypt full-app backups and require a strong passphrase. Keep the current 50 MiB static-site/200 MiB total backup limits distinct from the 500 MB import quota; fail safely and disclose the mismatch.

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

## v0.2.0 device and ingress safety

- Distinct device identity, owner proof, single-use short-lived challenge and explicit role approval.
- Default-deny capability policy; unknown roles/capabilities are rejected.
- Grants bind owner, device, role, capabilities, budgets, expiry and generation.
- Authenticated transport, replay protection, revocation and idempotent jobs.
- No arbitrary shell or unrestricted filesystem role.
- Visitor ingress reaches website routes only, never management, runtime broker or MCU control.
- Provider credentials are separate, short-lived/rotatable and never logged.

## Threat tests

Archive traversal, duplicate path collision, symlink escape, oversized compressed and expanded content, sensitive-file import, storage exhaustion, malicious MIME/path, LAN exposure, management-route pivot, backup tampering, preview escape, cross-site access, runtime egress, device cloning, replay, role escalation, stolen credential, job replay and provider outage.

## Acceptance

Security and privacy are mandatory G1/G8 items for each release. See [RELEASE_GATES.md](RELEASE_GATES.md). A threat model is not a substitute for test evidence.
