# AssayFlux Pocket Host — UI Design

**Status:** Five-destination Compose UI is implemented; top app chrome and Activity are extracted into dedicated files. Dashboard, Sites and Settings remain in the main composition and are being decomposed incrementally. Device/responsive screenshot acceptance remains pending.
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

- Bottom navigation destinations: **Dashboard**, **Sites**, **Activity**, **Learn** and **Settings**.
- Top app bar: **AssayFlux Pocket Host** on the left and a server-status indicator/dot on the right.
- Keep navigation usable with gesture and three-button system navigation.
- Preserve state appropriately when switching tabs; do not discard in-progress user input unexpectedly.
- Use labels with icons; never rely on icon-only navigation or actions where a text label is practical.

## 4. Screens

The active implementation uses five destinations: Dashboard, Sites, Activity, Learn and Settings. Sites owns website files and runtime projects; Activity owns operational logs. The owner authorized the four-screen UI implementation in one pass on 2026-11-15.

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

### 4.3 Sites and website files

- List site files and folders with an appropriate icon, size and modified date.
- Actions requested: upload, create new, edit, delete (with confirmation), and set `index.html`.
- Include a **Preview site** action that opens the site in an in-app WebView.
- Validate paths and preserve the existing site's import/staging/rollback safety boundaries.
- Do not imply arbitrary file editing or write access until the underlying capability is confirmed.
- Destructive actions require a clear confirmation dialog.

### 4.4 Activity and logs

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


## 11. Card-first navigation and Learn

- The owner requested a compact, interactive control-panel experience inspired by the supplied dashboard/login references.
- Use five compact destinations: **Dashboard**, **Sites**, **Activity**, **Learn** and **Settings**. Sites contains website import and file management; Activity contains operational logs.
- Use minimal monochrome iconography with consistent visual weight. Avoid emoji icons, gradients, neon and glass effects.
- Make the dashboard a card overview. Each card presents concise status/metrics and opens a focused detail area; avoid presenting every setting as one long continuous document.
- Use interactive charts only when backed by measured data. Label units, time window, sample count and limitations. Never fabricate time-series history from one current sample. Capacity Lab p95 measurements may be charted by concurrency; do not imply visitor-traffic trends.
- Add expandable beginner-friendly guides for website import, local/LAN/public access, dashboard metrics, capacity benchmarking, trusted Python/Node runtimes, activity/logs, backup/restore and device safety. Guides must reflect current implementation boundaries.
- Account/login UI must distinguish Owner and Operator. A login mockup is not security: credentials, sessions, roles and revocation must be enforced by the management API before account controls are called secure. Biometric unlock is optional and must use Android biometric APIs.
- The supplied pastel-pink reference is a layout/interaction reference, not approval to replace the approved navy palette.
- Responsive behavior remains subject to the validation matrix in section 9.

## 12. Change Log

- **2026-11-15:** Added Sites, Activity and Learn navigation destinations and an expandable in-app learning guide. Added first-use, domain/tunnel, dashboard, capacity, runtime safety, activity, backup and phone-safety lessons. Updated card-first design and chart-data integrity requirements. Device screenshot and accessibility validation remains pending.


## 13. Host accounts and console access

- Device pairing remains a physically approved bootstrap path. Use it only when an operator can approve the request on the phone.
- The first paired Owner session creates the initial Owner username and password before normal password sign-in is used.
- Passwords use salted PBKDF2-HMAC-SHA256 verifiers; the account registry is encrypted with an Android Keystore AES-GCM key in app-private no-backup storage.
- Owner can create and revoke Operator accounts. Operators can read device information, start/stop site runtimes and adjust/benchmark capacity; sensitive configuration and account administration remain Owner-only.
- Device bearer tokens are random, hashed in memory, expire after 12 hours and can be revoked. They are not persistent sessions.
- The web console stores its active token in tab-scoped sessionStorage. Use HTTPS for non-loopback browser origins; never expose the management API through the public website tunnel.
- Biometric unlock is not implemented in this pass; it must use Android BiometricPrompt and a Keystore-backed credential gate, not a simulated web prompt.


## Current repository evidence snapshot — 2026-11-15

This snapshot is anchored to main commit `671f3f731f4bf093449a95e285327115f41bb75b`. Re-check the linked workflow runs before using this document as a release approval; statuses can change after this snapshot.

### Merged implementation

- PR [#16 — interactive Learn section and card-first navigation](https://github.com/AssayFlux/PocketHost/pull/16) is merged. The app/console include the Learn experience and an initial measured-capacity visualization. The chart is based on recorded static benchmark samples; it is not synthetic live telemetry.
- PR [#17 — Owner and Operator host accounts](https://github.com/AssayFlux/PocketHost/pull/17) is merged. The source includes an encrypted app-private account registry, password verifiers, Owner/Operator authorization, account revocation and console sign-in/bootstrap flows. The PR describes instrumentation coverage, but this snapshot does not assert that the latest main-branch emulator workflow has passed.
- The app has a Learn destination and documentation at [LEARN_GUIDE.md](LEARN_GUIDE.md). The UI remains an initial implementation requiring responsive and device acceptance.

### Exact CI evidence at review time

- Main commit: `671f3f731f4bf093449a95e285327115f41bb75b`.
- [Quality workflow 43699334752](https://github.com/AssayFlux/PocketHost/actions/runs/43699334752): completed successfully. Its six source-quality jobs include Rust, control plane, Python, Node, TypeScript console and Android source compilation.
- [Android integration workflow 43699334785](https://github.com/AssayFlux/PocketHost/actions/runs/43699334785): pending when inspected. Do not infer APK assembly or emulator instrumentation success from the quality workflow.

### Remaining release gates — not completed by these merges

1. **Android acceptance:** finish integration workflow; verify APK packaging/signatures/ABI contents; pass API 26/33/35 instrumentation on the exact release SHA; test real ARMv7/ARM64 phones, foreground service, Doze, reboot, battery/thermal behavior, keyboard, accessibility, landscape and responsive layouts.
2. **Native authentication polish:** Android BiometricPrompt/Keystore unlock, secure Owner recovery, complete native login/logout/expired/revoked-session UX and end-to-end instrumentation remain unverified or incomplete. Console sign-in and API role enforcement do not by themselves prove native biometric login.
3. **Runtime isolation:** Python and Node execute under the Pocket Host app UID. The Node process and V8 heap limit are not a sandbox. Keep optional runtimes restricted to trusted code until OS-enforced filesystem/network isolation and enforceable resource quotas pass adversarial review.
4. **Cloudflare:** configure/reuse the owner's existing named tunnel on a physical device and verify the Public Hostname serves the intended site over HTTPS from an external network. The public hostname must never target the loopback management API.
5. **Capacity:** current benchmark and admission controls are first implementations. Complete representative cached/uncached and payload-size workloads, Python/Node trusted workload measurements, throughput and p50/p95/p99, Rust-native rate/fairness/bounded-queue controls, graceful drain, long-duration thermal tests and schedule restart/expiry acceptance.
6. **Operations:** extend backup beyond static-site scope; test restore and interruption; complete request-level redacted logs, durable/tamper-evident audit verification, storage-pressure behavior and reproducible daily reports.
7. **Optional remote management:** deploy the control plane on a trusted host, pair the real phone, then test desired configuration, leased commands, acknowledgements, revocation and recovery. Source code alone is not deployment evidence.
8. **Payments:** no live payment processing is implemented/approved. If enabled for launch, select a PSP and implement hosted/tokenized checkout, signed/replay-protected webhooks, idempotent durable ledger, reconciliation, encrypted backups and qualified security review. Never store PAN/CVV/PIN.
9. **Release:** configure production signing, verify upgrade paths and checksums, review dependencies/secrets and resolve all critical/high security findings.

### Product boundaries

- Static hosting remains the primary independent feature; optional runtimes, payments and remote management must not be prerequisites.
- Android foreground services improve continuity but cannot guarantee 24/7 uptime or override OEM/OS battery policies.
- Cloudflare Tunnel connectivity is not visitor authentication; use Cloudflare Access for private sites.
- Do not claim universal visitor counts, complete sandboxing, payment compliance, deployed remote control, production readiness or a signed release without the corresponding evidence.

**Release decision:** integrated development build; not production approved. This status changes only when the relevant code, tests, device/network evidence and security review are recorded against the exact release commit.

## 14. Compact control-panel refinement — 2026-11-15

- Replaced text-glyph bottom navigation with consistent Material outlined icons and compact labels.
- Reduced dashboard helper copy so live state and measurements carry the visual hierarchy.
- Split Settings into General, Network, Runtimes and About sections; only the selected group is displayed, reducing scrolling and cognitive load.
- Kept the approved navy palette, five primary destinations, existing hosting controls and optional Python/Node runtime workflows.
- The Learn guide now starts collapsed so users see the quick-start card and open only the guide they need.
- The Dashboard hosting control uses a navy status hero with a high-contrast action.
- Validation is pending the PR quality and Android integration workflows. Responsive screenshots and real-device acceptance are not claimed by source compilation alone.
