# Pocket Host — Mandatory Release Gates G0–G9

**Permanent rule:** Every Pocket Host version, without exception, must define, verify and close exactly ten release gates: G0, G1, G2, G3, G4, G5, G6, G7, G8 and G9. This applies to v0.1.0, v0.1.1, v0.2.0 and all future versions.

## Gate states and proof standard

Allowed states: NOT_STARTED, IN_PROGRESS, BLOCKED, FAIL, PASS.

A gate is PASS only when its implementation is complete, the required tests have actually run on the declared target, the evidence is recorded and a reviewer can reproduce the result from the source commit and artifact. A design document, source file, UI screenshot, compile-only job or simulator-only run cannot close a physical-device or external-service gate.

A change that invalidates evidence reopens the affected gate and any dependent gate. No gate may be skipped, silently waived, merged with another gate or marked PASS to meet a date.

## Common gate definitions

| Gate | Name | Mandatory meaning | Minimum evidence |
|---|---|---|---|
| **G0** | Scope & contract | Version goal, user journeys, compatibility, limits and in/out-of-scope are frozen | Approved scope and traceable acceptance requirements |
| **G1** | Architecture & security | Architecture, data boundaries, permissions, threat model and failure behavior are reviewed | Design review, threat/risk record and security requirements |
| **G2** | Implementation | All in-scope functionality is implemented and integrated; no release-critical stubs or hidden manual steps | Source commit, feature checklist and code review |
| **G3** | Automated verification | Relevant unit, integration, contract, static-analysis and regression suites pass | CI run URLs, test reports and critical-path coverage |
| **G4** | Build & artifact integrity | Reproducible release artifacts build for declared targets and pass signature/hash/package checks | Workflow run, artifact SHA-256, package inspection, SBOM/license record |
| **G5** | Install & compatibility | Fresh install, upgrade/migration, permissions and uninstall/recovery work on the declared support matrix | Device/OS/ABI matrix with install and upgrade logs |
| **G6** | Functional acceptance | End-to-end user journeys pass on actual target hardware/services | Step-by-step test record, screenshots/logs and observed result |
| **G7** | Reliability & recovery | Lifecycle, process death, network loss, storage pressure, quotas, performance and recovery meet limits | Soak/fault/restart/restore reports and resource measurements |
| **G8** | Security, privacy & UX | Threat tests, access controls, privacy, accessibility and established design-system behavior pass | Security report, permission review, accessibility and UI regression evidence |
| **G9** | Release candidate & documentation | Release notes, known issues, support matrix, runbooks, evidence bundle and owner sign-off are complete | RC artifact, complete gate ledger, docs links and explicit approval |

## Version-specific acceptance matrix

| Gate | v0.1.0 — First Hosting | v0.1.1 — Workbench & Runtime Development | v0.2.0 — Connected Device Fabric |
|---|---|---|---|
| **G0** | Freeze Android static-host scope, 500 MB ZIP/extracted caps, quotas, Android/ABI matrix and local/LAN journeys | Freeze Workbench, TypeScript, publishing, Python/Node scope and supported project/runtime matrix | Freeze roles, enrollment, first agent platform, ingress providers and hardware matrix |
| **G1** | Review Android service lifecycle, archive/path limits, LAN exposure, backup encryption and permissions | Review workspace/production isolation, preview origins, build sandbox, runtime UID, secrets and rollback | Review identity, key custody, authenticated transport, least privilege, control-plane and public-route isolation |
| **G2** | Complete folder/ZIP import, safe activation, static serving, local preview, LAN binding, service lifecycle, backup/restore and diagnostics | Complete Explorer/editor, workspace, preview, diffs/commits, TypeScript build, staged publish/rollback and runtime integration | Complete owner-approved enrollment/revocation, agent lifecycle, bounded jobs, control plane, audit and ingress adapters |
| **G3** | Pass importer/archive/path tests, Rust HTTP/MIME tests, Android service/UI tests, backup/restore and regression CI | Pass Workbench isolation/state tests, TypeScript compiler tests, publish-failure/rollback tests and Python/Node suites | Pass protocol/schema, crypto-negative, agent/job, API/DB, provider/route and revocation tests |
| **G4** | Build main APK for declared ABIs; inspect package, native libraries, signatures and hashes | Build main APK and optional companion packages; verify package separation, signatures, runtime assets and provenance | Build signed agent/control-plane/console/provider artifacts for each declared platform and verify SBOM/signatures |
| **G5** | Fresh install and upgrade on named Android versions; permissions, notification, storage and ABI compatibility | Upgrade from v0.1.0 without losing sites; install/remove optional runtimes; migrate workspace/commit/release data | Install/enroll/revoke/upgrade/uninstall each supported agent and verify reconnect/key rotation |
| **G6** | Import 500 MB-class sites, open locally and from second same-Wi-Fi device, verify assets/navigation and host controls | Edit, preview privately, diff, commit, publish with approval, rollback and run supported Python/Node fixtures | Enroll a real second device, assign a role, run/verify/revoke a job and reach an enabled public ingress |
| **G7** | Test backgrounding, screen-off, network changes, process/device restart, low storage, interrupted import/restore and sustained serving | Test large workspaces, concurrent edits, preview/build timeout, process death during publish, rollback, runtime crash and quotas | Test phone/control-plane/worker/network/storage/provider outages, lease expiry, replay, queue recovery and backpressure |
| **G8** | Verify archive traversal/symlink defenses, sensitive-file exclusions, no unintended public exposure, Android settings and unchanged design | Verify cross-site isolation, preview secrecy, build egress/resource limits, runtime isolation claims, secret handling and publish authorization | Verify cryptographic trust, role escalation resistance, device isolation, public-route separation, audit and revocation |
| **G9** | Publish v0.1.0 RC, user guide, supported-device matrix, 500 MB limits, known issues, evidence and owner approval | Publish v0.1.1 RC, Workbench/runtime docs, migration/rollback guide, package hashes and owner approval | Publish v0.2.0 RC, agent/provider matrix, security/recovery runbooks, provenance/SBOM and owner approval |

## Required gate ledger

Each release maintains one record for every gate with these fields:

| Field | Required value |
|---|---|
| Version / gate | Exact version and G0–G9 identifier |
| State | One of the five allowed states |
| Owner | Responsible engineer/reviewer |
| Criteria | Exact acceptance criteria |
| Source | Commit SHA |
| Build | Workflow/run URL and artifact name |
| Target | Device/OS/ABI or service environment |
| Test | Command or manual procedure |
| Evidence | Logs, reports, screenshots or measurements |
| Result | PASS/FAIL, date and reviewer |

**Release stop rule:** if any gate is not PASS, the version is not releasable. Fix the blocker, rerun the affected tests and update evidence. Never change a gate state merely to meet a schedule.
