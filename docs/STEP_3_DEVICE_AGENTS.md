# Step 3 — Autonomous Device Software and Role Workers

**Status:** Linux preview now has an authenticated HTTPS control-plane polling path; Step 3 gate remains open.

## Linux Node Agent 0.2.0-preview

A separately installable Python package exists at `agents/linux`. It is a narrow Linux preview, not a claim that every OS/architecture is ready.

Implemented:
- Independent P-256 device identity with SHA-256 SPKI device ID and private key file mode 0600.
- Protocol 1.0 enrollment challenge proof signing after verifying the owner's P-256 challenge signature; proof binds all advertised candidate metadata.
- Owner-signed role grant verification, including target device, owner fingerprint/key ID, P-256 signature, validity window, generation floor, role capability allow-list and resource budget shape.
- Durable SQLite WAL/FULL assignment and generation state with restrictive local file permissions.
- Durable queued/running/succeeded/failed job transitions, idempotency-key uniqueness and interrupted-job recovery.
- Owner-signed job-envelope verification before execution, plus per-job resource requests checked against the active role grant.
- A bounded HTTPS control-plane polling client using the existing device heartbeat, command lease and acknowledgement API. It refuses redirects, requires HTTPS except localhost development, bounds response sizes and reconnects with bounded backoff.
- A new `agent_job` command kind in the control plane. The worker accepts only owner-signed job envelopes and rechecks the installed grant before side effects; the control-plane bearer token is transport authentication, not job authority.
- A systemd launcher that selects remote polling when an endpoint and token are configured, otherwise retaining the local inbox preview. Network address families are explicitly limited to Unix and IP sockets.
- Default-deny typed executors: `native.health_check`, confined `storage.snapshot`, `backup.create` and `backup.verify`. Unsupported job kinds and arbitrary shell/script payloads are rejected.
- Storage snapshots are restricted to a root-configured per-agent namespace allow-list (default deny), reject symlink traversal and unsafe archive paths, enforce the granted byte budget, use immutable per-namespace snapshot IDs and verify archive CRC plus SHA-256.
- Python unit tests for transport endpoint/token/path constraints and unsupported-command rejection, in addition to identity, grant, job, storage and replay tests.

Install from a repository checkout:

```sh
python3 -m venv .venv
. .venv/bin/activate
pip install ./agents/linux
pockethost-agent identity
sudo sh agents/linux/install-systemd.sh
```

Configure `/etc/pockethost-agent.env` with the HTTPS control-plane endpoint and
the one-time-issued device token to enable remote polling. The token belongs to
a separate control-plane device record for the Linux worker; it is not the mobile
phone's token. The role grant and every job envelope remain signed by the mobile
owner identity.

## Important boundaries and remaining work

The Linux remote transport is a preview integration, not complete Step 2/3
acceptance. Enrollment and grant installation remain manual, and the mobile
Command Center is not yet integrated with the control-plane device registration,
role-grant distribution, signed job creation and acknowledgement UI. HTTPS
bearer authentication is not mutual TLS. There is no LAN discovery, BLE/USB
pairing, automatic owner-grant renewal/revocation polling, signed package
repository, automatic update/rollback or physical-device certification.
Storage and backup currently provide local snapshot/verification only; restore,
off-device replication and scheduling are not implemented. Build/publish and
traffic adapters remain absent.

The Android runtime companion remains a separate Android package/UID and is
not the general-purpose Linux worker agent. Runtime packs remain on the mobile
website-serving node.

## Remaining Step 3 deliverables

1. Integrate mobile owner enrollment, grant distribution, signed job creation and result/revocation UX with the control plane.
2. Add authenticated local discovery/pairing and supported BLE/USB transports, with replay/MITM/reconnect tests.
3. Implement owner-grant renewal and revocation polling, health lifecycle and signed update/rollback.
4. Implement isolated build/publish, traffic, storage/backup restore and native-job adapters.
5. Add Windows and macOS packages with OS-native service/sandbox controls.
6. Add architecture-specific Raspberry Pi/SBC packages and hardware acceptance.
7. Implement DemonCore/edge adapter integration with explicit board and capability profiles.
8. Add cross-role isolation, restart, network-loss, power-loss and resource-pressure tests.

## Acceptance

Step 3 is not complete until every advertised OS/architecture/role combination
has an independently installable signed package, verified enrollment and grant,
durable jobs, enforceable resource isolation, restart/reconnect behavior,
update/rollback and automated platform tests. Physical device testing remains
required before production support.
