# Runtime Companion — Existing Python and Node.js Provider

**Status:** IN PROGRESS  
**Release assignment:** v0.1.1  
**Package:** separate Android runtime-worker APK

## Product role

The companion supplies optional server-side language runtimes to the Android website-serving node. It is not the main Pocket Host app, not a website builder and not required for static hosting.

## Existing implementation

The current companion APK contains:
- CPython 3.11 through Chaquopy.
- Node.js mobile 24.22.1 through JNI/libnode.
- Host-side Binder service/client integration.
- Project transfer and lifecycle controls.
- Runtime status/metrics and trusted-code acknowledgement foundations.

The two runtimes currently ship together in one companion APK. They are not yet independent downloadable packages.

## Security boundary

Both runtimes share the companion application UID. This is not per-project OS isolation. Do not market the companion as a hardened hostile multi-tenant sandbox. Run trusted owner-controlled projects until filesystem, process, network egress and resource isolation tests pass.

## v0.1.1 Runtime Manager requirements

- User chooses only required runtime(s); no monolithic all-language bundle.
- Verify package signature, version, ABI and compatibility before activation.
- Install/update/remove without corrupting the main app or unrelated runtimes.
- Separate process lifecycle, health, logs, metrics and resource budgets.
- Typed, bounded Binder operations only.
- Static hosting works when the companion is absent, disabled or crashed.
- Runtime failure cannot stop static serving or sibling runtimes.

## Project transfer and IPC

Use bounded descriptors/archives, canonical app-private project roots, path validation, staged extraction and atomic activation. Distinguish package missing, version mismatch, Binder death, service-start denial, runtime crash and invalid project errors. Do not pass arbitrary shell strings or unrestricted file paths.

## Acceptance

Test companion install/removal, wrong signature, ABI mismatch, project transfer, path traversal, Binder death, process crash, resource exhaustion, cross-project access, egress, upgrade/rollback and static hosting during runtime outage. Android package installation alone is not runtime acceptance.
