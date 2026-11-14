# Embedded Python WSGI runtime

**Status:** implemented preview feature; emulator and physical-device validation are still in progress.

## What it runs

PocketHost embeds CPython 3.11 using Chaquopy. A selected project must contain a root-level `wsgi.py` with a callable named `application`:

```python
def application(environ, start_response):
    body = b"Hello from PocketHost"
    start_response(
        "200 OK",
        [
            ("Content-Type", "text/plain; charset=utf-8"),
            ("Content-Length", str(len(body))),
        ],
    )
    return [body]
```

A minimal starter is available in [examples/python-wsgi](../examples/python-wsgi/).

## Import and start

1. Select **Import Python WSGI project** in the Android app.
2. Choose the project directory through Android's Storage Access Framework.
3. Set a Python port different from the static HTTP port (default Python port: 8081).
4. Start the Python backend.
5. Open the displayed local preview URL.

The runtime is managed by the same user-started foreground hosting service as the static server. It is not started automatically at device boot.

## Current limits

- CPython 3.11 only.
- One WSGI application at a time.
- Root entrypoint must be named `wsgi.py` and export `application`.
- Maximum imported project size: 50 MiB.
- Maximum file count: 5,000.
- Maximum directory nesting: 32.
- Maximum concurrent WSGI worker threads: 16.
- Port range: 1024–65535.
- No automatic installation of `requirements.txt`.
- Third-party native extensions must be built for the matching Android ABI and are not guaranteed to work.
- No ASGI, WebSocket, background task supervisor or Node.js compatibility.
- No per-project CPU or memory sandbox/quotas.
- This is a preview backend, not a production multi-tenant execution environment.

## Security model

Python code executes inside the PocketHost application process with the app's Android UID and filesystem/network privileges. The runtime does **not** sandbox user code. Import only projects you own or trust.

The server binds to `127.0.0.1` by default. If the user explicitly enables LAN access, it binds to all local interfaces and is reachable by other devices on the same network. There is no visitor authentication, TLS termination or per-route access control in this backend. Do not enable LAN mode on untrusted networks and do not expose this backend to the public internet.

The project importer rejects common secret-bearing names such as `.env*`, `.ssh`, `.git`, private-key extensions and path escapes, but filename filtering is not a security sandbox. Do not store secrets in imported project folders.

## Validation

The CI pipeline runs Python syntax checks and host-side WSGI lifecycle tests. Android instrumentation smoke tests exercise the app on API 26, 33 and 35 emulators. Passing those tests does not replace physical-device tests for memory pressure, thermal behaviour, Wi-Fi changes, process lifecycle or OEM battery restrictions.

## Future work

Before calling Python production-ready, add explicit resource quotas, reliable request timeouts, safe dependency management, per-project isolation, structured access/error logs, device-level acceptance tests and a reviewed public-access security model.
