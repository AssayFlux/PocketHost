# Android Runtime Provider — Pocket Host 0.1.0

## Product role

The Android runtime companion provides the language runtime execution provider for the mobile website-serving node. In 0.1.0, customer-selected Python, Node and future language runtime packs execute only on this designated mobile server. Other enrolled devices perform assigned infrastructure jobs; they do not become arbitrary customer-language runtime hosts.

## Current Android foundation

- Host application: `com.assayflux.pockethost`.
- Runtime companion: `com.assayflux.pockethost.worker`.
- The companion is a separate Android package and runtime provider boundary.
- Python and Node currently share the companion package UID. This is not per-project OS isolation.

## Runtime Manager requirements

- Each language is an independently selectable signed/versioned pack.
- User explicitly chooses required languages and versions.
- Verify digest, signature, ABI, license and compatibility before installation.
- Install, update and remove one runtime without changing other packs.
- Runtime health, process lifecycle, logs, metrics and resource budgets are independent.
- Static ingress works with no runtime installed.
- A runtime failure cannot stop static serving or unrelated runtime processes.

## Project isolation

Each website project/component requires a distinct OS-enforced execution identity where Android supports it, with scoped filesystem, secrets, network and resource capabilities. A package boundary or separate interpreter alone is not isolation. Until adversarial tests prove isolation and egress enforcement, untrusted customer code must not be treated as safely sandboxed.

## IPC contract

Operations remain typed, bounded and versioned: provider discovery, project staging, start/stop, health, metrics and shutdown. Use bounded descriptors/files, path validation, staged extraction and atomic activation. Binder death, package missing, version mismatch, service-start denial and runtime crash are distinct error states.

## Acceptance

Test independent pack installation/removal, wrong signature, ABI mismatch, process crash, Binder death, project cross-access, network escape, resource exhaustion, static hosting during runtime outage, upgrade and rollback. Android package installation success alone does not prove runtime health.
