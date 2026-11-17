# Step 3 — Autonomous Device Software and Role Workers

**Status:** Linux agent foundation implemented; Step 3 gate remains open.

## Linux Node Agent 0.1.0

A separately installable Python package now exists at `agents/linux`. It is deliberately a narrow first supported platform slice, not a claim that every OS/architecture is ready.

Implemented:
- Independent P-256 device identity with SHA-256 SPKI device ID and private key file mode 0600.
- Protocol 1.0 enrollment challenge proof signing.
- Owner-signed role grant verification, including target device, owner fingerprint/key ID, P-256 signature, validity window, generation floor, role capability allow-list and resource budget shape.
- Durable SQLite WAL/FULL assignment and generation state.
- Idempotent job insertion by unique idempotency key.
- Default-deny typed job execution. The initial executable job is `native.health_check`; unsupported job kinds and arbitrary shell/script payloads are rejected.
- CLI commands for identity, challenge proof, grant installation and a supported health-check job.
- Python unit tests wired into the repository quality workflow.

Install from a repository checkout:

```sh
python3 -m venv .venv
. .venv/bin/activate
pip install ./agents/linux
pockethost-agent identity
```

## Important boundaries and remaining work

The Linux package is a developer/preview component. It does not yet include authenticated LAN discovery/TLS pairing, a mobile-to-agent enrollment API, background daemon/service supervision, sandboxed resource enforcement (cgroups/systemd), signed package repositories, automatic update/rollback, or complete storage/backup/build/traffic adapters. The local typed executor is intentionally allow-listed and incomplete.

The Android runtime companion remains a separate Android package/UID and is not the general-purpose Linux worker agent. Runtime packs remain on the mobile website-serving node.

## Remaining Step 3 deliverables

1. Complete authenticated transport and connect the Step 2 mobile trust authority to the independent agent.
2. Add a supervised Linux service, cgroup/systemd resource limits, health/reconnect lifecycle and signed upgrade/rollback.
3. Implement isolated role adapters for storage, backup, build/publish, traffic and native jobs.
4. Add Windows and macOS packages with OS-native service/sandbox controls.
5. Add architecture-specific Raspberry Pi/SBC packages and exact hardware acceptance.
6. Implement DemonCore/edge adapter integration with explicit board and capability profiles.
7. Add cross-role isolation, restart, network-loss, power-loss and resource-pressure tests.

## Acceptance

Step 3 is not complete until each advertised OS/architecture/role combination has an independently installable signed package, verified enrollment and grant, durable jobs, enforceable resource isolation, restart/reconnect behavior, update/rollback and automated platform tests. Physical device testing remains required before production support.
