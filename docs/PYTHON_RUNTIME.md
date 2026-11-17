# Python Runtime — Existing Companion Implementation

**STATUS: IN PROGRESS** Python is already implemented in the repository as an Android runtime companion integration. It is outside the main app's Step 4 scope, but it is not merely a future idea. The current implementation is substantial and has host-side tests; Android device acceptance, production hardening and independent downloadable package distribution remain open.

## Placement

Current placement: CPython is integrated in `runtime-worker/` through Chaquopy 3.11. It is delivered today as part of the separate runtime-worker companion APK, not yet as its own independently downloadable Python package. A future packaging task will split Python into its own signed/versioned add-on. It is intended for the mobile website-serving node, not storage, traffic or build workers.

## Implemented repository foundation

- CPython 3.11 is embedded through Chaquopy in the runtime companion.
- WSGI adapter loads a root `wsgi.py` exporting callable `application(environ, start_response)`.
- Loopback-only bind; Rust ingress remains the public/LAN front door.
- Multi-host routing supports up to 100 configured site routes.
- Request-body limit, socket timeout, bounded worker pool and request/byte/error metrics.
- Companion Binder API supports configure, start, multi-site start, stop, status and metrics.
- Project transfer uses bounded ZIP extraction, canonical path checks, staging and replacement recovery.
- Host-side tests cover WSGI serving/stopping, invalid ports, loopback binding, entrypoint validation, body limits, host routing, metrics, worker limits and failed imports. CI syntax-checks the embedded module and runs `tests/python/test_pockethost_runtime.py` with the Python suite.

This is an implemented integration foundation, not a fully accepted production runtime.

## Security status

The Android companion currently provides a host-versus-companion package boundary, not a distinct OS identity per project. Python projects may share the companion UID/interpreter. This is not a secure multi-tenant sandbox. Run only owner-controlled trusted code until per-project OS isolation and egress enforcement pass adversarial tests.

## Runtime pack contract

Declare Python version, Android ABI, bundled dependencies, license inventory, digest, signature, startup adapter, health check, memory/CPU limits and rollback version. Installation is optional and owner initiated.

## Remaining work / acceptance

- Verify on Android API/device matrix and physical devices; Python host-side tests do not replace Android instrumentation.
- Verify Binder lifecycle, process death/restart, foreground-service behavior and Android background restrictions.
- Independent Python package artifact, signing, download/install/update/rollback/removal are not implemented yet; current Python ships inside the companion APK.
- Per-project OS identity, filesystem isolation and egress enforcement remain incomplete; shared companion UID means trusted owner-controlled code only.
- Static site works when Python is absent or stopped.
- WSGI startup, request, timeout, cancellation and shutdown are bounded.
- Runtime crash does not stop Node or static serving.
- Test malicious archive, path traversal, dependency mismatch, project cross-access, network escape and memory exhaustion.
- Upgrade and rollback preserve project data.
