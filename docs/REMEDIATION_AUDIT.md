# Pocket Host — Remediation Audit Register

**Audit date:** 2026-11-18  
**Repository:** `AssayFlux/PocketHost`  
**Protected source branch:** `Alpha`  
**Remediation branch:** `audit/remediation-baseline-2026-11`  
**Starting source commit:** `2087938a35dc88ed7588d97ffed8bd65fe8b40a9`  
**Starting recursive tree:** 259 blobs, 72 directories; GitHub reported `truncated=false`.

This register separates confirmed defects from documentation drift, maintainability concerns and roadmap gaps. A finding marked **implemented / verification pending** means a source change and/or test was committed to this remediation branch, but the relevant test suite has not yet been run in a local Android/Rust/CI environment. It is not a claim of verified release readiness.

## 1. Severity-ranked findings

| ID | Severity | Area | Finding | Corrective action | Status |
|---|---|---|---|---|---|
| PH-001 | Critical | GitHub Actions security | `.github/workflows/quality.yml` granted `contents: write` to a PR job, ran PR-controlled Cargo build scripts, then exposed `GITHUB_TOKEN` to a later git push. A malicious same-repository PR could potentially plant a Git hook during build and execute it when the later push ran. This is a credible attack path, not evidence of exploitation. | Remove write permission and token-bearing push step. Run dependency checks with `--locked`; update lockfiles through a separately trusted workflow/change. | **Implemented; CI verification pending** |
| PH-002 | High | Relay transport correctness | Android accepted visitor bodies up to 8 MiB and JSON events up to 12 MiB, but bounded a single SSE line at 64 KiB. The gateway emits a request as one JSON `data:` line; Base64 expands an 8 MiB body to roughly 11.2 MiB before envelope overhead. Large POST/PUT requests could disconnect the stream and time out. | Set the line ceiling to the event ceiling plus the five-character `data:` prefix; add maximum-boundary framing coverage. | **Implemented; instrumentation pending** |
| PH-003 | Medium | Android network security | Relay and remote-control endpoint validators accepted cleartext HTTP for `localhost` (and Relay also `::1`), while Android network security configuration explicitly permits cleartext only for `127.0.0.1`. Configurations could be accepted but fail at connection time. | Restrict development HTTP endpoints to `127.0.0.1`, matching the manifest network policy; add rejection tests for localhost and IPv6 loopback aliases. Public endpoints remain HTTPS-only. | **Implemented; instrumentation pending** |
| PH-004 | Medium | ZIP import integrity | `SiteImporter` rejected traversal but did not reject duplicate archive paths, case-only path collisions or file/directory prefix conflicts. Extraction could overwrite an earlier entry or behave differently across filesystems. This is an archive ambiguity/integrity gap, not a demonstrated path-traversal exploit. | Track explicit entries, implicit directories, entry types and portable case-folded names; reject duplicates and conflicts before extraction. Add negative instrumentation cases. | **Implemented; instrumentation pending** |
| PH-005 | Medium | Capacity documentation | The Rust engine source sets `MAX_FILE_BYTES = 500_000_000`, while multiple documents called it a 50 MiB per-file response cap. Three separate limits were conflated: Rust serving (500,000,000 bytes), manual single-file import (50 MiB), and text editor (256 KiB). | Standardize the documentation: Rust per-file response = 500,000,000 bytes (500 MB decimal); manual file import = 50 MiB; editor = 256 KiB. ZIP and expanded-site import limits are each 500,000,000 bytes. | **Partially updated; document sweep pending** |
| PH-006 | Medium | Relay architecture documentation | Several documents still said Android RelayAgent reused RemoteControlAgent credentials and depended on remote-control configuration. Current source uses independent Android `RelayCredentialStore` credentials and HostingService starts Relay independently. | Correct architecture, public-ingress and control-plane descriptions. Keep the remaining gap explicit: standalone node executable and node-side credential issuer do not exist. | **Core documents updated; full sweep pending** |
| PH-007 | Medium | Repository baseline drift | Baseline documents referenced old commits and counts (252/257 files) while the current recursive tree contained 259 blobs and 72 directories. Some historical inventory sections were being presented as current. | Record source commit, tree counts, snapshot date and distinguish the historical detailed inventory from the current Git tree. | **Core baseline docs updated; final snapshot update pending** |
| PH-008 | Low | Backup roadmap drift | Roadmap claimed full-app backup capacity was below the 500 MB import limit. Current `ApplicationBackupManager` uses 500,000,000 bytes static-site root, 50 MiB Python, 100 MiB Node, 650,000,000 bytes total uncompressed and 680,000,000 bytes encrypted. | Replace obsolete mismatch statement with source-backed values; retain real-device near-limit and free-space acceptance as open. | **Roadmap updated; full sweep pending** |
| PH-009 | Low | Repository footprint | Nine design SVGs under `Assets/` occupy approximately 115.6 MB. Root `Pocket Host 1.svg` is byte-for-byte the same Git blob as `Assets/Pocket Host 1.svg` (14,353,338 bytes). | Keep assets unchanged in this pass because references/ownership were not exhaustively established. Record as an optimization candidate; check links before deleting or moving. | **Documented; no asset mutation** |
| PH-010 | Low | Android build efficiency | Gradle computed `selectedAbis` from `-PpockethostAbi`, but the Rust build task always compiled all three Android ABIs. CI's per-ABI APK loop therefore repeated unnecessary native builds. | Pass the selected ABI list into cargo-ndk. | **Implemented; Gradle build verification pending** |

## 2. Source-backed capacity contract

| Operation | Current source limit | Meaning |
|---|---:|---|
| ZIP input archive | 500,000,000 bytes | Compressed archive input cap |
| Expanded website contents | 500,000,000 bytes | Sum of imported file bytes |
| Files per site import | 5,000 | File count cap |
| Total archive entries | 10,000 | File + directory entries |
| Directory depth | 32 | Import path depth |
| Rust engine individual response file | 500,000,000 bytes | Static response ceiling; not the manual file-import limit |
| Manual single-file import | 50 MiB | `SiteFileManager.importFile` cap |
| Text editor | 256 KiB | Read/write editor cap |
| Full-app backup static site root | 500,000,000 bytes | Static tree quota |
| Full-app backup Python root | 50 MiB | Separate optional runtime quota |
| Full-app backup Node root | 100 MiB | Separate optional runtime quota |
| Full-app backup total uncompressed | 650,000,000 bytes | Archive-wide expanded cap |
| Full-app backup encrypted archive | 680,000,000 bytes | Output archive cap |
| Relay visitor request body | 8 MiB | Android request body bound |
| Relay JSON event | 12 MiB | Android assembled event bound |
| Relay SSE line | 12 MiB + 5 characters | Allows one `data:` prefix and the bounded JSON payload |

Decimal MB and binary MiB are intentionally distinguished. Do not describe 500,000,000 bytes as 500 MiB.

## 3. Architecture and roadmap gaps (not misclassified as defects)

1. **Standalone Relay Node is not implemented.** `relay-node/` contains a Cargo manifest scaffold only. The current visitor gateway remains in the combined Rust/Axum control-plane process, with PostgreSQL and in-memory RelayHub dependencies. Android host credentials are independent, but a standalone node-side issuer, durable node store, executable, installer, Termux service lifecycle and independent deployment are still missing.
2. **Public ingress acceptance is open.** No independently recorded real-domain DNS/TLS/content test from an external network is established by source inspection alone.
3. **Physical Android acceptance is open.** CI can compile/package debug APKs and has an API 26/33/35 emulator matrix, but this does not prove physical-device lifecycle, OEM battery behavior, storage pressure, thermal behavior or public connectivity.
4. **Release signing is not production evidence by itself.** Debug APK artifacts and matching debug signatures are not a signed production release or provenance attestation.
5. **Runtime companion isolation is bounded by Android package UID.** Python and Node customer code execute under the companion app's UID; this is not a hostile multi-tenant OS sandbox. Do not market it as one.
6. **Relay sessions are process-memory state.** Current RelayHub sessions and pending requests are lost on gateway restart; durable recovery belongs to the standalone Relay design.
7. **Large backup/import acceptance remains open.** Source constants do not prove 500 MB boundary behavior, low-storage preflight, peak memory or interrupted restore correctness on physical devices.

## 4. Verification plan and current evidence

| Check | Command / evidence | State |
|---|---|---|
| Rust engine tests | `cargo test --manifest-path rust-engine/Cargo.toml --locked` | Not run in this repository-edit session |
| Control-plane tests | `cargo test --manifest-path control-plane/Cargo.toml --locked` | Not run in this repository-edit session |
| Android Kotlin compile | `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin -PpockethostAbi=x86_64` | Not run in this repository-edit session |
| Android instrumentation | `./gradlew :app:connectedDebugAndroidTest -PpockethostAbi=x86_64` on API 26/33/35 | Not run in this repository-edit session |
| Python tests | `PYTHONPATH=agents/linux/src python -m unittest discover -s tests/python -v` | Not run in this repository-edit session |
| Console build | `cd web-console && npm ci && npm run build` | Not run in this repository-edit session |
| Node runtime smoke | `node tests/node/metrics-smoke.cjs` | Not run in this repository-edit session |
| Protocol contracts | `python scripts/validate-protocol.py` and protocol tests | Not run in this repository-edit session |
| Public Relay acceptance | Real DNS, TLS and visitor request from independent network | Not evidenced; remains open |
| Physical Android acceptance | Named physical devices and Android versions | Not evidenced; remains open |

## 5. Change log on the remediation branch

- `495cbccd0841c7dbe156da952e5afecf8e2b49ea`: removed the PR-time write-token/push path; bounded Relay SSE line framing against the maximum JSON event; added ZIP duplicate, case-fold and file/directory collision validation.
- `1d1592fd578d6111729df4905d804736ad7d1901`: added Relay SSE maximum-boundary and ZIP collision instrumentation tests; restricted cleartext development endpoint validation to 127.0.0.1; made cargo-ndk target only selected ABIs.
- Documentation and final baseline refresh are being applied after this register.

## 6. Audit limitations

This is a source/configuration audit with targeted cross-file tracing, not a formal penetration test, compiler run or exhaustive manual line-by-line proof of every tracked file. The starting Git tree was complete (not truncated), but the audit's depth varies by subsystem. All unverified claims remain labeled as such. No production credentials, deployment environment, physical device or live public Relay endpoint were exercised.
