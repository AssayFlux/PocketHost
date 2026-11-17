# Pocket Host 0.1.0 — System Acceptance

Record exact commit SHA, build provenance, device model, OS version, architecture, test command, result, logs and artifact digest for every run. Use PASS, FAIL or NOT TESTED. A compile or documentation update is not runtime acceptance.

## 1. Mobile command center

- Android and iOS authenticate to the same versioned control contract.
- Pair a host locally and remotely using a one-time challenge and fingerprint confirmation.
- Approve, rotate and revoke device credentials.
- Show fresh, stale, offline and degraded states accurately.
- Test app force-stop, reboot, uninstall, phone battery loss and mobile network change.
- Verify all remote workloads continue without the mobile app.
- Verify recovery and replacement-phone onboarding.

## 2. Host autonomy

- Host boots and restores its last authorized desired state.
- Control-plane loss does not stop active services.
- Reconnection reconciles desired and observed state without duplicate side effects.
- Expired/revoked credentials cannot authorize new sensitive operations.
- Local operator recovery works without Internet.

## 3. Component fault isolation

Independently crash, OOM, stop, corrupt or exhaust:
- One website component.
- One runtime provider.
- One site volume.
- One tunnel connector.
- Telemetry/log exporter.
- One host agent.
- Optional control plane.
- One edge gateway and one MCU.

Verify unrelated hosts, sites, components, tunnels and local ingress continue. Verify bounded degraded state and recovery.

## 4. Multi-runtime website

- One website contains static, Python and Node components.
- Components may be assigned to different eligible hosts.
- Site router sends requests only to the declared component.
- Internal routes require explicit authenticated capability.
- Runtime crash does not interrupt static delivery or sibling runtimes.
- Component update and rollback are independent.

## 5. Device and platform matrix

For each advertised Android, Linux, Windows, macOS, SBC or MCU profile:
- Install, enroll, reboot, update, revoke and uninstall.
- Verify capability discovery and resource limits.
- Verify lifecycle behavior, power/thermal handling and offline recovery.
- Verify the exact runtime/service compatibility matrix.
- For MCU boards, test real hardware pin mapping, memory, transport, watchdog and safe failure.

## 6. Networking

- Local LAN works without a public tunnel.
- AssayFlux Relay and Cloudflare Tunnel can run independently and concurrently.
- Failure, credential rotation or route removal in one provider does not affect the other or local ingress.
- Hostname conflicts are detected before activation.
- Public ingress cannot reach management, broker, debug or MCU control routes.
- External HTTPS and visitor authorization are tested from an independent network.

## 7. Security

- Per-project filesystem and network isolation.
- No cross-project secrets or identity leakage.
- Device identity, replay protection, revocation and owner approvals.
- Signed artifacts, update rollback and recovery.
- Bounded resource exhaustion, parser and archive adversarial tests.
- Independent review for each production security boundary.

## 8. Storage and recovery

- Backup integrity, wrong key, corruption, interrupted backup/restore and low storage.
- Atomic release activation and rollback.
- Host loss and device replacement.
- Restore state and credentials without cloning device identity.
- Validate recovery on real target storage.

## 9. Release

- Product version 0.1.0 and component versions recorded.
- Signed packages, matching expected identities, checksums and SBOM.
- Protocol/API compatibility and schema migration tested.
- Install/upgrade/rollback verified on named targets.
- Release notes and support matrix match observed evidence.
