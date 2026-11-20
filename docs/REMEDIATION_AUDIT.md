# Pocket Host — Remediation Audit Register

**Audit date:** 2026-11-18  

**Follow-up source/CI recheck:** 2026-11-20. The original audit findings are historical; statuses above have been reconciled against the current Alpha tree and newly observed CI.
**Repository:** `AssayFlux/PocketHost`  
**Protected source branch:** `Alpha`  
**Historical remediation branch:** `audit/remediation-baseline-2026-11` (no longer the active work branch; its recorded code commits are ancestors of Alpha commit `2a5193d1acdbe36cdff8c70eb420693029f898a6`)  
**Starting source commit:** `2087938a35dc88ed7588d97ffed8bd65fe8b40a9`  
**Starting recursive tree:** 259 blobs, 72 directories; GitHub reported `truncated=false`.

This register separates confirmed defects from documentation drift, maintainability concerns and roadmap gaps. **Implemented / verification pending** means code changes and/or tests are committed on the remediation branch, but the relevant test suite has not yet run. It is not a release-readiness claim.

## 1. Severity-ranked findings

| ID | Severity | Area | Finding and evidence | Correction | State |
|---|---|---|---|---|---|
| PH-001 | Critical | CI security | The former PR quality job granted `contents: write`, ran PR-controlled build scripts and exposed a token to a later push. This was a credible attack path, not evidence of exploitation. | PR #102 removed Relay CI write credentials and bot push behavior, committed the Relay lockfile and uses locked dependency resolution. | Fix merged to Alpha; fresh quality/PR CI verification pending |
| PH-002 | High | Relay transport | Android allowed 8 MiB request bodies / 12 MiB JSON events but limited one SSE line to 64 KiB, so larger visitor requests could disconnect and time out. | SSE line framing ceiling was aligned with the 12 MiB event limit plus `data: ` framing; Rust and Android framing regression tests were added. | Implementation and Rust tests merged; Android instrumentation pending |
| PH-003 | Medium | Android networking | Endpoint validators previously accepted cleartext aliases not permitted by Android network security configuration. | Development HTTP endpoints are restricted to `127.0.0.1`; public endpoints remain HTTPS-only and negative instrumentation cases were added. | Implementation merged; Android instrumentation pending |
| PH-004 | Medium | ZIP import | ZIP import previously did not consistently reject duplicate, case-only or file/directory prefix collisions. | Site import and full-app backup restore now validate portable paths, explicit/implicit directories and entry types before extraction. Regression fixtures cover import collisions and encrypted-backup case/prefix collisions; exact duplicate backup central-directory entries remain an additional fixture opportunity. | Implementation/tests merged; Android instrumentation pending |
| PH-005 | Medium | Capacity docs | Documentation previously conflated the 500,000,000-byte Rust/ZIP limits with 50 MiB manual single-file import and 256 KiB editor limits. | The source-backed limits are now distinguished across the capacity and release documentation; ZIP input and expanded site limits are each 500,000,000 bytes. | Documentation reconciled; physical near-limit acceptance remains open |
| PH-006 | Medium | Relay docs | Older documentation conflated RelayAgent and RemoteControlAgent credentials. | Documentation now describes the dedicated RelayCredentialStore and independent HostingService startup; remaining docs should be checked during final release-documentation sweep. | Core docs reconciled; final documentation sweep pending |
| PH-007 | Medium | Baseline drift | Historical baseline inventories and source/tree counts were presented as current. | Baseline docs label historical inventories and record source commit/tree metadata; final accepted-source snapshot and artifact inventory remain part of G9. | Historical docs reconciled; final release snapshot pending |
| PH-008 | Low | Backup roadmap | Documentation previously misstated backup capacity relative to site import limits. | Roadmap and baseline docs now distinguish static-site, Python, Node, total uncompressed and encrypted-output quotas. Near-limit physical-device and interrupted-restore acceptance remains open. | Documentation reconciled; device acceptance pending |
| PH-009 | Low | Repository footprint | Nine design SVGs under `Assets/` total approximately 115.6 MB. Root `Pocket Host 1.svg` is an exact duplicate blob of `Assets/Pocket Host 1.svg` (14,353,338 bytes). | Leave assets untouched until references/ownership are checked; record optimization candidate. | Documented |
| PH-010 | Low | Android build efficiency | Gradle previously computed selected ABIs while cargo-ndk built all three for each per-ABI build. | Selected ABI values are now passed through to cargo-ndk; all declared Relay ABIs have passed a CI build at least once. | Implemented; latest all-ABI run pending |

## 2. Source-backed capacity contract

| Operation | Limit | Meaning |
|---|---:|---|
| ZIP input | 500,000,000 bytes | Compressed archive |
| Expanded website | 500,000,000 bytes | Sum of extracted files |
| Site files / entries / depth | 5,000 / 10,000 / 32 | Import bounds |
| Rust individual response file | 500,000,000 bytes | Static serving ceiling |
| Manual single-file import | 50 MiB | `SiteFileManager.importFile` |
| Text editor | 256 KiB | Read/write editor |
| Backup static site | 500,000,000 bytes | Static tree quota |
| Backup Python / Node | 50 MiB / 100 MiB | Separate runtime quotas |
| Backup total uncompressed | 650,000,000 bytes | Archive-wide expanded cap |
| Backup encrypted output | 680,000,000 bytes | Output archive cap |
| Relay body / event / SSE line | 8 MiB / 12 MiB / 12 MiB + 6 chars | Request body, JSON payload, `data:` framing |

MB is decimal; MiB is binary. Do not call 500,000,000 bytes 500 MiB.

## 3. Important gaps, not misclassified defects

1. **Native Android Relay implementation exists; production acceptance is still open.** Alpha now contains a Kotlin foreground service, JNI bridge and Rust visitor gateway; authenticated outbound host sessions, owner pairing, durable credential/revocation state, DNS TXT validation and ACME certificate issuance/renewal are implemented. Remaining proof includes real DNS/TLS lifecycle, process/device restart recovery, visitor/management route isolation and acceptance from independent public networks on named physical devices.
2. **Public ingress implementation and spoof protection are automated; live acceptance remains open.** The shared Caddy trusted-proxy policy is mounted by production Compose. Quality run #44687694785 passed Caddy validation, trusted/untrusted peer spoofing and Compose checks after PR #103 corrected the client-IP placeholder. Real-domain DNS/TLS/content acceptance and a real Cloudflare Tunnel still need independent-network verification.
3. **Physical Android acceptance is open.** CI compile/package and API 26/33/35 emulator coverage do not prove OEM battery behavior, physical lifecycle, thermal/storage pressure or public connectivity.
4. **Debug APKs are not production release evidence.** They are not a signed production release or provenance attestation.
5. **Runtime companion is not a hostile multi-tenant OS sandbox.** Python and Node code run under the companion app UID.
6. **Relay live sessions are intentionally ephemeral.** Durable host identities, credential verifiers and route configuration permit reconnect; active SSE sockets and pending visitor requests are not replayed after gateway restart. A Rust restart regression test passed, but Android process-death and live-traffic recovery remain unproven.
7. **Near-limit backup/import acceptance remains open.** Constants and ZIP regression fixtures do not prove 500 MB boundary behavior, free-space preflight, peak memory or interrupted restore on physical devices.
8. **Android PR verification has improved but is not yet a release gate.** Kotlin and instrumentation-source compilation, native Relay Rust tests, all-ABI native builds and API 26/33/35 emulator instrumentation are configured for PRs and Alpha pushes. PR #109 adds a negative compile probe; its CI execution is still pending. Required branch-protection checks remain unverified.

## 4. Verification plan — evidence snapshot (2026-11-20)

| Check | Latest observed evidence | State |
|---|---|---|
| Rust engine | Quality run #44687896464 on PR #104 passed the Rust job, including the expanded sensitive-path tests. PR #105 adds runtime-transfer parity and more exclusions; its CI is queued. | PR #104 passed; PR #105 rerun pending |
| Control plane / Caddy | Quality run #44687150775 exposed the literal `{http.request.client_ip}` placeholder. PR #103 corrected it; quality run #44687694785 then passed all jobs, including Caddy validation, trusted/untrusted spoofing and Compose checks. | Passed on PR #103 commit; live tunnel acceptance remains open |
| Android Kotlin compile | Android compile passed on Alpha run #44684951777; PR #102 run #44687520015 has Android compile in progress with the updated workflow and tests. | Earlier pass; latest matrix in progress |
| Android instrumentation | PR #102 run #44687520015 is exercising the API 26/33/35 matrix; API 26 is in progress while the serialized API 33/35 jobs are queued. PRs #95, #98 and #101 add further instrumentation coverage. | In progress; no complete successful matrix yet |
| Native Relay Rust | Rust tests passed in PR #93 and PR #102 validation; PR #104's expanded static-serving deny-list tests also passed the Rust job in run #44687896464. | Passed on observed PR commits; latest Alpha verification pending |
| Relay / Cloudflared Android ABIs | All declared native ABIs and Cloudflared architecture checks passed in run #44684951777; PR #102 reran these successfully in run #44687520015. | Passed on PR #102 commit; latest all-ABI rerun pending |
| Python | Quality run #44687150775 passed. | Passed on that Alpha commit |
| Console | Quality run #44687150775 passed. | Passed on that Alpha commit |
| Node smoke | Quality run #44687150775 passed. | Passed on that Alpha commit |
| Protocol contracts | Latest Alpha protocol validation runs passed. | Passed on observed commits |
| Public Relay | Real DNS/TLS and visitor request from an independent network. | Not evidenced |
| Physical Android | Named physical devices, Android versions, battery/network/restart logs. | Not evidenced |

A green job is evidence only for the exact source SHA and target it ran against. New source changes reopen the relevant verification until the updated commit passes.

## 5. Remediation commit trail

- `7e9430a59dd9cf7163792fad1a6fcb2f8c881608`: CI token write path removed; Relay SSE line limit aligned with event limit; ZIP collision checks added.
- `5d7831d23d99acaf1510753db219ded1b3bf4993`: Relay framing and ZIP negative instrumentation tests added; cleartext validators restricted to 127.0.0.1; cargo-ndk uses selected ABIs.
- Baseline snapshot commit: `66c904c59b841786b9212f0871cfc590cd0de386`; tree `fee696d4b8c4f4c46d8ada3b3a8e0813b254e064` (260 blobs, 72 directories; recursive tree not truncated).
- Follow-up PR trail: #88 security hardening; #89 Relay ACME compile fix; #90 trusted proxy chain; #91 file-manager completeness; #93 Relay restart recovery test; #94 backup restore Kotlin compile fix; #95 lifecycle-aware dashboard; #97 trusted-proxy spoof test; #99 production proxy-policy mount; #101 Server Only mode regression; #102 read-only Relay CI; #103 Caddy client-IP placeholder; #104 expanded credential-path exclusions.

## 6. Audit limitations

This is a source/configuration audit with targeted cross-file tracing, not a formal penetration test, full compiler run or exhaustive manual line-by-line proof of every file. The starting Git tree was complete (not truncated), but audit depth varies by subsystem. No production credentials, deployment environment, physical device or live public Relay endpoint were exercised.


## 7. Follow-up deep-audit findings and release placement

The following issues were identified in the subsequent code/UI/build review. They are distinct from PH-001–PH-010 above. Unless a row explicitly says otherwise, these remain OPEN and must not be treated as fixed. The release assignment below is the planning target; the implementation must be rechecked against current source before a fix is committed.

| ID | Priority | Classification | Finding / affected area | Required correction and regression proof | Release |
|---|---|---|---|---|---|
| PH-011 | P0 | v0.1.0 public-ingress acceptance | Recheck of current Alpha found a native Android Relay implementation: Kotlin foreground service/JNI bridge, Rust visitor gateway, outbound authenticated SSE host sessions, short-lived single-use pairing invites with host-key proof, SQLite state, Android Keystore identity/config protection, DNS TXT validation and ACME TLS issuance/renewal. This is materially beyond the 2026-11-18 audit's manifest-scaffold finding, but does not prove production readiness. | Complete Android ABI/APK and service lifecycle tests; prove route/management isolation, pairing/revocation, TLS renewal, host reconnect and process/device restart recovery; then accept traffic from independent public networks on named physical devices. | Implementation present; end-to-end acceptance open |
| PH-012 | P0 | Security defect | Alpha previously allowed common credential files/directories into public website trees and the static server could serve them. | PR #104 expanded cloud SDK, Terraform/Pulumi, Firebase Admin SDK and SSH credential exclusions. PR #105 aligned the deny-list across Android import/file/backup, Rust static serving and runtime project transfer; it adds Azure/PKI/secrets directories and mobile provisioning profiles. | Policy parity merged; PR #104 Rust/quality tests passed; PR #105 Android/Rust verification pending |
| PH-013 | P0 | Public-ingress security defect | Caddy previously did not configure a narrowly scoped trusted-proxy chain for Cloudflared; visitor IP forwarding could collapse to the Cloudflared container address. | PR #97 adds a Docker spoofing test using the same shared policy as production; PR #99 mounts that policy into the Caddy service; PR #103 fixes the Caddy client-IP placeholder discovered by the first failing test. | Trusted/untrusted spoof test and quality CI passed in run #44687694785; real Cloudflare Tunnel/public visitor acceptance pending |
| PH-014 | P1 | Mode-enforcement defect | Server Only must not poll the fleet control plane, even when credentials remain configured, and local website hosting must continue. | PR #101 adds instrumentation that starts the website engine and agent in Command Center mode, switches to Server Only, invokes the service start path and asserts the agent stops while the website engine remains RUNNING. | Regression test merged to Alpha; Android CI pending |
| PH-015 | P1 | Archive-integrity defect | Full-app backup restore must reject duplicate ZIP paths, case-folded collisions and file/directory prefix conflicts before activation. | PR #98 adds encrypted-backup fixtures for case-only collisions and file/directory prefix conflicts. PR #111 adds a malformed authenticated encrypted ZIP fixture with an exact duplicate local/central-directory entry and asserts restore fails without changing the active site. | All three collision classes have regression fixtures merged; Android instrumentation pending |
| PH-016 | P1 | Android security hardening | Rust local loopback client-IP logic previously trusted `CF-Connecting-IP` from any loopback peer. | The engine now keys rate limits to the socket peer and ignores forwarding headers until an authenticated proxy contract exists; spoofing regression test is included and the Rust CI suite passed. | Implemented on Alpha; Rust CI passed; device test pending |
| PH-017 | P1 | Android build/release gap | Alpha's Android integration workflow previously did not trigger on pull requests, so quality checks could pass without compiling Android. | PRs compile Kotlin and instrumentation sources, run Relay Rust tests, build all declared native ABIs and run API 26/33/35 emulator instrumentation. PR #102 removes Relay CI write credentials and bot pushes and uses the committed lockfile with `--locked`. PR #109 adds a negative compile probe that injects malformed Kotlin and requires the compiler log to identify that file. Required branch-protection checks remain to be demonstrated. | Read-only PR CI and negative probe merged; probe CI and emulator matrix pending; branch-protection verification remains owner action |
| PH-018 | P1 | UI/security/reliability | MainActivity polling previously continued while the Activity was stopped, and service-status refresh overwrote transient operation feedback. Theme accent/secondary foreground pairs did not consistently meet WCAG AA contrast. | PR #95 merged lifecycle-aware polling and separate persistent/transient status rendering. PR #113 selects readable foregrounds for all theme accents, adjusts secondary text to at least 4.5:1 across surfaces, tests every light/dark palette text pair, and adds a 150% system-font-scale Hosting dashboard instrumentation test. Broader semantic UI assertions and manual accessibility review remain. | Lifecycle fix merged; contrast/font-scale fixes in PR #113, CI pending |
| PH-019 | P1 | Data-integrity defect | Website file manager previously listed only the first 150 paths and stopped at depth 8 while importer accepts larger/deeper trees. | PR #91 merged complete discovery, search, 100-entry pages and a visible discovered-entry count. Instrumentation covers 165+ files and depth 10. | Merged to Alpha; Android instrumentation pending |
| PH-020 | P1 | Build/reliability defect | Control-plane `cargo check --locked` failed in the latest observed quality workflow; exact failure cause was not captured in the audit record. | Retrieve and record the failing compiler output, correct the root cause, and require locked check/test success. Do not guess the cause or waive the failure. | v0.1.2 |
| PH-021 | P1 | Reliability/security | Linux agent timeout and memory budgets are validated but not enforced by OS/process limits; storage hashing reads entire files into memory. | Apply process/OS resource limits, stream hashing, and test timeout/OOM/large archive behavior. | v0.1.2 |
| PH-022 | P1 | Data lifecycle | Failed/expired remote deployment commands can leave uploaded artifact files/rows behind; device command and audit-event tables have no demonstrated retention policy. | Add lifecycle-aware cleanup, quotas, orphan reconciliation and bounded audit/command retention with tests for failed, expired and abandoned deployments. | v0.1.2 |
| PH-023 | P2 | Workbench correctness | Workbench change summaries accept a live root but compare against an older baseline, so live-site drift can be missed. | Compare against the current live release or explicitly reject stale base revisions; test production changes made outside Workbench. | v0.1.1 |
| PH-024 | P2 | Workbench data integrity | Workbench rename moves a file before validation and does not restore it if validation fails. | Validate in staging or roll back the move on failure; test invalid destination names and filesystem errors. | v0.1.1 |
| PH-025 | P2 | Workbench capacity consistency | Workbench workspace cap is 50 MiB while website import/hosting supports up to 500,000,000 bytes; the lower limit is not prominent in UI. | Either align the capacity with measured device budgets or disclose the lower Workbench limit before opening a project; test boundary behavior. | v0.1.1 |
| PH-026 | P2 | Runtime isolation / product claim | Python and Node execute under a shared companion UID; the network sandbox probe is not wired into ordinary customer execution. This is a documented design limitation, not proof of an exploit. | Either implement and verify per-project OS isolation, egress restrictions and resource quotas, or explicitly constrain supported use to trusted owner code and remove sandbox/security claims. | v0.1.1 |
| PH-027 | P2 | Web-console hardening | Web console has no dedicated test/lint scripts; it polls while hidden, imports Google Fonts, and the repository does not define a CSP (deployment may add one). | Add UI tests/lint, visibility-aware polling, document font/privacy dependency, and verify/set CSP at the actual hosting edge. | v0.1.2 |
| PH-028 | P2 | Maintainability | MainActivity is approximately 1,457 lines and mixes navigation, state, polling, import, pairing and remote actions. CSS is heavily compressed. | Extract ViewModels/use cases and format styles without changing approved design; require UI regression coverage. | v0.1.1 |
| PH-029 | P2 | Runtime/agent scope gap | Linux agent executor implements only a subset (4 of 9) of schema job kinds; unsupported jobs are default-denied. | Keep unsupported jobs explicitly rejected, document the accepted subset and implement additional kinds only with role-specific resource and negative tests. | v0.1.2 |
| PH-030 | P2 | Relay reliability | Live SSE sessions and pending visitor requests are intentionally ephemeral; gateway restart must not replay stale requests, while durable host credentials/revocations and configured routes must permit safe reconnect. | PR #93 merged a Rust restart regression test for credential persistence, empty session/pending maps after reconstruction, fail-closed pending response behavior and fresh host reconnect. Physical Android process-death and live-traffic recovery remain required. | Rust PR CI passed; Android/device acceptance pending |
| PH-031 | P1 | Acceptance evidence gap | No recorded physical Android device and independent public-network acceptance proves the full v0.1.0 journey yet. | Complete the G0–G9 acceptance matrix on named hardware and external networks; record artifact hashes, DNS/TLS/content and management-route isolation evidence. | v0.1.0 |

## 8. Required implementation order

1. Close v0.1.0 public-ingress and static-site security blockers first: PH-011–PH-019 and PH-030–PH-031.
2. Complete v0.1.0 Android build, physical-device, recovery and external-network acceptance; keep G0–G9 open until evidence is recorded.
3. Complete v0.1.1 Workbench and optional runtime corrections: PH-023–PH-026 and PH-028.
4. Complete v0.1.2 fleet/control-plane and Linux-agent hardening: PH-020–PH-022, PH-027 and PH-029.
5. After v0.1.0 gates all pass and the owner approves the release, lock the exact commit/artifacts under the policy in [RELEASE_BASELINE_POLICY.md](RELEASE_BASELINE_POLICY.md). No later feature or ordinary fix is committed to that baseline.


## 5. Current-Alpha re-audit addendum — 2026-11-20 (supersedes stale CI snapshot above)

**Exact audited source:** `Alpha` at `cb5a925b98cf66510b9d38af37fe1c5cca846b84`. Recursive Git tree: 290 blobs / 75 directories, not truncated. This addendum supersedes historical statements above that Android CI is configured or merely pending on the current Alpha source.

### Fresh findings

| ID | Severity | Finding | Current evidence / disposition |
|---|---|---|---|
| PH-011 | Critical release-process gap | The current Alpha tree contains only `.github/workflows/quality.yml`. The former `.github/workflows/android-integration.yml` and separate protocol workflow are absent. | Current quality run #44699649949 passed six non-Android jobs only. It does not compile the Android app, build Relay/Cloudflared Android ABIs, compile instrumentation tests or run API 26/33/35 emulators. Restore Android CI before G3 can close. |
| PH-012 | High release-evidence gap | No release tag or published GitHub release is present in the checked listings. | No production APK/AAB, signing evidence, digest, provenance or SBOM is evidenced for current Alpha. G4/G9 remain open. |
| PH-013 | Medium documentation drift | Multiple docs still describe old workflow runs/checkpoint SHAs as current, and README contains contradictory Relay implementation claims. | Update the gate ledger, deep audit, UI audit and README to pin the current source and distinguish source presence from verification. |
| PH-014 | Medium repository footprint | Current tree contains 290 blobs / 75 directories. | Earlier inventory counts (259/260 blobs) are historical and must not be presented as current. The large SVG asset footprint remains a separate optimization candidate; do not delete assets without reference/ownership review. |

### Current source and CI facts

- Latest Alpha commit is merge commit `cb5a925b98cf66510b9d38af37fe1c5cca846b84`, merging PR #119 “Add the AssayFlux author signature to PocketHost About”.
- PR #118 “PocketHost v0.1.0 UI architecture and responsive acceptance pass” is merged. UI source modules and dedicated-page instrumentation test definitions are in the tree.
- The latest exact-Alpha workflow run [#44699649949](https://github.com/AssayFlux/PocketHost/actions/runs/44699649949) completed successfully. Its six green jobs are `rust`, `control-plane`, `python-runtime`, `console`, `node-runtime` and `gradle-wrapper`. It is not an Android build/test workflow.
- The quality workflow runs Rust website-engine tests, Control Plane/Postgres tests, Caddy validation, trusted-proxy spoof tests, Compose profile validation, Python/Linux-agent tests, web-console build, Node smoke test and Gradle wrapper validation.
- Android source contains a native Relay foreground service, JNI bridge, Rust gateway/ACME implementation, Keystore-encrypted config store and credential/revocation persistence. This is implementation evidence, not proof of a live public Relay deployment or physical-device acceptance.
- The README's older statements that the Relay foreground service/TLS/identity/persistence are merely planned or absent conflict with the current source. The Relay docs and source comments should be reconciled carefully; do not infer operational acceptance from code presence.
- The UI contains dedicated Home, Hosting, Sites, Workbench, Devices, Activity, Learn and Settings surfaces. Deployments, in-app Cloudflare Access policy management and automated DNS record/continuous TXT management are explicitly marked coming soon/partial in the UI audit.
- About/Author branding is merged: AssayFlux Pocket Host, v0.1.0, AssayFlux attribution, signature component and Md Safarujjaman.

### Updated disposition

The prior source-level remediation findings PH-001 through PH-010 remain historical findings with their recorded code changes, but their old CI status rows must not be treated as current-source evidence. Current CI demonstrates broad non-Android quality checks only. Android compilation/instrumentation, APK integrity, physical acceptance, public ingress acceptance and release publication remain open. See the current [v0.1.0 gate ledger](V0.1.0_GATE_STATUS.md) for the gate-by-gate decision.
