# Pocket Host Mobile Design System 0.1.0

**Platforms:** Android and iOS command-center applications  
**Design language:** Pocket Host Design Language V1  
**Purpose:** serve websites from the mobile device in both operating modes, with fleet administration available only in the combined mode.

## 1. Brand and visual language

- Elegant calligraphic/script Pocket Host wordmark for brand moments only.
- Refined readable display typography for page titles; legible sans-serif for controls and body.
- Minimal monochrome line icons; semantic colours only for state.
- Ten palettes: Classic Ivory, Midnight, Forest, Burgundy, Ocean, Graphite, Sandstone, Lavender, Copper and Arctic.
- Appearance: System, Light and Dark.
- Respect Android font/display scaling, iOS Dynamic Type, accessibility contrast, reduced motion and platform safe areas.

## 2. Mobile operating modes

A prominent mode control exposes exactly two mutually exclusive choices:

- **Server only:** serve websites from this mobile device. Show website status, domains, runtime manager, local server settings and serving health. Hide fleet discovery, device enrollment, device approvals, role assignment, worker management and fleet command controls.
- **Command Center + Server:** serve websites and provide the complete fleet command center, including device discovery, verification, owner approvals, role assignment, jobs, monitoring and recovery.

There is no Command Center-only mode. Both modes include the mobile website server. The distinction is whether fleet command capabilities are enabled.

Switching modes must not revoke or stop already-authorized worker assignments. Entering Command Center + Server requires owner authentication. Switching to Server only removes access to fleet administration on this phone but does not erase enrolled devices or cancel their accepted autonomous work.

iOS displays only capabilities that pass its platform lifecycle and distribution requirements; never imply arbitrary always-on hosting.

## 3. Home and navigation

Top bar: brand, fleet selector, notifications and separate owner menu.

Primary drawer:
- Overview and operating mode.
- Devices and pairing.
- Role assignments and job queues.
- Websites and publishing.
- Runtime Manager (mobile server only).
- Storage and backups.
- Traffic and tunnels.
- Build/publish activity.
- Edge devices / DemonCore.
- Monitoring, security, approvals and audit.
- Settings and help.

Every destination must work. Unsupported capabilities are labelled unavailable, not shown as active controls.

## 4. Fleet dashboard

The home screen is a fleet overview:
- Device counts by healthy, degraded, offline and pending approval.
- Current jobs and assigned roles.
- Website/server status and installed runtimes in both modes.
- Storage, traffic and build/publish health.
- Recent security approvals and alerts.
- Clear stale/offline timestamps.

The dashboard must not vertically overflow. Use compact summaries and dedicated detail screens for long lists. Each device card shows identity, platform, assigned roles, connection, health, resource pressure and last seen.

## 5. Device discovery and verification

- Scan QR, enter device ID/code, discover on LAN or BLE, or use supported USB/remote enrollment.
- Show device fingerprint, model, OS, software signature/version, capability profile, requested roles and permissions.
- One-time verification code is short-lived, single-use and bound to the candidate device key.
- Owner explicitly approves each role and capability.
- Show states: discovered, verification pending, owner approval pending, enrolled, connected, stale, offline, revoked and recovery required.
- Discovery alone never creates a trusted device.

## 6. Device details and role assignment

Show:
- Identity and trust status.
- Hardware/OS capabilities and unsupported capabilities.
- Installed Pocket Host node software and version.
- Assigned roles, individual job queue and permission scopes.
- CPU, memory, storage, thermal, battery and network health.
- Data namespaces, peer links, updates, logs and audit.

Role assignment is an explicit flow: choose role, show required permissions/data/network access/resource budget, validate compatibility, request owner approval, then dispatch. Provide pause, reassign and revoke with clear impact.

Roles: Storage, Traffic, Build/Publish, Native Backend/Jobs, Backup/Recovery, Edge Gateway and Mobile Website Server.

## 7. Website and Runtime Manager

- Website editor/deployment shows static and dynamic components.
- Runtime Manager is available only for the mobile website-serving node in 0.1.0.
- Show separate signed language packs, versions, ABI, size, license and compatibility.
- User installs only selected language runtimes.
- Display which components require which installed runtime.
- Static content remains available if an optional runtime is absent or fails.
- Build worker artifacts are verified and staged before explicit activation on the mobile serving node.

## 8. Storage, traffic and publishing

- Storage nodes: namespace, quota, encryption, replication and backup health.
- Traffic nodes: exact site routes, proxy/cache policy, upstream and provider health.
- Build/publish workers: source scope, build log, artifact digest, provenance, test result and activation approval.
- Never expose active release activation as a worker-side arbitrary command.

## 9. Connectivity and edge devices

- Local Network, AssayFlux Relay and Cloudflare Tunnel are separate provider controls.
- Show provider-specific health and hostname routes; support independent and concurrent use.
- Edge devices show board identity, firmware, safe capabilities, last telemetry and explicit command approval.
- Never expose raw GPIO or unrestricted MCU shell as ordinary controls.

## 10. Resilience and security UX

Show the smallest failing component and what remains healthy. Display offline state, last sync, expired grants, resource limits and recovery options. Every sensitive action names target device, role, data and consequences. Include revoke, rotate, restore and rollback with audit outcomes.

## 11. Accessibility and platform behavior

- Android: respect system font scale, display size, insets, keyboard, foreground-service requirements, Doze and battery policies.
- iOS: native navigation, safe areas, Dynamic Type, VoiceOver, Keychain/Secure Enclave and background restrictions.
- Touch targets at least 48dp / 44pt as appropriate.
- Screen-reader labels, logical focus, contrast, reduced motion, portrait/landscape and tablet layouts.
- No clipped controls at large text sizes.

## 12. Acceptance

Test both modes, device verification/revocation, role assignment and job lifecycle, large text, screen readers, app termination, phone reboot, offline hosts, stale telemetry, role-specific permissions, runtime installation and worker artifact approval. No UI control may imply an unimplemented capability.


## Built-in web baseline and optional runtimes

- The mobile server app includes HTML/CSS static hosting by default in both operating modes.
- TypeScript belongs to the built-in web toolchain and is compiled to JavaScript before publication; never present raw TypeScript as directly browser-executable.
- Additional server-side language runtimes appear as separate signed/versioned optional packs in Runtime Manager. Installing one must not be required for basic HTML/CSS/TypeScript websites.
- Clearly distinguish the built-in web toolchain from optional server-side runtime packs in setup, status, storage and update screens.
- Do not show a runtime as installed/ready until its package verification and health checks pass.
