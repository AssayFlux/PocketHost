# AssayFlux Pocket Host — UI Design

**Status:** Initial four-tab Compose implementation committed; device/responsive acceptance remains pending.
**Product identity:** AssayFlux Pocket Host (repository: `AssayFlux/PocketHost`). “Phone Web Host” describes the use case and does not rename the application.
**Authority:** This document is the single source of truth for PocketHost UI implementation. Read it before implementing each screen. If a design decision changes, update this document in the same commit as the corresponding UI code.

## 1. Precedence and existing product decisions

This specification is layered on top of the owner's earlier PocketHost instructions. Earlier instructions take precedence wherever there is a conflict.

- The user-approved app display name is **AssayFlux Pocket Host**. The GitHub repository remains `AssayFlux/PocketHost`; do not rename repository paths, package IDs, modules or technical identifiers without separate approval. “Phone Web Host” describes the use case, not the app name.
- The owner explicitly approved replacing the earlier teal/bronze/ivory palette with the navy palette in the 2026-11-15 brief. The navy palette below is now active. Preserve the existing icon style unless a separate change is approved.
- Preserve the existing native Android architecture: Kotlin and Jetpack Compose, no Termux and no root; Rust remains the HTTP engine; TypeScript remains the management-console language.
- Do not add features, change existing code, or rename things without owner approval. A UI control must not imply that a backend capability exists when it does not.
- Ask before major product or architecture decisions.
- Implement screens in the approved order and stop after each screen for owner review.

## 2. Design style

### 2.1 Active styling

Use the owner-approved classic, clean, timeless visual system: light theme by default, optional dark theme, no gradients, neon or glass effects. The navy palette below is authoritative. Keep the interface calm, readable, practical and consistent. Use system sans-serif typography for body text and monospace for URLs, IP addresses, ports and logs. Use clear hierarchy, strong contrast, generous spacing and recognizable controls.

### 2.2 Active palette and dimensions

The owner approved this palette on 2026-11-15:
- Light theme by default, optional dark theme.
- No gradients, neon or glass effects.
- Background `#F7F7F5`; cards `#FFFFFF`; text `#1C1C1C`; secondary text `#6B6B6B`; accent navy `#1F3A5F`; success `#2E7D32`; error `#C62828`; warning `#B26A00`; border `#E2E2DE`.
- System sans-serif for body; monospace for URLs, IPs and logs.
- 8dp spacing grid, 12dp rounded cards, 1dp borders and subtle shadows only.
- Tap targets at least 48dp; text at least 14sp.
- Minimal 150–200ms animations.

These palette, spacing, sizing, accessibility and motion requirements are active.

## 3. Navigation

- Bottom navigation destinations: **Dashboard**, **Files**, **Logs**, **Settings**.
- Top app bar: **AssayFlux Pocket Host** on the left and a server-status indicator/dot on the right.
- Keep navigation usable with gesture and three-button system navigation.
- Preserve state appropriately when switching tabs; do not discard in-progress user input unexpectedly.
- Use labels with icons; never rely on icon-only navigation or actions where a text label is practical.

## 4. Screens

Build in this order: Dashboard, Settings, Files, Logs. Each screen must be implemented only after the owner approves proceeding to that screen.

### 4.1 Dashboard

- Prominent server status card showing **Running** or **Stopped** and one context-appropriate **Start** or **Stop** action.
- Site URL with tap-to-copy and share action.
- Local IP and port.
- Public URL when configured and available.
- Summary statistics: uptime, requests today and data served.
- Separate readable status cards for battery, temperature and network.
- Show honest unavailable/unknown states instead of fabricated values.
- Warn when battery is below 20% or the phone is overheating, using available Android telemetry.
- Make it clear that Android background restrictions and device/network conditions can affect availability.

### 4.2 Settings

Provide settings/navigation entries for:
- Port.
- Auto-start on boot.
- Keep-awake and battery-optimization reminder.
- HTTPS.
- Domain/tunnel.
- Password protection.
- Theme.
- Backup/restore.
- About.

Only expose a setting as functional if the existing implementation supports it. For missing capabilities, do not create fake switches or claim that enabling a control configures a service. Mark unsupported items clearly or defer their controls until implementation is approved and available. Reuse the owner's already-configured Cloudflare Tunnel; do not ask them to create another tunnel.

### 4.3 Files

- List site files and folders with an appropriate icon, size and modified date.
- Actions requested: upload, create new, edit, delete (with confirmation), and set `index.html`.
- Include a **Preview site** action that opens the site in an in-app WebView.
- Validate paths and preserve the existing site's import/staging/rollback safety boundaries.
- Do not imply arbitrary file editing or write access until the underlying capability is confirmed.
- Destructive actions require a clear confirmation dialog.

### 4.4 Logs

- Request list fields: time, client IP, path and HTTP status code.
- Filter chips: **All**, **Errors**, **Today**.
- Search bar.
- Clear logs action with confirmation.
- Keep log content monospace where appropriate and redact secrets/sensitive values.
- Provide useful empty, loading and error states.

## 5. Behavior and system integration

- Maintain a persistent foreground-service notification while the server runs, including a **Stop** action.
- Warn below 20% battery and when Android thermal telemetry indicates overheating/serious thermal pressure.
- Use plain-language errors with a next step where possible.
- Provide clear empty states.
- Confirm destructive actions (delete, clear logs, overwrite/restore and other irreversible operations).
- Keep status indicators consistent with actual service/runtime state; never show Running merely because a toggle is enabled.
- Respect Android lifecycle and background-execution restrictions. A foreground service is not a guarantee of 24/7 uptime.
- Keep the website request path separate from the local management API. Never expose the management API through the public website hostname.

## 6. Responsive layout and accessibility

- Support compact legacy phones from 320dp, normal and large phones, tablets and landscape.
- Phones use a single-column layout. Tablets and sufficiently wide landscape layouts may use two columns (list on the left, details on the right).
- Dashboard cards use one column on narrow screens and two columns when sufficient width is available.
- Do not use fixed pixel widths or heights. Use flexible Compose layouts and constraints.
- Use dp for dimensions and sp for text. Respect system font scale and display size.
- Content scrolls when it does not fit; no clipping, overlap or inaccessible controls.
- Respect status/navigation bars, cutouts, rounded corners and safe drawing insets.
- Keep bottom navigation usable with gesture and button navigation.
- Maintain readable contrast and meaningful semantics/content descriptions.
- Interactive targets are at least 48dp wherever practical; body text is at least 14sp unless system scaling or platform conventions require larger text.

## 7. Keyboard and form behavior

- Configure Android window resizing (adjustResize) and Compose WindowInsets/imePadding or framework-equivalent behavior.
- When the keyboard opens, keep the focused field visible; forms and Settings scroll to the focused input.
- Bottom navigation must not cover fields or primary form actions.
- Keep Save, Upload and Apply actions reachable while the keyboard is open.
- Use appropriate keyboard types: numeric for port, URI/URL for domains and password input for secrets.
- Configure Next to move to the next field and Done to close the keyboard where appropriate.
- Tapping outside an input dismisses the keyboard without accidentally triggering destructive actions.
- Preserve entered text through rotation and keyboard show/hide, using lifecycle-aware state/ViewModel or saved state.
- Logs and Files search bars remain visible above the keyboard.

## 8. Code and implementation rules

- Keep UI code modular and composable; avoid a monolithic activity/screen.
- Maintain one central theme definition for colors and typography. Do not scatter color literals across screens.
- Follow the active PocketHost palette until the owner explicitly approves a palette change.
- Use reusable components for cards, status, empty states, dialogs, filters and labeled actions.
- Keep UI state derived from real application/service state; do not duplicate server truth in local UI toggles.
- Use accessible labels for icons and controls.
- Do not introduce dependencies or new product capabilities without approval.
- Re-read this document before building each screen.
- Screen sequence: **Dashboard → Settings → Files → Logs**.
- The owner explicitly authorized the four-screen UI implementation in one pass on 2026-11-15. Report the completed pass and actual validation evidence together. Ask before adding features or making new major product decisions.

## 9. Required validation matrix after each screen

Test and report the result for each of these configurations:
- Widths: 320dp, 360dp, 411dp and 600dp.
- Portrait and landscape.
- Largest supported/system font size.
- Keyboard open for screens/forms containing inputs.
- Navigation bars, safe insets and scrolling.
- Empty, loading, success, error and relevant server lifecycle states.

Do not claim a configuration was tested unless a test or inspection actually ran. If automated screenshot/device coverage is unavailable, state that limitation and do not mark the matrix complete.

## 10. Change Log

- **2026-11-15:** Initial UI specification recorded from the owner's Phone Web Host design brief.
- **2026-11-15:** Owner explicitly approved the classic navy palette and set the app display name to **AssayFlux Pocket Host**. Repository name and technical identifiers remain unchanged. UI implementation was awaiting owner approval at the time of this entry.

- **2026-11-15:** Owner authorized implementation in one pass. Added the four-tab Compose shell, top-bar server status, central navy light/dark theme, one Start/Stop control, telemetry warnings, local URL copy/share, file metadata listing, website import/backup actions, Logs filters, and an in-app loopback WebView preview.
- **2026-11-15:** Implementation boundary recorded: request-level IP/path/status logs, local TLS and built-in visitor password controls are not represented as working controls because they are not implemented. File create/edit/delete/index selection, operational-log clearing and opt-in boot start are now implemented; physical boot behavior and responsive/device acceptance remain untested. Device-size/landscape/large-font/keyboard screenshot validation remains pending; CI source compilation is the first gate.

- **2026-11-15:** Quality CI run [43692174009](https://github.com/AssayFlux/PocketHost/actions/runs/43692174009) passed all six jobs on implementation commit `d291557e3aed4c0412590d80af75df196d9632a6`, including Android Kotlin and Android-test source compilation. No APK or emulator/device screenshot matrix was run.

- **2026-11-15:** Added guarded static-site file create/edit/delete and HTML entry-page selection, with traversal checks, 256 KiB text-editor limit, hosting-stopped requirement and destructive-action confirmations. Added confirmed operational-log clearing.
- **2026-11-15:** Added opt-in boot receiver, battery-optimisation settings shortcut, About/connection-security information and persistent elapsed-time uptime display.
- **2026-11-15:** Quality CI [43693751852](https://github.com/AssayFlux/PocketHost/actions/runs/43693751852) passed all six jobs on `ad0ccab69920b32070d4ef60fe866a6c413c1a7c`, including Android Kotlin and Android-test source compilation. No APK, emulator screenshot matrix or physical-device boot/tunnel test was run.
