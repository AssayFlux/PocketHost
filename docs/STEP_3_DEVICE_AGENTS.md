# Device Agents and Workers — v0.2.0 Workstream

**Status:** Linux preview foundation; production agent acceptance open.  
**Release assignment:** v0.2.0 Connected Device Fabric.

## Linux Node Agent preview

The repository includes a separately installable Python package under agents/linux with:
- P-256 identity and metadata-bound challenge proof.
- Owner-signed grant and job verification.
- Durable SQLite job state.
- HTTPS control-plane polling.
- Allow-listed native.health_check, storage.snapshot, backup.create and backup.verify executors.
- Default-deny namespace access.

This is a preview, not a certified production agent.

## Current limitations

- Enrollment and grant installation remain manual.
- HTTPS bearer transport is not mutual TLS.
- Mobile Command Center/control-plane integration is incomplete.
- Grant renewal and revocation polling are incomplete.
- No production Windows/macOS/SBC agents are certified.
- Build/publish, traffic proxy, full restore/replication and signed updates remain open.
- Physical hardware acceptance remains open.

## v0.2.0 work

1. Complete mobile owner enrollment, grant distribution, signed jobs and results.
2. Add authenticated discovery/pairing and supported BLE/USB paths.
3. Implement grant renewal, revocation polling, health and signed update/rollback.
4. Implement bounded storage/backup restore, build/publish preparation, traffic and native-job adapters.
5. Add OS-native isolation and resource limits.
6. Build platform-specific packages and support matrix.
7. Add SBC and DemonCore adapters only with named hardware/capability profiles.
8. Test cross-role isolation, restart, network loss, power loss and resource pressure.

## Acceptance

Every advertised OS/architecture/role combination needs its own signed package, verified enrollment/grant, durable jobs, enforceable resource isolation, restart/reconnect, update/rollback and automated plus physical-device tests. Use G0–G9.
