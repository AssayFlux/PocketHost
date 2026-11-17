# Node.js Runtime Provider — Pocket Host 0.1.0

## Component contract

A Node component declares Node version, architecture, entry point, dependency bundle, resource budget, health check, persistent data and required capabilities.

The Android provider currently integrates Node.js mobile 24.22.1 through a native JNI bridge in the runtime companion. Dependencies must be bundled. Desktop Node package managers, arbitrary shell commands, process managers and unrestricted child processes are not implied.

## Request path

Visitor -> selected ingress provider -> Rust website ingress -> site router -> authenticated component listener -> Node process -> Rust -> visitor.

The Node component does not own public ingress.

## Isolation

The Android Node process shares the runtime companion application UID with other runtime projects. A preload/listener restriction is not an OS-enforced network sandbox and does not block native/outbound socket bypasses. Run only owner-controlled trusted code until per-project OS isolation and egress enforcement pass adversarial tests.

Other host platforms require native Node provider adapters with exact OS/architecture compatibility profiles.

## Failure behavior

- A Node component crash affects only that component.
- Static hosting and unrelated components remain available.
- Resource budgets, health checks, restart backoff and crash-loop detection are enforced by the host supervisor.
- Deployments are staged and independently rollback-capable.
