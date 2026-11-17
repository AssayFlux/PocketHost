# Pocket Host — Design Language V1 (Authoritative Android UI Specification)

**Version:** V1  
**Owner approval:** 2026-11-17  
**Status:** Authoritative design contract; implementation and device acceptance are tracked separately.  
**Supersedes:** Earlier Android UI layout descriptions, including the five-destination bottom-navigation shell and any V1-inconsistent dashboard scrolling/card proportions.

This document is the single source of truth for native Pocket Host visual design, login/app-unlock, dashboard, service carousel, information centre, hamburger navigation, user menu, typography, iconography, themes, dimensions and responsive behaviour. Product/security scope remains governed by `docs/NORTH_STAR.md` and the security architecture documents.

## 1. Design principles

Pocket Host V1 is **classic, elegant, restrained, information-dense and Android-native**.

- Use warm, calm surfaces, carefully separated cards, subtle borders and restrained elevation.
- Prefer clear hierarchy and useful information over decoration.
- Use minimal **monochrome icons throughout the app**. Keep a consistent icon family, stroke weight and optical size. Semantic colour is reserved for success, warning, error and selected states.
- Avoid neon, excessive gradients, glassmorphism, noisy backgrounds, oversized banners and unnecessary nested cards.
- Use a consistent spacing and shape system while allowing content to adapt to Android window size, font scale and display size.
- The home dashboard has a distinctive composition. Do not force its carousel/information-centre layout onto feature detail pages.
- Preserve real feature actions and state. Never present placeholder data, planned features or unavailable telemetry as operational.

## 2. Brand and typography

### 2.1 Pocket Host identity
- The Pocket Host wordmark is the signature brand element and uses an elegant calligraphic/script display typeface.
- Use the AssayFlux identity as a restrained secondary brand mark.
- Do not use script typography for body text, navigation labels, metrics, code, logs or controls.
- The production wordmark font should be bundled and properly licensed so OEM-specific Android cursive substitutions do not change the brand. Until that is done, a generic cursive fallback is an implementation limitation, not final brand acceptance.

### 2.2 Type hierarchy
| Role | Typeface direction | Target size |
|---|---|---:|
| Pocket Host wordmark | Calligraphic/script | 30–36sp; login may use 34–40sp |
| Feature/page title | Elegant serif display | 25–28sp |
| Section heading | Serif or restrained sans-serif medium | 19–22sp |
| Service-card title | Sans-serif medium | 16–18sp |
| Body and control labels | Highly readable sans-serif | 14–16sp |
| Supporting caption | Sans-serif | 11–13sp |
| Metrics | Tabular numerals | 22–30sp |
| Hostnames, ports, logs, code | Monospace | Contextual |

Respect Android font scaling. These are baseline sizes, not permission to clip content or force a fixed-height layout.

## 3. Colour and theme system

Provide ten coordinated classic palettes:
1. Classic Ivory
2. Midnight
3. Forest
4. Burgundy
5. Ocean
6. Graphite
7. Sandstone
8. Lavender
9. Copper
10. Arctic

Each palette defines page background, elevated/card surface, primary and secondary text, outline, accent, selected/focused states and semantic status colours. Maintain readable contrast in both light and dark interpretations. Do not recolour only buttons while leaving other surfaces inconsistent.

Appearance mode:
- **System:** follow Android's current light/dark setting.
- **Light:** explicit light appearance.
- **Dark:** explicit dark appearance.

Persist both appearance mode and palette selection in app preferences. Apply them consistently to login/unlock, dashboard, drawer, user menu, feature pages, settings, dialogs, sheets, inputs, charts, system bars and loading/empty/error states. Changing a palette must not change the semantic meaning of status colours.

## 4. Iconography

- Minimal monochrome icons across all screens and navigation.
- One consistent icon family and stroke weight; avoid mixing filled, outlined and multicolour packs.
- Typical sizes: 20–24dp for standard actions/navigation; 22–24dp for drawer rows; 16–18dp for compact status metadata; 28–32dp for service-card identity.
- Use semantic colour only where it conveys state (success, warning, error) or a selected/focused control.
- Every icon-only action needs a meaningful accessibility label and at least a 48dp interactive touch target.

## 5. Login / app-unlock screen

The first screen is a dedicated, quiet, branded authentication panel—not a dashboard.

Visual hierarchy:
1. Pocket Host calligraphic wordmark, with restrained AssayFlux identity.
2. Short welcoming/supporting line.
3. The supported authentication action, clearly presented.
4. Biometric fingerprint action and secure device-credential fallback where supported.
5. Clear feedback for unavailable biometrics, cancellation, lockout and failure.

Layout:
- Centre a single rounded panel on a calm theme background.
- Target content width 320–400dp, capped by available width and safe insets.
- Panel radius approximately 20dp; content spacing 16–24dp.
- If a supported account-authentication flow is later introduced, its identifier/password fields target 52–56dp height and primary action 48–52dp height.
- Adapt for keyboard visibility, compact height, landscape, large fonts and system insets. Allow the screen to scroll only when needed to keep every control reachable.

**Authentication truth:** the current native app provides an optional local biometric app lock. It does not currently implement native username/password account authentication. Do not invent local credentials, imply a cloud account exists, or show a decorative sign-in action as functional. Management-console authentication is separate. The unlock screen must remain secure and must not bypass the existing lock.

## 6. Home dashboard — fixed composition

The dashboard is a deliberately composed, screen-fitting home surface. **The dashboard itself must not vertically scroll. The only scrollable dashboard component is the service carousel, and it scrolls horizontally.** The information centre does not scroll in either direction.

Top-to-bottom hierarchy:
1. Compact brand/header region.
2. Horizontal control bar with hamburger on the left and user/account control on the right.
3. Tall portrait service-card carousel.
4. Fixed information centre with four or five compact information bars/cards.

Use available window constraints and system insets to fit the complete composition. Dashboard scrolling must not be introduced as a workaround for compact screens. On short screens or with large font/display settings, intelligently compact spacing, typography and secondary detail, and reflow the information bars while keeping all essential information visible and controls accessible. If a particular configuration cannot show all content legibly, surface an explicit compact/adaptive layout rather than clipping or silently enabling page scrolling.

### 6.1 Brand/header region
- Pocket Host wordmark is visible at the top without consuming excessive vertical space.
- Keep the brand/header hierarchy compact and balanced.
- Do not duplicate large branding banners on every feature screen.

### 6.2 Top control bar
- One clear horizontal control surface beneath the brand region.
- Hamburger/navigation control on the **left**.
- User/account control on the **right**.
- Controls have clear pressed, expanded/collapsed and focus states.
- Keep both independently accessible with suitable touch targets.

### 6.3 Service carousel — portrait rectangles
This is the main visual feature of the dashboard.

- Cards are **vertically long portrait-oriented rectangles**, not square tiles and not landscape cards.
- Target card aspect ratio: width:height approximately **0.68–0.75**.
- Target width 145–165dp and height 205–235dp on a typical phone, derived from available width rather than hardcoded to a device.
- The active/centre card is subtly larger (approximately 1.04–1.07x); neighbouring cards remain partially visible on both sides.
- Horizontal swipe/pager only. No vertical scrolling inside the carousel.
- Show clear page position and provide accessible previous/next actions in addition to swipe.
- Cards use restrained radius (18–22dp), clear service icon, title, truthful status, a small number of useful metrics and a clear destination/action.
- Services may include Hosting, Websites, Relay & Tunnels, Runtimes, Security, Capacity and Backups where those destinations are supported.
- Do not fabricate uptime, traffic, availability, security health, runtime isolation or backup success.
- Card proportions and spacing must preserve the portrait character across phone sizes.

### 6.4 Fixed information centre — four or five non-scrolling bars
- Display **four or five information bars/cards simultaneously**, as much as the available screen can comfortably fit.
- The information centre is **not vertically scrollable and not horizontally scrollable**.
- The bars are compact, information-dense, carefully composed components—not a notification feed requiring scrolling.
- Use a responsive grid/arrangement to fit the bars: typically a 2-column arrangement for four compact cards and a wider fifth activity/status card when space allows. Adapt arrangement for narrow or short windows without clipping.
- Use monochrome icons, concise labels, prominent key values, small graphs/sparklines, progress indicators and semantic status where useful.
- Each bar should communicate multiple useful facts at a glance. Avoid tiny text and decorative graphs without meaningful data.
- Possible information: CPU/load, RAM, storage, hosting/service status, network/connectivity/traffic, security alerts, backup/runtime readiness and recent activity/errors. Select the most useful four or five for the current state.
- Prioritise errors and warnings over low-value telemetry. Missing or stale data must say unknown/unavailable/stale—not zero, normal or healthy.
- A bar may open its relevant detail destination when tapped, but its container does not scroll.

## 7. Hamburger navigation drawer

The hamburger menu is the primary global navigation system; it replaces the old bottom-navigation shell.

Visual structure:
- Branded drawer header with compact calligraphic Pocket Host identity.
- Clearly grouped navigation sections, each with a minimal monochrome icon and readable label.
- Strong selected-destination treatment using a subtle surface/accent, not a large colourful tile.
- Consistent row height and generous touch targets (minimum 48dp; approximately 52–56dp preferred).
- Drawer width target 300–320dp, capped by the available window width.
- On compact/large-font screens, the drawer itself may scroll vertically so every destination remains reachable. This does not make the dashboard scroll.
- Support Android Back, outside-tap dismissal, focus traversal and TalkBack.

Navigation inventory (show only supported destinations; do not create fake pages):
- Home
- Hosting
- Websites & Files
- Relay & Networking
- Python Runtime
- Node Runtime
- Security & Access
- Capacity & Performance
- Backups & Recovery
- Activity & Logs
- Learn
- Settings
- About

Each destination must route to its own correct feature surface. Do not map unrelated destinations to one generic page merely to make every menu item clickable. If two functions genuinely share an implementation surface, show distinct section context and preserve their correct state/action contracts. Mark unavailable features honestly.

## 8. User / account dropdown menu

The user control on the right side of the top control bar opens a separate user/account menu; it is not the hamburger drawer.

Visual structure:
- Compact identity/header region using only a real available identity.
- Clear menu rows with monochrome icons, concise labels and consistent spacing.
- Menu width target approximately 240–300dp, capped by available space.
- Rounded surface, subtle outline/elevation and clear focus/pressed states.
- Dismiss on outside tap and Android Back.
- No invented avatar, username, email, cloud identity or session state.

Only expose actions that actually exist. Supported shortcuts may include app settings, security settings, About and local app lock where wired. Profile management, account switching, cloud session management and sign-out must not be shown as working capabilities unless their underlying identity/session contract exists. The local biometric app lock must not be described as cloud sign-out.

## 9. Feature screens and shared components

Feature pages inherit V1 colours, typography, card shapes, spacing and monochrome icons, but use layouts suited to their function. Do not force the dashboard's fixed carousel and information bars onto every destination.

| Destination | Appropriate layout |
|---|---|
| Hosting | Hosting state, start/stop controls, site/service summary and diagnostics |
| Websites & Files | Website list, file manager, import/publish/preview/rollback actions |
| Relay & Networking | Connection state, tunnel configuration, network details and traffic |
| Python Runtime | Dedicated Python environment, lifecycle, logs and safety context |
| Node Runtime | Dedicated Node environment, lifecycle, logs and safety context |
| Security & Access | Owner/visitor access, permissions, sessions and alerts |
| Capacity & Performance | CPU, memory, storage, thermal/network telemetry and meaningful graphs |
| Backups & Recovery | Backup history, validation, restore and recovery actions |
| Activity & Logs | Searchable/filterable event and log history |
| Learn | Guides, onboarding and tutorials |
| Settings | Appearance, general preferences, network, runtimes, notifications, security and app information |
| About | Product identity, version, licences and diagnostics |

Feature pages may vertically scroll when their content requires it. Keep controls reachable and avoid nested vertical scroll regions unless there is a clear interaction reason.

## 10. Measurement and spacing reference

All values are dp unless marked sp. These are design targets; use constraints and window insets, not fixed screen dimensions.

| Element | V1 target |
|---|---:|
| Dashboard horizontal outer padding | 12–16dp |
| Brand/header region | compact; typically 44–56dp |
| Top control bar | 56–64dp |
| Gap between major dashboard regions | 12–16dp |
| Portrait service card width | 145–165dp typical |
| Portrait service card height | 205–235dp typical |
| Service card aspect ratio | 0.68–0.75 width:height |
| Centre card emphasis | 1.04–1.07x |
| Visible adjacent-card peek | approximately 20–36dp |
| Carousel region height | approximately 225–250dp |
| Information bar height | approximately 64–82dp |
| Information-bar gap | 8–10dp |
| Standard feature card radius | 16dp |
| Service card radius | 18–22dp |
| Information card radius | 12–14dp |
| Standard button height | 48–52dp |
| Compact button height | 40–44dp |
| Input height | 52–56dp |
| Drawer width | 300–320dp, capped by window |
| User menu width | 240–300dp, capped by window |
| Standard spacing rhythm | 4/8/12/16/24dp |
| Minimum interactive touch target | 48 × 48dp |

Do not force all four/five information cards into a fixed pixel height if that makes their content illegible. Derive the dashboard layout from the available height after status/navigation bars and safe insets.

## 11. Android responsiveness, accessibility and motion

- Respect status/navigation bar insets, display cutouts, edge-to-edge layout and IME/keyboard insets.
- Respect system font scale and display-size preferences. Text may grow; content must reflow rather than clip.
- Support compact phones, normal phones, tablets/expanded windows, portrait and landscape.
- Keep the dashboard non-vertically-scrollable in every supported configuration; adapt the composition rather than allowing hidden overflow.
- Only the service carousel scrolls horizontally on the dashboard. Prevent gesture conflicts between pager and surrounding content.
- Information centre remains static/non-scrolling.
- Drawer and long feature/settings pages may scroll vertically when needed.
- Minimum 48dp touch targets, meaningful TalkBack descriptions, logical focus order, keyboard/switch access and sufficient contrast.
- Charts and sparklines require text equivalents for accessibility.
- Use restrained motion; respect reduced-motion preferences. Menus dismiss predictably and Android Back behaves correctly.
- Preserve state and unsaved work when navigating between destinations.

## 12. Acceptance checklist

V1 is not accepted until all applicable checks pass:

1. Bundled/licensed calligraphic brand typeface renders consistently; script font is limited to branding.
2. All ten palettes render coherently in System, Light and Dark modes and persist after process/app restart.
3. Login/app-unlock screen is branded, adaptive and secure; biometric failure/cancel/unavailable paths are tested.
4. Dashboard fits without vertical scrolling on supported compact, normal, expanded, landscape and large-font configurations.
5. Service cards are portrait rectangles at the specified proportion; active card emphasis, adjacent previews, horizontal swipe and page position work.
6. Information centre shows four or five useful bars simultaneously, never scrolls, and uses real data with correct unknown/stale states.
7. Hamburger drawer has the complete supported navigation inventory, clear grouping and correct independent destination mapping.
8. User/account dropdown is visually distinct from the drawer and exposes only real supported identity/session actions.
9. Minimal monochrome icon system is consistent across login, dashboard, drawer, user menu, feature pages and settings.
10. Feature screens preserve existing state/action contracts and scroll correctly where needed.
11. API 26, 33 and 35 build/instrumentation results are recorded against the exact commit SHA.
12. Screenshots and accessibility hierarchy are reviewed for login, dashboard, carousel, information centre, drawer, user menu, feature destinations, settings, light/dark and large-font layouts.
13. No superseded bottom-navigation or vertically scrolling dashboard contract remains authoritative.

## 13. Implementation truth and open verification

The repository contains an implementation of the branded home shell, theme preferences, drawer, user/app dropdown, carousel and information centre. This document defines the desired V1 behaviour; source presence alone does not prove that every detail is implemented or accepted.

Known implementation/verification concerns to track honestly:
- The current branding may still use Android's generic cursive fallback; bundle and verify a licensed calligraphy font for consistent branding.
- Verify that service cards are genuinely portrait-oriented at V1 proportions (not square or landscape).
- Verify and correct dashboard scrolling: the dashboard container must not scroll vertically; only the service carousel may scroll horizontally; the information centre must remain fixed and non-scrolling.
- Verify the information centre shows four or five compact, information-rich bars with meaningful graphs/icons and no hidden overflow.
- Inspect and test drawer and user dropdown design, accessibility, dismissal and route correctness; do not treat mere clickability as independent destination implementation.
- Verify the biometric screen and every theme across API 26/33/35, compact/expanded/landscape and large font scales.
- Android build, emulator/device screenshots, TalkBack and exact-SHA CI results must be checked before claiming acceptance.

**No production, accessibility or device-acceptance claim is made by this design document.**
