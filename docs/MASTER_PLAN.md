# Pocket Host — Master Development Plan

**Repository:** AssayFlux/PocketHost  
**Working branch:** Alpha  
**Current delivery target:** v0.1.0 — First Hosting  
**Roadmap:** v0.1.0 → v0.1.1 → v0.2.0  
**Planning rule:** Every version passes G0–G9 before release

## 1. Product objective

Pocket Host is a modular personal infrastructure platform. The first deliverable is a reliable Android website host: import a static website, serve it locally and over LAN, and make it reachable worldwide through either optional Cloudflare Tunnel or the owner's own AssayFlux Relay Node using the owner's domain over HTTP/HTTPS. The Relay Node is a separate Termux/Android or Linux product; a paid VPS is not required, but public IPv4/IPv6 reachability is. Later releases add safe in-app development and a connected device fabric.

The old five-step delivery sequence is retired. The new three-version pathway is the only active release roadmap until the owner approves an expansion.

## 2. Non-negotiable product rules

1. The Android phone is the first supported website-serving device and the owner-facing mobile interface.
2. Exactly two mobile modes exist: **Server only** and **Command Center + Server**. Both serve websites. There is no Command Center-only mode.
3. The existing Pocket Host Design Language V1 is preserved. No redesign is authorized by this roadmap. Keep the established minimal monochrome visual language, calligraphic brand treatment, readable typography, classic themes, light/dark/system appearance, intended login/account experience, owner menu and hamburger navigation. Source baseline note: the current native app has optional biometric unlock but no conventional login page yet; browser-console owner/passkey login is separate. Fix implementation defects without changing the design identity.
4. v0.1.0 static hosting works without Python, Node.js or any companion APK.
5. Website folder/ZIP import limit is 500,000,000 bytes for the ZIP archive and independently 500,000,000 bytes for extracted website contents. File count, entry count, depth, path and sensitive-file limits remain enforced.
6. The Rust engine's 50 MiB per-file response cap is separate from the 500 MB website import limit.
7. Python and Node.js already have repository implementations in the separate runtime-worker companion APK. They are optional, in progress and assigned to v0.1.1 acceptance; do not describe them as completed or securely sandboxed.
8. React + TypeScript + Vite in web-console are the separate browser operator console. They are not Android app runtimes. A React/Vite website may be served in v0.1.0 from its prebuilt static output.
9. The main-app TypeScript-to-JavaScript compiler is a v0.1.1 authoring/build feature, not a v0.1.0 static hosting dependency.
10. Workbench edits a private workspace, never the active production site. Preview, validated publishing, owner approval and rollback are v0.1.1.
11. External devices use separate software for their OS/architecture. Discovery is not trust. Integration requires identity proof and explicit owner approval.
12. Worker roles are bounded, separately authorized and revocable. No arbitrary shell or unrestricted filesystem permission is implied.
13. Local website hosting never depends on public ingress, the remote control plane or another device. Public ingress is optional and must fail independently without stopping local/LAN hosting.
14. Source presence, UI presence, compilation and simulation are not product acceptance. Support claims require named target hardware/OS tests.
15. Every release must define and pass G0, G1, G2, G3, G4, G5, G6, G7, G8 and G9. This rule is permanent.
16. The current full-app backup implementation supports a 500,000,000-byte static-site root, 650,000,000-byte total uncompressed archive and 680,000,000-byte encrypted archive. The static-site backup cap matches the importer's expanded-content allowance; Python and Node roots remain separately limited to 50 MiB and 100 MiB. Near-limit backup/restore and physical-device free-space tests remain mandatory.
17. Public visitor ingress must terminate at the selected website only. Management API, owner sessions, device enrollment, runtime brokers, Workbench drafts and MCU controls must never be reachable through visitor hostnames.

## 3. Release pathway

### v0.1.0 — First Hosting

**Promise:** an owner can install Pocket Host on a supported Android phone, import a real static website and serve it locally, over the same Wi-Fi network and publicly through a custom domain using Cloudflare Tunnel or AssayFlux Relay.

In scope:
- Main Android APK and bundled Rust static website engine.
- Folder and ZIP import with staging, path validation and safe activation.
- 500 MB ZIP archive and 500 MB extracted-content caps; 5,000 files, 10,000 entries and 32 levels.
- HTML, CSS, browser JavaScript, common static assets and correct MIME types.
- Local preview, configurable HTTP port and opt-in LAN binding.
- Access from another device on the same Wi-Fi.
- Public custom-domain hosting through Cloudflare Tunnel, using an owner-created named tunnel and encrypted Android token storage.
- Standalone first-party AssayFlux Relay Node installable on Termux/Android and Linux owner hardware, with DNS TXT hostname proof, independent host credentials, authenticated outbound host session and visitor-only routing to the selected static website. No paid VPS/Docker/PostgreSQL prerequisite; public IPv4/IPv6 reachability is required.
- Minimal owner-facing Relay setup surface and Termux/Linux install/start/stop/status flow for registering a host/site, creating a hostname binding, displaying the DNS TXT proof challenge and revoking the route. The existing web-console panel is currently coupled to the fleet control plane; the v0.1.0 setup flow must work independently. The full browser operator console remains v0.2.0.
- Public HTTP and HTTPS entry points for verified custom hostnames; HTTPS certificates are validated at the public edge and HTTP redirects to HTTPS by default. Plain HTTP content serving is an explicit owner opt-in target; current Relay edge does not implement this option.
- Independent provider status, start/stop, reconnect and diagnostics for Cloudflare Tunnel and the standalone AssayFlux Relay Node. Same-host manual failover is not automatic and requires DNS/load-balancer changes.
- External verification from mobile data or an unrelated network, including DNS, TLS, expected site content and management-route isolation.
- Foreground hosting service, visible notification, start/stop/restart and Android lifecycle behavior.
- Permission, network, battery and thermal-state feedback.
- Existing encrypted full-app backup/restore and interrupted activation recovery, with the backup/import capacity mismatch explicitly resolved before release.
- Diagnostics for host startup, import failure and visitor connection.
- APK build, install, fresh-install/upgrade checks and physical Android acceptance.

Explicitly out of scope:
- Full Workbench editor/preview/publish workflow.
- On-device TypeScript compilation or React/Vite build.
- Python/Node execution as a requirement for static websites.
- Worker enrollment, distributed jobs, production control plane or multi-device roles.
- iOS, Windows, macOS, SBC or MCU certification.

A v0.1.0 website can be built elsewhere and imported as static files. For Vite projects, import the contents of the production dist directory. The website root must contain index.html (the ZIP importer also accepts one top-level directory containing index.html).

### v0.1.1 — Workbench & Runtime Development

**Promise:** the owner can safely develop, preview, version and publish websites inside Pocket Host, and can use optional server-side runtimes.

In scope:
- GitHub-inspired Workbench: project selector, nested Explorer, Editor, tabs, unsaved state, file operations, search and diagnostics.
- Private draft workspace separated from the active immutable website release.
- Isolated preview with no production-secret access.
- Per-file change summaries/diffs, durable local commits and release/deployment history.
- Bounded bundled TypeScript-to-browser-JavaScript compiler and actionable diagnostics.
- Staged immutable releases, digest validation, explicit owner approval, atomic activation and last-known-good rollback.
- Complete and verify the existing Python 3.11 and Node.js 24.22.1 companion integration, including install, IPC, lifecycle, health, crash and upgrade behavior.
- Keep runtimes optional and separately installed from the main app; static serving remains available when absent or failed.
- Multi-site isolation, storage budgets, larger project handling and backup/restore tests.

The existing companion currently contains both runtime implementations in one APK. Independent per-language package distribution is a packaging goal, not a claim about the current artifact.

### v0.2.0 — Connected Device Fabric

**Promise:** the owner can safely connect separately installed devices and delegate explicit infrastructure roles, then scale and coordinate infrastructure beyond the first phone host.

In scope:
- Device identity, enrollment ceremony, owner approval, signed role grants, renewal and revocation.
- Authenticated mobile-to-agent transport and reconnect handling.
- First production worker agent on a named platform and a precise support matrix.
- Bounded storage/backup, traffic, build/publish preparation and native-job roles.
- Durable job contracts, audit, resource budgets, health, signed updates and rollback.
- Optional Rust control plane and browser operator console.
- Multi-host Relay scheduling, regional gateway scaling and advanced health-based failover beyond the single-host public-ingress baseline delivered in v0.1.0.
- Strict separation of public visitor routes from management, runtime broker and MCU controls.
- Cross-device recovery, fault isolation and operational security acceptance.

## 4. Mandatory G0–G9 release rule

The normative gate definitions and per-version matrix are in [RELEASE_GATES.md](RELEASE_GATES.md).

- Every release has exactly ten gates, G0 through G9.
- Each gate records owner, measurable criteria, source commit, build, target, test procedure and evidence.
- Allowed states: NOT_STARTED, IN_PROGRESS, BLOCKED, FAIL and PASS.
- PASS requires implementation and actual test evidence on the required target.
- A document, screenshot of an unimplemented UI, successful compile or emulator-only test cannot independently close a physical-device gate.
- No gate may be skipped, waived silently or marked PASS by schedule pressure.
- All ten gates must be PASS before release approval.
- A code or scope change that invalidates evidence reopens the affected gate and its dependent gates.
- Release record includes source SHA, workflow URL, artifact SHA-256, supported devices/OS, test evidence, known issues and owner sign-off.

## 5. Execution discipline

1. Work only on the active release scope unless a blocking dependency is documented.
2. Finish the v0.1.0 vertical slice before expanding Workbench, runtime or fleet work.
3. Audit and reuse existing components; do not create duplicate registries, site roots or design systems.
4. Keep UI, importer, backup, API and documentation limits consistent.
5. Test failure paths as well as success paths: invalid ZIP, path traversal, low storage, process death, permission denial, network loss and recovery.
6. Build installable APK artifacts explicitly. Source compilation is not packaging.
7. Preserve the established UI design and Android settings behavior.
8. Update README and affected docs in the same change as scope or limit changes.
9. Never claim a gate is closed without a reproducible evidence reference.
10. Record every known limitation and the exact release where it will be addressed.

## 6. Active work order

The current target is v0.1.0. Complete the import-and-host path, Cloudflare integration and the standalone owner-operated Termux/Linux Relay Node. Verify 500 MB boundaries and backup recovery, test folder/ZIP imports, run the Rust static engine, validate localhost/LAN, implement the standalone public gateway and node-side credential issuance, test DNS/TLS/HTTP/HTTPS from an independent network, exercise foreground/gateway lifecycle and recovery, build the APK and Relay Node artifacts, and test on named physical devices. Workbench completion, runtime acceptance and full device-fabric integration remain in later releases.
