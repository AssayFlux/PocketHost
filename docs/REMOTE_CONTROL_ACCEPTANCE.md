# Pocket Host — Connected Device Acceptance (v0.2.0)

**Release assignment:** v0.2.0 — Connected Device Fabric.  
**Status:** acceptance plan; not a statement that the fabric is production-ready.

## 1. Mobile modes

- Server only: website serving enabled; fleet controls unavailable.
- Command Center + Server: website serving plus owner fleet controls.
- No standalone Command Center-only mode.
- Local hosting must work without the control plane or workers.

## 2. Device verification and transports

- Device identity is derived from its public key.
- Candidate proves possession of the private key.
- Challenge is single-use, owner/device-bound and short-lived.
- Owner reviews platform, software provenance, capabilities, permissions and requested role.
- Explicit approval issues a signed, scoped grant.
- Wi-Fi/LAN, BLE, USB and remote transports use the same identity and authorization model.
- Revocation, expiry and generation changes are enforced by the agent.

## 3. Role assignment

Roles include storage, backup/recovery, traffic, build/publish preparation, native jobs and edge gateway. Each role must have a least-privilege permission set, resource budget, data namespace, network scope, health check, update policy and revocation path. Arbitrary shell and unrestricted filesystem access are not accepted.

## 4. Website serving and runtimes

Static hosting works without a runtime companion. Python 3.11 and Node.js 24.22.1 are optional integrations in the separate runtime-worker APK and are accepted separately in v0.1.1. Runtime crashes must not interrupt static serving.

## 5. Agent and job acceptance

A real second device must enroll, receive a grant, accept a signed typed job, report a result, survive a reconnect and reject a revoked/expired grant. Job delivery is at-least-once; mutations must be idempotent. The agent revalidates grant, target, expiry, capability and budget before side effects.

## 6. Platform matrix

Do not claim generic Linux, Windows, macOS, iOS, SBC or MCU support. Record exact OS version, architecture, board, installer, signature and tested roles. Only named accepted targets may be advertised as supported.

## 7. Connectivity

Local Network and Cloudflare Tunnel are independent of the fleet control plane. The target standalone AssayFlux Relay Node must also be independent of fleet admin credentials and APIs; current RelayAgent/gateway are still coupled and this is a v0.1.0 prerequisite. Test DNS/TLS and visitor access from an independent network. Visitor ingress must not expose management API, control plane, runtime broker or MCU controls.

## 8. Security, storage and recovery

Test credential theft/revocation, replay, role escalation, device clone, cross-role access, control-plane outage, worker crash, network partition, storage exhaustion, signed update rollback and restore. Accepted local work may continue only within its stored grant and lease.

## 9. Release

All v0.2.0 G0–G9 gates must pass. Required evidence includes artifact hashes, device/OS matrix, enrollment and job logs, revocation test, fault/recovery report, security review, provider tests and owner sign-off.
