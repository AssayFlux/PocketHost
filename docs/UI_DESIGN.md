# Pocket Host — Authoritative Android UI Design

**Status:** New owner-approved design direction, 2026-11-17  
**Authority:** This document replaces all earlier native UI layout and navigation specifications. Product scope, security boundaries and release acceptance remain governed by `docs/NORTH_STAR.md` and the security architecture documents.

## 1. Design authority and non-negotiable structure

The owner's two supplied phone sketches define the intended experience:

1. A dedicated authentication / app-unlock screen.
2. A custom home screen shown after successful authentication.

The UI is mobile-first, card-led, classic, restrained and adaptive. The sketches define structure and hierarchy; their demonstration colours are not the production palette.

Do not restore the previous five-destination bottom-navigation dashboard as the primary app shell. Do not discard working product features or their state/action contracts when replacing their presentation.

## 2. Visual language

Use a timeless, premium, classic visual language:
- Warm off-white page canvas: `#F5F3EE`.
- Near-white cards: `#FFFEFC`.
- Primary navy: `#243B5A`.
- Primary text: `#20252B`.
- Secondary slate: `#626A73` / `#64748B`.
- Warm brass accent: `#9A7044`, sparingly.
- Success: `#34764A`; warning: `#9A7044`; error: `#B6423B`.
- Subtle warm-grey borders: `#DEDAD1`.
- Card radius 16–24dp, restrained elevation, 8dp spacing rhythm.
- System sans-serif typography; monospace for hostnames, addresses, ports and logs.
- No neon, gradients, glassmorphism or decorative colour blocks.

Dark mode is a coordinated charcoal/slate interpretation of the same system. Respect Android's system appearance unless the user explicitly chooses an in-app override.

## 3. Authentication screen

The launch/locked experience is a single centred, rounded authentication panel on a quiet background, matching the second owner sketch.

Visual order:
1. Pocket Host / AssayFlux brand identity.
2. Account identifier field, if a supported account-login flow exists.
3. Password field, if a supported account-login flow exists.
4. Primary sign-in action, if a supported account-login flow exists.
5. Distinct biometric action with fingerprint icon, using Android BiometricPrompt / existing biometric host integration.

The current native app has an optional local biometric app lock; it does not currently expose a native username/password sign-in flow. Do not invent local credentials, store passwords locally, or make a decorative sign-in button appear functional. Until a real native account-authentication contract is implemented, the supported launch gate must clearly present the existing biometric unlock and its secure device-credential fallback where supported. Management web-console authentication remains a separate flow.

The panel must adapt to keyboard visibility, safe insets, font scale, compact screens and landscape. Authentication failure, cancellation and unavailable biometrics must be communicated clearly without bypassing the lock.

## 4. Home screen — owner sketch 1

After successful authentication, the home screen has four vertical regions in this exact order:

### 4.1 Brand region
- Pocket Host logo/wordmark and AssayFlux identity at the very top.
- Compact, readable hierarchy; no oversized banner that wastes phone space.

### 4.2 Account and navigation control bar
A single horizontal card directly below branding:
- Left: hamburger control opens the primary navigation drawer/sidebar.
- Right: account/profile control opens the user dropdown.
- Both controls are independently accessible and have clear expanded/collapsed states.

The navigation drawer exposes the product's available modules, grouped sensibly: Home, Hosting, Websites/Sites, Relay & Tunnels, Runtimes, Security, Capacity & Performance, Backups, Activity & Logs, Learn, Settings. Hide unavailable modules or mark them honestly as planned/disabled; never imply unimplemented functionality.

The account menu contains only real account/session features available to the signed-in identity, such as profile, account/security settings, lock and sign-out. Do not invent a username or profile.

### 4.3 Horizontally swipeable service carousel
- Main feature area is a horizontal pager/carousel.
- The selected central card is visibly larger than the adjacent previews.
- Previous and next cards remain partially visible to communicate horizontal navigation.
- Page indicators show the current position.
- Support touch swipes, accessible previous/next actions, keyboard/switch access where applicable, and card activation.
- Cards represent real product services and actions: Hosting, Websites, Relay & Tunnels, Runtimes, Security, Capacity and Backups.
- Each card contains a clear title/icon, truthful operational status, a small number of useful live metrics and a primary action to the detailed feature.
- The carousel must never fabricate uptime, visitor counts, public reachability, relay availability, runtime isolation or backup health.
- On narrow screens, size cards from available width; do not use fixed device-specific pixel widths.

### 4.4 Notification and extra-information centre
Below the carousel, show a vertically stacked series of full-width horizontal information cards. They are a live information centre, not decorative placeholders.

Include relevant available data:
- Errors, warnings and important notifications.
- Hosting/service state and recent events.
- Device information: battery, memory, free storage and thermal state where available.
- Network state: connectivity, LAN and connector/tunnel status.
- Backup and runtime health when meaningful.

Use semantic severity, timestamps and concise summaries. Prioritize actionable errors, allow dismissal/archive where supported, and link a card to the relevant detail screen. Missing telemetry must display as unknown/unavailable, not as zero or healthy.

## 5. Navigation and feature preservation

The hamburger drawer is the primary global navigation. The home carousel is the prominent service launcher. Detailed screens open from the drawer, service cards and notification cards.

Preserve existing feature logic and callbacks for:
- Hosting lifecycle, status and preview.
- Site import, file management, publishing and rollback.
- Relay/tunnel configuration and status.
- Python/Node runtime management and safety warnings.
- Owner/visitor security, sessions and permissions.
- Capacity and device telemetry.
- Backup, restore and recovery.
- Activity/logs, Learn and all existing Settings subsections.

These features may be reorganized into detail destinations, but must not be deleted, stubbed, silently disabled or disconnected merely to simplify the UI. Do not show planned capabilities as active.

## 6. Adaptive Android behaviour

Use available window size and constraints, not hardcoded screen dimensions:
- Compact phones: compact brand/header, centred active carousel card with partial previews, vertically scrollable information cards.
- Larger phones: more generous card width and visible adjacent previews.
- Tablets/expanded windows: constrain content width and use available space for richer information layouts.
- Landscape: reflow without clipping or losing navigation.
- Respect status/navigation bars, display cutouts, IME/keyboard and edge-to-edge safe insets.
- Respect system font scale and display size; allow content to grow and scroll rather than truncate.
- Respect system dark/light appearance, contrast, reduced motion and Android back behaviour.
- All interactive targets at least 48dp; meaningful TalkBack labels and logical traversal.
- Avoid nested scroll conflicts between the page and horizontal pager.

## 7. Motion and interaction

Use short, restrained transitions. Carousel motion should communicate position without distracting. Menus need clear open/close state and outside/back dismissal. Provide non-gesture ways to change carousel page. Preserve unsaved work when moving between destinations.

## 8. Acceptance gates

The redesign is not complete until:
1. Login/biometric lock and successful unlock are verified without weakening authentication.
2. Home hierarchy matches both owner sketches.
3. Drawer and account menu work and expose only real capabilities.
4. Carousel swipe, page indicators, card activation and accessibility actions work.
5. Information cards reflect real application/backend/device state.
6. All existing feature flows remain reachable and functional.
7. Compact, normal, expanded/tablet, landscape, keyboard-visible and large-font layouts are inspected.
8. Light/dark screenshots and TalkBack traversal are reviewed.
9. Android CI passes on API 26, 33 and 35, with screenshots and hierarchy artifacts inspected.
10. No old bottom-navigation shell or superseded UI specification remains as the authoritative design.

## 9. Current implementation and honest gaps

**Source implemented on `ui/owner-home-shell` (2026-11-17):**
- The native entry/locked screen remains the existing optional biometric app lock, with a redesigned branded card and fingerprint action. There is no native username/password account-login API; management-console login is separate.
- The new home shell is the initial authenticated destination. It has Pocket Host / AssayFlux branding, a hamburger control, an account/app dropdown, and no bottom navigation bar.
- The hamburger drawer exposes Home, Hosting, Websites & Files, Relay & Networking, Python & Node Runtimes, Security & Access, Capacity & Performance, Backups & Recovery, Activity & Logs, Learn, Settings and About. Destinations are mapped to existing native Dashboard, Sites, Activity, Learn and Settings General/Network/Runtimes/About flows.
- The home carousel presents Hosting, Websites, Relay & Tunnels, Runtimes, Security, Capacity and Backups as horizontally swipeable cards with adjacent-card previews and page indicators. Service status and metrics are bound to current app state; planned capabilities are not represented as verified.
- The information centre reads current hosting status, connection count, DeviceTelemetry battery/memory/device/network fields and recent OperationalLog entries. Backup/runtime notes are labelled with their actual limitations.
- Existing detailed Dashboard, Sites, Activity, Learn and Settings composables and their state/action callbacks remain the implementation destinations. Backups and security-related controls remain within their existing Sites/Settings surfaces.
- The account dropdown currently provides supported app settings, security settings and About routes; it does not invent a username, profile identity, cloud account, or sign-out capability.

**Still required before UI acceptance:**
- Compile/package the Android host and run API 26/33/35 instrumentation on the exact branch SHA.
- Review actual screenshots and accessibility hierarchy for login, home, drawer, account menu, all detail destinations and Settings subsections.
- Verify carousel centering, swipe/page indicators, navigation/back behaviour, notification data freshness and empty/error/offline states.
- Verify compact and expanded widths, tablet, landscape, keyboard-visible and large font scaling; complete human TalkBack and contrast review.
- Confirm biometric cancellation/unavailable/fallback behaviour without bypassing the local app lock.
- Reconcile any UI test assumptions that still expect the removed bottom-navigation bar.

Source implementation is not proof of successful Android compilation, emulator/device acceptance or release approval.