# Runtime Companion — Existing Python and Node.js Provider

**STATUS: IN PROGRESS** The runtime companion is an existing, separately installable Android APK in the repository. It currently integrates CPython 3.11 and Node.js mobile 24.22.1. It is not part of the main Pocket Host app or Step 4, and it is not required for static HTML/CSS/JavaScript/TypeScript websites. The future goal is to split the two existing runtimes into independently downloadable packages and add further languages.

## Product role

The current companion APK contains both Python and Node.js integrations. They are implemented foundations, but they are not yet independently downloadable packages. The future Runtime Library will split them into separate signed/versioned artifacts and add 18 further language packages. The 20-language catalog and exact status split are defined in [FUTURE_RUNTIME_LIBRARY.md](FUTURE_RUNTIME_LIBRARY.md).

## Existing Android foundation

- Host application: `com.assayflux.pockethost`.
- Runtime companion package: `com.assayflux.pockethost.worker`.
- The companion is a separate Android package/provider boundary in the current repository.
- Python CPython 3.11 and Node.js mobile 24.22.1 integrations already exist in this companion.
- The current companion APK packages both runtimes together; independent Python and Node package artifacts are not yet delivered.
- Python and Node currently share the companion package UID; this is not per-project OS isolation.

## Future Runtime Manager requirements

- One independently selectable signed/versioned package per language.
- User explicitly chooses required languages and versions; no all-in-one runtime bundle.
- Verify digest, signature, ABI, license and compatibility before installation.
- Install, update and remove one runtime without changing the main app or other packs.
- Runtime health, process lifecycle, logs, metrics and resource budgets are independent.
- Main-app static ingress works with zero runtime packages installed.
- A runtime failure cannot stop static serving or unrelated runtime processes.

## Project isolation

Each website project/component requires a distinct OS-enforced execution identity where Android supports it, with scoped filesystem, secrets, network and resource capabilities. A package boundary or separate interpreter alone is not isolation. Until adversarial tests prove isolation and egress enforcement, untrusted customer code must not be treated as safely sandboxed.

## IPC contract

Operations remain typed, bounded and versioned: provider discovery, project staging, start/stop, health, metrics and shutdown. Use bounded descriptors/files, path validation, staged extraction and atomic activation. Binder death, package missing, version mismatch, service-start denial and runtime crash are distinct error states.

## Acceptance

Test independent package installation/removal, wrong signature, ABI mismatch, process crash, Binder death, project cross-access, network escape, resource exhaustion, static hosting during runtime outage, upgrade and rollback. Android package installation success alone does not prove runtime health.
