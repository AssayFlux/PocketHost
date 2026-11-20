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
| PH-001 | Critical | CI security | `.github/workflows/quality.yml` granted `contents: write` to a PR job, ran PR-controlled Cargo build scripts, then exposed `GITHUB_TOKEN` to a later push. A malicious same-repository PR could potentially plant a Git hook during build and execute it when the later push ran. This is a credible attack path, not evidence of exploitation. | Remove write permission and token-bearing push step; use `cargo check --locked`. | Implemented; CI pending |
| PH-002 | High | Relay transport | Android allowed 8 MiB request bodies / 12 MiB JSON events but limited one SSE line to 64 KiB. The gateway emits one JSON `data:` line; Base64 expands 8 MiB to about 11.2 MiB before envelope overhead. Larger visitor requests could disconnect and time out. | Set line ceiling to 12 MiB plus the 6-character `data: ` prefix; add max-boundary regression coverage. | Implemented; instrumentation pending |
| PH-003 | Medium | Android networking | Endpoint validators accepted cleartext `localhost` (Relay also `::1`) while network security XML permits only `127.0.0.1`. Accepted settings could fail at connection time. | Restrict development HTTP endpoints to `127.0.0.1`; test rejected aliases. Public endpoints remain HTTPS-only. | Implemented; instrumentation pending |
| PH-004 | Medium | ZIP import | `SiteImporter` checked traversal but did not reject duplicate archive paths, case-only collisions or file/directory prefix conflicts. This is an archive integrity/ambiguity gap, not a demonstrated traversal exploit. | Track explicit paths, implicit directories, entry types and case-folded portable names; reject collisions before extraction. Add negative tests. | Implemented; instrumentation pending |
| PH-005 | Medium | Capacity docs | Rust source sets `MAX_FILE_BYTES = 500_000_000`; several docs said 50 MiB. The separate limits are Rust serving 500,000,000 bytes, manual single-file import 50 MiB, and text editor 256 KiB. | Standardize all docs and keep these operations distinct. ZIP input and expanded site content are each 500,000,000 bytes. | Identified docs reconciled; test verification pending |
| PH-006 | Medium | Relay docs | Some docs said Android RelayAgent reused RemoteControlAgent credentials and required remote-control config. Source now uses dedicated `RelayCredentialStore`; HostingService starts Relay independently. | Correct architecture and public-ingress docs. Keep missing native inbound gateway and node-side credential issuer explicit. | Core docs updated; sweep pending |
| PH-007 | Medium | Baseline drift | Baselines cited old commits and 252/257 files while current tree contained 259 blobs and 72 directories; historical inventory was described as current. | Record source commit/tree counts and clearly label the older path inventory historical. | Core docs updated; final refresh pending |
| PH-008 | Low | Backup roadmap | Roadmap claimed backup capacity was below the 500 MB import limit. Source has static 500,000,000 bytes, Python 50 MiB, Node 100 MiB, 650,000,000 bytes total uncompressed and 680,000,000 bytes encrypted. | Replace obsolete mismatch claim; retain near-limit/device testing as open. | Roadmap and baseline docs reconciled; test verification pending |
| PH-009 | Low | Repository footprint | Nine design SVGs under `Assets/` total approximately 115.6 MB. Root `Pocket Host 1.svg` is an exact duplicate blob of `Assets/Pocket Host 1.svg` (14,353,338 bytes). | Leave assets untouched until references/ownership are checked; record optimization candidate. | Documented |
| PH-010 | Low | Android build efficiency | Gradle computed `selectedAbis` but cargo-ndk always built all three ABIs, repeating work in per-ABI CI builds. | Pass selected ABI list into cargo-ndk. | Implemented; Gradle pending |

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

1. **Native Android Relay service is not implemented.** `relay-node/` contains a Cargo manifest scaffold only. The partial visitor gateway remains inside the combined Rust/Axum control-plane process, with PostgreSQL and an in-memory RelayHub. Android host credentials are independent; the Android JNI engine, inbound listener, Kotlin foreground service, node-side credential issuer and durable node store remain missing.
2. **Public ingress acceptance is open.** No independently recorded real-domain DNS/TLS/content test from an external network is established by source inspection.
3. **Physical Android acceptance is open.** CI compile/package and API 26/33/35 emulator coverage do not prove OEM battery behavior, physical lifecycle, thermal/storage pressure or public connectivity.
4. **Debug APKs are not production release evidence.** They are not a signed production release or provenance attestation.
5. **Runtime companion is not a hostile multi-tenant OS sandbox.** Python and Node code run under the companion app UID.
6. **Relay sessions are in-memory.** Active sessions and pending requests are lost on gateway restart.
7. **Near-limit backup/import acceptance remains open.** Constants do not prove 500 MB boundary behavior, free-space preflight, peak memory or interrupted restore on physical devices.

## 4. Verification plan

| Check | Command / evidence | State |
|---|---|---|
| Rust engine | `cargo test --manifest-path rust-engine/Cargo.toml --locked` | Not run in this editing session |
| Control plane | `cargo test --manifest-path control-plane/Cargo.toml --locked` | Not run |
| Android compile | `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin -PpockethostAbi=x86_64` | Not run |
| Android instrumentation | `./gradlew :app:connectedDebugAndroidTest -PpockethostAbi=x86_64` on API 26/33/35 | Not run |
| Python | `PYTHONPATH=agents/linux/src python -m unittest discover -s tests/python -v` | Not run |
| Console | `cd web-console && npm ci && npm run build` | Not run |
| Node smoke | `node tests/node/metrics-smoke.cjs` | Not run |
| Protocol | `python scripts/validate-protocol.py` and protocol tests | Not run |
| Public Relay | Real DNS/TLS and visitor request from independent network | Not evidenced |
| Physical Android | Named physical devices and Android versions | Not evidenced |

## 5. Remediation commit trail

- `7e9430a59dd9cf7163792fad1a6fcb2f8c881608`: CI token write path removed; Relay SSE line limit aligned with event limit; ZIP collision checks added.
- `5d7831d23d99acaf1510753db219ded1b3bf4993`: Relay framing and ZIP negative instrumentation tests added; cleartext validators restricted to 127.0.0.1; cargo-ndk uses selected ABIs.
- Baseline snapshot commit: `66c904c59b841786b9212f0871cfc590cd0de386`; tree `fee696d4b8c4f4c46d8ada3b3a8e0813b254e064` (260 blobs, 72 directories; recursive tree not truncated).

## 6. Audit limitations

This is a source/configuration audit with targeted cross-file tracing, not a formal penetration test, full compiler run or exhaustive manual line-by-line proof of every file. The starting Git tree was complete (not truncated), but audit depth varies by subsystem. No production credentials, deployment environment, physical device or live public Relay endpoint were exercised.


## 7. Follow-up deep-audit findings and release placement

The following issues were identified in the subsequent code/UI/build review. They are distinct from PH-001–PH-010 above. Unless a row explicitly says otherwise, these remain OPEN and must not be treated as fixed. The release assignment below is the planning target; the implementation must be rechecked against current source before a fix is committed.

| ID | Priority | Classification | Finding / affected area | Required correction and regression proof | Release |
|---|---|---|---|---|---|
| PH-011 | P0 | v0.1.0 public-ingress acceptance | Recheck of current Alpha found a native Android Relay implementation: Kotlin foreground service/JNI bridge, Rust visitor gateway, outbound authenticated SSE host sessions, short-lived single-use pairing invites with host-key proof, SQLite state, Android Keystore identity/config protection, DNS TXT validation and ACME TLS issuance/renewal. This is materially beyond the 2026-11-18 audit's manifest-scaffold finding, but does not prove production readiness. | Complete Android ABI/APK and service lifecycle tests; prove route/management isolation, pairing/revocation, TLS renewal, host reconnect and process/device restart recovery; then accept traffic from independent public networks on named physical devices. | Implementation present; end-to-end acceptance open |
| PH-012 | P0 | Security defect | Alpha previously allowed common credential files/directories into public website trees and the static server could serve them. | A shared sensitive-path deny-list now covers folder/ZIP/single-file import, file operations, prepared restore, full-app backup export/restore and Rust static serving. Android and Rust regression tests were added. | Implemented on Alpha; Android instrumentation and device verification pending |
| PH-013 | P0 | Public-ingress security defect | Caddy previously did not configure a narrowly scoped trusted-proxy chain for Cloudflared; visitor IP forwarding could collapse to the Cloudflared container address. | PR #90 configures Caddy to trust only the configured Cloudflared IPv4 peer (/32), prefer `CF-Connecting-IP`, and normalize the upstream client IP. Validate Caddy/Compose and test spoofed/untrusted peers plus distinct rate-limit buckets. | Implemented in PR #90; CI and live tunnel acceptance pending |
| PH-014 | P1 | Mode-enforcement defect | The earlier audit reported Server Only could leave the fleet agent active. Recheck of current Alpha found the Server Only UI calls `RemoteControlAgent.stop()` and the agent start guard refuses to run outside `COMMAND_CENTER_SERVER`. | Preserve the existing mode switch and start guard; add/retain a regression proving website hosting remains active while fleet polling stops after a live mode transition. | Source enforcement present on Alpha; dedicated mode-transition test pending |
| PH-015 | P1 | Archive-integrity defect | Full-app backup restore previously did not reject duplicate ZIP paths, case-folded collisions or file/directory prefix conflicts consistently. | Backup restore now tracks explicit paths, implicit directories, portable case-folded names and entry types before extraction. Dedicated encrypted-backup collision fixtures still need to run. | Implementation present on Alpha; dedicated backup tests pending |
| PH-016 | P1 | Android security hardening | Rust local loopback client-IP logic previously trusted `CF-Connecting-IP` from any loopback peer. | The engine now keys rate limits to the socket peer and ignores forwarding headers until an authenticated proxy contract exists; spoofing regression test is included and the Rust CI suite passed. | Implemented on Alpha; Rust CI passed; device test pending |
| PH-017 | P1 | Android build/release gap | Alpha's Android integration workflow previously did not trigger on pull requests, so quality checks could pass without compiling Android. | Android compile and emulator jobs now trigger on PRs; native Relay Rust tests and all-ABI native builds are being added to PR validation with read-only permissions. A deliberate broken-Kotlin negative test and required branch-protection checks remain to be demonstrated. | PR trigger merged; validation in progress |
| PH-018 | P1 | UI/security/reliability | MainActivity polling previously ran while the Activity was stopped, and service-status refresh overwrote transient operation feedback. Accessibility contrast/font-size and stronger rendered-content assertions remain. | Polling now starts/stops with the Activity lifecycle; persistent service status is displayed separately from transient action feedback. Remaining work: accessibility contrast/scale audit and semantic UI assertions that verify meaningful rendered content. | Partially implemented on engineering branch; Android compile/instrumentation pending |
| PH-019 | P1 | Data-integrity defect | Website file manager lists only the first 150 paths and stops at depth 8 while importer accepts larger/deeper trees. | Add paginated/searchable navigation and a visible completeness indicator; test sites exceeding 150 entries and depth 8. | v0.1.0 |
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
| PH-030 | P2 | Relay reliability | Existing RelayHub keeps active sessions and pending requests in memory; gateway restart loses them. | Use durable route/session recovery semantics and reconnect-safe request handling; test node restart with active and pending traffic. | v0.1.0 |
| PH-031 | P1 | Acceptance evidence gap | No recorded physical Android device and independent public-network acceptance proves the full v0.1.0 journey yet. | Complete the G0–G9 acceptance matrix on named hardware and external networks; record artifact hashes, DNS/TLS/content and management-route isolation evidence. | v0.1.0 |

## 8. Required implementation order

1. Close v0.1.0 public-ingress and static-site security blockers first: PH-011–PH-019 and PH-030–PH-031.
2. Complete v0.1.0 Android build, physical-device, recovery and external-network acceptance; keep G0–G9 open until evidence is recorded.
3. Complete v0.1.1 Workbench and optional runtime corrections: PH-023–PH-026 and PH-028.
4. Complete v0.1.2 fleet/control-plane and Linux-agent hardening: PH-020–PH-022, PH-027 and PH-029.
5. After v0.1.0 gates all pass and the owner approves the release, lock the exact commit/artifacts under the policy in [RELEASE_BASELINE_POLICY.md](RELEASE_BASELINE_POLICY.md). No later feature or ordinary fix is committed to that baseline.
