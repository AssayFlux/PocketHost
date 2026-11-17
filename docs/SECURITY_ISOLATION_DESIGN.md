# Pocket Host 0.1.0 — Security and Isolation Architecture

## 1. Protected assets

- Owner/team identity, sessions, recovery credentials and device keys.
- Website source, deployment artifacts, persistent data and backups.
- Runtime secrets, database credentials and internal service tokens.
- Host operating-system data and management interfaces.
- Tunnel credentials, hostname ownership proofs and certificates.
- Device identity, MCU provisioning keys and OTA signing authority.
- Logs, audit records and private visitor information.

## 2. Threat actors and assumptions

Treat uploaded application code, dependencies, archives, network peers, LAN discovery, public visitors, compromised service cells and compromised edge nodes as untrusted. A device being on the same Wi-Fi, enrolled in the fleet or owned by the same account does not automatically grant access to other devices or projects.

## 3. Identity hierarchy

Keep distinct cryptographic identities for:
- Human owner and team member.
- Mobile command-center installation.
- Host device and host agent.
- Website/service component.
- Visitor/client.
- MCU/edge node.
- Runtime package publisher and deployment signer.

Use device-bound keys where supported, short-lived scoped credentials, explicit owner approvals, rotation, revocation and recovery. Never embed fleet-wide secrets in runtime packages, firmware images or source repositories.

## 4. Plane separation

**Control plane:** identity, enrollment, desired state, commands, policy and audit.

**Service data plane:** website traffic, internal service calls, artifacts and storage.

**Device/edge plane:** MCU telemetry and allow-listed hardware operations.

Public visitor ingress may reach only the website ingress and explicitly published service routes. It must never reach management APIs, runtime brokers, debug endpoints, artifact administration or MCU control.

## 5. Workload isolation

Each project/component receives:
- Distinct OS-enforced execution identity where supported.
- Dedicated project data roots and explicitly mounted persistent volumes.
- Scoped secrets delivered only at runtime.
- Deny-by-default filesystem and network access.
- Resource ceilings, timeouts, process limits and bounded queues.
- Explicit service-to-service capabilities and authenticated routes.

Runtime packs may be shared as immutable software. Runtime processes, data, credentials and permissions are not shared by default.

Platform enforcement must be proven separately:
- Android: isolated processes/services, Binder capability brokerage, SELinux and platform-specific filesystem/network constraints.
- Linux: namespaces, cgroups, seccomp, capabilities, mount isolation and an unprivileged service identity.
- Windows: service identities, ACLs, restricted tokens/AppContainer where appropriate, firewall and job-object limits.
- macOS: launchd identities, sandbox/entitlements and platform-supported filesystem/network controls.
- Embedded Linux: exact kernel, init, filesystem and hardware security profile.
- MCU: board-specific firmware privilege model, safe GPIO profile, bounded parser, watchdog and authenticated device protocol.

A process boundary, package boundary, language-level sandbox or container label alone is not proof of isolation.

## 6. Device-to-device trust

- Discovery is unauthenticated hinting only.
- Pairing uses short-lived one-time challenges and owner approval of the exact key/fingerprint and requested role.
- Connections use standard authenticated encryption such as reviewed TLS configurations.
- Commands are typed, versioned, bounded, expiring, scoped and auditable.
- Replay protection, idempotency and durable acknowledgement are mandatory.
- Revocation closes sessions and invalidates credentials within a bounded interval.
- Devices never receive the owner's master credential or unrelated device secrets.

## 7. Fault containment as security

A compromised or exhausted component must not:
- Read sibling project files or host-private state.
- Access another component's secrets.
- Bind arbitrary public listeners or create unapproved egress.
- Reach loopback management, cloud metadata, private networks or other devices without an explicit capability.
- Modify host supervisor, deployment trust roots or update policy.
- Exhaust unrelated components through unbounded CPU, memory, disk, file descriptors, queues or connections.

The host supervisor, website ingress, runtime broker and identity store must be separate trust boundaries. A failure in telemetry, a tunnel or the mobile client must not disable local hosting.

## 8. Supply chain and updates

- Sign host agents, runtime packs, deployment manifests, firmware and update metadata.
- Verify artifact digest, signature, publisher, compatibility and rollback policy before activation.
- Stage updates, run health checks and atomically activate.
- Preserve last-known-good artifacts and recover from power loss, disk full and network interruption.
- Prevent downgrade to revoked or vulnerable versions according to explicit policy.
- Generate SBOM and license inventory for distributed artifacts.

## 9. DemonCore/MCU boundary

DCP payload parsing is not authentication. MCU commands require a claimed device identity, authenticated transport, replay protection, owner authorization, per-board allow-list and audit. Never bridge public website requests directly to GPIO. A compromised MCU must not gain host-agent privileges or other device keys.

## 10. Required adversarial tests

- Cross-project file, secret and process isolation.
- Native/runtime escape and alternate network-stack attempts.
- Loopback, private, link-local and metadata endpoint denial.
- Forged, replayed, expired and revoked device commands.
- Pairing brute force and identity substitution.
- Oversized/malformed protocol and archive inputs.
- Resource exhaustion and crash-loop containment.
- Host, runtime, storage, tunnel and control-plane independent failure.
- Update tampering, rollback and interrupted activation.
- MCU unauthorized pin, replay and compromised-node containment.

Security support is declared only for exact tested OS/device/runtime profiles and reviewed threat models.
