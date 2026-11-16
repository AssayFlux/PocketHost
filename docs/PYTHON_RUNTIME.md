# PYTHON RUNTIME — CPYTHON 3.11 / WSGI

**Source position:** CPython 3.11 / Chaquopy is integrated in the companion package.  
**Release position:** runtime end-to-end, API 26/33/35 and per-project isolation are not accepted. Trusted owner code only.

## 1. Application contract

A Python project must include a root `wsgi.py` exporting callable `application(environ, start_response)`.

```python
def application(environ, start_response):
    body = b"Hello from Pocket Host"
    start_response("200 OK", [
        ("Content-Type", "text/plain; charset=utf-8"),
        ("Content-Length", str(len(body))),
    ])
    return [body]
```

A minimal example is in `examples/python-wsgi/`.

## 2. Current source architecture

- CPython 3.11 and Chaquopy are packaged in `:runtime-worker`, not the host APK.
- Host validates a project root, creates a bounded archive and transfers a read-only FD over signature-protected Binder IPC.
- Companion extracts to companion-owned project storage.
- WSGI currently runs under companion application UID.
- Python listener is pinned to `127.0.0.1`; Rust is the intended ingress and reverse-proxy front door.
- Current WSGI worker supports configured thread bounds and multiple hostname routes.
- Dependencies are prepared/bundled; automatic pip install is not supported.

## 3. Import limits and operations

Archive entry, expanded file and total project size are bounded in source. Traversal and common credential paths are rejected/excluded. These controls are not a complete secret scanner. Never put credentials in project archives. Active roots must not be replaced while serving. Failed imports must preserve the previous active version.

## 4. Security limitations

The companion UID protects host-app-private files from ordinary project code, but Python and Node projects share the companion UID. Python multi-site projects may share one interpreter. Outbound network egress is not OS-enforced and loopback services may be reachable.

This is not a sandbox or multi-tenant runtime. Execute only code the owner controls and trusts. Do not host arbitrary visitor-submitted code.

## 5. Target isolated-cell contract

The target architecture is one Android isolated service/UID per project. The trusted broker transfers only project/scratch capabilities and a host-created visitor listener. Native policy must install before importing project code and fail closed. Python migration must use one interpreter per cell, with no project imports in the broker.

See `docs/SECURITY_GATE_2_EXECUTION_PLAN.md` and `docs/RUNTIME_COMPANION.md`.

## 6. Acceptance

- CPython starts and reports expected version on API 26/33/35.
- Real WSGI request routes visitor → Rust → Python → Rust → visitor.
- Multiple projects have distinct isolated UIDs and cannot read each other's files.
- Outbound socket/DNS/native networking bypasses fail.
- Worker stop, crash, rebind and restart work; static origin remains available.
- Memory/thread/request limits and error handling are measured.
- Independent security review accepts the cell boundary before untrusted code support.
