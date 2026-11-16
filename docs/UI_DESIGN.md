# ASSAYFLUX POCKET HOST — USER INTERFACE SPECIFICATION

**Authority:** this document governs native app UI. Product scope and release sequence are governed by `docs/NORTH_STAR.md`.  
**Status:** five-destination Compose UI source exists; visual, accessibility and device acceptance remain.

## 1. Product identity and precedence

- Display name: AssayFlux Pocket Host.
- Repository: AssayFlux/PocketHost.
- Host package: `com.assayflux.pockethost`.
- Companion package: `com.assayflux.pockethost.worker`.
- Earlier owner instructions take precedence over inferred design preferences.
- Do not rename packages, modules, app identity or features without explicit approval.
- A control must never imply a capability that is not implemented and available.

## 2. Visual language

Classic, clean, timeless and operational. Light theme is default; dark theme is optional. Preserve existing icon style and use monochrome icons. Cards are the main grouping primitive. No gradients, neon or glass effects.

Active navy palette:
- Background `#F7F7F5`
- Cards `#FFFFFF`
- Primary text `#1C1C1C`
- Secondary text `#6B6B6B`
- Accent navy `#1F3A5F`
- Success `#2E7D32`
- Warning `#B26A00`
- Error `#C62828`
- Border `#E2E2DE`

Use system sans-serif for body and monospace for hostnames, addresses, ports and logs. 8dp spacing grid, 12dp card radius, 1dp borders, restrained shadow. Tap targets >=48dp and body text >=14sp. Motion is subtle and short; respect reduced-motion preferences.

## 3. Navigation

Five destinations with visible icon and label:
1. Dashboard — hosting summary, health and primary actions.
2. Sites — site registry, files, hostnames, deployment and runtime.
3. Activity — logs, events, alerts and operational history.
4. Learn — explanations, guided workflows and limitations.
5. Settings — app, networking, security, backup, capacity and advanced preferences.

Top bar displays AssayFlux Pocket Host and a truthful service state. State survives tab changes; unsaved work is not discarded unexpectedly.

## 4. Dashboard

Show real data only:
- Host service state and start/stop action.
- Active site count and hostname status.
- Selected networking mode and connector state.
- Device health: memory, storage, battery and thermal condition where available.
- Capacity budget and current admission state.
- Recent operational events and backup status.

Do not show invented visitor counts, public reachability or tunnel health. Distinguish local running, connector ready and external HTTPS verified.

## 5. Sites

- Static site import, validation, preview, publish, update and rollback.
- Site name, hostname, deployment state and last update.
- Python/Node cards only when the corresponding runtime is available and supported; show trusted-code warning until Gate 2 passes.
- Clear per-site route and runtime state.
- Destructive operations require confirmation and preserve rollback where possible.

## 6. Networking experience

Offer clear separate choices:
- AssayFlux Relay — planned; show disabled/coming later until gateway and agent exist.
- Cloudflare Tunnel — existing named tunnel; token entry, connector state and external verification.
- Local Network — LAN-only access with clear network scope.
- Direct Hosting — defer/advanced only; never default.

Do not show AssayFlux Relay as selectable/working before enrollment, gateway, stream forwarding, domain and security tests pass. Explain that relay hides phone IP from ordinary visitors, not from relay infrastructure. Visitor access mode is separate from Owner/Operator login.

## 7. Authentication and security

Separate:
- Owner/Operator management authentication.
- Visitor authentication for each hosted site.

Show Public, Private and Restricted site modes only when backend enforcement exists. Explain session expiry, device revocation and recovery. Never reuse visitor token as management credential.

## 8. Capacity and operations

Show observed metrics with timestamp and source. Distinguish observed peak, sustainable measured safe envelope and current admission budget. Show test conditions and warn when calibration is stale. Display logs with redaction; never reveal tunnel tokens, passwords, session tokens or private keys.

## 9. Learn and Settings

Learn explains import, hosting, networking, security limitations, backups and troubleshooting. Settings owns appearance, app lifecycle, network connectors, authentication, capacity profiles, backup/restore, diagnostics and advanced controls. Extract Settings into its own composable when implementation sequence permits; do not duplicate business logic.

## 10. Accessibility and acceptance

- TalkBack labels and logical traversal.
- 48dp minimum targets.
- Contrast and scalable font validation.
- Compact screens, landscape and keyboard behavior.
- Light/dark screenshot review.
- Loading, empty, offline, permission-denied, error and recovery states.
- No UI state that is more optimistic than the backend.
- Test API 26, 33, 35 and physical target devices.

UI completion is not production acceptance without screenshot and accessibility evidence.

## Current implementation snapshot — 2026-11-16

- Dashboard, Sites, Activity and Learn have dedicated Compose screen files; the five-destination navigation is wired in the main activity.
- Settings currently remains embedded in the main activity with General, Network, Runtimes and About sections; it has not yet been extracted into a dedicated screen/state contract.
- Existing screens provide static import/preview/publish controls, backup/restore, operational logs and Learn topics. Visual regression screenshots, TalkBack traversal, scalable-font/contrast checks, compact-screen and landscape acceptance remain unverified.
- Gate 7 remains open until Settings is completed/refactored, first-run publishing is validated end to end without a terminal, and accessibility/visual acceptance evidence is recorded.

