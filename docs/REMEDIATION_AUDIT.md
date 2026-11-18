# Pocket Host — Remediation Audit Register

**Audit date:** 2026-11-18  
**Repository:** `AssayFlux/PocketHost`  
**Protected source branch:** `Alpha`  
**Remediation branch:** `audit/remediation-baseline-2026-11`  
**Starting source commit:** `2087938a35dc88ed7588d97ffed8bd65fe8b40a9`  
**Starting recursive tree:** 259 blobs, 72 directories; GitHub reported `truncated=false`.

This register separates confirmed defects from documentation drift, maintainability concerns and roadmap gaps. **Implemented / verification pending** means code changes and/or tests are committed on the remediation branch, but the relevant test suite has not yet run. It is not a release-readiness claim.

## 1. Severity-ranked findings

| ID | Severity | Area | Finding and evidence | Correction | State |
|---|---|---|---|---|---|
| PH-001 | Critical | CI security | `.github/workflows/quality.yml` granted `contents: write` to a PR job, ran PR-controlled Cargo build scripts, then exposed `GITHUB_TOKEN` to a later push. A malicious same-repository PR could potentially plant a Git hook during build and execute it when the later push ran. This is a credible attack path, not evidence of exploitation. | Remove write permission and token-bearing push step; use `cargo check --locked`. | Implemented; CI pending |
| PH-002 | High | Relay transport | Android allowed 8 MiB request bodies / 12 MiB JSON events but limited one SSE line to 64 KiB. The gateway emits one JSON `data:` line; Base64 expands 8 MiB to about 11.2 MiB before envelope overhead. Larger visitor requests could disconnect and time out. | Set line ceiling to 12 MiB plus the 5-character `data:` prefix; add max-boundary regression coverage. | Implemented; instrumentation pending |
| PH-003 | Medium | Android networking | Endpoint validators accepted cleartext `localhost` (Relay also `::1`) while network security XML permits only `127.0.0.1`. Accepted settings could fail at connection time. | Restrict development HTTP endpoints to `127.0.0.1`; test rejected aliases. Public endpoints remain HTTPS-only. | Implemented; instrumentation pending |
| PH-004 | Medium | ZIP import | `SiteImporter` checked traversal but did not reject duplicate archive paths, case-only collisions or file/directory prefix conflicts. This is an archive integrity/ambiguity gap, not a demonstrated traversal exploit. | Track explicit paths, implicit directories, entry types and case-folded portable names; reject collisions before extraction. Add negative tests. | Implemented; instrumentation pending |
| PH-005 | Medium | Capacity docs | Rust source sets `MAX_FILE_BYTES = 500_000_000`; several docs said 50 MiB. The separate limits are Rust serving 500,000,000 bytes, manual single-file import 50 MiB, and text editor 256 KiB. | Standardize all docs and keep these operations distinct. ZIP input and expanded site content are each 500,000,000 bytes. | Core docs updated; historical sections labeled |
| PH-006 | Medium | Relay docs | Some docs said Android RelayAgent reused RemoteControlAgent credentials and required remote-control config. Source now uses dedicated `RelayCredentialStore`; HostingService starts Relay independently. | Correct architecture and public-ingress docs. Keep missing standalone gateway/node-side issuer explicit. | Core docs updated; sweep pending |
| PH-007 | Medium | Baseline drift | Baselines cited old commits and 252/257 files while current tree contained 259 blobs and 72 directories; historical inventory was described as current. | Record source commit/tree counts and clearly label the older path inventory historical. | Core docs updated; snapshot recorded |
| PH-008 | Low | Backup roadmap | Roadmap claimed backup capacity was below the 500 MB import limit. Source has static 500,000,000 bytes, Python 50 MiB, Node 100 MiB, 650,000,000 bytes total uncompressed and 680,000,000 bytes encrypted. | Replace obsolete mismatch claim; retain near-limit/device testing as open. | Roadmap updated; sweep pending |
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
| Relay body / event / SSE line | 8 MiB / 12 MiB / 12 MiB + 5 chars | Request body, JSON payload, `data:` framing |

MB is decimal; MiB is binary. Do not call 500,000,000 bytes 500 MiB.

## 3. Important gaps, not misclassified defects

1. **Standalone Relay Node is not implemented.** `relay-node/` contains a Cargo manifest scaffold only. The visitor gateway remains inside the combined Rust/Axum control-plane process, with PostgreSQL and in-memory RelayHub dependencies. Android host credentials are independent; node-side credential issuer, durable standalone store, executable, installer and Termux service lifecycle remain missing.
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
- Baseline snapshot commit: `c00eac2735ca353b0ef965fd5a40ece9fbcc7085` (260 blobs, 72 directories; recursive tree not truncated). Subsequent documentation-only commits may update the baseline record without changing those source counts.

## 6. Audit limitations

This is a source/configuration audit with targeted cross-file tracing, not a formal penetration test, full compiler run or exhaustive manual line-by-line proof of every file. The starting Git tree was complete (not truncated), but audit depth varies by subsystem. No production credentials, deployment environment, physical device or live public Relay endpoint were exercised.
