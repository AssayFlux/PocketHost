# Pocket Host 0.1.0 — System Acceptance

Record exact commit SHA, build provenance, device/board model, OS/firmware version, architecture, test command, result, logs and artifact digest. Use PASS, FAIL or NOT TESTED. Compilation and simulated devices do not prove production support.

## 1. Mobile modes and command center

- Android exposes exactly two mutually exclusive modes: Server only and Command Center + Server. Website serving remains enabled in both; Server only cannot enroll, approve or assign fleet devices.
- iOS must not introduce a standalone Command Center-only mode. Advertise the two-mode experience only where the website-serving role passes platform lifecycle acceptance; otherwise clearly mark the iOS deliverable as not yet supported under this product contract.
- iOS platform restrictions must be handled through capability/availability messaging, not by redefining the product as Command Center-only.
- Turning mobile Server off does not stop other device assignments.
- Force-stop, reboot, battery loss and uninstall of mobile app do not stop autonomous jobs on enrolled worker nodes.
- Replacement-phone recovery and owner re-enrollment work.

## 2. Device verification and transports

- **Current implementation:** test the Android manual challenge/proof exchange, metadata binding, owner approval and local registry.
- **Future transport acceptance:** pair via authenticated Wi-Fi/LAN and one-time code; verify candidate key and owner approval.
- Test wrong, expired, replayed, brute-forced and already-used codes.
- Verify unknown devices cannot receive jobs, access namespaces or proxy traffic.
- Test BLE discovery and control separately from bulk transfer when implemented; not currently accepted.
- Test USB only on named hardware/OS combinations with supported APIs; not currently accepted.
- Switching transport preserves the same device identity and does not bypass authorization.
- Revocation closes active sessions and cancels queued unstarted jobs.

## 3. Role assignment

For each supported device, install the role-specific package and test:
- Storage-only cannot execute or proxy.
- Traffic-only cannot access management, runtime broker or MCU controls.
- Build/publish worker cannot sign or activate directly.
- Native backend/job worker accepts only signed allow-listed packages.
- Backup worker cannot browse unrelated live files.
- Edge gateway exposes only board-verified MCU capabilities.
- Multiple roles on one device are tested for isolation and resource contention.

## 4. Mobile runtime and website serving

- Static hosting works with no language runtime installed.
- Static hosting works without the runtime companion. Python/Node companion behavior is tested separately; independent runtime packages are future work.
- Multiple selected languages serve one site through declared routes.
- Each project/component must have distinct OS-enforced identity and scoped data/network before untrusted code is supported. Current Python/Node companion shares its application UID and is not a per-project sandbox.
- Runtime crash does not interrupt static serving or sibling runtimes.
- Worker build artifact is signature/digest checked, staged and health-tested before activation.
- Failed build/deployment leaves active release untouched and rollback works.

## 5. Worker and fleet autonomy

- Device reboot resumes accepted jobs safely.
- Phone/control-plane loss does not stop local accepted work.
- Network partition, duplicate delivery, expired lease and interrupted job reconcile deterministically.
- One worker failure does not stop sibling workers.
- Storage exhaustion, CPU/memory pressure, thermal derating and power loss remain contained.
- Update rollback and device replacement preserve data without cloning identity.

## 6. Platform and hardware matrix

For every advertised Android, iOS, Linux, Windows, macOS, SBC and MCU role:
- Install, enroll, reboot, update, revoke and uninstall.
- Verify exact OS permissions, architecture, resource limits and transport support.
- Test real hardware and lifecycle constraints.
- For MCU, verify pin mapping, watchdog, firmware signature, safe state and physical behavior.

## 7. Connectivity

- Local LAN works without public tunnels.
- AssayFlux Relay and Cloudflare Tunnel operate independently and concurrently.
- Provider failure leaves other providers and local ingress available.
- Public routes reach only approved website ingress.
- Management, runtime broker, worker control and MCU endpoints remain private.

## 8. Security, storage and recovery

- Test identity spoofing, role escalation, replay, malicious worker, artifact tampering and cross-namespace access.
- Test encrypted backup, wrong key, corruption, interruption, low storage and restore.
- Verify revocation and credential rotation.
- Verify MCU gateway failure does not affect website serving.
- Complete independent security review for production boundaries.

## 9. Release

- Signed platform-specific packages and firmware.
- SBOM, provenance, checksums and compatibility declarations.
- Install/upgrade/rollback verified on named devices.
- Published support matrix matches observed evidence.
