# Pocket Host Mobile Design System 0.1.0

**Platforms:** Android and iOS command-center applications  
**Design language:** Pocket Host Design Language V1  
**Purpose:** one coherent mobile-first fleet command center with platform-native interaction and accessibility.

## 1. Brand and typography

- Use the Pocket Host wordmark in an elegant calligraphic/script font for brand moments only.
- Use a refined, readable display serif or high-quality display face for important page titles.
- Use a highly legible sans-serif for body copy, settings, labels, metrics and controls.
- Never use script fonts for navigation labels, forms, numbers, code or accessibility-critical text.
- Keep typography hierarchy calm, editorial and consistent across Android and iOS.

## 2. Visual language

- Minimal monochrome line icons as the default.
- Neutral surfaces, careful spacing, restrained borders and subtle depth.
- Semantic colours only for state: healthy, warning, error, selected and informational.
- Avoid decorative gradients, noisy backgrounds and excessive colour.
- Support ten classic palettes: Classic Ivory, Midnight, Forest, Burgundy, Ocean, Graphite, Sandstone, Lavender, Copper and Arctic.
- Appearance modes: System, Light and Dark.
- Theme and appearance preferences are synchronized as user preferences, but each client respects platform accessibility and contrast settings.

## 3. Mobile command-center information architecture

The home screen is a fleet overview, not a single-device server dashboard.

Top bar:
- Pocket Host brand/wordmark.
- Device/fleet scope selector.
- Notification/attention indicator.
- Separate owner/profile menu.

Primary navigation drawer:
- Overview.
- Devices and pairing.
- Websites and services.
- Deployments.
- Runtime catalog.
- Networking and tunnels.
- Storage and backups.
- Access and team.
- Monitoring and activity.
- Security and approvals.
- Settings and help.

Every item must route to a real destination. Do not display unsupported or inert features.

## 4. Dashboard

- No vertical scrolling on the compact home overview where the device size permits; use responsive sections and a deliberate "View all" destination for long lists.
- The device/service carousel is horizontal only where it improves scanning; it must not become the only way to discover fleet health.
- Display fleet summary: healthy, degraded, offline, pending approval and active workloads.
- Show a compact set of information cards with truthful live values and timestamps.
- Each device card identifies the device, role, platform, health, active workload count and last-seen time.
- Show stale/offline state explicitly; never display cached data as live.
- A selected device opens its full details and controls.

## 5. Device onboarding and pairing

- QR pairing, nearby discovery and manual code entry.
- Nearby discovery is untrusted until the user approves a cryptographic identity fingerprint.
- Display device name, model, owner, requested capabilities and requested role before approval.
- Use short-lived, one-time challenges.
- Show clear states: discovered, awaiting approval, enrolled, connected, stale, revoked and recovery required.
- Pairing and revocation must be accessible and confirm destructive operations.

## 6. Device detail

Sections:
- Overview and live health.
- Hardware capabilities and compatibility.
- Services/components assigned.
- Resource usage and limits.
- Network and ingress providers.
- Storage and backups.
- Updates and rollback.
- Logs, diagnostics and audit.
- Permissions and device identity.

Actions are explicit, scoped and confirmation-gated according to risk.

## 7. Website/service management

A website is a group of independently managed components:
- Site identity, domains and visitor policy.
- Static frontend and multiple backend components.
- Runtime/version and host placement per component.
- Health, logs, resource limits, secrets and storage per component.
- Authenticated internal routes.
- Independent start, stop, restart, update, drain and rollback.
- Site-level deployment status must not hide a failed component.

## 8. Runtime catalog

- Show supported runtime versions and compatible device profiles.
- Distinguish installed software from running component instances.
- Show package signature, version, architecture, license and source.
- Explain when a device lacks required capabilities.
- Installation/update is staged and rollback-capable.
- A runtime pack never implies per-project isolation.

## 9. Networking and tunnels

- Separate Local Network, AssayFlux Relay and Cloudflare Tunnel controls.
- Allow independent enablement and concurrent use.
- Show provider-specific connection, hostname, certificate, traffic and error state.
- Show the target website/site route before activation.
- Detect hostname conflicts and certificate/DNS requirements.
- Provider failure must not hide or disable other connectivity paths.

## 10. Resilience and security UX

- Prominent but calm indicators for stale, degraded, offline, failed and recovery-required states.
- Show which component is affected and which services remain healthy.
- Do not present a global red error when only one component fails.
- Explain offline mode and last successful synchronization time.
- Display sensitive action scope, target device and consequences before confirmation.
- Provide revoke, rotate, restore and rollback flows with audit outcomes.
- Do not expose raw secrets in logs or screenshots.

## 11. Platform behavior and accessibility

Android:
- Respect system font scale, display size, edge-to-edge insets, navigation mode, keyboard and foreground-service notifications.
- Use Android biometric/device credential APIs for local app protection where available.
- Respect Doze, background execution and battery optimization behavior.

iOS:
- Use SwiftUI navigation, safe areas, Dynamic Type, VoiceOver, system appearance and native permission prompts.
- Respect background execution limits; do not imply the iOS app is an always-on host.
- Use Keychain/Secure Enclave capabilities where available for client credentials and device-bound keys.

Both:
- Minimum 44pt/48dp interactive target as appropriate to platform.
- Screen-reader labels, logical focus order, adequate contrast and reduced-motion support.
- No clipped controls at large text sizes.
- Support portrait and landscape where useful, compact and large phones, and tablet layouts.
- All important actions work without relying on colour alone.

## 12. Acceptance

- Every navigation destination is functional.
- Android and iOS expose equivalent fleet-management capabilities within OS constraints.
- Dashboard never falsely reports stale data as live.
- Pairing, revocation and recovery are tested end-to-end.
- Large text, screen readers, landscape, compact devices and tablets pass.
- One component failure is shown locally and does not obscure unrelated healthy components.
- Mobile client termination does not stop remote host workloads.
