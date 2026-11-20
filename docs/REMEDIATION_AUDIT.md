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
2. **Public ingress acceptance is open.** A Caddy trusted-proxy policy and spoofing integration test have been added, but the first CI run exposed a Caddy client-IP placeholder defect. PR #103 corrected the placeholder; PR #99 mounts the shared policy in production Compose. A clean CI pass of the trusted/untrusted peer test and real-domain DNS/TLS/content acceptance are still required.
3. **Physical Android acceptance is open.** CI compile/package and API 26/33/35 emulator coverage do not prove OEM battery behavior, physical lifecycle, thermal/storage pressure or public connectivity.
4. **Debug APKs are not production release evidence.** They are not a signed production release or provenance attestation.
5. **Runtime companion is not a hostile multi-tenant OS sandbox.** Python and Node code run under the companion app UID.
6. **Relay live sessions are intentionally ephemeral.** Durable host identities, credential verifiers and route configuration permit reconnect; active SSE sockets and pending visitor requests are not replayed after gateway restart. A Rust restart regression test passed, but Android process-death and live-traffic recovery remain unproven.
7. **Near-limit backup/import acceptance remains open.** Constants and ZIP regression fixtures do not prove 500 MB boundary behavior, free-space preflight, peak memory or interrupted restore on physical devices.
8. **Android PR verification has improved but is not yet a release gate.** Kotlin and instrumentation-source compilation, native Relay Rust tests and all-ABI native builds are configured for PRs; API 26/33/35 emulator instrumentation runs on Alpha pushes. Required branch protection and a deliberate broken-Kotlin negative test remain unproven.

## 4. Verification plan — evidence snapshot (2026-11-20)

| Check | Latest observed evidence | State |
|---|---|---|
| Rust engine | Quality run #44687150775 on Alpha passed the Rust job; PR #104 adds further deny-list tests and awaits rerun. | Earlier pass; current security changes pending |
| Control plane / Caddy | Quality run #44687150775 failed specifically in the trusted-proxy integration test: Caddy forwarded the literal `{http.request.client_ip}` placeholder. PR #103 changes it to `{client_ip}`; PR #99 mounts the shared policy in production Compose. | Root cause corrected in source; clean rerun pending |
| Android Kotlin compile | Android compile passed on Alpha run #44684951777 before later lifecycle, file-manager and backup-test changes. | Earlier pass; current source compile pending |
| Android instrumentation | API 26/33/35 emulator jobs have not produced a complete successful matrix in the observed runs. PRs #95, #98 and #101 add further instrumentation coverage. | Pending |
| Native Relay Rust | Rust tests passed in PR #93 and PR #102 validation. | Passed for those commits; rerun with latest changes pending |
| Relay / Cloudflared Android ABIs | All declared native ABIs and Cloudflared architecture checks passed in run #44684951777; later all-ABI run is pending. | Earlier pass; latest rerun pending |
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

## 6. Audit limitations

This is a source/configuration audit with targeted cross-file tracing, not a formal penetration test, full compiler run or exhaustive manual line-by-line proof of every file. The starting Git tree was complete (not truncated), but audit depth varies by subsystem. No production credentials, deployment environment, physical device or live public Relay endpoint were exercised.


## 7. Follow-up deep-audit findings and release placement

The following issues were identified in the subsequent code/UI/build review. They are distinct from PH-001–PH-010 above. Unless a row explicitly says otherwise, these remain OPEN and must not be treated as fixed. The release assignment below is the planning target; the implementation must be rechecked against current source before a fix is committed.

| ID | Priority | Classification | Finding / affected area | Required correction and regression proof | Release |
|---|---|---|---|---|---|
| PH-011 | P0 | v0.1.0 public-ingress acceptance | Recheck of current Alpha found a native Android Relay implementation: Kotlin foreground service/JNI bridge, Rust visitor gateway, outbound authenticated SSE host sessions, short-lived single-use pairing invites with host-key proof, SQLite state, Android Keystore identity/config protection, DNS TXT validation and ACME TLS issuance/renewal. This is materially beyond the 2026-11-18 audit's manifest-scaffold finding, but does not prove production readiness. | Complete Android ABI/APK and service lifecycle tests; prove route/management isolation, pairing/revocation, TLS renewal, host reconnect and process/device restart recovery; then accept traffic from independent public networks on named physical devices. | Implementation present; end-to-end acceptance open |
| PH-012 | P0 | Security defect | Alpha previously allowed common credential files/directories into public website trees and the static server could serve them. | Shared Kotlin import/file/backup policy and Rust serving policy additionally reject cloud SDK credential stores (`.config/gcloud`, `.azure`), Terraform/Pulumi state, `credentials.json`, service-account variants, Firebase Admin SDK JSON, SSH identity files and Windows-style path separators. ZIP import and Rust serving regression fixtures cover these paths. | Expanded deny-list/tests merged in PR #104; Android/Rust CI pending |
| PH-013 | P0 | Public-ingress security defect | Caddy previously did not configure a narrowly scoped trusted-proxy chain for Cloudflared; visitor IP forwarding could collapse to the Cloudflared container address. | PR #97 adds a Docker spoofing test using the same shared policy as production; PR #99 mounts that policy into the Caddy service; PR #103 fixes the Caddy client-IP placeholder discovered by the first failing test. | Implementation merged; clean spoof-test rerun and live tunnel acceptance pending |
| PH-014 | P1 | Mode-enforcement defect | Server Only must not poll the fleet control plane, even when credentials remain configured, and local website hosting must continue. | PR #101 adds instrumentation that starts the website engine and agent in Command Center mode, switches to Server Only, invokes the service start path and asserts the agent stops while the website engine remains RUNNING. | Regression test merged to Alpha; Android CI pending |
| PH-015 | P1 | Archive-integrity defect | Full-app backup restore previously did not reject duplicate ZIP paths, case-folded collisions or file/directory prefix conflicts consistently. | Backup restore now validates explicit paths, implicit directories, portable case-folded names and entry types before extraction. PR #98 adds encrypted-backup fixtures for case-only collisions and file/directory prefix conflicts; duplicate exact central-directory entries remain a separate fixture opportunity. | Implementation and regression tests merged to Alpha; Android instrumentation pending |
| PH-016 | P1 | Android security hardening | Rust local loopback client-IP logic previously trusted `CF-Connecting-IP` from any loopback peer. | The engine now keys rate limits to the socket peer and ignores forwarding headers until an authenticated proxy contract exists; spoofing regression test is included and the Rust CI suite passed. | Implemented on Alpha; Rust CI passed; device test pending |
| PH-017 | P1 | Android build/release gap | Alpha's Android integration workflow previously did not trigger on pull requests, so quality checks could pass without compiling Android. | PRs compile Kotlin and instrumentation sources, run Relay Rust tests and build all declared native ABIs. API 26/33/35 emulator instrumentation runs on Alpha pushes only, not PRs. PR #102 also removes Relay CI write credentials and bot pushes and uses the committed lockfile with `--locked`. A deliberate broken-Kotlin negative test and required branch-protection checks remain to be demonstrated. | Read-only PR CI merged; latest emulator matrix and negative-check enforcement pending |
| PH-018 | P1 | UI/security/reliability | MainActivity polling previously continued while the Activity was stopped, and service-status refresh overwrote transient operation feedback. Accessibility contrast/font scaling still need a full audit. | PR #95 merged lifecycle-aware polling and separate persistent/transient status rendering, with instrumentation coverage for stop/resume and both messages. Remaining: accessibility contrast/scale audit and broader semantic UI assertions. | Merged to Alpha; Android instrumentation pending |
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
