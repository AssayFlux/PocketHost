# RUNTIME COMPANION — HOST, BROKER AND EXECUTION-CELL CONTRACT

**Current state:** companion package and Binder integration exist in source. Production per-project isolated cells are not implemented/accepted.  
**Packages:** host `com.assayflux.pockethost`; companion `com.assayflux.pockethost.worker`.

## 1. Why the companion exists

Android package UIDs protect host-app-private data from code running under a different package UID. Pocket Host moved CPython/Chaquopy and Node mobile native components out of the host APK into a companion application. This is a host-versus-companion boundary only.

## 2. Current source placement

| Component | Package/process | Responsibility |
|---|---|---|
| Rust HTTP engine | Host UID | Website ingress, static serving, reverse proxy |
| Management API | Host UID | Authenticated loopback administration |
| Companion service | Worker UID, `:runtime_worker` | Binder control, project transfer, Python lifecycle |
| Node service | Worker UID, `:node` | Node mobile 24.22.1 runtime |

Python and Node processes share the companion application UID. They are not separate per-project identities.

## 3. Binder contract

- Signature-level `BIND_RUNTIME_WORKER` permission.
- Explicit binding to companion service.
- Verify calling UID/package and matching app signing certificate.
- Negotiate protocol version before operations.
- Keep operations bounded: transfer, lifecycle, status, metrics and shutdown.
- Handle binder death, rebind, stale companion version and process restart.
- Never send large project payloads in Binder byte arrays; use bounded file descriptors.

Host and companion APKs must be signed with the same certificate.

## 4. Project transfer

The host creates a bounded archive and transfers a read-only FD. Companion validates entries and extracts into staged companion-owned storage before activation. Current source bounds include archive entry count, per-file and total expanded size limits, traversal rejection and common credential-path filtering. These are import hardening, not a complete secret scanner or sandbox.

Replacement must be atomic where supported; preserve old project on failed import. Do not replace active roots. Log a redacted reason and keep recovery material bounded.

## 5. Current security limitations

- All companion projects share one UID and app-private storage.
- Python multi-site hosting can load several projects into one interpreter.
- Node shares the same companion UID.
- Companion has INTERNET permission; no OS-enforced per-project egress denial.
- Loopback services are reachable at network layer and require authentication.
- Python loopback binding and Node listener preload do not constrain native/outbound sockets.

Only owner-controlled trusted code is permitted. No visitor-submitted or hostile code.

## 6. Target broker/cell architecture

The companion becomes a trusted broker only. It must never import customer modules or execute project code. Each project receives one Android isolated service process/UID and a bounded set of explicit capabilities:
- Its own project data and scratch descriptors only.
- A host-created visitor listener capability for Rust-routed requests.
- No host credentials, account state, sibling descriptors or general network access.

Native policy must install before runtime code and fail closed. Validate descriptor transport and SELinux behavior on API 26/33/35. If the selected filesystem or socket handoff fails, redesign; do not weaken platform security.

Full sequence and adversarial tests: `docs/SECURITY_GATE_2_EXECUTION_PLAN.md`. Relay connectivity is separately specified in `docs/ASSAYFLUX_RELAY.md`.

## 7. Acceptance

- Host and companion APKs install/upgrade together with matching signer.
- Binder caller and protocol checks reject unauthorized/stale clients.
- Transfer bounds, corruption, traversal, interruption and rollback tests pass.
- Python and Node real HTTP requests route through Rust.
- Companion death/rebind and static-origin continuity pass.
- Gate 2 per-project isolation passes API 26/33/35 and independent review before untrusted code is considered.
