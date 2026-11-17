# Optional Runtime Provider — Future Runtime Library

**Product status: future add-on infrastructure, not part of Step 4 and not required by the main Pocket Host app.** The main app independently provides HTML/CSS static serving and is intended to bundle TypeScript compilation to browser JavaScript. This companion/provider boundary exists for separately downloaded server-side language runtimes only.

## Product role

The future Runtime Library will let users download only the language runtimes they choose. Each language is an independent signed/versioned package with its own installation, health, update, rollback and removal lifecycle. The 20-package catalog is defined in [FUTURE_RUNTIME_LIBRARY.md](FUTURE_RUNTIME_LIBRARY.md). Current manifests are planning placeholders, not downloadable artifacts.

## Existing Android foundation

- Host application: `com.assayflux.pockethost`.
- Runtime companion package: `com.assayflux.pockethost.worker`.
- The companion is a separate Android package/provider boundary in the current repository.
- Existing Python/Node foundation does not mean the future 20-package catalog is implemented or production-ready.
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
