# Android Runtime Provider — Pocket Host 0.1.0

## Responsibility

The Android runtime companion packages Python and Node runtime integrations separately from the main Android host application. It is an Android-specific provider adapter. It does not define the cross-platform runtime contract for Linux, Windows or macOS.

## Current package boundary

- Host package: `com.assayflux.pockethost`.
- Runtime companion: `com.assayflux.pockethost.worker`.
- Rust engine and local management API run in the host app.
- Python and Node runtime services run in the companion package.
- Host/companion communication uses signature-protected Binder IPC with caller and protocol checks.

The host and companion require a compatible signing identity for the protected IPC contract.

## Current execution and security boundary

Python and Node projects execute under the companion application's UID. Multiple projects within that package are not isolated from each other by the package boundary. Python/Node language-level restrictions and listener binding are not OS-enforced network isolation.

Therefore:
- Execute only owner-controlled trusted code.
- Do not run visitor-submitted or arbitrary third-party code.
- Do not claim multi-tenant sandboxing or per-project egress denial.
- Do not store credentials inside project roots.
- Static website serving remains independent of runtime availability.

## Broker contract

The companion is being treated as a trusted runtime provider/broker boundary. Its public operations must remain typed, bounded and versioned: provider discovery, project staging, start/stop, health, metrics and shutdown. Project data transfer uses bounded descriptors/files, path validation, staged extraction and atomic activation. The broker must not receive broad access to unrelated host secrets.

## Target execution-cell contract

Each project/component requires a distinct OS-enforced execution identity where the host OS supports it. A cell receives only its own project data, explicit persistent volumes, scoped secrets and approved visitor listener/service capabilities. Network and filesystem restrictions must be enforced below the language runtime and verified against native code.

## Failure behavior

- Companion crash must not stop static ingress or unrelated hosts.
- Runtime process crash must not stop sibling components.
- Binder death must be reported distinctly from package missing, version mismatch, service start denial and runtime crash.
- Restart uses bounded backoff and crash-loop detection.
- Deployment activation is staged and rollback-capable.
