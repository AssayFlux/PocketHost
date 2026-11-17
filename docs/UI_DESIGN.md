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

Active classic navy palette:
- Background / page canvas `#F5F3EE` (warm paper)
- Cards / primary surfaces `#FFFEFC`
- Primary text `#20252B`
- Secondary text `#626A73`
- Accent navy `#243B5A`
- Muted slate `#64748B`
- Warm brass accent `#9A7044` (sparingly)
- Success `#34764A`
- Warning `#9A7044`
- Error `#B6423B`
- Border `#DEDAD1`

Use system sans-serif for body and monospace for hostnames, addresses, ports and logs. Follow an 8dp spacing grid, 16dp standard card radius (20dp for prominent feature panels), subtle 1dp borders and restrained elevation. Keep cards clearly separated from the warm page canvas. Tap targets >=48dp and body text >=14sp. Motion is subtle and short; respect reduced-motion preferences.

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

- Dashboard, Sites, Activity, Learn and Settings have dedicated Compose screen components; five-destination navigation and Settings subsection navigation are wired through the root activity/state contract.
- Sites provides folder/ZIP import, validation, preview/publish controls and encrypted backup/restore entry points. Its empty state now gives a terminal-free first-publish walkthrough, including the root `index.html` requirement and how to start hosting and preview the result.
- UIAutomator source traverses all five destinations and four Settings sections, exercises visitor credential creation/revocation, and captures screenshots plus accessibility hierarchy XML. This is automated acceptance source, not proof of human TalkBack, contrast, scalable-font, compact-screen or landscape acceptance.
- Gate 7 remains open until the first-publish path is executed end to end, API 26/33/35 artifacts are reviewed, and human accessibility/responsive visual acceptance is recorded.

## Navigation accessibility artifact coverage — 2026-11-16

- Android instrumentation source now traverses all five bottom-navigation destinations using their icon content descriptions, checks that the top bar reflects the selected destination, and asserts destination-specific content.
- The test saves a screenshot and accessibility hierarchy XML for each destination. The Android API 26/33/35 workflow collects the full screenshots directory as an artifact.
- These are automated acceptance definitions; actual screenshots, TalkBack traversal, scalable-font/contrast and compact-screen review are not accepted until the workflow succeeds and artifacts are inspected.

## Settings extraction and subsection acceptance source — 2026-11-16

- Device preferences (dark theme, biometric app unlock, auto-start on boot and battery-optimization settings) now render through a dedicated Compose Settings screen component with explicit state and action contracts.
- Settings section navigation is a separate Compose component. The UIAutomator navigation test now visits General, Network, Runtimes and About and captures a screenshot plus accessibility hierarchy for each subsection, in addition to the five main destinations.
- This is source-level extraction and test coverage; Settings' network/runtime/about content still shares state with the root activity. Executed artifacts and human accessibility/responsive visual review remain required.

## Visitor access and publishing UI smoke coverage — 2026-11-16

- The Settings/Network UIAutomator flow now seeds a temporary registered static site, verifies hostname-route and visitor-policy controls, switches the site to Private, issues the one-time shared visitor credential, confirms the credential is registered, revokes it, and confirms the credential is removed.
- The Sites destination smoke test also verifies folder import, ZIP import and encrypted backup export entry points without requiring a terminal. These are source test definitions; they still require successful API 26/33/35 execution and artifact review.



## Classic card refinement — 2026-11-17

- The native Material 3 theme now uses a warm paper canvas, near-white cards, confident navy actions, slate secondary text and a restrained brass accent.
- Dark mode uses a coordinated warm charcoal/slate palette rather than a separate visual language.
- Shared app chrome has clearer brand hierarchy, a readable Running/Stopped service pill and a persistent five-destination navigation bar with explicit selected-state colours.
- Dashboard, Sites, Activity and Settings card corners are standardized at 16dp; theme shape tokens cover 8dp, 12dp, 16dp, 20dp and 24dp surfaces.
- Keep all operational claims tied to actual service/backend state. Visual polish must not make Relay, public reachability, runtime isolation or external HTTPS appear more ready than they are.
- Visual and accessibility acceptance still requires CI/device screenshots, TalkBack traversal, contrast/font scaling and compact/landscape review.
