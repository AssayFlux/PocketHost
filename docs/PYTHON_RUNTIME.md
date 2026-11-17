# Python Runtime Provider — Pocket Host 0.1.0

## Placement

Python is an optional customer-language runtime installed on the mobile website-serving node only in 0.1.0. It is not installed on storage, traffic, build or other worker nodes. Build workers may build/test a project and return an immutable artifact; the mobile serving node verifies and runs it.

## Current Android provider

The Android provider currently integrates CPython 3.11 through Chaquopy in the runtime companion. Its WSGI adapter expects a root `wsgi.py` exporting callable `application(environ, start_response)`. Dependencies are bundled; arbitrary on-device package installation is not implied.

## Security status

The Android companion currently provides a host-versus-companion package boundary, not a distinct OS identity per project. Python projects may share the companion UID/interpreter. This is not a secure multi-tenant sandbox. Run only owner-controlled trusted code until per-project OS isolation and egress enforcement pass adversarial tests.

## Runtime pack contract

Declare Python version, Android ABI, bundled dependencies, license inventory, digest, signature, startup adapter, health check, memory/CPU limits and rollback version. Installation is optional and owner initiated.

## Acceptance

- Static site works when Python is absent or stopped.
- WSGI startup, request, timeout, cancellation and shutdown are bounded.
- Runtime crash does not stop Node or static serving.
- Test malicious archive, path traversal, dependency mismatch, project cross-access, network escape and memory exhaustion.
- Upgrade and rollback preserve project data.
