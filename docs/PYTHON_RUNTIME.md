# Python WSGI runtime

**Source integration:** CPython 3.11 / Chaquopy now runs in the separate-UID runtime companion package. Build and Android instrumentation for the migration are pending; do not treat source integration as a verified release.

## What it runs

A Python project must contain a root-level `wsgi.py` with a callable named `application`:

```python
def application(environ, start_response):
    body = b"Hello from Pocket Host"
    start_response("200 OK", [
        ("Content-Type", "text/plain; charset=utf-8"),
        ("Content-Length", str(len(body))),
    ])
    return [body]
```

A starter project is available in [examples/python-wsgi](../examples/python-wsgi/).

## Runtime architecture

- CPython 3.11 is packaged in `:runtime-worker`, not in the host APK.
- Host `PythonHostRuntime` validates the imported host-side project path, creates a bounded archive and transfers it through a read-only file descriptor over the authenticated Binder protocol.
- The companion extracts the project into its own private `files/projects` directory.
- WSGI code runs under the companion app UID, not the main Pocket Host UID.
- Python binds to `127.0.0.1`; Rust is the public/LAN listener and reverse-proxies registered hostnames to the Python port.
- Multi-site routing supports up to 100 hostnames in one WSGI worker.
- Worker concurrency is configurable from 2 to 16 threads.

## Current transfer bounds

- 10,000 archive entries.
- 64 MiB maximum extracted file size.
- 256 MiB maximum total extracted project size.
- Symlinked source files, traversal paths and common credential files/directories are rejected or excluded.
- Active project roots cannot be overwritten while WSGI is serving them.

Common secret files are excluded from transfer. This is not a complete secret scanner; do not put credentials in runtime projects. Dependency installation from `requirements.txt` is not performed automatically.

## Security model and limitations

The companion's separate Android UID prevents ordinary Python code from directly reading host-app-private files. It does **not** isolate one Python project from another project stored under the same companion UID. Outbound network access is not yet restricted by an enforceable firewall, and loopback services are reachable from the companion.

Therefore this remains a trusted-code runtime, not a multi-tenant sandbox. Execute only owner-controlled code. Do not host arbitrary third-party Python projects publicly.

The WSGI worker cap is an application-level concurrency bound, not a hard CPU or total-memory quota. Android may terminate the worker under memory pressure.

## Validation

The source test suite covers WSGI lifecycle, host routing, request body limits, metrics and worker bounds. Android instrumentation is being extended to assert that the actual Python process UID differs from the host app UID. CI for the companion migration must pass on API 26, 33 and 35 before this migration is considered verified.

Physical-device checks for process death, battery restrictions, memory pressure, thermal behaviour and network changes remain separate acceptance work.
