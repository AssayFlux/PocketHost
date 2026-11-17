# Pocket Host — Mobile Design System V1

**Design status:** existing approved visual language; preserve it.  
**Roadmap effect:** no redesign is authorized by v0.1.0, v0.1.1 or v0.2.0.

## 1. Brand and visual language

- Elegant calligraphic/script Pocket Host wordmark for brand moments only.
- Refined readable display typography for page titles; legible sans-serif for controls and body.
- Minimal monochrome line icons; semantic colours only for state.
- Classic palettes: Classic Ivory, Midnight, Forest, Burgundy, Ocean, Graphite, Sandstone, Lavender, Copper and Arctic.
- Appearance: System, Light and Dark.
- Preserve the established login, owner/account menu, hamburger navigation and app layout.
- Respect Android font/display scaling, contrast, reduced motion, keyboard/IME insets and platform safe areas.

## 2. Mobile operating modes

Exactly two mutually exclusive modes:
- **Server only:** website serving and website settings; fleet discovery, enrollment, approvals, role assignment and worker management hidden/disabled.
- **Command Center + Server:** website serving plus fleet administration when those capabilities are delivered and accepted.

There is no standalone Command Center-only mode. Both modes retain website serving.

## 3. Navigation and release scope

The established hamburger structure remains the navigation source. v0.1.0 prioritizes Overview, Websites & Files, hosting settings, diagnostics, account and settings. Future destinations may remain visible only when clearly labelled as unavailable/planned; no control may imply an unimplemented capability.

Workbench remains a planned first-class destination and completes in v0.1.1. Device administration becomes fully functional in v0.2.0.

## 4. v0.1.0 hosting UX

- Show website import progress and validation errors.
- State the 500 MB ZIP and 500 MB expanded-site limits, plus file/entry quotas.
- Distinguish loopback/local preview from LAN access.
- Make LAN binding explicit opt-in and show the active address/port.
- Show foreground service and notification state.
- Explain Android notification, battery and network restrictions.
- Provide clear start/stop/restart, recovery and backup status.
- Avoid clipped options, unscrollable pages, hidden actions and fixed bars that overlap the keyboard.

## 5. v0.1.1 Workbench UX

Workbench has Explorer, Editor, Preview, Changes, Commits and Deployments. On phones prioritize a single-pane editor with a collapsible file drawer and compact tab strip. On tablets, use a resizable two-pane layout where supported. Dirty state and unsaved-change protection are mandatory. Preview and deployment states must be distinguishable.

## 6. v0.2.0 device UX

Device cards show identity, platform, software version, roles, trust state, connection, health, resource pressure and last-seen time. Enrollment shows fingerprint, requested permissions, resource budget and explicit owner approval. Revocation and role changes show impact before confirmation.

## 7. Accessibility and Android behavior

- Respect system font/display scale and portrait/landscape.
- Ensure every screen scrolls fully and bottom actions remain reachable.
- Handle status/navigation bars, edge-to-edge insets and keyboard resize.
- Keep touch targets accessible and labels meaningful.
- Support screen readers, contrast, reduced motion and focus order.
- Respect Android permission and notification settings; explain denied permissions rather than repeatedly prompting.
- Test low-memory, background, process death and OEM battery-management behavior.

## 8. Acceptance

UI acceptance is G8 for every release. Test both mobile modes, all v0.1.0 hosting flows, long text, font scaling, keyboard, scrolling, system appearance and permissions. Preserve the visual language while correcting implementation defects.


## Source audit note — current login implementation

The design contract retains the intended login/account experience and owner menu. In the audited Android source, however, MainActivity opens the app's main surface directly unless the optional biometric app-unlock gate is enabled; there is no conventional native username/password login screen yet. Owner/session/passkey login is implemented in the separate browser console. Keep this distinction explicit in UI status and release documentation; do not label the native login page complete until it exists and passes UI acceptance.


## Navigation synchronization — 2026-11-17

The Android drawer is the single source of top-level navigation. Its destinations must map one-to-one to real screens or settings sections:

- Home → Home
- Hosting → Dashboard
- Websites & files → Sites
- Workbench → Workbench
- Devices → Devices (visible only in Command Center + Server mode)
- Network & tunnels → Settings / Network
- Runtimes → Settings / Runtimes
- Activity & logs → Activity
- Learn → Learn
- Settings → Settings / General
- About → Settings / About

Do not add separate drawer shortcuts for Capacity, Backups, Security or Relay when they merely alias an existing destination. Their controls remain inside Hosting, Websites & files, or Network & tunnels. This prevents duplicate highlighted rows and misleading navigation. Remove sample service URLs from user-facing input placeholders; users should enter their own endpoint.
