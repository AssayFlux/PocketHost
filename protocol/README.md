# Pocket Host Interoperability Protocol

**Protocol family:** `pockethost.device`  
**Current version:** `1.0`  
**Schema dialect:** JSON Schema 2020-12

This directory is the machine-readable, cross-platform contract shared by the Android owner app, runtime companion, future Linux/Windows/macOS/SBC agents and the remote control plane. Platform implementations must consume these schemas rather than inventing platform-specific enrollment or job payloads.

## Compatibility rules

- A message declares `protocol` and `message_type`; unknown major versions are rejected.
- Minor additions are backward-compatible only when fields are optional and unknown fields are safely ignored.
- Unknown roles, capabilities, job kinds, transports and permission grants are rejected by default.
- Device identity is a public-key fingerprint. Private keys never leave the device.
- Discovery data is untrusted. Pairing requires proof of key possession, a short-lived challenge and explicit owner approval.
- A role grant is scoped to one device identity, one role, explicit capabilities, resource budgets and an expiry/revocation generation.
- Jobs are typed, bounded, expiring and idempotent. Arbitrary shell/script execution is not a protocol operation.
- Credential material is never placed in these messages or telemetry. Credentials are provisioned separately over authenticated encrypted channels.

## Message flow

1. Agent creates a device key and emits a `device_hello` with public identity and a bounded capability profile.
2. Owner app verifies the fingerprint and one-time challenge, then approves an exact role and permission set.
3. Authority issues a signed role grant bound to the device key and monotonically increasing grant generation.
4. Agent persists accepted desired state and acknowledges only after durable storage.
5. Authority sends typed jobs referencing the active grant; agent validates signature, expiry, idempotency and local resource limits before execution.
6. Agent reports bounded observed state and job result. Revocation invalidates the grant generation and active sessions.

## Schemas

- `schemas/device-hello.schema.json`
- `schemas/role-grant.schema.json`
- `schemas/job-envelope.schema.json`
- `protocol-version.json`

## Product boundaries

The mobile device remains the website-serving node in both operating modes. `server_only` disables fleet administration; `command_center_server` enables it. Neither mode creates a standalone command-center-only configuration. Customer-selected language runtimes remain on the mobile serving node in baseline 0.1.0; worker agents perform only their explicitly granted bounded roles.
