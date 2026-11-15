"""PocketHost embedded Python WSGI host.

A project must provide wsgi.py with a callable named application.
This runtime intentionally binds to loopback unless LAN access is explicitly enabled.
Imported project code is untrusted executable code and runs with the app's privileges.
"""
from __future__ import annotations

import importlib.util
import json
import os
import sys
import threading
import time
from socketserver import ThreadingMixIn
from wsgiref.simple_server import WSGIRequestHandler, WSGIServer, make_server

_lock = threading.RLock()
_server = None
_thread = None
_module_name = None
_project_root = None
_metrics_lock = threading.Lock()
_metrics = {"requests": 0, "bytes_sent": 0, "errors": 0, "active": 0, "peak_active": 0, "last_request_ms": 0}
MAX_REQUEST_BODY_BYTES = 10 * 1024 * 1024
SOCKET_TIMEOUT_SECONDS = 15


class _BoundedThreadingWSGIServer(ThreadingMixIn, WSGIServer):
    daemon_threads = True
    allow_reuse_address = True
    request_queue_size = 32
    max_workers = 16

    def get_request(self):
        request, client_address = super().get_request()
        request.settimeout(SOCKET_TIMEOUT_SECONDS)
        return request, client_address

    def __init__(self, *args, **kwargs):
        self._slots = threading.BoundedSemaphore(self.max_workers)
        super().__init__(*args, **kwargs)

    def process_request(self, request, client_address):
        if not self._slots.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, client_address)
        except BaseException:
            self._slots.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self._slots.release()


class _RequestBodyLimit:
    def __init__(self, application):
        self.application = application

    def __call__(self, environ, start_response):
        raw_length = environ.get("CONTENT_LENGTH", "")
        try:
            length = int(raw_length) if raw_length else 0
        except (TypeError, ValueError):
            start_response("400 Bad Request", [("Content-Length", "0")])
            return [b""]
        if length < 0:
            start_response("400 Bad Request", [("Content-Length", "0")])
            return [b""]
        if length > MAX_REQUEST_BODY_BYTES:
            start_response("413 Payload Too Large", [("Content-Length", "0")])
            return [b""]
        return self.application(environ, start_response)


class _MetricsMiddleware:
    def __init__(self, application):
        self.application = application

    def __call__(self, environ, start_response):
        started = time.monotonic()
        with _metrics_lock:
            _metrics["requests"] += 1
            _metrics["active"] += 1
            _metrics["peak_active"] = max(_metrics["peak_active"], _metrics["active"])

        def finish(iterable, failed=False):
            def counted():
                try:
                    for chunk in iterable:
                        if chunk:
                            with _metrics_lock:
                                _metrics["bytes_sent"] += len(chunk)
                        yield chunk
                except BaseException:
                    with _metrics_lock:
                        _metrics["errors"] += 1
                    raise
                finally:
                    close = getattr(iterable, "close", None)
                    if close:
                        close()
                    with _metrics_lock:
                        if failed:
                            _metrics["errors"] += 1
                        _metrics["active"] = max(0, _metrics["active"] - 1)
                        _metrics["last_request_ms"] = int((time.monotonic() - started) * 1000)
            return counted()

        try:
            return finish(self.application(environ, start_response))
        except BaseException:
            with _metrics_lock:
                _metrics["errors"] += 1
                _metrics["active"] = max(0, _metrics["active"] - 1)
                _metrics["last_request_ms"] = int((time.monotonic() - started) * 1000)
            raise


class _QuietRequestHandler(WSGIRequestHandler):
    server_version = "PocketHost"
    sys_version = ""

    def log_message(self, format, *args):
        # Request logging is handled by PocketHost's Android diagnostics layer.
        return


def _load_application(project_root: str):
    root = os.path.realpath(project_root)
    entry = os.path.join(root, "wsgi.py")
    if not os.path.isdir(root):
        raise ValueError("Python project directory does not exist.")
    if not os.path.isfile(entry):
        raise ValueError("Python project must contain wsgi.py.")
    added_path = root not in sys.path
    if added_path:
        sys.path.insert(0, root)

    name = "_pockethost_user_wsgi"
    previous = sys.modules.pop(name, None)
    spec = importlib.util.spec_from_file_location(name, entry)
    if spec is None or spec.loader is None:
        if added_path and root in sys.path:
            sys.path.remove(root)
        raise ImportError("Unable to load wsgi.py.")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    try:
        spec.loader.exec_module(module)
        application = getattr(module, "application", None)
        if not callable(application):
            raise TypeError("wsgi.py must expose a callable named application.")
        return _RequestBodyLimit(application), name
    except BaseException:
        sys.modules.pop(name, None)
        if previous is not None:
            sys.modules[name] = previous
        if added_path and root in sys.path:
            sys.path.remove(root)
        raise


def start(project_root: str, port: int, allow_lan: bool = False) -> str:
    global _server, _thread, _module_name, _project_root
    port = int(port)
    if port < 1024 or port > 65535:
        raise ValueError("Python port must be between 1024 and 65535.")
    with _lock:
        if _server is not None:
            return "ALREADY_RUNNING"
        application, module_name = _load_application(project_root)
        application = _MetricsMiddleware(application)
        host = "0.0.0.0" if bool(allow_lan) else "127.0.0.1"
        try:
            server = make_server(
                host,
                port,
                application,
                server_class=_BoundedThreadingWSGIServer,
                handler_class=_QuietRequestHandler,
            )
        except BaseException:
            sys.modules.pop(module_name, None)
            root = os.path.realpath(project_root)
            if root in sys.path:
                sys.path.remove(root)
            raise
        thread = threading.Thread(
            target=server.serve_forever,
            kwargs={"poll_interval": 0.25},
            name="PocketHost-Python-WSGI",
            daemon=True,
        )
        try:
            thread.start()
        except BaseException:
            server.server_close()
            sys.modules.pop(module_name, None)
            root = os.path.realpath(project_root)
            if root in sys.path:
                sys.path.remove(root)
            raise
        _server, _thread, _module_name = server, thread, module_name
        _project_root = os.path.realpath(project_root)
        return "RUNNING"


def stop() -> str:
    global _server, _thread, _module_name, _project_root
    with _lock:
        server, thread, module_name, project_root = _server, _thread, _module_name, _project_root
        if server is None:
            return "STOPPED"
        _server = _thread = _module_name = _project_root = None
    server.shutdown()
    server.server_close()
    if thread is not None and thread is not threading.current_thread():
        thread.join(timeout=3.0)
    if module_name:
        sys.modules.pop(module_name, None)
    if project_root in sys.path:
        sys.path.remove(project_root)
    return "STOPPED"


def status() -> str:
    with _lock:
        return "RUNNING" if _server is not None and _thread is not None and _thread.is_alive() else "STOPPED"



def metrics() -> dict:
    """Return process-local WSGI counters, not OS-enforced resource limits."""
    with _metrics_lock:
        return dict(_metrics)


def metrics_json() -> str:
    return json.dumps(metrics(), separators=(",", ":"))
