# PocketHost Control Plane

Independent Rust service for remotely managing registered PocketHost Android devices. The Android phone remains the hosting origin; this service stores device inventory/configuration and queues narrowly-scoped commands for an authenticated device agent to poll and acknowledge.

## Requirements

- Rust stable
- PostgreSQL 14+
- TLS termination at a trusted reverse proxy (Caddy, nginx, or a managed HTTPS edge)

## Run

```sh
export DATABASE_URL='postgres://pockethost:change-me@127.0.0.1/pockethost'
export POCKETHOST_CONTROL_ADMIN_TOKEN="$(openssl rand -hex 32)"
export POCKETHOST_CONTROL_BIND='127.0.0.1:8080'
cargo run --manifest-path control-plane/Cargo.toml
```

Apply migrations automatically at startup. Keep the service behind HTTPS; never expose its HTTP listener directly to the public internet. Use a secret manager for the administrator token and rotate it after staff changes.

## API

- `GET /health`: liveness.
- `POST /v1/admin/devices`: register a device; returns its device token once.
- `GET /v1/admin/devices`: inventory and last heartbeat.
- `GET /v1/admin/devices/{id}/config`: read desired configuration.
- `PUT /v1/admin/devices/{id}/config`: optimistic versioned update; queues `apply_config`.
- `POST /v1/admin/devices/{id}/commands`: queue a typed deployment/site/tunnel/runtime command.
- `GET /v1/device/commands`: authenticated device agent polls queued commands.
- `POST /v1/device/heartbeat`: authenticated agent reports bounded JSON state.
- `POST /v1/device/commands/{command}/ack`: agent acknowledges execution.

All protected routes require `Authorization: Bearer <token>`. Device tokens are random 256-bit values; only SHA-256 hashes are persisted. Commands are allowlisted and payloads are size-bounded. No arbitrary shell/SQL execution endpoint exists.

## Device agent protocol

The Android management agent must use an outbound HTTPS connection to poll commands and send heartbeats. It must validate every command against the local site registry, allowed filesystem roots, current runtime state and resource policy before applying it. It must report rejected commands as failed with a sanitized result. The backend never receives filesystem access to the phone.

## Current integration boundary

This control plane is a separately deployable service and its device protocol is implemented here. Android-side registration, polling, heartbeat scheduling, command execution/acknowledgement and browser console integration are separate integration tasks; until those are connected and tested, remote management is not end-to-end available.
