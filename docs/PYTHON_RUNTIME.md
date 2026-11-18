# Python Runtime — Existing Companion Implementation

**Status:** IN PROGRESS  
**Release assignment:** v0.1.1 — Workbench, Runtime & Linux Relay Installation  
**Location:** runtime-worker/

## 1. Placement

Python is an existing Android runtime integration, not a greenfield idea and not part of the v0.1.0 static hosting dependency. CPython 3.11 is integrated through Chaquopy inside the separate runtime-worker companion APK. The companion currently ships Python and Node together.

## 2. Implemented repository foundation

The current source includes:
- CPython 3.11 embedded through Chaquopy.
- Python WSGI adapter and loopback serving.
- Multi-host routing.
- Bounded workers, request body and timeout behavior.
- Runtime lifecycle, metrics and host-side IPC integration.
- Host tests for start/stop, invalid ports, loopback binding, entrypoint validation, body limits, host routing, metrics, worker limits and failed imports.

These are implementation foundations. They do not prove Android device acceptance or secure execution of hostile code.

## 3. Security status

Python and Node currently share the companion application UID. This package boundary is not an OS-enforced per-project sandbox. Do not run untrusted customer code or claim multi-tenant isolation until filesystem, process, network egress and resource isolation pass adversarial tests.

## 4. v0.1.1 completion requirements

- Verify APK installation, signature and ABI behavior on named Android devices.
- Verify typed Binder IPC, project staging and path confinement.
- Verify start/stop, process crash, Binder death, host restart and recovery.
- Enforce CPU, memory, request, worker, disk and timeout budgets.
- Restrict filesystem access to project-scoped data.
- Prove outbound network policy or explicitly document its limitations.
- Ensure Python failure cannot stop static hosting or Node.
- Define independent package distribution only after package/signature/IPC compatibility is tested.

## 5. Runtime pack contract

Each project declares Python version, entrypoint, host/port policy, filesystem namespace, environment-secret references, resource budget, health check and compatible runtime package. Secrets are injected through scoped secure channels and never copied into source archives or logs.

## 6. Acceptance

Run the Python test suite and Android instrumentation on physical devices. Verify WSGI response, invalid entrypoint, invalid port, request limits, concurrency, timeout, crash/restart, isolation and static-host independence. Record all results in v0.1.1 G0–G9.
